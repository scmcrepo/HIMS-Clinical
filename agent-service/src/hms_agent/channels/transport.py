"""Channel transports (WO-006 W-002, WO-007 V-002).

Provider adapters live behind narrow Protocols so switching BSP or telephony
vendor touches one class. That is not speculative generality: the roadmap names
"e.g. Gupshup, Twilio" and "e.g. Exotel, Twilio" precisely because the choice is
open, and in India the choice often changes after a pricing negotiation.

Every adapter here is credential-driven and fails loudly when unconfigured,
rather than silently no-oping. A message that was never sent is invisible from
the patient's side — they simply never hear back — so an unconfigured transport
must raise, not shrug.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol

import httpx

from ..observability import EventLogger
from ..pii import mask_phone

log = EventLogger(__name__)


class TransportNotConfigured(RuntimeError):
    """Raised when a provider is missing credentials.

    Deliberately an error rather than a silent skip: an unsent message is a
    patient waiting for a reply that will never come.
    """


class MessageTransport(Protocol):
    """Outbound messaging (WhatsApp/SMS)."""

    def send_text(self, to: str, text: str) -> str: ...
    def send_template(self, to: str, template: str, params: list[str],
                      language: str = "en") -> str: ...


class VoiceTransport(Protocol):
    """Telephony control."""

    def answer(self, call_id: str) -> None: ...
    def play(self, call_id: str, audio: bytes) -> None: ...
    def transfer(self, call_id: str, destination: str) -> None: ...
    def hangup(self, call_id: str) -> None: ...


class SpeechToText(Protocol):
    def transcribe(self, audio: bytes, language: str = "hi-IN") -> str: ...


class TextToSpeech(Protocol):
    def synthesise(self, text: str, language: str = "hi-IN") -> bytes: ...


# ── WhatsApp ────────────────────────────────────────────────────────────────

@dataclass(slots=True)
class MetaCloudTransport:
    """WhatsApp Business Cloud API (Meta direct).

    Also the shape most BSPs proxy, so a Gupshup or Karix adapter is usually this
    class with a different base URL and auth header.
    """

    phone_number_id: str
    access_token: str
    base_url: str = "https://graph.facebook.com/v21.0"
    client: Any = None

    def _http(self) -> httpx.Client:
        if self.client is None:
            self.client = httpx.Client(timeout=15.0)
        return self.client

    def _post(self, payload: dict[str, Any]) -> str:
        if not self.phone_number_id or not self.access_token:
            raise TransportNotConfigured(
                "WhatsApp is not configured. Set HMS_AGENT_WHATSAPP_PHONE_NUMBER_ID "
                "and HMS_AGENT_WHATSAPP_ACCESS_TOKEN.")
        response = self._http().post(
            f"{self.base_url}/{self.phone_number_id}/messages",
            json=payload,
            headers={"Authorization": f"Bearer {self.access_token}"})
        if response.status_code >= 400:
            # The body echoes the recipient number; log the status only.
            log.error("whatsapp.send.failed", status=response.status_code,
                      to=mask_phone(str(payload.get("to", ""))))
            raise RuntimeError(f"WhatsApp send failed: {response.status_code}")
        body = response.json()
        message_id = (body.get("messages") or [{}])[0].get("id", "")
        log.info("whatsapp.sent", to=mask_phone(str(payload.get("to", ""))),
                 message_id=message_id, kind=payload.get("type"))
        return message_id

    def send_text(self, to: str, text: str) -> str:
        """Free-form. Only valid inside the 24-hour customer service window —
        the caller is responsible for checking, since only it knows when the
        patient last wrote in."""
        return self._post({"messaging_product": "whatsapp", "to": to,
                           "type": "text", "text": {"body": text}})

    def send_template(self, to: str, template: str, params: list[str],
                      language: str = "en") -> str:
        components = [{"type": "body", "parameters":
                       [{"type": "text", "text": p} for p in params]}] if params else []
        return self._post({
            "messaging_product": "whatsapp", "to": to, "type": "template",
            "template": {"name": template, "language": {"code": language},
                         "components": components}})


@dataclass(slots=True)
class TemplateRegistry:
    """Approved WhatsApp templates.

    Template approval takes days, so which templates exist is operational fact,
    not something to discover at send time. Registering them here lets a send
    outside the 24h window fail locally with a useful message instead of being
    rejected by Meta after the patient has already been left waiting.
    """

    templates: dict[str, int] = field(default_factory=dict)

    def register(self, name: str, param_count: int) -> None:
        self.templates[name] = param_count

    def validate(self, name: str, params: list[str]) -> None:
        if name not in self.templates:
            raise ValueError(
                f"Template {name!r} is not registered as approved. Approval takes "
                "days; register it here once Meta has approved it.")
        expected = self.templates[name]
        if len(params) != expected:
            raise ValueError(
                f"Template {name!r} expects {expected} parameter(s), got {len(params)}")


# ── Voice ───────────────────────────────────────────────────────────────────

@dataclass(slots=True)
class ExotelVoiceTransport:
    """Exotel telephony.

    Chosen as the reference implementation because it is India-native and keeps
    call media in-country, which matters for DPDP residency in a way that a
    global provider's default region does not.
    """

    account_sid: str
    api_key: str
    api_token: str
    base_url: str = "https://api.exotel.com/v1/Accounts"
    client: Any = None

    def _http(self) -> httpx.Client:
        if self.client is None:
            self.client = httpx.Client(timeout=15.0)
        return self.client

    def _require(self) -> None:
        if not self.account_sid or not self.api_key:
            raise TransportNotConfigured(
                "Voice is not configured. Set HMS_AGENT_EXOTEL_ACCOUNT_SID, "
                "HMS_AGENT_EXOTEL_API_KEY and HMS_AGENT_EXOTEL_API_TOKEN.")

    def answer(self, call_id: str) -> None:
        self._require()
        log.info("voice.answered", call_id=call_id)

    def play(self, call_id: str, audio: bytes) -> None:
        self._require()
        log.debug("voice.play", call_id=call_id, bytes=len(audio))

    def transfer(self, call_id: str, destination: str) -> None:
        """Hand the caller to a human.

        The destination is a desk extension, not a patient number, so it is safe
        to log — and being able to see which desk a call went to is the whole
        point when someone asks why a caller was bounced.
        """
        self._require()
        self._http().post(
            f"{self.base_url}/{self.account_sid}/Calls/{call_id}/transfer",
            auth=(self.api_key, self.api_token), data={"To": destination})
        log.info("voice.transferred", call_id=call_id, destination=destination)

    def hangup(self, call_id: str) -> None:
        self._require()
        self._http().post(
            f"{self.base_url}/{self.account_sid}/Calls/{call_id}/hangup",
            auth=(self.api_key, self.api_token))
        log.info("voice.hungup", call_id=call_id)


@dataclass(slots=True)
class BhashiniStt:
    """Bhashini speech-to-text.

    The government stack, which keeps audio in-country. That is the deciding
    factor over a lower-latency foreign API: a call recording is health data the
    moment a patient describes a symptom.
    """

    endpoint: str
    api_key: str
    client: Any = None

    def transcribe(self, audio: bytes, language: str = "hi-IN") -> str:
        if not self.endpoint:
            raise TransportNotConfigured(
                "STT is not configured. Set HMS_AGENT_STT_ENDPOINT and "
                "HMS_AGENT_STT_API_KEY, or supply a self-hosted Whisper endpoint.")
        client = self.client or httpx.Client(timeout=10.0)
        response = client.post(
            self.endpoint,
            headers={"Authorization": self.api_key},
            json={"audio": audio.hex(), "config": {"language": {"sourceLanguage": language}}})
        if response.status_code >= 400:
            raise RuntimeError(f"STT failed: {response.status_code}")
        # The transcript is PHI the moment it exists — returned, never logged.
        return str(response.json().get("transcript", ""))


@dataclass(slots=True)
class BhashiniTts:
    endpoint: str
    api_key: str
    client: Any = None

    def synthesise(self, text: str, language: str = "hi-IN") -> bytes:
        if not self.endpoint:
            raise TransportNotConfigured(
                "TTS is not configured. Set HMS_AGENT_TTS_ENDPOINT and "
                "HMS_AGENT_TTS_API_KEY.")
        client = self.client or httpx.Client(timeout=10.0)
        response = client.post(
            self.endpoint,
            headers={"Authorization": self.api_key},
            json={"text": text, "config": {"language": {"sourceLanguage": language}}})
        if response.status_code >= 400:
            raise RuntimeError(f"TTS failed: {response.status_code}")
        return bytes.fromhex(response.json().get("audio", ""))
