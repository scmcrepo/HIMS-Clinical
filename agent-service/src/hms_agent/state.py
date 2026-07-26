"""The graph state.

Two rules shape this structure.

First, PII stays out wherever possible. LangGraph checkpointers persist state by
default, so every field here is a data copy that a DPDP erasure request would
have to reach. Holding `patient_id` and re-fetching per turn keeps the blast
radius at one table.

Second, tenant and branch are carried explicitly rather than inferred. The Java
side resolves them from a ThreadLocal that does not survive a thread hop; here
there is no ambient context at all, so anything that acts on hospital data must
be told which hospital.
"""

from __future__ import annotations

from enum import Enum
from typing import Annotated, Any, Literal, TypedDict

from pydantic import BaseModel


class Channel(str, Enum):
    WHATSAPP = "whatsapp"
    VOICE = "voice"
    WEB = "web"
    TEST = "test"


class Intent(str, Enum):
    SCHEDULING = "scheduling"
    ABHA = "abha"
    CLAIMS = "claims"
    BILLING = "billing"
    SMALLTALK = "smalltalk"
    UNKNOWN = "unknown"


class HitlStatus(str, Enum):
    NONE = "none"
    WAITING = "waiting_for_human"
    RESOLVED = "resolved"
    TIMED_OUT = "timed_out"


class ConsentPurpose(str, Enum):
    """Mirrors the backend ConsentPurpose vocabulary."""
    TREATMENT = "TREATMENT"
    AGENT_MESSAGING = "AGENT_MESSAGING"
    AGENT_VOICE = "AGENT_VOICE"
    INSURANCE_CLAIM = "INSURANCE_CLAIM"
    ABHA_LINKAGE = "ABHA_LINKAGE"


# Which consent a channel requires before the agent may act at all.
CHANNEL_CONSENT: dict[str, ConsentPurpose] = {
    "whatsapp": ConsentPurpose.AGENT_MESSAGING,
    "voice": ConsentPurpose.AGENT_VOICE,
}


class EscalationReason(str, Enum):
    LOW_CONFIDENCE = "low_confidence"
    HUMAN_REQUESTED = "human_requested"
    DISTRESS = "distress"
    VALIDATION_FAILED = "validation_failed"
    TOOL_FAILURE = "tool_failure"
    TURN_LIMIT = "turn_limit"
    CONSENT_MISSING = "consent_missing"


class Message(BaseModel):
    role: Literal["user", "agent", "human_operator", "system"]
    content: str
    at: str | None = None


def append_messages(left: list[Message] | None, right: list[Message] | None) -> list[Message]:
    """Reducer: message lists accumulate rather than replace."""
    return (left or []) + (right or [])


def merge_dict(left: dict[str, Any] | None, right: dict[str, Any] | None) -> dict[str, Any]:
    return {**(left or {}), **(right or {})}


def append_list(left: list[Any] | None, right: list[Any] | None) -> list[Any]:
    return (left or []) + (right or [])


class AgentState(TypedDict, total=False):
    """State threaded through the graph.

    Reducers are attached where concurrent or repeated node writes should
    accumulate rather than clobber.
    """

    # ── identity and routing context (never optional in practice)
    run_id: str
    correlation_id: str
    tenant_id: str
    branch_id: str | None
    channel: str
    language: str

    # ── conversation
    messages: Annotated[list[Message], append_messages]
    latest_input: str

    # ── intent
    intent: str
    confidence: float
    turn_count: int

    # ── the domain fields the roadmap names
    patient_id: str | None
    abha_status: str | None
    selected_slot: dict[str, Any] | None
    insurance_provider: str | None

    # ── working data (no PII: ids and structural facts only)
    scratch: Annotated[dict[str, Any], merge_dict]

    # ── consent, per DPDP purpose limitation
    consent_purposes: Annotated[list[str], append_list]

    # ── human-in-the-loop
    hitl_status: str
    escalation_reason: str | None
    escalation_detail: str | None
    operator_decision: dict[str, Any] | None

    # ── shadow mode
    shadow_mode: bool
    proposed_actions: Annotated[list[dict[str, Any]], append_list]

    # ── outcome
    outcome: str | None
    reply: str | None


def new_state(
    *,
    run_id: str,
    correlation_id: str,
    tenant_id: str,
    latest_input: str,
    branch_id: str | None = None,
    channel: Channel | str = Channel.TEST,
    language: str = "en",
    patient_id: str | None = None,
    shadow_mode: bool = True,
) -> AgentState:
    return AgentState(
        run_id=run_id,
        correlation_id=correlation_id,
        tenant_id=tenant_id,
        branch_id=branch_id,
        channel=channel.value if isinstance(channel, Channel) else channel,
        language=language,
        messages=[Message(role="user", content=latest_input)],
        latest_input=latest_input,
        intent=Intent.UNKNOWN.value,
        confidence=0.0,
        turn_count=0,
        patient_id=patient_id,
        abha_status=None,
        selected_slot=None,
        insurance_provider=None,
        scratch={},
        consent_purposes=[],
        hitl_status=HitlStatus.NONE.value,
        escalation_reason=None,
        escalation_detail=None,
        operator_decision=None,
        shadow_mode=shadow_mode,
        proposed_actions=[],
        outcome=None,
        reply=None,
    )
