"""Voice pipeline: latency accounting, barge-in, streaming chunks."""
import time

import pytest

from hms_agent.channels.voice import (
    LATENCY_BUDGET_SECONDS,
    STAGE_BUDGETS,
    StageTiming,
    TurnBudget,
    TurnState,
    VoiceTurn,
    split_for_streaming,
)


class TestBudget:
    def test_records_each_stage_separately(self):
        # "The call was slow" is unactionable; per-stage timing is the point.
        b = TurnBudget()
        for stage in ("stt", "llm", "tts"):
            b.start(stage)
            b.stop(stage)
        assert set(b.breakdown()) == {"stt", "llm", "tts"}

    def test_identifies_the_worst_stage(self):
        b = TurnBudget()
        b.start("stt"); b.stop("stt")
        b.start("llm"); time.sleep(0.02); b.stop("llm")
        assert b.worst_stage() == "llm"

    def test_flags_a_stage_over_its_allowance(self):
        assert StageTiming("llm", STAGE_BUDGETS["llm"] + 0.5).over_budget
        assert not StageTiming("llm", STAGE_BUDGETS["llm"] - 0.1).over_budget

    def test_fast_turn_is_within_budget(self):
        b = TurnBudget()
        b.start("stt"); b.stop("stt")
        assert b.within_budget

    def test_slow_turn_breaches_budget(self):
        b = TurnBudget()
        b.timings.append(StageTiming("llm", LATENCY_BUDGET_SECONDS + 0.1))
        assert not b.within_budget

    def test_stopping_an_unstarted_stage_is_an_error(self):
        with pytest.raises(ValueError):
            TurnBudget().stop("stt")

    def test_stage_allowances_leave_headroom(self):
        # Network and orchestration need room inside the 1.8s target.
        assert sum(STAGE_BUDGETS.values()) < LATENCY_BUDGET_SECONDS


class TestBargeIn:
    def test_caller_speech_interrupts_the_agent(self):
        turn = VoiceTurn()
        turn.begin_speaking()
        assert turn.on_caller_speech() is True
        assert turn.state is TurnState.INTERRUPTED

    def test_further_chunks_are_suppressed_after_interruption(self):
        turn = VoiceTurn()
        turn.begin_speaking()
        turn.speak_chunk("Your appointment is")
        turn.on_caller_speech()
        assert turn.speak_chunk("confirmed for Tuesday") is False
        assert len(turn.spoken_chunks) == 1

    def test_speech_while_listening_is_not_an_interruption(self):
        turn = VoiceTurn()
        assert turn.on_caller_speech() is False

    def test_barge_in_can_be_disabled(self):
        turn = VoiceTurn(allow_barge_in=False)
        turn.begin_speaking()
        assert turn.on_caller_speech() is False
        assert turn.state is TurnState.SPEAKING

    def test_records_where_the_interruption_landed(self):
        turn = VoiceTurn()
        turn.begin_speaking()
        turn.speak_chunk("a"); turn.speak_chunk("b")
        turn.on_caller_speech()
        assert turn.interrupted_at == 2


class TestStreamingChunks:
    def test_splits_into_sentences(self):
        text = ("Your appointment with Doctor Sharma is confirmed for Tuesday. "
                "Please arrive fifteen minutes early for registration.")
        assert len(split_for_streaming(text)) >= 2

    def test_merges_short_fragments(self):
        # One provider round trip per "Yes." is a net latency loss.
        assert len(split_for_streaming("Yes. No. Ok.")) == 1

    def test_empty_text_yields_nothing(self):
        assert split_for_streaming("") == []

    def test_every_chunk_is_speakable(self):
        chunks = split_for_streaming("First sentence here is long enough. Second one also.")
        assert all(c.strip() and c.endswith(".") for c in chunks)
