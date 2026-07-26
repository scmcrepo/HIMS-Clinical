"""Escalations must reach the Copilot queue — and must not be lost if that fails."""
import httpx

from hms_agent.graph import build_graph
from hms_agent.hms_client import HmsClient
from hms_agent.state import EscalationReason, HitlStatus, new_state


def client_for(handler):
    return HmsClient("http://hms.test/api", "tok",
                     client=httpx.Client(transport=httpx.MockTransport(handler)))


def base_state(text, **kw):
    return new_state(run_id=kw.pop("run_id", "run-1"), correlation_id="c-1",
                     tenant_id="t-1", latest_input=text, **kw)


def run(graph, state):
    return graph.invoke(state, config={"configurable": {"thread_id": state["run_id"]}})


class TestEscalationFiling:
    def test_escalation_is_filed_with_the_gateway(self):
        filed = []

        def handler(request):
            if "hitl/escalations" in request.url.path:
                filed.append(__import__("json").loads(request.content))
                return httpx.Response(201, json={"data": {"id": "e-1"}})
            return httpx.Response(200, json={"data": {}})

        g = build_graph(client=client_for(handler))
        run(g, base_state("please connect me to a human"))

        assert len(filed) == 1
        body = filed[0]
        assert body["reason"] == EscalationReason.HUMAN_REQUESTED.value
        assert body["runId"] == "run-1"
        assert body["transcript"], "the operator cannot judge a conversation they cannot read"

    def test_tenant_is_not_sent_in_the_body(self):
        # The gateway takes tenant from the token. Sending it would let a caller
        # file into another hospital's queue by asserting an id.
        filed = []

        def handler(request):
            if "hitl/escalations" in request.url.path:
                filed.append(__import__("json").loads(request.content))
            return httpx.Response(201, json={"data": {}})

        g = build_graph(client=client_for(handler))
        run(g, base_state("please connect me to a human"))

        assert "tenantId" not in filed[0]
        assert "tenant_id" not in filed[0]

    def test_a_filing_failure_does_not_lose_the_escalation(self):
        # If the queue is unreachable the graph must still pause and the patient
        # must still be told a person is coming.
        def handler(request):
            if "hitl/escalations" in request.url.path:
                return httpx.Response(500, json={"message": "queue down"})
            return httpx.Response(200, json={"data": {}})

        g = build_graph(client=client_for(handler))
        out = run(g, base_state("please connect me to a human"))

        assert out["hitl_status"] == HitlStatus.WAITING.value
        assert out["reply"], "the patient must still hear something"

    def test_distress_is_filed_with_its_reason_intact(self):
        # The backend gives distress a much shorter deadline, so the reason
        # reaching it correctly is a safety property.
        filed = []

        def handler(request):
            if "hitl/escalations" in request.url.path:
                filed.append(__import__("json").loads(request.content))
            return httpx.Response(201, json={"data": {}})

        g = build_graph(client=client_for(handler))
        run(g, base_state("my father has chest pain, this is urgent"))

        assert filed[0]["reason"] == EscalationReason.DISTRESS.value

    def test_no_escalation_is_filed_on_a_clean_run(self):
        filed = []

        def handler(request):
            if "hitl/escalations" in request.url.path:
                filed.append(1)
            if "bed-occupancy" in request.url.path:
                return httpx.Response(200, json={"data": {"free": 3}})
            return httpx.Response(200, json={"data": {}})

        g = build_graph(client=client_for(handler))
        run(g, base_state("book an appointment with a doctor tomorrow"))

        assert filed == []
