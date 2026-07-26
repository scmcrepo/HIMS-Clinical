"""Graph assembly.

    START -> supervisor -> {scheduling | abha | claims | billing | hitl | respond} -> END

Sub-agents route onward to HITL when they escalate, so a tool failure mid-flow
reaches a human rather than dead-ending in an error message.
"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, START, StateGraph

from ..classifier import Classifier
from ..hms_client import HmsClient
from ..shadow import ShadowGuard
from ..state import AgentState, HitlStatus
from . import nodes as N


def _route_after_subagent(state: AgentState) -> str:
    """Sub-agents that escalated go to HITL; everything else finishes."""
    if state.get("hitl_status") == HitlStatus.WAITING.value:
        return N.HITL
    return END


def build_graph(
    *,
    client: HmsClient,
    classifier: Classifier | None = None,
    shadow_guard: ShadowGuard | None = None,
    interrupt_fn: Callable[[dict[str, Any]], Any] | None = None,
    confidence_threshold: float = 0.80,
    max_turns: int = 25,
    checkpointer: Any | None = None,
    compile_graph: bool = True,
) -> Any:
    """Assemble the agent graph.

    Everything external is injected. That is what makes the whole graph testable
    without a model provider, a database or a network — and the model provider in
    particular is blocked on a residency decision, so a graph that could not be
    built without one would have blocked the entire campaign.
    """
    # No explicit annotation: StateGraph is generic over the state type, and
    # annotating it as bare StateGraph erases the parameter to Never, which makes
    # every add_node call a type error.
    graph = StateGraph(AgentState)

    # The suppressions below apply to factory-built nodes only. LangGraph's
    # `_Node` protocol rejects a closure typed as Callable[[AgentState],
    # AgentState], even though that is exactly what it invokes at runtime. The
    # plainly-defined nodes type-check fine, so this is a stub limitation rather
    # than a real mismatch, and the runtime contract is covered by graph tests.
    graph.add_node(N.SUPERVISOR, N.make_supervisor(  # type: ignore[call-overload]
        classifier=classifier,
        confidence_threshold=confidence_threshold,
        max_turns=max_turns,
        client=client))
    graph.add_node(  # type: ignore[call-overload]
        N.SCHEDULING, N.make_scheduling_agent(client, shadow_guard))
    graph.add_node(  # type: ignore[call-overload]
        N.BILLING, N.make_billing_agent(client))
    graph.add_node(  # type: ignore[call-overload]
        N.ABHA, N.make_abha_agent(client, shadow_guard))
    graph.add_node(  # type: ignore[call-overload]
        N.CLAIMS, N.make_claims_agent(client, shadow_guard))
    graph.add_node(  # type: ignore[call-overload]
        N.HITL, N.make_hitl_node(interrupt_fn, client))
    graph.add_node(N.RESPOND, N.respond)

    graph.add_edge(START, N.SUPERVISOR)
    graph.add_conditional_edges(
        N.SUPERVISOR,
        N.route_from_supervisor,
        {N.SCHEDULING: N.SCHEDULING, N.ABHA: N.ABHA, N.CLAIMS: N.CLAIMS,
         N.BILLING: N.BILLING, N.HITL: N.HITL, N.RESPOND: N.RESPOND},
    )
    for sub in (N.SCHEDULING, N.ABHA, N.CLAIMS, N.BILLING):
        graph.add_conditional_edges(sub, _route_after_subagent,
                                    {N.HITL: N.HITL, END: END})
    graph.add_edge(N.HITL, END)
    graph.add_edge(N.RESPOND, END)

    if not compile_graph:
        return graph
    return graph.compile(checkpointer=checkpointer or MemorySaver())
