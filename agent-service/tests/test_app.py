"""Webhook contract: signature, dedupe, acknowledgement, dispatch."""
import hashlib
import hmac
import json

from fastapi.testclient import TestClient

from hms_agent.app import create_app
from hms_agent.config import Settings

SECRET = "app-secret"


def signed(body: bytes) -> str:
    return "sha256=" + hmac.new(SECRET.encode(), body, hashlib.sha256).hexdigest()


def envelope(msg_id="wamid.1", text="book an appointment", frm="919876543210"):
    return {"entry": [{"changes": [{"value": {"messages": [
        {"id": msg_id, "from": frm, "type": "text", "timestamp": "1700000000",
         "text": {"body": text}}]}}]}]}


def build(**overrides):
    calls = []

    def runner(**kw):
        calls.append(kw)

    settings = Settings(whatsapp_app_secret=SECRET,
                        whatsapp_verify_token="verify-me", **overrides)
    return TestClient(create_app(settings, runner=runner)), calls


class TestHealth:
    def test_health_reports_shadow_mode(self):
        client, _ = build()
        assert client.get("/health").json()["shadow_mode"] is True

    def test_readiness_names_missing_configuration(self):
        # Cheaper to learn here than when the first patient messages in.
        client, _ = build()
        body = client.get("/ready").json()
        assert body["ready"] is False
        assert any("TOKEN" in p for p in body["problems"])

    def test_readiness_passes_when_configured(self):
        client, _ = build(hms_agent_token="tok")
        assert client.get("/ready").json()["ready"] is True


class TestWhatsAppVerification:
    def test_correct_token_returns_the_challenge(self):
        client, _ = build()
        r = client.get("/webhooks/whatsapp", params={
            "hub.mode": "subscribe", "hub.verify_token": "verify-me",
            "hub.challenge": "12345"})
        assert r.status_code == 200 and r.text == "12345"

    def test_wrong_token_is_rejected(self):
        client, _ = build()
        r = client.get("/webhooks/whatsapp", params={
            "hub.mode": "subscribe", "hub.verify_token": "wrong", "hub.challenge": "x"})
        assert r.status_code == 403


class TestWhatsAppInbound:
    def test_valid_signature_is_accepted_and_dispatched(self):
        client, calls = build()
        body = json.dumps(envelope()).encode()
        r = client.post("/webhooks/whatsapp", content=body,
                        headers={"X-Hub-Signature-256": signed(body)})
        assert r.status_code == 200
        assert calls and calls[0]["text"] == "book an appointment"
        assert calls[0]["channel"] == "whatsapp"

    def test_bad_signature_is_rejected_and_never_dispatched(self):
        client, calls = build()
        body = json.dumps(envelope()).encode()
        r = client.post("/webhooks/whatsapp", content=body,
                        headers={"X-Hub-Signature-256": "sha256=deadbeef"})
        assert r.status_code == 403
        assert calls == []

    def test_missing_signature_is_rejected(self):
        client, _ = build()
        body = json.dumps(envelope()).encode()
        assert client.post("/webhooks/whatsapp", content=body).status_code == 403

    def test_a_retry_is_deduplicated(self):
        # Meta retries any non-2xx; without this the second delivery books again.
        client, calls = build()
        body = json.dumps(envelope()).encode()
        headers = {"X-Hub-Signature-256": signed(body)}
        client.post("/webhooks/whatsapp", content=body, headers=headers)
        client.post("/webhooks/whatsapp", content=body, headers=headers)
        assert len(calls) == 1

    def test_distinct_messages_both_dispatch(self):
        client, calls = build()
        for mid in ("wamid.1", "wamid.2"):
            body = json.dumps(envelope(msg_id=mid)).encode()
            client.post("/webhooks/whatsapp", content=body,
                        headers={"X-Hub-Signature-256": signed(body)})
        assert len(calls) == 2

    def test_malformed_body_still_returns_200(self):
        # A non-2xx makes Meta retry a payload that can never succeed.
        client, _ = build()
        body = b"not json"
        r = client.post("/webhooks/whatsapp", content=body,
                        headers={"X-Hub-Signature-256": signed(body)})
        assert r.status_code == 200

    def test_status_callbacks_do_not_dispatch(self):
        client, calls = build()
        body = json.dumps({"entry": [{"changes": [{"value": {"statuses": [{"id": "x"}]}}]}]}).encode()
        r = client.post("/webhooks/whatsapp", content=body,
                        headers={"X-Hub-Signature-256": signed(body)})
        assert r.status_code == 200 and calls == []

    def test_a_runner_failure_does_not_break_the_webhook(self):
        # By dispatch time the provider is already acknowledged.
        def boom(**kw):
            raise RuntimeError("graph exploded")

        settings = Settings(whatsapp_app_secret=SECRET)
        client = TestClient(create_app(settings, runner=boom))
        body = json.dumps(envelope()).encode()
        r = client.post("/webhooks/whatsapp", content=body,
                        headers={"X-Hub-Signature-256": signed(body)})
        assert r.status_code == 200


class TestVoice:
    def test_incoming_call_returns_a_stream_instruction(self):
        client, _ = build()
        r = client.post("/webhooks/voice/incoming",
                        data={"CallSid": "call-1", "From": "+919876543210"})
        assert r.status_code == 200
        assert r.json()["action"] == "stream"

    def test_a_transcript_dispatches_a_turn(self):
        client, calls = build()
        r = client.post("/webhooks/voice/transcript", json={
            "call_id": "call-1", "transcript": "I need an appointment",
            "from": "+919876543210"})
        assert r.status_code == 200
        assert calls and calls[0]["channel"] == "voice"

    def test_an_empty_transcript_is_ignored(self):
        client, calls = build()
        client.post("/webhooks/voice/transcript",
                    json={"call_id": "c", "transcript": "   "})
        assert calls == []
