"""Scoring and rollout gating: the evidence bar for letting the agent talk to patients."""

from hms_agent.rollout import (
    MIN_SAMPLES,
    STAGE_THRESHOLDS,
    RolloutConfig,
    Stage,
    ToolScore,
    can_promote,
    score_proposals,
)


def rows(tool="book_slot", scored=0, agreed=0, unscored=0, missed=0):
    out = []
    for i in range(agreed):
        out.append({"tool": tool, "agreed": True})
    for i in range(scored - agreed):
        out.append({"tool": tool, "agreed": False})
    for i in range(unscored):
        out.append({"tool": tool, "agreed": None})
    for i in range(missed):
        out.append({"tool": tool, "agreed": False,
                    "human_escalated": True, "agent_escalated": False})
    return out


class TestToolScore:
    def test_unscored_rate_is_none_not_zero(self):
        # "No evidence" and "always wrong" must not look the same on a dashboard.
        assert ToolScore("book_slot", scored=0, agreed=0).agreement_rate is None

    def test_rate_is_computed(self):
        assert ToolScore("book_slot", scored=4, agreed=3).agreement_rate == 0.75

    def test_evidence_bar(self):
        assert not ToolScore("x", MIN_SAMPLES - 1, 0).has_enough_evidence
        assert ToolScore("x", MIN_SAMPLES, 0).has_enough_evidence


class TestScoring:
    def test_unreviewed_proposals_do_not_count_toward_the_rate(self):
        report = score_proposals(rows(scored=10, agreed=10, unscored=90),
                                 Stage.WHATSAPP_SCHEDULING)
        assert report.total_proposals == 100
        assert report.tools[0].scored == 10

    def test_a_perfect_but_tiny_sample_is_not_ready(self):
        # This is the failure mode the evidence bar exists to prevent.
        report = score_proposals(rows(scored=3, agreed=3), Stage.WHATSAPP_SCHEDULING)
        assert report.overall_rate == 1.0
        assert not report.ready
        assert any("insufficient evidence" in b for b in report.blockers)

    def test_enough_evidence_and_a_good_rate_is_ready(self):
        report = score_proposals(rows(scored=100, agreed=95), Stage.WHATSAPP_SCHEDULING)
        assert report.ready, report.blockers

    def test_a_poor_rate_blocks_even_with_evidence(self):
        report = score_proposals(rows(scored=100, agreed=70), Stage.WHATSAPP_SCHEDULING)
        assert not report.ready
        assert any("below the" in b for b in report.blockers)

    def test_no_proposals_at_all_is_blocked(self):
        report = score_proposals([], Stage.WHATSAPP_SCHEDULING)
        assert not report.ready
        assert any("run shadow mode first" in b for b in report.blockers)

    def test_claims_bar_is_stricter_than_scheduling(self):
        # A wrong claim costs money and is slow to unwind.
        assert STAGE_THRESHOLDS[Stage.CLAIMS_AUTOMATION] > STAGE_THRESHOLDS[Stage.WHATSAPP_SCHEDULING]
        good_for_whatsapp = rows(scored=100, agreed=94)
        assert score_proposals(good_for_whatsapp, Stage.WHATSAPP_SCHEDULING).ready
        assert not score_proposals(good_for_whatsapp, Stage.CLAIMS_AUTOMATION).ready

    def test_per_tool_scores_are_separated(self):
        data = rows("book_slot", scored=60, agreed=58) + rows("fetch_ledger", scored=60, agreed=20)
        report = score_proposals(data, Stage.WHATSAPP_SCHEDULING)
        assert {t.tool for t in report.tools} == {"book_slot", "fetch_ledger"}
        assert not report.ready
        assert any("fetch_ledger" in b for b in report.blockers)


class TestSafetyOverride:
    def test_a_missed_escalation_blocks_regardless_of_the_average(self):
        # A good average does not offset a conversation that should have reached
        # a human and did not.
        data = rows(scored=200, agreed=199) + rows(missed=1)
        report = score_proposals(data, Stage.WHATSAPP_SCHEDULING)
        assert report.missed_escalations == 1
        assert not report.ready
        assert any("escalated" in b for b in report.blockers)

    def test_agreement_alone_would_have_passed(self):
        clean = score_proposals(rows(scored=200, agreed=199), Stage.WHATSAPP_SCHEDULING)
        assert clean.ready


class TestPromotion:
    def _ready(self, stage):
        return score_proposals(rows(scored=200, agreed=200), stage)

    def test_one_step_promotion_with_evidence_is_allowed(self):
        ok, why = can_promote(Stage.SHADOW, Stage.WHATSAPP_SCHEDULING,
                              self._ready(Stage.WHATSAPP_SCHEDULING))
        assert ok, why

    def test_skipping_a_stage_is_refused(self):
        # Skipping means enabling a channel whose agreement was never measured.
        ok, why = can_promote(Stage.SHADOW, Stage.CLAIMS_AUTOMATION,
                              self._ready(Stage.CLAIMS_AUTOMATION))
        assert not ok
        assert "one stage at a time" in why

    def test_promotion_without_evidence_is_refused(self):
        ok, why = can_promote(Stage.SHADOW, Stage.WHATSAPP_SCHEDULING,
                              score_proposals(rows(scored=2, agreed=2),
                                              Stage.WHATSAPP_SCHEDULING))
        assert not ok
        assert "insufficient evidence" in why

    def test_a_report_for_the_wrong_stage_is_refused(self):
        ok, why = can_promote(Stage.SHADOW, Stage.WHATSAPP_SCHEDULING,
                              self._ready(Stage.VOICE_RECEPTION))
        assert not ok
        assert "readiness report is for" in why

    def test_rollback_is_always_allowed(self):
        ok, _ = can_promote(Stage.CLAIMS_AUTOMATION, Stage.SHADOW,
                            score_proposals([], Stage.SHADOW))
        assert ok

    def test_turning_off_never_needs_justification(self):
        ok, _ = can_promote(Stage.CLAIMS_AUTOMATION, Stage.OFF, score_proposals([], Stage.OFF))
        assert ok


class TestRolloutConfig:
    def test_shadow_stage_executes_nothing(self):
        cfg = RolloutConfig("t-1", stage=Stage.SHADOW, enabled_channels=frozenset({"whatsapp"}))
        assert cfg.is_shadow()
        assert not cfg.allows("whatsapp")

    def test_live_stage_allows_only_enabled_channels(self):
        cfg = RolloutConfig("t-1", stage=Stage.WHATSAPP_SCHEDULING,
                            enabled_channels=frozenset({"whatsapp"}))
        assert cfg.allows("whatsapp")
        assert not cfg.allows("voice")

    def test_kill_switch_stops_everything(self):
        # At 2am you need one flag, not a decision about which stage to unwind.
        cfg = RolloutConfig("t-1", stage=Stage.CLAIMS_AUTOMATION, kill_switch=True,
                            enabled_channels=frozenset({"whatsapp", "voice"}))
        assert not cfg.allows("whatsapp")
        assert not cfg.allows("voice")
        assert not cfg.is_shadow()

    def test_off_allows_nothing(self):
        cfg = RolloutConfig("t-1", stage=Stage.OFF, enabled_channels=frozenset({"whatsapp"}))
        assert not cfg.allows("whatsapp")
