"""WhatsApp Business Platform channel (roadmap Phase 3).

Three realities of the WhatsApp Business Platform shape this module, and each one
is a bug if ignored:

1. **Webhooks are at-least-once.** Meta retries on any non-2xx and sometimes on
   timeouts. Handling the same message twice books two appointments, so dedupe on
   message id is mandatory rather than defensive.

2. **Signatures must be verified.** The webhook URL is public. Without signature
   verification anyone who finds it can drive the agent on behalf of any patient.

3. **The 24-hour window.** Outside 24h from the user's last message, only
   pre-approved template messages may be sent. Free-form sends are rejected, and
   template approval takes days — so the code must know which mode it is in
   rather than discovering it at runtime.

Provider-agnostic: the payload parser is separated from the transport so
switching BSP (Gupshup, Twilio, Karix) touches one class.
"""

from __future__ import annotations

import hashlib
import hmac
import time
from dataclasses import dataclass, field
from typing import Any

from ..observability import EventLogger
from ..pii import mask_phone

log = EventLogger(__name__)

# Meta's documented window for free-form replies.
CUSTOMER_SERVICE_WINDOW_SECONDS = 24 * 60 * 60


class SignatureError(Exception):
    """Webhook signature did not verify. Treat as hostile, not as a bug."""


@dataclass(slots=True)
class InboundMessage:
    message_id: str
    from_number: str
    text: str
    timestamp: float
    channel: str = "whatsapp"
    raw: dict[str, Any] = field(default_factory=dict)

    def safe_repr(self) -> dict[str, Any]:
        """Loggable form. The number is masked; the body is never logged at all."""
        return {
            "message_id": self.message_id,
            "from": mask_phone(self.from_number),
            "length": len(self.text),
        }


def verify_signature(app_secret: str, raw_body: bytes, header_value: str | None) -> None:
    """Verify Meta's `X-Hub-Signature-256`.

    Uses `compare_digest` because a naive `==` leaks timing information about how
    many leading bytes matched, which is enough to forge a signature given
    patience.
    """
    if not header_value:
        raise SignatureError("missing signature header")
    expected = hmac.new(app_secret.encode(), raw_body, hashlib.sha256).hexdigest()
    provided = header_value.removeprefix("sha256=").strip()
    if not hmac.compare_digest(expected, provided):
        raise SignatureError("signature mismatch")


class MessageDeduplicator:
    """Remembers seen message ids for a bounded window.

    In-memory here, which is correct for a single process and wrong for a scaled
    deployment — two replicas would each process a retry once. Swap for Redis or
    the HMS idempotency table before running more than one instance; that is
    recorded in the work order rather than hidden behind a comment.
    """

    def __init__(self, ttl_seconds: int = 3600, max_entries: int = 50_000) -> None:
        self._ttl = ttl_seconds
        self._max = max_entries
        self._seen: dict[str, float] = {}

    def seen_before(self, message_id: str) -> bool:
        now = time.time()
        self._evict(now)
        if message_id in self._seen:
            return True
        self._seen[message_id] = now
        return False

    def _evict(self, now: float) -> None:
        if len(self._seen) < self._max:
            expired = [k for k, t in self._seen.items() if now - t > self._ttl]
        else:
            # Over capacity: drop the oldest half rather than growing unbounded.
            ordered = sorted(self._seen.items(), key=lambda kv: kv[1])
            expired = [k for k, _ in ordered[: len(ordered) // 2]]
        for k in expired:
            self._seen.pop(k, None)


def parse_webhook(payload: dict[str, Any]) -> list[InboundMessage]:
    """Extract messages from a Meta webhook envelope.

    Tolerant by design: status callbacks, read receipts and reactions arrive on
    the same endpoint, and raising on them would make Meta retry a payload that
    will never succeed.
    """
    messages: list[InboundMessage] = []
    for entry in payload.get("entry", []) or []:
        for change in entry.get("changes", []) or []:
            value = change.get("value") or {}
            for msg in value.get("messages", []) or []:
                if msg.get("type") != "text":
                    log.info("whatsapp.message.skipped", reason="unsupported_type",
                             message_type=msg.get("type"))
                    continue
                body = (msg.get("text") or {}).get("body", "")
                messages.append(InboundMessage(
                    message_id=msg.get("id", ""),
                    from_number=msg.get("from", ""),
                    text=body,
                    timestamp=float(msg.get("timestamp", time.time())),
                    raw=msg,
                ))
    return messages


def within_service_window(last_inbound_at: float | None, now: float | None = None) -> bool:
    """Whether a free-form reply is permitted."""
    if last_inbound_at is None:
        return False
    return (now or time.time()) - last_inbound_at < CUSTOMER_SERVICE_WINDOW_SECONDS


@dataclass(slots=True)
class OutboundMessage:
    to_number: str
    text: str | None = None
    template_name: str | None = None
    template_params: list[str] = field(default_factory=list)

    def validate_for_window(self, in_window: bool) -> None:
        """Fail locally rather than at the provider.

        A rejected send outside the window is silent from the patient's side:
        they simply never hear back. Catching it here turns an invisible failure
        into a loud one.
        """
        if in_window:
            if not self.text and not self.template_name:
                raise ValueError("message must carry text or a template")
            return
        if not self.template_name:
            raise ValueError(
                "Outside the 24-hour customer service window only pre-approved "
                "templates may be sent. Provide template_name."
            )
