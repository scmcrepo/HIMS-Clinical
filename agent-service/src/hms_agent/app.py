"""FastAPI entry point: webhooks and health (W-002, V-002).

Everything that reaches the agent from the outside world arrives here. Three
properties matter more than routing:

**Verify before parsing.** Signature checks run on the raw body before anything
is deserialised. Parsing first means acting on attacker-controlled structure.

**Acknowledge fast, work asynchronously.** Meta retries any non-2xx and Exotel
holds the call leg open. Running a graph inline would blow the webhook timeout
and cause duplicate deliveries, so the handler acknowledges and hands off.

**Dedupe before work, not after.** Retries are normal, not exceptional. Checking
the message id after processing means the second delivery has already booked a
second appointment.
"""

from __future__ import annotations

import asyncio
from typing import Any

from fastapi import BackgroundTasks, FastAPI, Header, Request, Response

from .channels.whatsapp import MessageDeduplicator, SignatureError, parse_webhook, verify_signature
from .config import Settings, load_settings
from .observability import EventLogger, configure_logging, correlation_context, new_correlation_id
from .pii import mask_phone

log = EventLogger(__name__)


def create_app(settings: Settings | None = None, runner: Any = None) -> FastAPI:
    """Build the app.

    ``runner`` is the callable that actually executes a conversation turn. It is
    injected so the transport layer can be tested without a graph, a model or a
    database — the webhook contract and the orchestration are genuinely separate
    concerns and coupling them would make both harder to verify.
    """
    cfg = settings or load_settings()
    configure_logging()
    app = FastAPI(title="HMS Agent Service", version="0.1.0")
    dedup = MessageDeduplicator()

    app.state.settings = cfg
    app.state.runner = runner
    app.state.dedup = dedup

    # ── health ──────────────────────────────────────────────────────────────

    @app.get("/health")
    def health() -> dict[str, Any]:
        return {"status": "up", "shadow_mode": cfg.shadow_mode,
                "residency_enforced": cfg.enforce_data_residency}

    @app.get("/ready")
    def ready() -> dict[str, Any]:
        """Readiness reports configuration honestly.

        A deployment missing its HMS token is not ready, and saying so here is
        cheaper than discovering it when the first patient messages in.
        """
        problems: list[str] = []
        if not cfg.hms_agent_token:
            problems.append("HMS_AGENT_HMS_AGENT_TOKEN is not set")
        if not cfg.hms_base_url:
            problems.append("HMS_AGENT_HMS_BASE_URL is not set")
        return {"ready": not problems, "problems": problems}

    # ── WhatsApp ────────────────────────────────────────────────────────────

    @app.get("/webhooks/whatsapp")
    def whatsapp_verify(request: Request) -> Response:
        """Meta's subscription handshake."""
        params = request.query_params
        expected = getattr(cfg, "whatsapp_verify_token", "")
        if params.get("hub.mode") == "subscribe" and params.get("hub.verify_token") == expected:
            return Response(content=params.get("hub.challenge", ""), media_type="text/plain")
        log.warning("whatsapp.verify.rejected")
        return Response(status_code=403)

    @app.post("/webhooks/whatsapp")
    async def whatsapp_inbound(
        request: Request,
        background: BackgroundTasks,
        x_hub_signature_256: str | None = Header(default=None),
    ) -> Response:
        raw = await request.body()

        # Signature first, on the raw bytes, before any parsing.
        secret = getattr(cfg, "whatsapp_app_secret", "")
        if secret:
            try:
                verify_signature(secret, raw, x_hub_signature_256)
            except SignatureError:
                log.warning("whatsapp.signature.rejected")
                return Response(status_code=403)

        try:
            payload = await request.json()
        except Exception:  # noqa: BLE001 - any parse failure is handled the same way.
            # Malformed body: 200 anyway. A non-2xx makes Meta retry a payload
            # that can never succeed.
            return Response(status_code=200)

        for message in parse_webhook(payload):
            if dedup.seen_before(message.message_id):
                log.info("whatsapp.duplicate.ignored", message_id=message.message_id)
                continue
            background.add_task(_dispatch, app, message.from_number, message.text,
                                "whatsapp", message.message_id)

        # Acknowledge immediately; the graph runs in the background.
        return Response(status_code=200)

    # ── Voice ───────────────────────────────────────────────────────────────

    @app.post("/webhooks/voice/incoming")
    async def voice_incoming(request: Request) -> dict[str, Any]:
        """Inbound call. Returns the telephony provider's control response."""
        # Exotel posts form-encoded, Twilio too, but some gateways use JSON.
        # Accept either rather than 400-ing on a provider swap.
        try:
            payload = dict(await request.form())
        except Exception:  # noqa: BLE001 - fall back to JSON below.
            payload = {}
        if not payload:
            try:
                payload = await request.json()
            except Exception:  # noqa: BLE001 - empty body is handled below.
                payload = {}
        call_id = str(payload.get("CallSid") or payload.get("CallId")
                      or payload.get("call_id") or "")
        caller = str(payload.get("From") or payload.get("from") or "")
        with correlation_context(correlationId=new_correlation_id()):
            log.info("voice.incoming", call_id=call_id, caller=mask_phone(caller))
        return {"call_id": call_id, "action": "stream",
                "stream_url": f"/webhooks/voice/stream/{call_id}"}

    @app.post("/webhooks/voice/transcript")
    async def voice_transcript(request: Request, background: BackgroundTasks) -> Response:
        """A completed utterance from the streaming STT."""
        body = await request.json()
        call_id = str(body.get("call_id", ""))
        text = str(body.get("transcript", ""))
        caller = str(body.get("from", ""))
        if not text.strip():
            return Response(status_code=200)
        background.add_task(_dispatch, app, caller, text, "voice", call_id)
        return Response(status_code=200)

    return app


async def _dispatch(app: FastAPI, from_number: str, text: str,
                    channel: str, external_id: str) -> None:
    """Run one turn.

    Wrapped so a failure here can never propagate back into the webhook
    response — by the time this runs the provider has already been acknowledged,
    and an exception would only be lost.
    """
    runner = app.state.runner
    if runner is None:
        log.warning("dispatch.no_runner", channel=channel)
        return
    correlation_id = new_correlation_id()
    with correlation_context(correlationId=correlation_id, runId=external_id):
        try:
            result = runner(from_number=from_number, text=text, channel=channel,
                            external_id=external_id, correlation_id=correlation_id)
            if asyncio.iscoroutine(result):
                await result
        except Exception as exc:  # noqa: BLE001 - background task boundary.
            # Nothing upstream can handle this; log it or lose it entirely.
            log.error("dispatch.failed", channel=channel,
                      error_type=type(exc).__name__)
