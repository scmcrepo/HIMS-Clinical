"""Gateway client: retries, idempotency, correlation propagation, error mapping."""
import httpx
import pytest

from hms_agent.hms_client import HmsAuthError, HmsClient, HmsError, ToolCall
from hms_agent.observability import correlation_context


def make_client(handler, **kw):
    transport = httpx.MockTransport(handler)
    return HmsClient("http://hms.test/api", "tok", client=httpx.Client(transport=transport), **kw)


class TestHeaders:
    def test_propagates_correlation_run_and_branch(self):
        seen = {}

        def handler(request):
            seen.update(request.headers)
            return httpx.Response(200, json={"data": []})

        client = make_client(handler)
        with correlation_context(correlationId="c-1", runId="r-1", branchId="b-1"):
            client.check_slot_availability("prov", "2026-08-01")

        assert seen["x-correlation-id"] == "c-1"
        assert seen["x-run-id"] == "r-1"
        assert seen["x-branch-id"] == "b-1"
        assert seen["authorization"] == "Bearer tok"

    def test_book_slot_always_sends_an_idempotency_key(self):
        # An LLM that retries without one double-books a patient.
        seen = {}

        def handler(request):
            seen.update(request.headers)
            return httpx.Response(200, json={"data": {"id": "a-1"}})

        make_client(handler).book_slot(
            provider_id="p", slot_id="s", appointment_date="2026-08-01")
        assert seen.get("x-idempotency-key")

    def test_caller_supplied_idempotency_key_is_used(self):
        seen = {}

        def handler(request):
            seen.update(request.headers)
            return httpx.Response(200, json={"data": {}})

        make_client(handler).book_slot(
            provider_id="p", slot_id="s", appointment_date="2026-08-01",
            idempotency_key="run-42:book_slot")
        assert seen["x-idempotency-key"] == "run-42:book_slot"


class TestErrorMapping:
    def test_structured_error_preserves_code_and_retryable(self):
        def handler(request):
            return httpx.Response(409, json={
                "message": "Slot is fully booked",
                "data": {"code": "SLOT_FULL", "retryable": False}})

        with pytest.raises(HmsError) as exc:
            make_client(handler).book_slot(
                provider_id="p", slot_id="s", appointment_date="2026-08-01")
        assert exc.value.code == "SLOT_FULL"
        assert exc.value.retryable is False

    def test_401_is_an_auth_error_and_never_retried(self):
        calls = []

        def handler(request):
            calls.append(1)
            return httpx.Response(401, json={"message": "bad token"})

        with pytest.raises(HmsAuthError):
            make_client(handler, max_retries=3).check_bed_occupancy()
        assert len(calls) == 1, "a revoked token stays revoked; retrying is pointless"

    def test_403_is_an_auth_error(self):
        def handler(request):
            return httpx.Response(403, json={"message": "out of scope"})

        with pytest.raises(HmsAuthError):
            make_client(handler).check_bed_occupancy()


class TestRetries:
    def test_5xx_is_retried_then_succeeds(self):
        calls = []

        def handler(request):
            calls.append(1)
            if len(calls) < 3:
                return httpx.Response(503, json={"message": "unavailable"})
            return httpx.Response(200, json={"data": {"free": 4}})

        result = make_client(handler, max_retries=3).check_bed_occupancy()
        assert result == {"free": 4}
        assert len(calls) == 3

    def test_gives_up_after_max_retries(self):
        calls = []

        def handler(request):
            calls.append(1)
            return httpx.Response(500, json={"message": "boom"})

        with pytest.raises(HmsError):
            make_client(handler, max_retries=2).check_bed_occupancy()
        assert len(calls) == 3  # initial + 2 retries

    def test_non_retryable_4xx_is_not_retried(self):
        calls = []

        def handler(request):
            calls.append(1)
            return httpx.Response(400, json={
                "message": "bad", "data": {"code": "BAD_INPUT", "retryable": False}})

        with pytest.raises(HmsError):
            make_client(handler, max_retries=3).check_bed_occupancy()
        assert len(calls) == 1


class TestToolCall:
    def test_fingerprint_is_stable_across_key_order(self):
        a = ToolCall(tool="book_slot", method="POST", path="/x",
                     body={"slotId": "s", "providerId": "p"})
        b = ToolCall(tool="book_slot", method="POST", path="/x",
                     body={"providerId": "p", "slotId": "s"})
        assert a.fingerprint() == b.fingerprint()

    def test_fingerprint_changes_with_arguments(self):
        a = ToolCall(tool="book_slot", method="POST", path="/x", body={"slotId": "s1"})
        b = ToolCall(tool="book_slot", method="POST", path="/x", body={"slotId": "s2"})
        assert a.fingerprint() != b.fingerprint()
