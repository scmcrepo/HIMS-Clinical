"""Graph nodes.

The supervisor evaluates intent and routes; sub-agents own one domain each; the
HITL node interrupts the graph and waits for a human.

A note on why escalation is an interrupt rather than an exception: an exception
unwinds the stack and loses the conversation. An interrupt paired with a durable
checkpoint lets a front-desk operator pick the run up forty minutes later, in a
different process, with the state intact — which is the only version of
human-in-the-loop that survives contact with a busy hospital.
"""

from __future__ import annotations

import time
from collections.abc import Callable
from typing import Any

from ..classifier import Classification, Classifier, RuleClassifier
from ..hms_client import HmsClient, HmsError, ToolCall
from ..observability import (
    HITL_ESCALATIONS,
    HITL_PENDING,
    NODE_TRANSITIONS,
    EventLogger,
)
from ..shadow import ShadowGuard
from ..state import (
    CHANNEL_CONSENT,
    AgentState,
    EscalationReason,
    HitlStatus,
    Intent,
    Message,
)

log = EventLogger(__name__)

SUPERVISOR = "supervisor"
SCHEDULING = "scheduling_agent"
ABHA = "abha_agent"
CLAIMS = "claims_agent"
BILLING = "billing_agent"
HITL = "hitl"
RESPOND = "respond"


def _escalate(state: AgentState, reason: EscalationReason, detail: str) -> AgentState:
    """Mark the run for human handoff. The HITL node does the actual interrupting."""
    HITL_ESCALATIONS.labels(reason=reason.value).inc()
    log.warning("agent.hitl.escalated", reason=reason.value, detail=detail,
             intent=state.get("intent"), confidence=state.get("confidence"))
    return {
        "hitl_status": HitlStatus.WAITING.value,
        "escalation_reason": reason.value,
        "escalation_detail": detail,
    }


def make_supervisor(classifier: Classifier | None = None,
                    confidence_threshold: float = 0.80,
                    max_turns: int = 25,
                    client: HmsClient | None = None) -> Callable[[AgentState], AgentState]:
    """Central routing node.

    Checks run in a deliberate order: distress and explicit human requests are
    evaluated *before* intent, because someone saying "I can't breathe" must not
    be routed into a scheduling flow just because they also said "doctor".
    """
    clf = classifier or RuleClassifier()

    def supervisor(state: AgentState) -> AgentState:
        turn = state.get("turn_count", 0) + 1
        text = state.get("latest_input", "")

        if turn > max_turns:
            # A conversation this long is going nowhere; a human should take it.
            return {"turn_count": turn,
                    **_escalate(state, EscalationReason.TURN_LIMIT,
                                f"exceeded {max_turns} turns without resolution")}

        result: Classification = clf.classify(text, language=state.get("language", "en"))

        # Consent gate. Checked after distress classification but before any
        # routing, because a patient in distress must reach a human whether or
        # not they ever ticked a box about automated messaging — refusing to help
        # someone in trouble on a consent technicality would be indefensible.
        if not result.distress and client is not None and state.get("patient_id"):
            required = CHANNEL_CONSENT.get(str(state.get("channel", "")))
            if required is not None and not client.check_consent(
                    str(state["patient_id"]), required.value):
                return {"turn_count": turn, "intent": result.intent.value,
                        "confidence": result.confidence,
                        **_escalate(state, EscalationReason.CONSENT_MISSING,
                                    f"patient has not consented to {required.value}; "
                                    "a human must obtain consent before the agent acts")}

        if result.distress:
            return {"turn_count": turn, "intent": result.intent.value,
                    "confidence": result.confidence,
                    **_escalate(state, EscalationReason.DISTRESS,
                                "possible distress or medical urgency detected in the message")}

        if result.human_requested:
            return {"turn_count": turn, "intent": result.intent.value,
                    "confidence": result.confidence,
                    **_escalate(state, EscalationReason.HUMAN_REQUESTED,
                                "the caller asked for a person")}

        if result.confidence < confidence_threshold:
            return {"turn_count": turn, "intent": result.intent.value,
                    "confidence": result.confidence,
                    **_escalate(state, EscalationReason.LOW_CONFIDENCE,
                                f"intent confidence {result.confidence:.2f} below "
                                f"threshold {confidence_threshold:.2f}")}

        log.info("agent.node.routed", intent=result.intent.value,
                 confidence=result.confidence, turn=turn)
        return {"turn_count": turn, "intent": result.intent.value,
                "confidence": result.confidence,
                "hitl_status": HitlStatus.NONE.value}

    return supervisor


def route_from_supervisor(state: AgentState) -> str:
    """Conditional edge out of the supervisor."""
    if state.get("hitl_status") == HitlStatus.WAITING.value:
        NODE_TRANSITIONS.labels(from_node=SUPERVISOR, to_node=HITL).inc()
        return HITL

    intent = state.get("intent", Intent.UNKNOWN.value)
    target = {
        Intent.SCHEDULING.value: SCHEDULING,
        Intent.ABHA.value: ABHA,
        Intent.CLAIMS.value: CLAIMS,
        Intent.BILLING.value: BILLING,
    }.get(intent, RESPOND)
    NODE_TRANSITIONS.labels(from_node=SUPERVISOR, to_node=target).inc()
    return target


def make_scheduling_agent(client: HmsClient,
                          guard: ShadowGuard | None = None) -> Callable[[AgentState], AgentState]:
    """Slot negotiation and booking.

    Two hops by necessity: the HMS requires a concrete slotId, so the agent must
    look up availability before it can book anything. That is a constraint of the
    existing `AppointmentSchedulingService`, not a design choice.
    """
    shadow = guard or ShadowGuard()

    def scheduling_agent(state: AgentState) -> AgentState:
        scratch = state.get("scratch", {})
        provider_id = scratch.get("provider_id")
        date = scratch.get("date")

        if not provider_id or not date:
            # The slot-negotiation dialogue (which doctor, which day) belongs to
            # the model. Until the hosting decision lands, ask plainly.
            return {"reply": "Which doctor would you like to see, and on which day?",
                    "outcome": "awaiting_detail"}

        try:
            slots = client.check_slot_availability(provider_id, date)
        except HmsError as exc:
            return _escalate(state, EscalationReason.TOOL_FAILURE,
                             f"slot lookup failed: {exc.code}")

        if not slots:
            return {"reply": "There are no slots available then. Would another day work?",
                    "outcome": "no_slots"}

        chosen = slots[0]
        call = ToolCall(tool="book_slot", method="POST",
                        path="/agent/v1/tools/book-slot",
                        body={"providerId": provider_id, "slotId": chosen.get("slotId"),
                              "appointmentDate": date,
                              "patientId": state.get("patient_id")})

        if not shadow.intercept(dict(state), "book_slot", call.body or {}, call.fingerprint()):
            return {"selected_slot": chosen,
                    "proposed_actions": [{"tool": "book_slot",
                                          "fingerprint": call.fingerprint()}],
                    "reply": "I've drafted a booking for review.",
                    "outcome": "shadow_proposed"}

        try:
            booking = client.book_slot(
                provider_id=provider_id, slot_id=chosen.get("slotId", ""),
                appointment_date=date, patient_id=state.get("patient_id"),
                idempotency_key=f"{state.get('run_id')}:book_slot")
        except HmsError as exc:
            return _escalate(state, EscalationReason.TOOL_FAILURE,
                             f"booking failed: {exc.code}")

        return {"selected_slot": chosen,
                "scratch": {"appointment_id": booking.get("id")},
                "reply": "Your appointment is confirmed.",
                "outcome": "booked"}

    return scheduling_agent


def make_billing_agent(client: HmsClient) -> Callable[[AgentState], AgentState]:
    def billing_agent(state: AgentState) -> AgentState:
        patient_id = state.get("patient_id")
        if not patient_id:
            return {"reply": "I'll need to identify you first. Could you share your "
                            "registered mobile number?",
                    "outcome": "awaiting_identity"}
        try:
            ledger = client.fetch_billing_ledger(patient_id)
        except HmsError as exc:
            return _escalate(state, EscalationReason.TOOL_FAILURE,
                             f"ledger fetch failed: {exc.code}")
        # The amount goes to the patient in the reply but never into a log line.
        return {"scratch": {"ledger_balance": ledger.get("balance")},
                "reply": f"Your outstanding balance is {ledger.get('balance', 'unavailable')}.",
                "outcome": "ledger_read"}

    return billing_agent


def abha_agent(state: AgentState) -> AgentState:
    """ABHA onboarding — blocked pending sandbox credentials (WO-003).

    Deliberately does not fake the flow. ABDM requires real sandbox credentials
    and a certification cycle, and a plausible-looking stub would hide that the
    integration does not exist.
    """
    return {**_escalate(state, EscalationReason.VALIDATION_FAILED,
                        "ABHA onboarding is not yet integrated (WO-003 blocked on "
                        "ABDM sandbox credentials); routing to a human"),
            "abha_status": "not_integrated"}


def claims_agent(state: AgentState) -> AgentState:
    """TPA / claims — blocked pending NHCX credentials (WO-008/WO-009)."""
    return {**_escalate(state, EscalationReason.VALIDATION_FAILED,
                        "claims automation is not yet integrated (WO-008 blocked on "
                        "NHCX gateway credentials); routing to a human"),
            "insurance_provider": state.get("insurance_provider")}


def make_hitl_node(interrupt_fn: Callable[[dict[str, Any]], Any] | None = None,
                   client: HmsClient | None = None
                   ) -> Callable[[AgentState], AgentState]:
    """Pause the graph and surface the run to the Copilot dashboard.

    `interrupt_fn` is injectable so tests can drive the pause without a
    checkpointer; production passes LangGraph's `interrupt`.
    """

    def hitl(state: AgentState) -> AgentState:
        HITL_PENDING.inc()
        # Built as a typed local: reading it back out of the payload dict widens
        # it to the union of every value type in there.
        transcript: list[dict[str, Any]] = [
            m.model_dump() if isinstance(m, Message) else dict(m)
            for m in state.get("messages", [])
        ]
        payload: dict[str, Any] = {
            "run_id": state.get("run_id"),
            "correlation_id": state.get("correlation_id"),
            "tenant_id": state.get("tenant_id"),
            "branch_id": state.get("branch_id"),
            "reason": state.get("escalation_reason"),
            "detail": state.get("escalation_detail"),
            "intent": state.get("intent"),
            "confidence": state.get("confidence"),
            "channel": state.get("channel"),
            # The operator needs the transcript to act, so it travels to the
            # dashboard — but it is never written to a log line.
            "transcript": transcript,
            "raised_at": time.time(),
        }
        log.info("agent.hitl.waiting", reason=state.get("escalation_reason"))

        # File it with the HMS so it appears in the Copilot queue and inherits a
        # deadline. Failing to file must not lose the escalation: the graph still
        # pauses and the patient is still told a person is coming.
        if client is not None:
            try:
                client.raise_escalation(
                    run_id=str(state.get("run_id", "")),
                    channel=str(state.get("channel", "unknown")),
                    reason=str(state.get("escalation_reason", "unknown")),
                    detail=state.get("escalation_detail"),
                    intent=state.get("intent"),
                    confidence=state.get("confidence"),
                    transcript=transcript,
                    proposed_actions=state.get("proposed_actions") or [],
                )
            except HmsError as exc:
                log.error("agent.hitl.file_failed", error_code=exc.code,
                          reason=state.get("escalation_reason"))

        if interrupt_fn is None:
            # No checkpointer wired: stop cleanly rather than pretending to wait.
            return {"hitl_status": HitlStatus.WAITING.value,
                    "outcome": "escalated",
                    "reply": "Let me put you through to one of our staff."}

        decision = interrupt_fn(payload)
        HITL_PENDING.dec()

        if not isinstance(decision, dict):
            decision = {"action": "unknown", "raw": decision}

        log.info("agent.hitl.resolved", action=decision.get("action"),
                 operator=decision.get("operator_id"))
        return {"hitl_status": HitlStatus.RESOLVED.value,
                "operator_decision": decision,
                "outcome": f"human_{decision.get('action', 'resolved')}",
                "reply": decision.get("reply") or "One of our staff has taken over."}

    return hitl


def respond(state: AgentState) -> AgentState:
    """Terminal node for anything with no specialist owner."""
    if state.get("reply"):
        return {}
    return {"reply": "How can I help you today?", "outcome": "smalltalk"}
