"""Shadow-mode scoring and rollout gating (roadmap Phase 6, P-002/P-004).

Shadow mode produces proposals. This module turns them into a decision: is the
agent good enough to turn on, for which intent, on which channel.

The point is to replace "we think it works" with a number. A hospital deciding
whether to let software talk to patients deserves evidence, and the roadmap's own
phased rollout — WhatsApp scheduling first, then voice, then claims — only means
something if each stage has a bar to clear.

Two design positions worth stating:

**Insufficient evidence is not the same as failure.** A tool with three scored
proposals and 100% agreement is not ready; it is unmeasured. ``ReadinessReport``
distinguishes the two, because conflating them is how a pilot gets promoted on a
handful of lucky samples.

**Disagreement is weighted by consequence.** A wrong booking is recoverable; a
missed distress escalation is not. Scoring treats a missed escalation as a hard
blocker regardless of the overall agreement rate.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any

from .observability import EventLogger

log = EventLogger(__name__)


class Stage(str, Enum):
    """The roadmap's phased rollout, lowest risk first."""
    OFF = "off"
    SHADOW = "shadow"
    WHATSAPP_SCHEDULING = "whatsapp_scheduling"
    VOICE_RECEPTION = "voice_reception"
    CLAIMS_AUTOMATION = "claims_automation"


# Ordering is the promotion path: a stage may only be enabled if the one before
# it has already cleared its bar.
STAGE_ORDER: list[Stage] = [
    Stage.OFF,
    Stage.SHADOW,
    Stage.WHATSAPP_SCHEDULING,
    Stage.VOICE_RECEPTION,
    Stage.CLAIMS_AUTOMATION,
]

# Minimum scored proposals before an agreement rate means anything.
MIN_SAMPLES = 50

# Per-stage agreement bar. Claims are strictest: a wrong claim submission costs
# the hospital money and the patient time, and unwinding one is slow.
STAGE_THRESHOLDS: dict[Stage, float] = {
    Stage.WHATSAPP_SCHEDULING: 0.90,
    Stage.VOICE_RECEPTION: 0.93,
    Stage.CLAIMS_AUTOMATION: 0.97,
}


@dataclass(slots=True)
class ToolScore:
    tool: str
    scored: int
    agreed: int

    @property
    def agreement_rate(self) -> float | None:
        """None when nothing has been scored — not 0.0.

        "No evidence" and "always wrong" must not look the same on a go-live
        dashboard.
        """
        return self.agreed / self.scored if self.scored else None

    @property
    def has_enough_evidence(self) -> bool:
        return self.scored >= MIN_SAMPLES


@dataclass(slots=True)
class ReadinessReport:
    stage: Stage
    threshold: float
    tools: list[ToolScore] = field(default_factory=list)
    missed_escalations: int = 0
    total_proposals: int = 0

    @property
    def overall_rate(self) -> float | None:
        scored = sum(t.scored for t in self.tools)
        if not scored:
            return None
        return sum(t.agreed for t in self.tools) / scored

    @property
    def blockers(self) -> list[str]:
        """Everything standing between here and turning this stage on."""
        out: list[str] = []

        if self.missed_escalations:
            # Not weighed against the agreement rate. A conversation that should
            # have reached a human and did not is a safety failure, and a good
            # average does not offset it.
            out.append(
                f"{self.missed_escalations} conversation(s) should have been escalated "
                "to a human and were not — resolve before enabling this stage")

        thin = [t.tool for t in self.tools if not t.has_enough_evidence]
        if thin:
            out.append(
                f"insufficient evidence for {', '.join(sorted(thin))} "
                f"(need {MIN_SAMPLES} scored proposals each)")

        for t in self.tools:
            rate = t.agreement_rate
            if rate is not None and t.has_enough_evidence and rate < self.threshold:
                out.append(
                    f"{t.tool} agreement {rate:.1%} is below the "
                    f"{self.threshold:.0%} bar for {self.stage.value}")

        if not self.tools:
            out.append("no scored proposals at all — run shadow mode first")

        return out

    @property
    def ready(self) -> bool:
        return not self.blockers

    def summary(self) -> dict[str, Any]:
        return {
            "stage": self.stage.value,
            "threshold": self.threshold,
            "ready": self.ready,
            "overall_agreement": self.overall_rate,
            "total_proposals": self.total_proposals,
            "missed_escalations": self.missed_escalations,
            "tools": [
                {"tool": t.tool, "scored": t.scored, "agreed": t.agreed,
                 "agreement": t.agreement_rate,
                 "enough_evidence": t.has_enough_evidence}
                for t in self.tools
            ],
            "blockers": self.blockers,
        }


def score_proposals(rows: list[dict[str, Any]], stage: Stage) -> ReadinessReport:
    """Turn recorded proposals into a readiness verdict.

    ``rows`` come from ``ProposalStore.read_all()``. Rows with ``agreed`` unset
    have not been reviewed yet and are counted toward the total but not toward
    the rate — an unreviewed proposal is not evidence of anything.
    """
    threshold = STAGE_THRESHOLDS.get(stage, 1.0)
    report = ReadinessReport(stage=stage, threshold=threshold, total_proposals=len(rows))

    per_tool: dict[str, ToolScore] = {}
    for row in rows:
        tool = str(row.get("tool", "unknown"))
        score = per_tool.setdefault(tool, ToolScore(tool=tool, scored=0, agreed=0))
        agreed = row.get("agreed")
        if agreed is None:
            continue
        score.scored += 1
        if agreed:
            score.agreed += 1
        # A proposal the human escalated but the agent did not is a safety miss.
        if not agreed and row.get("human_escalated") and not row.get("agent_escalated"):
            report.missed_escalations += 1

    report.tools = sorted(per_tool.values(), key=lambda t: t.tool)
    log.info("rollout.scored", stage=stage.value, tools=len(report.tools),
             overall=report.overall_rate, ready=report.ready)
    return report


@dataclass(slots=True)
class RolloutConfig:
    """Which stages are live, per tenant.

    ``kill_switch`` is separate from stage and deliberately blunt: when something
    is going wrong at 2am, whoever is awake needs one flag that stops everything,
    not a decision about which stage to unwind.
    """
    tenant_id: str
    stage: Stage = Stage.SHADOW
    kill_switch: bool = False
    enabled_channels: frozenset[str] = frozenset()

    def allows(self, channel: str) -> bool:
        if self.kill_switch or self.stage in (Stage.OFF, Stage.SHADOW):
            return False
        return channel in self.enabled_channels

    def is_shadow(self) -> bool:
        """Shadow mode: read, propose, never execute."""
        return self.stage is Stage.SHADOW and not self.kill_switch


def can_promote(current: Stage, target: Stage, report: ReadinessReport) -> tuple[bool, str]:
    """Whether a tenant may move from one stage to the next.

    Promotion is one step at a time and evidence-gated. Skipping a stage means
    enabling a channel whose agreement rate was never measured, which is exactly
    the failure the phased rollout exists to prevent.
    """
    if target is Stage.OFF:
        return True, "turning the agent off never needs justification"

    try:
        ci, ti = STAGE_ORDER.index(current), STAGE_ORDER.index(target)
    except ValueError:
        return False, f"unknown stage: {current} -> {target}"

    if ti < ci:
        return True, "rolling back is always allowed"
    if ti == ci:
        return True, "no change"
    if ti - ci > 1:
        return False, (
            f"cannot skip from {current.value} to {target.value}; "
            f"promote one stage at a time so each is measured")
    if report.stage is not target:
        return False, f"readiness report is for {report.stage.value}, not {target.value}"
    if not report.ready:
        return False, "; ".join(report.blockers)
    return True, f"agreement {report.overall_rate:.1%} clears the {report.threshold:.0%} bar"
