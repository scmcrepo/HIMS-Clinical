"""PII redaction is the single control standing between this service and a DPDP
breach, so it gets tested harder than anything else here."""
import pytest

from hms_agent.pii import REDACTED, is_pii_field, mask_aadhaar, mask_free_text, mask_phone, redact


class TestFieldClassification:
    @pytest.mark.parametrize("field", [
        "name", "patient_name", "patientName", "PatientName", "tempPatientName",
        "phone", "mobile", "contactNumber", "aadhaar", "abhaAddress",
        "address", "diagnosis", "transcript", "memberId", "dob",
    ])
    def test_pii_fields_detected(self, field):
        assert is_pii_field(field)

    @pytest.mark.parametrize("field", [
        "patientId", "patient_id", "tenantId", "branchId", "abhaId",
        "slotId", "appointmentId", "status", "count",
    ])
    def test_surrogate_ids_are_not_pii(self, field):
        # Logging the id is the entire convention; flagging it would make the
        # rule unusable and get it disabled.
        assert not is_pii_field(field)


class TestMasking:
    def test_phone_keeps_last_four(self):
        assert mask_phone("+919876543210") == "XXXXXXXX3210"

    def test_phone_too_short_is_fully_redacted(self):
        assert mask_phone("12") == REDACTED

    def test_aadhaar_keeps_last_four(self):
        assert mask_aadhaar("1234 5678 9012") == "XXXX XXXX 9012"


class TestFreeText:
    def test_redacts_indian_mobile(self):
        assert "9876543210" not in mask_free_text("call me on 9876543210")

    def test_redacts_mobile_with_country_code(self):
        assert "9876543210" not in mask_free_text("reach +91 9876543210 anytime")

    def test_redacts_aadhaar(self):
        assert "1234" not in mask_free_text("aadhaar 1234 5678 9012 please")

    def test_redacts_abha_address(self):
        assert "ramesh" not in mask_free_text("my abha is ramesh.kumar@abdm").lower()

    def test_redacts_email(self):
        assert "ramesh@example.com" not in mask_free_text("write to ramesh@example.com")

    def test_leaves_ordinary_text_alone(self):
        text = "I would like an appointment tomorrow morning"
        assert mask_free_text(text) == text

    def test_does_not_mangle_uuids(self):
        # Surrogate ids travel through free text in escalation details.
        uid = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
        assert uid in mask_free_text(f"patient {uid} escalated")


class TestRecursiveRedaction:
    def test_nested_structures(self):
        payload = {
            "patientId": "p-1",
            "patient": {"name": "Ramesh Kumar", "phone": "+919876543210"},
            "appointments": [{"slotId": "s-1", "notes": "call 9876543210"}],
        }
        out = redact(payload)
        assert out["patientId"] == "p-1"
        assert out["patient"]["name"] == REDACTED
        assert out["patient"]["phone"] == "XXXXXXXX3210"
        assert "9876543210" not in out["appointments"][0]["notes"]
        assert out["appointments"][0]["slotId"] == "s-1"

    def test_survives_deep_nesting_without_recursing_forever(self):
        deep = current = {}
        for _ in range(40):
            current["child"] = {}
            current = current["child"]
        current["name"] = "Ramesh"
        assert redact(deep) is not None

    def test_handles_non_string_scalars(self):
        assert redact({"count": 3, "ok": True, "ratio": 1.5}) == {
            "count": 3, "ok": True, "ratio": 1.5}
