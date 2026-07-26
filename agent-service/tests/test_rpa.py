"""RPA worker: flow correctness, failure handling, audit recording, PII hygiene."""
import json

import pytest

from hms_agent.rpa.worker import (
    PortalError,
    PortalFlow,
    PortalSession,
    StepStatus,
    run_claim_submission,
)

FLOW = PortalFlow(
    tpa_code="ACME_TPA",
    login_url="https://portal.test/login",
    username_selector="#user", password_selector="#pass", submit_selector="#go",
    logged_in_marker="#dashboard",
    claim_url="https://portal.test/claim",
    claim_number_selector="#claimno",
    document_upload_selector="#docs",
    submit_claim_selector="#submit",
    status_selector="#status",
    captcha_marker="#captcha",
    error_selector="#error",
)


class FakeVault:
    def __init__(self, creds=None):
        # Distinctive values: a one-character password would appear by chance
        # in any JSON dump and make the leak assertion meaningless.
        self._creds = creds if creds is not None else {
            "username": "hospital-svc-account", "password": "s3cr3t-p0rtal-pw"}

    def get(self, tpa_code):
        return self._creds


class FakeDriver:
    """Records every operation so tests can assert on the flow."""

    def __init__(self, present=("#dashboard",), status="RECEIVED", texts=None, fail_on=None):
        self.calls = []
        self._present = set(present)
        self._status = status
        self._texts = texts or {}
        self._fail_on = fail_on
        self.closed = False
        self.screenshots = 0

    def _maybe_fail(self, op):
        if self._fail_on == op:
            raise RuntimeError("portal blew up")

    def goto(self, url): self._maybe_fail("goto"); self.calls.append(("goto", url))
    def fill(self, sel, val): self._maybe_fail("fill"); self.calls.append(("fill", sel, val))
    def click(self, sel): self._maybe_fail("click"); self.calls.append(("click", sel))
    def upload(self, sel, path): self._maybe_fail("upload"); self.calls.append(("upload", path))
    def exists(self, sel): return sel in self._present
    def text_of(self, sel): return self._texts.get(sel, self._status)
    def screenshot(self): self.screenshots += 1; return b"png"
    def close(self): self.closed = True


class TestLogin:
    def test_successful_login_fills_and_submits(self):
        driver = FakeDriver()
        PortalSession(driver, FLOW, FakeVault(), "c-1").login()
        assert ("fill", "#user", "hospital-svc-account") in driver.calls
        assert ("click", "#go") in driver.calls

    def test_missing_credentials_fail_before_touching_the_browser(self):
        driver = FakeDriver()
        session = PortalSession(driver, FLOW, FakeVault({}), "c-1")
        with pytest.raises(PortalError) as exc:
            session.login()
        assert exc.value.code == "CREDENTIALS_MISSING"
        assert driver.calls == []

    def test_captcha_stops_the_run_rather_than_guessing(self):
        # Solving it is neither legal nor reliable; a human takes over.
        driver = FakeDriver(present=("#captcha",))
        with pytest.raises(PortalError) as exc:
            PortalSession(driver, FLOW, FakeVault(), "c-1").login()
        assert exc.value.code == "CAPTCHA_PRESENTED"
        assert exc.value.retryable is False

    def test_missing_dashboard_marker_is_a_login_failure(self):
        driver = FakeDriver(present=())
        with pytest.raises(PortalError) as exc:
            PortalSession(driver, FLOW, FakeVault(), "c-1").login()
        assert exc.value.code == "LOGIN_FAILED"
        assert exc.value.retryable is True

    def test_a_failure_captures_a_screenshot(self):
        driver = FakeDriver(present=())
        with pytest.raises(PortalError):
            PortalSession(driver, FLOW, FakeVault(), "c-1").login()
        assert driver.screenshots == 1


class TestClaimSubmission:
    def test_uploads_every_document(self):
        driver = FakeDriver()
        session = PortalSession(driver, FLOW, FakeVault(), "c-1")
        session.login()
        session.submit_claim("CLM-1", ["/tmp/a.pdf", "/tmp/b.pdf"])
        uploads = [c for c in driver.calls if c[0] == "upload"]
        assert len(uploads) == 2

    def test_portal_error_is_surfaced_not_swallowed(self):
        driver = FakeDriver(present=("#dashboard", "#error"),
                            texts={"#error": "Policy number invalid"})
        session = PortalSession(driver, FLOW, FakeVault(), "c-1")
        session.login()
        with pytest.raises(PortalError) as exc:
            session.submit_claim("CLM-1", [])
        assert exc.value.code == "PORTAL_REJECTED"

    def test_status_is_recorded(self):
        driver = FakeDriver(status="UNDER_REVIEW")
        session = PortalSession(driver, FLOW, FakeVault(), "c-1")
        session.login()
        assert session.submit_claim("CLM-1", []) == "UNDER_REVIEW"
        assert session.record.outcome == "submitted"


class TestAuditRecord:
    def test_a_record_exists_even_when_the_run_fails(self):
        # For a legacy portal this is the only audit trail that will exist.
        record = run_claim_submission(FakeDriver(present=()), FLOW, FakeVault(),
                                      "c-1", "CLM-1", [])
        assert record.outcome.startswith("failed:")
        assert record.steps

    def test_successful_run_records_every_step(self):
        record = run_claim_submission(FakeDriver(), FLOW, FakeVault(), "c-1", "CLM-1", ["/a.pdf"])
        assert record.outcome == "submitted"
        assert all(s.status is StepStatus.OK for s in record.steps)

    def test_the_driver_is_always_closed(self):
        driver = FakeDriver(present=())
        run_claim_submission(driver, FLOW, FakeVault(), "c-1", "CLM-1", [])
        assert driver.closed

    def test_credentials_never_reach_the_record(self):
        # The session record is written to disk and read by humans; the vault
        # values must not travel with it.
        record = run_claim_submission(FakeDriver(), FLOW, FakeVault(), "c-1", "CLM-1", [])
        dumped = json.dumps(record.redacted())
        assert "s3cr3t-p0rtal-pw" not in dumped
        assert "hospital-svc-account" not in dumped

    def test_portal_error_text_is_masked(self):
        # Portal messages routinely quote the member or policy number.
        driver = FakeDriver(present=("#dashboard", "#error"),
                            texts={"#error": "Rejected for member 9876543210"})
        record = run_claim_submission(driver, FLOW, FakeVault(), "c-1", "CLM-1", [])
        assert "9876543210" not in json.dumps(record.redacted())

    def test_driver_exception_is_wrapped_not_leaked(self):
        record = run_claim_submission(FakeDriver(fail_on="goto"), FLOW, FakeVault(),
                                      "c-1", "CLM-1", [])
        assert record.outcome == "failed:STEP_FAILED"


class TestTimeBudget:
    def test_session_budget_is_enforced(self):
        session = PortalSession(FakeDriver(), FLOW, FakeVault(), "c-1", session_timeout=-1)
        with pytest.raises(PortalError) as exc:
            session.login()
        assert exc.value.code == "SESSION_TIMEOUT"
