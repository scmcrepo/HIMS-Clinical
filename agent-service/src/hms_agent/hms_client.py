"""Client for the HMS agent gateway (`/api/agent/v1`, WO-001).

This is the only way the agent service reaches hospital data. It never opens a
database connection, and that constraint is the load-bearing one in the whole
architecture: going through the REST API means an agent booking an appointment is
subject to the same tenant filters, the same RBAC, the same audit stamping and
the same business rules as a receptionist doing it by hand. Direct SQL would
bypass all four and quietly become a second, weaker copy of the system's safety
apparatus.
"""

from __future__ import annotations

import hashlib
import json
import time
import uuid
from dataclasses import dataclass, field
from typing import Any, Self

import httpx

from .observability import (
    TOOL_DURATION,
    TOOL_INVOCATIONS,
    EventLogger,
    get_context,
)
from .pii import redact

log = EventLogger(__name__)


class HmsError(Exception):
    """A structured failure from the gateway.

    The gateway returns a stable `code` and a `retryable` flag precisely so the
    agent can decide what to do next. A bare 500 teaches a model nothing except
    to loop or give up, so preserve both fields all the way up.
    """

    def __init__(self, code: str, message: str, *, retryable: bool = False,
                 status: int | None = None, correlation_id: str | None = None) -> None:
        super().__init__(f"{code}: {message}")
        self.code = code
        self.message = message
        self.retryable = retryable
        self.status = status
        self.correlation_id = correlation_id


class HmsAuthError(HmsError):
    """Token rejected. Never retried — a revoked token stays revoked."""


@dataclass(slots=True)
class ToolCall:
    """A record of one gateway call, for shadow-mode comparison and audit."""
    tool: str
    method: str
    path: str
    params: dict[str, Any] = field(default_factory=dict)
    body: dict[str, Any] | None = None
    idempotency_key: str | None = None

    def fingerprint(self) -> str:
        """Stable hash of the intended action, used to compare against a human's."""
        payload = json.dumps(
            {"tool": self.tool, "params": self.params, "body": self.body},
            sort_keys=True, default=str)
        return hashlib.sha256(payload.encode()).hexdigest()[:16]


class HmsClient:
    """Synchronous gateway client.

    Sync rather than async on purpose: LangGraph nodes are sync by default, tool
    calls are sequential within a turn, and the concurrency that matters here is
    across runs, not within one.
    """

    def __init__(self, base_url: str, token: str, *, timeout: float = 10.0,
                 max_retries: int = 2, client: httpx.Client | None = None) -> None:
        self._base = base_url.rstrip("/")
        self._token = token
        self._timeout = timeout
        self._max_retries = max_retries
        self._client = client or httpx.Client(timeout=timeout)

    # ── plumbing ─────────────────────────────────────────────────────────────

    def _headers(self, idempotency_key: str | None = None) -> dict[str, str]:
        ctx = get_context()
        headers = {
            "Authorization": f"Bearer {self._token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        }
        # Propagating these is what keeps one patient interaction traceable across
        # WhatsApp -> this service -> the backend -> Postgres. Minting a fresh id
        # downstream breaks the chain.
        if cid := ctx.get("correlationId"):
            headers["X-Correlation-Id"] = cid
        if rid := ctx.get("runId"):
            headers["X-Run-Id"] = rid
        if bid := ctx.get("branchId"):
            headers["X-Branch-Id"] = bid
        if idempotency_key:
            headers["X-Idempotency-Key"] = idempotency_key
        return headers

    @staticmethod
    def _parse_error(response: httpx.Response) -> HmsError:
        code, message, retryable = "UNKNOWN", response.text[:200], False
        try:
            payload = response.json()
            message = payload.get("message") or message
            data = payload.get("data") or {}
            if isinstance(data, dict):
                code = data.get("code", code)
                retryable = bool(data.get("retryable", False))
        except (ValueError, AttributeError):
            pass
        if response.status_code in (401, 403):
            return HmsAuthError(code if code != "UNKNOWN" else "UNAUTHORIZED",
                                message, retryable=False, status=response.status_code)
        # 5xx and 429 are retryable even if the server forgot to say so.
        if response.status_code >= 500 or response.status_code == 429:
            retryable = True
        return HmsError(code, message, retryable=retryable, status=response.status_code)

    def _request(self, call: ToolCall) -> dict[str, Any]:
        url = f"{self._base}{call.path}"
        attempt = 0
        started = time.perf_counter()

        while True:
            attempt += 1
            try:
                response = self._client.request(
                    call.method, url,
                    params=call.params or None,
                    json=call.body,
                    headers=self._headers(call.idempotency_key),
                )
            except httpx.TimeoutException as exc:
                error: HmsError = HmsError("TIMEOUT", str(exc), retryable=True)
            except httpx.HTTPError as exc:
                error = HmsError("TRANSPORT_ERROR", str(exc), retryable=True)
            else:
                if response.status_code < 400:
                    duration = time.perf_counter() - started
                    TOOL_DURATION.labels(tool=call.tool).observe(duration)
                    TOOL_INVOCATIONS.labels(tool=call.tool, outcome="success").inc()
                    log.info("agent.tool.completed", tool=call.tool,
                             duration_ms=round(duration * 1000),
                             replayed=response.headers.get("Idempotency-Replayed") == "true")
                    try:
                        return response.json()
                    except ValueError:
                        return {}
                error = self._parse_error(response)

            if error.retryable and attempt <= self._max_retries:
                # Exponential backoff. A retried write is safe only because
                # book_slot carries an idempotency key (WO-001/T-008); without one
                # this loop would double-book a patient.
                log.warning("agent.tool.retrying", tool=call.tool, attempt=attempt,
                         error_code=error.code)
                time.sleep(min(0.25 * (2 ** (attempt - 1)), 2.0))
                continue

            TOOL_INVOCATIONS.labels(tool=call.tool, outcome="failure").inc()
            log.error("agent.tool.failed", tool=call.tool, error_code=error.code,
                      retryable=error.retryable, status=error.status)
            raise error

    # ── tools ────────────────────────────────────────────────────────────────

    def check_slot_availability(self, provider_id: str, date: str) -> list[dict[str, Any]]:
        """Slots for a provider on a date.

        Required before booking: the HMS `bookAppointment` path needs a concrete
        slotId, so an agent cannot go straight from "tomorrow morning" to a
        booking. This call is what turns natural language into a choosable list.
        """
        result = self._request(ToolCall(
            tool="check_slot_availability", method="GET",
            path="/agent/v1/tools/slot-availability",
            params={"providerId": provider_id, "date": date},
        ))
        return result.get("data") or []

    def book_slot(self, *, provider_id: str, slot_id: str, appointment_date: str,
                  patient_id: str | None = None, notes: str | None = None,
                  idempotency_key: str | None = None) -> dict[str, Any]:
        """Book an appointment. Idempotency key is mandatory.

        Generated here if the caller omits it, because a model that retries
        without one double-books a patient and the hospital finds out in the
        waiting room.
        """
        body: dict[str, Any] = {
            "providerId": provider_id,
            "slotId": slot_id,
            "appointmentDate": appointment_date,
        }
        if patient_id:
            body["patientId"] = patient_id
        if notes:
            body["notes"] = notes
        result = self._request(ToolCall(
            tool="book_slot", method="POST", path="/agent/v1/tools/book-slot",
            body=body, idempotency_key=idempotency_key or str(uuid.uuid4()),
        ))
        return result.get("data") or {}

    def fetch_billing_ledger(self, patient_id: str) -> dict[str, Any]:
        result = self._request(ToolCall(
            tool="fetch_billing_ledger", method="GET",
            path="/agent/v1/tools/billing-ledger",
            params={"patientId": patient_id},
        ))
        return result.get("data") or {}

    def check_bed_occupancy(self, ward: str | None = None) -> dict[str, Any]:
        params = {"ward": ward} if ward else {}
        result = self._request(ToolCall(
            tool="check_bed_occupancy", method="GET",
            path="/agent/v1/tools/bed-occupancy", params=params,
        ))
        return result.get("data") or {}

    def raise_escalation(self, *, run_id: str, channel: str, reason: str,
                         detail: str | None = None, intent: str | None = None,
                         confidence: float | None = None,
                         transcript: list[dict[str, Any]] | None = None,
                         proposed_actions: list[dict[str, Any]] | None = None,
                         timeout_seconds: int | None = None) -> dict[str, Any]:
        """File the run into the Copilot queue and return the escalation record.

        Tenant and branch are deliberately absent from the body — the gateway
        takes them from the token. Sending them would let a caller file into
        another hospital's queue by asserting an id.

        The transcript travels because an operator cannot judge a conversation
        they cannot read; the gateway encrypts it at rest and never logs it.
        """
        body: dict[str, Any] = {
            "runId": run_id,
            "channel": channel,
            "reason": reason,
            "detail": detail,
            "intent": intent,
            "confidence": confidence,
            "transcript": transcript or [],
            "proposedActions": proposed_actions or [],
        }
        if timeout_seconds is not None:
            body["timeoutSeconds"] = timeout_seconds

        result = self._request(ToolCall(
            tool="raise_escalation", method="POST",
            path="/agent/v1/hitl/escalations", body=body))
        return result.get("data") or {}

    def check_consent(self, patient_id: str, purpose: str) -> bool:
        """Whether this patient permits this purpose.

        Fails closed. If the check itself errors we return False, because
        treating "I could not verify consent" as "consent granted" is precisely
        the assumption DPDP exists to prevent.
        """
        try:
            result = self._request(ToolCall(
                tool="check_consent", method="GET",
                path="/agent/v1/tools/consent",
                params={"patientId": patient_id, "purpose": purpose}))
            return bool((result.get("data") or {}).get("granted", False))
        except HmsError:
            log.warning("agent.consent.check_failed", purpose=purpose)
            return False

    def tool_schema(self) -> dict[str, Any]:
        """Tool definitions, generated by the backend from its OpenAPI document.

        Fetched rather than hand-maintained here: two copies of a schema diverge,
        and the first symptom is a model confidently passing a parameter that no
        longer exists.
        """
        result = self._request(ToolCall(
            tool="tool_schema", method="GET", path="/agent/v1/tools/schema"))
        return result.get("data") or {}

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> Self:
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()


def safe_summary(payload: Any) -> Any:
    """Redacted view of a payload, for logs and audit records."""
    return redact(payload)
