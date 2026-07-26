"""Graph behaviour: routing, escalation, HITL interrupt and resume, shadow mode."""
import httpx

from hms_agent.classifier import RuleClassifier
from hms_agent.graph import build_graph
from hms_agent.hms_client import HmsClient
from hms_agent.shadow import ProposalStore, ShadowGuard
from hms_agent.state import EscalationReason, HitlStatus, Intent, new_state


def client_for(handler):
    return HmsClient("http://hms.test/api", "tok",
                     client=httpx.Client(transport=httpx.MockTransport(handler)))


def ok_handler(request):
    if "slot-availability" in request.url.path:
        return httpx.Response(200, json={"data": [
            {"slotId": "s-1", "start": "10:00", "remaining": 2}]})
    if "book-slot" in request.url.path:
        return httpx.Response(200, json={"data": {"id": "appt-1"}})
    if "billing-ledger" in request.url.path:
        return httpx.Response(200, json={"data": {"balance": "1200.00"}})
    return httpx.Response(200, json={"data": {}})


def run(graph, state):
    return graph.invoke(state, config={"configurable": {"thread_id": state["run_id"]}})


def base_state(text, **kw):
    return new_state(run_id=kw.pop("run_id", "run-1"), correlation_id="c-1",
                     tenant_id="t-1", latest_input=text, **kw)


class TestClassifier:
    def test_confident_scheduling_intent(self):
        r = RuleClassifier().classify("I want to book an appointment with a doctor tomorrow")
        assert r.intent is Intent.SCHEDULING
        assert r.confidence >= 0.80

    def test_vague_input_is_low_confidence(self):
        # Honest calibration matters: an over-confident classifier skips the
        # human handoff exactly when it is most needed.
        assert RuleClassifier().classify("hmm").confidence < 0.80

    def test_distress_detected(self):
        assert RuleClassifier().classify("my father has chest pain, urgent").distress

    def test_distress_in_code_mixed_speech(self):
        assert RuleClassifier().classify("bahut dard ho raha hai").distress

    def test_human_request_detected(self):
        assert RuleClassifier().classify("let me talk to a real person").human_requested


class TestRouting:
    def test_scheduling_routes_to_scheduling_agent(self):
        g = build_graph(client=client_for(ok_handler))
        out = run(g, base_state("book an appointment with a doctor tomorrow"))
        assert out["intent"] == Intent.SCHEDULING.value
        assert out["hitl_status"] != HitlStatus.WAITING.value

    def test_billing_intent_reaches_the_ledger(self):
        g = build_graph(client=client_for(ok_handler), shadow_guard=ShadowGuard())
        out = run(g, base_state("what is my outstanding bill payment balance",
                                patient_id="p-1"))
        assert out["outcome"] == "ledger_read"
        assert "1200.00" in out["reply"]


class TestEscalation:
    def test_distress_escalates_before_intent_routing(self):
        # "doctor" would otherwise route this into scheduling.
        g = build_graph(client=client_for(ok_handler))
        out = run(g, base_state("I can't breathe, I need a doctor now"))
        assert out["hitl_status"] == HitlStatus.WAITING.value
        assert out["escalation_reason"] == EscalationReason.DISTRESS.value

    def test_explicit_human_request_escalates(self):
        g = build_graph(client=client_for(ok_handler))
        out = run(g, base_state("please connect me to a human"))
        assert out["escalation_reason"] == EscalationReason.HUMAN_REQUESTED.value

    def test_low_confidence_escalates(self):
        g = build_graph(client=client_for(ok_handler))
        out = run(g, base_state("uh"))
        assert out["escalation_reason"] == EscalationReason.LOW_CONFIDENCE.value

    def test_threshold_is_respected(self):
        g = build_graph(client=client_for(ok_handler), confidence_threshold=0.99)
        out = run(g, base_state("book an appointment with a doctor tomorrow"))
        assert out["escalation_reason"] == EscalationReason.LOW_CONFIDENCE.value

    def test_abha_is_blocked_not_faked(self):
        # A plausible stub would hide that ABDM integration does not exist.
        g = build_graph(client=client_for(ok_handler))
        out = run(g, base_state("I want to create my abha health id"))
        assert out["hitl_status"] == HitlStatus.WAITING.value
        assert out["abha_status"] == "not_integrated"

    def test_tool_failure_escalates_rather_than_dead_ending(self):
        def failing(request):
            return httpx.Response(500, json={"message": "db down"})

        g = build_graph(client=client_for(failing), shadow_guard=ShadowGuard())
        state = base_state("book an appointment with a doctor tomorrow")
        state["scratch"] = {"provider_id": "prov-1", "date": "2026-08-01"}
        out = run(g, state)
        assert out["escalation_reason"] == EscalationReason.TOOL_FAILURE.value


class TestShadowMode:
    def test_shadow_mode_proposes_without_booking(self, tmp_path):
        booked = []

        def handler(request):
            if "book-slot" in request.url.path:
                booked.append(1)
            return ok_handler(request)

        store = ProposalStore(tmp_path / "p.jsonl")
        g = build_graph(client=client_for(handler), shadow_guard=ShadowGuard(store))
        state = base_state("book an appointment with a doctor tomorrow", shadow_mode=True)
        state["scratch"] = {"provider_id": "prov-1", "date": "2026-08-01"}
        out = run(g, state)

        assert out["outcome"] == "shadow_proposed"
        assert not booked, "shadow mode must never execute a write"
        assert len(store.read_all()) == 1

    def test_live_mode_actually_books(self, tmp_path):
        booked = []

        def handler(request):
            if "book-slot" in request.url.path:
                booked.append(1)
            return ok_handler(request)

        g = build_graph(client=client_for(handler),
                        shadow_guard=ShadowGuard(ProposalStore(tmp_path / "p.jsonl")))
        state = base_state("book an appointment with a doctor tomorrow", shadow_mode=False)
        state["scratch"] = {"provider_id": "prov-1", "date": "2026-08-01"}
        out = run(g, state)

        assert out["outcome"] == "booked"
        assert booked


class TestHitl:
    def test_interrupt_receives_the_full_context_an_operator_needs(self):
        captured = {}

        def fake_interrupt(payload):
            captured.update(payload)
            return {"action": "approved", "operator_id": "u-9",
                    "reply": "Booked for you."}

        g = build_graph(client=client_for(ok_handler), interrupt_fn=fake_interrupt)
        out = run(g, base_state("please connect me to a human"))

        # The dashboard cannot be useful without these.
        assert captured["reason"] == EscalationReason.HUMAN_REQUESTED.value
        assert captured["correlation_id"] == "c-1"
        assert captured["tenant_id"] == "t-1"
        assert captured["transcript"], "operator needs the conversation to act on it"
        assert out["hitl_status"] == HitlStatus.RESOLVED.value
        assert out["operator_decision"]["operator_id"] == "u-9"
        assert out["reply"] == "Booked for you."

    def test_without_a_checkpointer_it_stops_cleanly(self):
        g = build_graph(client=client_for(ok_handler), interrupt_fn=None)
        out = run(g, base_state("please connect me to a human"))
        assert out["hitl_status"] == HitlStatus.WAITING.value
        assert out["outcome"] == "escalated"

    def test_operator_override_is_recorded(self):
        def fake_interrupt(payload):
            return {"action": "overridden", "operator_id": "u-3",
                    "reason": "patient wanted a different doctor"}

        g = build_graph(client=client_for(ok_handler), interrupt_fn=fake_interrupt)
        out = run(g, base_state("uh"))
        assert out["outcome"] == "human_overridden"
        assert out["operator_decision"]["reason"]


class TestStateHygiene:
    def test_turn_limit_escalates(self):
        g = build_graph(client=client_for(ok_handler), max_turns=1)
        state = base_state("book an appointment with a doctor tomorrow")
        state["turn_count"] = 5
        out = run(g, state)
        assert out["escalation_reason"] == EscalationReason.TURN_LIMIT.value

    def test_tenant_and_branch_survive_the_run(self):
        # Nothing here has ambient tenant context; losing it mid-graph would mean
        # acting on the wrong hospital.
        g = build_graph(client=client_for(ok_handler))
        state = base_state("book an appointment with a doctor tomorrow", branch_id="b-1")
        out = run(g, state)
        assert out["tenant_id"] == "t-1"
        assert out["branch_id"] == "b-1"
