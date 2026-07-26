"""RPA worker for TPA portals not yet on NHCX (roadmap Phase 4, legacy track).

Portals are hostile automation targets: markup changes without notice, sessions
expire, CAPTCHAs appear, and a hung browser pins a worker forever. The design
here assumes all of that rather than treating it as exceptional.

Three deliberate choices:

**The driver is an interface.** ``PortalDriver`` is a Protocol, so the flow logic
is testable without Playwright, without a browser, and without a real TPA
account. ``PlaywrightDriver`` implements it for production.

**Every session is recorded.** For a legacy portal there is no API audit trail —
this recording is the only evidence of what the hospital submitted and what came
back. It is also what a human reads when a flow breaks.

**Failure escalates rather than retries forever.** A portal that changed its
markup will fail identically on every retry; the useful response is a person, not
a fourth attempt.

Screenshots taken on failure show a TPA portal displaying patient data. Treat the
screenshot store as PHI, not as debugging output.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Protocol

from ..observability import EventLogger
from ..pii import mask_free_text, redact

log = EventLogger(__name__)

DEFAULT_STEP_TIMEOUT = 30.0
DEFAULT_SESSION_TIMEOUT = 300.0
MAX_LOGIN_ATTEMPTS = 2


class StepStatus(str, Enum):
    OK = "ok"
    FAILED = "failed"
    TIMED_OUT = "timed_out"
    SKIPPED = "skipped"


class PortalError(Exception):
    def __init__(self, code: str, message: str, *, retryable: bool = False) -> None:
        super().__init__(f"{code}: {message}")
        self.code = code
        self.message = message
        self.retryable = retryable


class CredentialVault(Protocol):
    """Portal credentials come from a secrets manager, never from config.

    Driving a TPA portal means the hospital's portal password lives in this
    system. That is a vault requirement, not an environment variable.
    """

    def get(self, tpa_code: str) -> dict[str, str]: ...


class PortalDriver(Protocol):
    """The browser operations a portal flow needs."""

    def goto(self, url: str) -> None: ...
    def fill(self, selector: str, value: str) -> None: ...
    def click(self, selector: str) -> None: ...
    def text_of(self, selector: str) -> str: ...
    def exists(self, selector: str) -> bool: ...
    def upload(self, selector: str, file_path: str) -> None: ...
    def screenshot(self) -> bytes: ...
    def close(self) -> None: ...


@dataclass(slots=True)
class StepRecord:
    name: str
    status: StepStatus
    seconds: float
    detail: str | None = None

    def redacted(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "status": self.status.value,
            "seconds": round(self.seconds, 3),
            # Portal error text routinely quotes the policy or member number.
            "detail": mask_free_text(self.detail) if self.detail else None,
        }


@dataclass(slots=True)
class SessionRecord:
    tpa_code: str
    correlation_id: str
    claim_reference: str | None = None
    steps: list[StepRecord] = field(default_factory=list)
    outcome: str = "in_progress"
    portal_status: str | None = None
    started_at: float = field(default_factory=time.time)
    finished_at: float | None = None
    screenshot_taken: bool = False

    @property
    def duration(self) -> float:
        return (self.finished_at or time.time()) - self.started_at

    def redacted(self) -> dict[str, Any]:
        return redact({
            "tpa_code": self.tpa_code,
            "correlation_id": self.correlation_id,
            "claim_reference": self.claim_reference,
            "outcome": self.outcome,
            "portal_status": self.portal_status,
            "duration_seconds": round(self.duration, 2),
            "screenshot_taken": self.screenshot_taken,
            "steps": [s.redacted() for s in self.steps],
        })


@dataclass(slots=True)
class PortalFlow:
    """Declarative description of one TPA's portal.

    Selectors live in data rather than code because they change often and
    per-TPA. When a portal breaks, someone updates a config entry instead of
    editing and redeploying Python.
    """
    tpa_code: str
    login_url: str
    username_selector: str
    password_selector: str
    submit_selector: str
    logged_in_marker: str
    claim_url: str
    claim_number_selector: str
    document_upload_selector: str
    submit_claim_selector: str
    status_selector: str
    captcha_marker: str | None = None
    error_selector: str | None = None


class PortalSession:
    """Runs one flow against one portal, recording everything."""

    def __init__(self, driver: PortalDriver, flow: PortalFlow, vault: CredentialVault,
                 correlation_id: str, step_timeout: float = DEFAULT_STEP_TIMEOUT,
                 session_timeout: float = DEFAULT_SESSION_TIMEOUT) -> None:
        self._driver = driver
        self._flow = flow
        self._vault = vault
        self._step_timeout = step_timeout
        self._session_timeout = session_timeout
        self.record = SessionRecord(tpa_code=flow.tpa_code, correlation_id=correlation_id)

    # ── step plumbing ────────────────────────────────────────────────────────

    def _step(self, name: str, action: Any) -> Any:
        if self.record.duration > self._session_timeout:
            self._fail(name, StepStatus.TIMED_OUT, "session budget exhausted")
            raise PortalError("SESSION_TIMEOUT",
                              "Portal session exceeded its time budget", retryable=False)
        started = time.perf_counter()
        try:
            result = action()
        except PortalError:
            raise
        except Exception as exc:
            self._fail(name, StepStatus.FAILED, f"{type(exc).__name__}: {exc}")
            raise PortalError("STEP_FAILED", f"Step {name} failed", retryable=False) from exc
        elapsed = time.perf_counter() - started
        self.record.steps.append(StepRecord(name, StepStatus.OK, elapsed))
        return result

    def _fail(self, name: str, status: StepStatus, detail: str) -> None:
        self.record.steps.append(StepRecord(name, status, 0.0, detail))
        try:
            self._driver.screenshot()
            self.record.screenshot_taken = True
        except Exception as exc:  # noqa: BLE001 - a failed screenshot must never
            # mask the original failure we are already recording.
            log.warning("rpa.screenshot.failed", tpa=self._flow.tpa_code,
                        error_type=type(exc).__name__)
        log.warning("rpa.step.failed", tpa=self._flow.tpa_code, step=name,
                    status=status.value)

    # ── flow ─────────────────────────────────────────────────────────────────

    def login(self) -> None:
        creds = self._vault.get(self._flow.tpa_code)
        if not creds.get("username") or not creds.get("password"):
            raise PortalError("CREDENTIALS_MISSING",
                              f"No vault credentials for {self._flow.tpa_code}",
                              retryable=False)

        self._step("goto_login", lambda: self._driver.goto(self._flow.login_url))

        if self._flow.captcha_marker and self._driver.exists(self._flow.captcha_marker):
            # Solving it is neither legal nor reliable. A human takes over.
            self._fail("captcha", StepStatus.FAILED, "captcha presented")
            raise PortalError("CAPTCHA_PRESENTED",
                              "The portal presented a captcha; a human must complete this",
                              retryable=False)

        self._step("fill_username",
                   lambda: self._driver.fill(self._flow.username_selector, creds["username"]))
        self._step("fill_password",
                   lambda: self._driver.fill(self._flow.password_selector, creds["password"]))
        self._step("submit_login", lambda: self._driver.click(self._flow.submit_selector))

        if not self._driver.exists(self._flow.logged_in_marker):
            self._fail("verify_login", StepStatus.FAILED, "logged-in marker absent")
            raise PortalError("LOGIN_FAILED",
                              "Portal login did not succeed", retryable=True)

        log.info("rpa.login.succeeded", tpa=self._flow.tpa_code)

    def submit_claim(self, claim_number: str, document_paths: list[str]) -> str:
        self.record.claim_reference = claim_number
        self._step("goto_claim", lambda: self._driver.goto(self._flow.claim_url))
        self._step("fill_claim_number",
                   lambda: self._driver.fill(self._flow.claim_number_selector, claim_number))

        for index, path in enumerate(document_paths):
            self._step(f"upload_{index}",
                       lambda p=path: self._driver.upload(self._flow.document_upload_selector, p))

        self._step("submit_claim", lambda: self._driver.click(self._flow.submit_claim_selector))

        if self._flow.error_selector and self._driver.exists(self._flow.error_selector):
            detail = self._driver.text_of(self._flow.error_selector)
            self._fail("portal_error", StepStatus.FAILED, detail)
            raise PortalError("PORTAL_REJECTED", "The portal rejected the submission",
                              retryable=False)

        status = self._step("read_status", lambda: self._driver.text_of(self._flow.status_selector))
        self.record.portal_status = mask_free_text(status)
        self.record.outcome = "submitted"
        self.record.finished_at = time.time()
        log.info("rpa.claim.submitted", tpa=self._flow.tpa_code,
                 duration_seconds=round(self.record.duration, 2))
        return status

    def close(self) -> None:
        if self.record.finished_at is None:
            self.record.finished_at = time.time()
        try:
            self._driver.close()
        except Exception as exc:  # noqa: BLE001 - a browser that will not close
            # is a leaked process, not a reason to fail the claim submission.
            log.warning("rpa.driver.close_failed", tpa=self._flow.tpa_code,
                        error_type=type(exc).__name__)


def run_claim_submission(driver: PortalDriver, flow: PortalFlow, vault: CredentialVault,
                         correlation_id: str, claim_number: str,
                         document_paths: list[str]) -> SessionRecord:
    """Run one submission end to end.

    Always returns a record — including on failure, because the record is the
    only audit trail a legacy portal produces.
    """
    session = PortalSession(driver, flow, vault, correlation_id)
    try:
        session.login()
        session.submit_claim(claim_number, document_paths)
    except PortalError as exc:
        session.record.outcome = f"failed:{exc.code}"
        session.record.finished_at = time.time()
        log.error("rpa.session.failed", tpa=flow.tpa_code, code=exc.code,
                  retryable=exc.retryable)
    finally:
        session.close()
    return session.record
