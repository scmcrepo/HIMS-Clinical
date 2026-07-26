"""ABHA and claims sub-agents: multi-turn flows, consent, PII in state."""
import json

import httpx

from hms_agent.graph import build_graph
from hms_agent.shadow import ProposalStore, ShadowGuard
from hms_agent.state import Channel, EscalationReason, HitlStatus, new_state


def client_for(handler):
    from hms_agent.hms_client import HmsClient
    return HmsClient("http://hms.test/api", "tok",
                     client=httpx.Client(transport=httpx.MockTransport(handler)))


def make_handler(*, consent=True, abha_state="NONE", otp_ok=True, claim_state=None,
                 record=None, nhcx_error=None):
    def handler(request):
        path = request.url.path
        if record is not None and request.method == "POST":
            try:
                record.append({"path": path, "body": json.loads(request.content)})
            except ValueError:
                record.append({"path": path, "body": None})
        if "tools/consent" in path:
            return httpx.Response(200, json={"data": {"granted": consent}})
        if "abha/status" in path:
            return httpx.Response(200, json={"data": {"state": abha_state}})
        if "abha/otp" in path:
            return httpx.Response(200, json={"data": {"transactionId": "txn-1"}})
        if "abha/verify" in path:
            if not otp_ok:
                return httpx.Response(400, json={
                    "message": "bad otp", "data": {"code": "OTP_INVALID", "retryable": False}})
            return httpx.Response(200, json={"data": {"abhaAddress": "ramesh@abdm"}})
        if "claims/status" in path:
            return httpx.Response(200, json={"data": {"state": claim_state or "SUBMITTED"}})
        if "claims/" in path:
            if nhcx_error:
                return httpx.Response(503, json={
                    "message": "x", "data": {"code": nhcx_error, "retryable": False}})
            return httpx.Response(200, json={"data": {"correlationId": "corr-1"}})
        return httpx.Response(200, json={"data": {}})
    return handler


def st(text, **kw):
    kw.setdefault("shadow_mode", False)
    return new_state(run_id="run-1", correlation_id="c-1", tenant_id="t-1",
                     latest_input=text, channel=Channel.WEB, **kw)


def run(g, s):
    return g.invoke(s, config={"configurable": {"thread_id": s["run_id"]}})


ABHA_MSG = "I want to create my abha health id using aadhaar"
CLAIM_MSG = "is my insurance policy going to cover this, cashless claim"


class TestAbhaFlow:
    def test_unidentified_patient_is_asked_to_identify(self):
        g = build_graph(client=client_for(make_handler()))
        assert run(g, st(ABHA_MSG))["outcome"] == "awaiting_identity"

    def test_missing_consent_escalates(self):
        # Creating a national health identity is its own purpose under DPDP.
        g = build_graph(client=client_for(make_handler(consent=False)))
        out = run(g, st(ABHA_MSG, patient_id="p-1"))
        assert out["escalation_reason"] == EscalationReason.CONSENT_MISSING.value

    def test_already_linked_is_reported_not_re_enrolled(self):
        calls = []
        g = build_graph(client=client_for(make_handler(abha_state="LINKED", record=calls)))
        out = run(g, st(ABHA_MSG, patient_id="p-1"))
        assert out["outcome"] == "already_linked"
        assert not any("abha/otp" in c["path"] for c in calls)

    def test_turn_one_requests_an_otp(self):
        g = build_graph(client=client_for(make_handler()))
        s = st(ABHA_MSG, patient_id="p-1")
        s["scratch"] = {"login_id": "9876543210", "abha_mode": "mobile"}
        out = run(g, s)
        assert out["outcome"] == "abha_otp_sent"
        assert out["abha_txn_id"] == "txn-1"

    def test_the_login_id_does_not_persist_into_state(self):
        # State is checkpointed to durable storage; an Aadhaar or mobile left
        # here becomes a copy an erasure request has to chase.
        g = build_graph(client=client_for(make_handler()))
        s = st(ABHA_MSG, patient_id="p-1")
        s["scratch"] = {"login_id": "9876543210"}
        out = run(g, s)
        assert out["scratch"].get("login_id") is None

    def test_turn_two_verifies_and_links(self):
        g = build_graph(client=client_for(make_handler()))
        s = st(ABHA_MSG, patient_id="p-1")
        s["abha_txn_id"] = "txn-1"
        s["scratch"] = {"otp": "123456"}
        out = run(g, s)
        assert out["abha_status"] == "LINKED"
        assert out["outcome"] == "abha_linked"

    def test_the_otp_is_cleared_after_use(self):
        # A persisted OTP is a live credential sitting on disk.
        g = build_graph(client=client_for(make_handler()))
        s = st(ABHA_MSG, patient_id="p-1")
        s["abha_txn_id"] = "txn-1"
        s["scratch"] = {"otp": "123456"}
        assert run(g, s)["scratch"].get("otp") is None

    def test_a_wrong_otp_offers_a_retry_rather_than_escalating(self):
        # Mistyping a code is a normal conversational event.
        g = build_graph(client=client_for(make_handler(otp_ok=False)))
        s = st(ABHA_MSG, patient_id="p-1")
        s["abha_txn_id"] = "txn-1"
        s["scratch"] = {"otp": "000000"}
        out = run(g, s)
        assert out["outcome"] == "otp_retry"
        assert out["hitl_status"] != HitlStatus.WAITING.value

    def test_repeated_otp_failures_escalate(self):
        g = build_graph(client=client_for(make_handler(otp_ok=False)))
        s = st(ABHA_MSG, patient_id="p-1")
        s["abha_txn_id"] = "txn-1"
        s["scratch"] = {"otp": "000000", "otp_attempts": 2}
        assert run(g, s)["hitl_status"] == HitlStatus.WAITING.value

    def test_shadow_mode_does_not_enrol(self, tmp_path):
        calls = []
        g = build_graph(client=client_for(make_handler(record=calls)),
                        shadow_guard=ShadowGuard(ProposalStore(tmp_path / "p.jsonl")))
        s = st(ABHA_MSG, patient_id="p-1", shadow_mode=True)
        s["abha_txn_id"] = "txn-1"
        s["scratch"] = {"otp": "123456"}
        out = run(g, s)
        assert out["outcome"] == "shadow_proposed"
        assert not any("abha/verify" in c["path"] for c in calls)


class TestClaimsFlow:
    def test_missing_consent_escalates(self):
        g = build_graph(client=client_for(make_handler(consent=False)))
        out = run(g, st(CLAIM_MSG, patient_id="p-1"))
        assert out["escalation_reason"] == EscalationReason.CONSENT_MISSING.value

    def test_unknown_payer_is_asked_for(self):
        g = build_graph(client=client_for(make_handler()))
        assert run(g, st(CLAIM_MSG, patient_id="p-1"))["outcome"] == "awaiting_payer"

    def test_eligibility_is_submitted_without_waiting(self):
        # NHCX answers on a callback hours later; blocking would hold the
        # conversation open for something that cannot resolve in-turn.
        g = build_graph(client=client_for(make_handler()))
        s = st(CLAIM_MSG, patient_id="p-1")
        s["scratch"] = {"payer_code": "ACME"}
        out = run(g, s)
        assert out["outcome"] == "claim_submitted"
        assert out["scratch"]["claim_correlation_id"] == "corr-1"

    def test_an_encounter_makes_it_a_preauth(self):
        calls = []
        g = build_graph(client=client_for(make_handler(record=calls)))
        s = st(CLAIM_MSG, patient_id="p-1")
        s["scratch"] = {"payer_code": "ACME", "encounter_id": "enc-1"}
        run(g, s)
        assert any("claims/preauth" in c["path"] for c in calls)

    def test_pending_status_sets_a_realistic_expectation(self):
        g = build_graph(client=client_for(make_handler(claim_state="ACKNOWLEDGED")))
        s = st(CLAIM_MSG, patient_id="p-1")
        s["scratch"] = {"claim_correlation_id": "corr-1"}
        assert run(g, s)["outcome"] == "claim_pending"

    def test_approval_is_reported(self):
        g = build_graph(client=client_for(make_handler(claim_state="APPROVED")))
        s = st(CLAIM_MSG, patient_id="p-1")
        s["scratch"] = {"claim_correlation_id": "corr-1"}
        assert run(g, s)["outcome"] == "claim_approved"

    def test_a_rejection_goes_to_a_human(self):
        # Financial consequences plus options the agent should not improvise.
        g = build_graph(client=client_for(make_handler(claim_state="REJECTED")))
        s = st(CLAIM_MSG, patient_id="p-1")
        s["scratch"] = {"claim_correlation_id": "corr-1"}
        assert run(g, s)["hitl_status"] == HitlStatus.WAITING.value

    def test_unconfigured_nhcx_routes_to_manual_handling(self):
        # A deployment without credentials is a config state, not a patient-
        # facing failure.
        g = build_graph(client=client_for(
            make_handler(nhcx_error="NHCX_NOT_CONFIGURED")))
        s = st(CLAIM_MSG, patient_id="p-1")
        s["scratch"] = {"payer_code": "ACME"}
        out = run(g, s)
        assert out["hitl_status"] == HitlStatus.WAITING.value
        assert "manually" in out["escalation_detail"]

    def test_shadow_mode_does_not_submit(self, tmp_path):
        calls = []
        g = build_graph(client=client_for(make_handler(record=calls)),
                        shadow_guard=ShadowGuard(ProposalStore(tmp_path / "p.jsonl")))
        s = st(CLAIM_MSG, patient_id="p-1", shadow_mode=True)
        s["scratch"] = {"payer_code": "ACME"}
        out = run(g, s)
        assert out["outcome"] == "shadow_proposed"
        assert not any("claims/eligibility" in c["path"] for c in calls)
