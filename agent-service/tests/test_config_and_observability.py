"""Residency enforcement and log hygiene."""
import json
import logging

import pytest

from hms_agent.config import ResidencyViolation, Settings, load_settings
from hms_agent.observability import (
    EventLogger,
    JsonFormatter,
    correlation_context,
    get_context,
)


class TestResidency:
    def test_offshore_llm_endpoint_is_rejected(self):
        # The failure this prevents is silent: everything works, patient
        # utterances just quietly leave the country.
        with pytest.raises(ResidencyViolation) as exc:
            load_settings(llm_endpoint="https://api.someprovider.com/v1")
        assert "India" in str(exc.value)

    def test_india_region_host_is_allowed(self):
        s = load_settings(llm_endpoint="https://dhruva.bhashini.gov.in/services")
        assert s.llm_endpoint

    def test_subdomain_of_allowed_host_is_allowed(self):
        assert load_settings(llm_endpoint="https://x.abdm.gov.in/api")

    def test_empty_endpoint_is_fine(self):
        # Blocked-pending-decision is a valid state; it must not crash boot.
        assert load_settings().llm_endpoint == ""

    def test_enforcement_can_be_disabled_explicitly(self):
        s = load_settings(llm_endpoint="https://api.elsewhere.com",
                          enforce_data_residency=False)
        assert s.llm_endpoint

    def test_confidence_threshold_is_validated(self):
        with pytest.raises(ValueError):
            Settings(confidence_threshold=1.7)

    def test_token_is_not_in_repr(self):
        s = Settings(hms_agent_token="super-secret-token")
        assert "super-secret-token" not in repr(s)


class TestCorrelationContext:
    def test_context_is_restored_after_the_block(self):
        with correlation_context(correlationId="a"):
            assert get_context()["correlationId"] == "a"
        assert "correlationId" not in get_context()

    def test_context_is_restored_on_exception(self):
        # A leaked id would misattribute the next run's log lines.
        try:
            with correlation_context(correlationId="a"):
                raise RuntimeError("boom")
        except RuntimeError:
            pass
        assert "correlationId" not in get_context()

    def test_nested_contexts_merge_then_unwind(self):
        with correlation_context(correlationId="a"):
            with correlation_context(runId="r"):
                ctx = get_context()
                assert ctx["correlationId"] == "a" and ctx["runId"] == "r"
            assert "runId" not in get_context()


class TestLogHygiene:
    def _emit(self, caplog, **fields):
        logger = EventLogger("test.hygiene")
        with caplog.at_level(logging.INFO):
            logger.info("agent.test.event", **fields)
        record = caplog.records[-1]
        return json.loads(JsonFormatter().format(record))

    def test_event_name_and_context_are_present(self, caplog):
        with correlation_context(correlationId="c-9", tenantId="t-1"):
            payload = self._emit(caplog, tool="book_slot")
        assert payload["event"] == "agent.test.event"
        assert payload["correlationId"] == "c-9"
        assert payload["tenantId"] == "t-1"

    def test_pii_fields_are_redacted_in_log_output(self, caplog):
        payload = self._emit(caplog, patient_name="Ramesh Kumar",
                             phone="+919876543210", patientId="p-1")
        assert payload["patient_name"] == "[REDACTED]"
        assert "9876543210" not in json.dumps(payload)
        assert payload["patientId"] == "p-1"

    def test_free_text_identifiers_are_redacted(self, caplog):
        payload = self._emit(caplog, detail="patient rang from 9876543210")
        assert "9876543210" not in json.dumps(payload)
