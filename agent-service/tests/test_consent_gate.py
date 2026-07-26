"""The consent gate: no automated interaction without a recorded purpose."""
import httpx

from hms_agent.graph import build_graph
from hms_agent.hms_client import HmsClient
from hms_agent.state import Channel, EscalationReason, HitlStatus, new_state


def client_for(handler):
    return HmsClient("http://hms.test/api", "tok",
                     client=httpx.Client(transport=httpx.MockTransport(handler)))


def handler_with_consent(granted, record=None):
    def handler(request):
        if "tools/consent" in request.url.path:
            if record is not None:
                record.append(dict(request.url.params))
            return httpx.Response(200, json={"data": {"granted": granted}})
        if "slot-availability" in request.url.path:
            return httpx.Response(200, json={"data": [{"slotId": "s-1"}]})
        return httpx.Response(200, json={"data": {}})
    return handler


def state(text, **kw):
    return new_state(run_id="run-1", correlation_id="c-1", tenant_id="t-1",
                     latest_input=text, **kw)


def run(g, s):
    return g.invoke(s, config={"configurable": {"thread_id": s["run_id"]}})


class TestConsentGate:
    def test_missing_consent_escalates_instead_of_acting(self):
        g = build_graph(client=client_for(handler_with_consent(False)))
        out = run(g, state("book an appointment with a doctor tomorrow",
                           channel=Channel.WHATSAPP, patient_id="p-1"))
        assert out["hitl_status"] == HitlStatus.WAITING.value
        assert out["escalation_reason"] == EscalationReason.CONSENT_MISSING.value

    def test_granted_consent_lets_the_run_proceed(self):
        g = build_graph(client=client_for(handler_with_consent(True)))
        out = run(g, state("book an appointment with a doctor tomorrow",
                           channel=Channel.WHATSAPP, patient_id="p-1"))
        assert out["escalation_reason"] != EscalationReason.CONSENT_MISSING.value

    def test_the_right_purpose_is_checked_per_channel(self):
        seen = []
        g = build_graph(client=client_for(handler_with_consent(True, seen)))
        run(g, state("book an appointment with a doctor tomorrow",
                     channel=Channel.VOICE, patient_id="p-1"))
        assert seen and seen[0]["purpose"] == "AGENT_VOICE"

    def test_a_failed_consent_check_fails_closed(self):
        # "Could not verify" must never be treated as "granted".
        def handler(request):
            if "tools/consent" in request.url.path:
                return httpx.Response(500, json={"message": "consent service down"})
            return httpx.Response(200, json={"data": {}})

        g = build_graph(client=client_for(handler))
        out = run(g, state("book an appointment with a doctor tomorrow",
                           channel=Channel.WHATSAPP, patient_id="p-1"))
        assert out["escalation_reason"] == EscalationReason.CONSENT_MISSING.value

    def test_distress_bypasses_the_consent_gate(self):
        # Refusing to help someone in trouble on a consent technicality would be
        # indefensible. They reach a human either way.
        checked = []
        g = build_graph(client=client_for(handler_with_consent(False, checked)))
        out = run(g, state("my father has chest pain, urgent",
                           channel=Channel.WHATSAPP, patient_id="p-1"))
        assert out["escalation_reason"] == EscalationReason.DISTRESS.value
        assert checked == []

    def test_unidentified_callers_are_not_consent_gated(self):
        # No patient id means nothing personal is being acted on yet; the gate
        # applies once they are identified.
        checked = []
        g = build_graph(client=client_for(handler_with_consent(False, checked)))
        out = run(g, state("book an appointment with a doctor tomorrow",
                           channel=Channel.WHATSAPP))
        assert out["escalation_reason"] != EscalationReason.CONSENT_MISSING.value
        assert checked == []

    def test_channels_without_a_mapped_purpose_are_not_gated(self):
        g = build_graph(client=client_for(handler_with_consent(False)))
        out = run(g, state("book an appointment with a doctor tomorrow",
                           channel=Channel.WEB, patient_id="p-1"))
        assert out["escalation_reason"] != EscalationReason.CONSENT_MISSING.value
