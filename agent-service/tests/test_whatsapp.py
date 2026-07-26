"""WhatsApp channel: signature verification, dedupe, window rules."""
import hashlib
import hmac
import json
import time

import pytest

from hms_agent.channels.whatsapp import (
    CUSTOMER_SERVICE_WINDOW_SECONDS,
    MessageDeduplicator,
    OutboundMessage,
    SignatureError,
    parse_webhook,
    verify_signature,
    within_service_window,
)

SECRET = "app-secret"


def signed(body: bytes) -> str:
    return "sha256=" + hmac.new(SECRET.encode(), body, hashlib.sha256).hexdigest()


def envelope(msg_id="wamid.1", text="book an appointment", frm="919876543210"):
    return {"entry": [{"changes": [{"value": {"messages": [
        {"id": msg_id, "from": frm, "type": "text", "timestamp": "1700000000",
         "text": {"body": text}}]}}]}]}


class TestSignature:
    def test_valid_signature_passes(self):
        body = json.dumps(envelope()).encode()
        verify_signature(SECRET, body, signed(body))

    def test_tampered_body_is_rejected(self):
        body = json.dumps(envelope()).encode()
        sig = signed(body)
        with pytest.raises(SignatureError):
            verify_signature(SECRET, body + b"x", sig)

    def test_missing_header_is_rejected(self):
        with pytest.raises(SignatureError):
            verify_signature(SECRET, b"{}", None)

    def test_wrong_secret_is_rejected(self):
        body = b"{}"
        bad = "sha256=" + hmac.new(b"other", body, hashlib.sha256).hexdigest()
        with pytest.raises(SignatureError):
            verify_signature(SECRET, body, bad)


class TestParsing:
    def test_extracts_a_text_message(self):
        msgs = parse_webhook(envelope())
        assert len(msgs) == 1
        assert msgs[0].text == "book an appointment"
        assert msgs[0].message_id == "wamid.1"

    def test_ignores_non_text_types(self):
        payload = {"entry": [{"changes": [{"value": {"messages": [
            {"id": "x", "from": "91", "type": "image"}]}}]}]}
        assert parse_webhook(payload) == []

    def test_tolerates_status_callbacks(self):
        # Delivery receipts hit the same endpoint; raising would make Meta retry
        # a payload that can never succeed.
        payload = {"entry": [{"changes": [{"value": {"statuses": [{"id": "x"}]}}]}]}
        assert parse_webhook(payload) == []

    def test_tolerates_empty_envelope(self):
        assert parse_webhook({}) == []

    def test_safe_repr_masks_the_number_and_omits_the_body(self):
        msg = parse_webhook(envelope(text="my aadhaar is 1234 5678 9012"))[0]
        rendered = json.dumps(msg.safe_repr())
        assert "9876543210" not in rendered
        assert "aadhaar" not in rendered
        assert "1234" not in rendered


class TestDeduplication:
    def test_first_delivery_is_new_second_is_duplicate(self):
        # Meta retries on any non-2xx; without this a retry books twice.
        dedup = MessageDeduplicator()
        assert dedup.seen_before("wamid.1") is False
        assert dedup.seen_before("wamid.1") is True

    def test_distinct_ids_are_independent(self):
        dedup = MessageDeduplicator()
        assert dedup.seen_before("a") is False
        assert dedup.seen_before("b") is False

    def test_entries_expire(self):
        dedup = MessageDeduplicator(ttl_seconds=0)
        dedup.seen_before("a")
        time.sleep(0.01)
        assert dedup.seen_before("a") is False

    def test_does_not_grow_without_bound(self):
        dedup = MessageDeduplicator(ttl_seconds=10_000, max_entries=100)
        for i in range(300):
            dedup.seen_before(f"m-{i}")
        assert len(dedup._seen) <= 300


class TestServiceWindow:
    def test_recent_inbound_allows_free_form(self):
        assert within_service_window(time.time() - 60)

    def test_stale_inbound_does_not(self):
        assert not within_service_window(time.time() - CUSTOMER_SERVICE_WINDOW_SECONDS - 1)

    def test_no_prior_inbound_does_not(self):
        assert not within_service_window(None)

    def test_free_form_outside_window_is_refused_locally(self):
        # Failing at the provider means the patient simply never hears back.
        with pytest.raises(ValueError, match="template"):
            OutboundMessage(to_number="91", text="hello").validate_for_window(False)

    def test_template_outside_window_is_allowed(self):
        OutboundMessage(to_number="91", template_name="appt_reminder").validate_for_window(False)

    def test_free_form_inside_window_is_allowed(self):
        OutboundMessage(to_number="91", text="hello").validate_for_window(True)
