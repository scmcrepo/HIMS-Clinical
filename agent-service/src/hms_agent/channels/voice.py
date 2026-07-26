"""Voice pipeline (roadmap Phase 3).

The roadmap sets an end-to-end budget of 1.8 seconds. That number is only
defensible if each stage is measured separately — "the call felt slow" is not
something you can fix. This module owns the budget accounting and the barge-in
state machine; the actual STT/TTS providers sit behind interfaces because
choosing them is a residency decision (see config.assert_residency).

Barge-in is treated as a requirement, not a refinement. A caller who cannot
interrupt a talking agent will talk over it, the STT will capture the agent's own
prompt mixed with their speech, and the turn is lost.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Protocol

from ..observability import EventLogger

log = EventLogger(__name__)

# Roadmap target. Exceeding it means callers start speaking over the agent.
LATENCY_BUDGET_SECONDS = 1.8

# Indicative per-stage allowances that sum under budget with headroom for
# network and orchestration. Used to attribute a breach to a stage rather than
# to declare a stage "wrong".
STAGE_BUDGETS = {"stt": 0.40, "llm": 0.80, "tts": 0.30}


class TurnState(str, Enum):
    LISTENING = "listening"
    THINKING = "thinking"
    SPEAKING = "speaking"
    INTERRUPTED = "interrupted"
    ENDED = "ended"


class SpeechToText(Protocol):
    def stream(self, audio_chunk: bytes) -> str | None:
        """Return a partial or final transcript, or None if not ready."""


class TextToSpeech(Protocol):
    def synthesise(self, text: str) -> bytes: ...


@dataclass(slots=True)
class StageTiming:
    stage: str
    seconds: float

    @property
    def over_budget(self) -> bool:
        allowance = STAGE_BUDGETS.get(self.stage)
        return allowance is not None and self.seconds > allowance


@dataclass(slots=True)
class TurnBudget:
    """Per-turn latency accounting.

    Records each stage separately so a breach can be attributed. Without the
    split you know the call was slow and nothing else.
    """
    timings: list[StageTiming] = field(default_factory=list)
    _open: dict[str, float] = field(default_factory=dict)

    def start(self, stage: str) -> None:
        self._open[stage] = time.perf_counter()

    def stop(self, stage: str) -> StageTiming:
        started = self._open.pop(stage, None)
        if started is None:
            raise ValueError(f"stage {stage!r} was never started")
        timing = StageTiming(stage, time.perf_counter() - started)
        self.timings.append(timing)
        if timing.over_budget:
            log.warning("voice.stage.over_budget", stage=stage,
                        seconds=round(timing.seconds, 3),
                        allowance=STAGE_BUDGETS.get(stage))
        return timing

    @property
    def total_seconds(self) -> float:
        return sum(t.seconds for t in self.timings)

    @property
    def within_budget(self) -> bool:
        return self.total_seconds <= LATENCY_BUDGET_SECONDS

    def breakdown(self) -> dict[str, float]:
        out: dict[str, float] = {}
        for t in self.timings:
            out[t.stage] = round(out.get(t.stage, 0.0) + t.seconds, 4)
        return out

    def worst_stage(self) -> str | None:
        """Which stage to look at first when the budget is blown."""
        if not self.timings:
            return None
        return max(self.timings, key=lambda t: t.seconds).stage


class VoiceTurn:
    """Barge-in aware turn state machine.

    The agent speaks in sentence-sized chunks rather than one blob, so an
    interruption can stop playback within a chunk instead of after the whole
    reply. Waiting for a full response before starting TTS is the single easiest
    way to blow the 1.8s budget.
    """

    def __init__(self, allow_barge_in: bool = True) -> None:
        self.state = TurnState.LISTENING
        self.allow_barge_in = allow_barge_in
        self.budget = TurnBudget()
        self.spoken_chunks: list[str] = []
        self.interrupted_at: int | None = None

    def begin_thinking(self) -> None:
        self.state = TurnState.THINKING

    def begin_speaking(self) -> None:
        self.state = TurnState.SPEAKING

    def speak_chunk(self, text: str) -> bool:
        """Emit one chunk. Returns False if the turn was interrupted."""
        if self.state is TurnState.INTERRUPTED:
            return False
        self.spoken_chunks.append(text)
        return True

    def on_caller_speech(self) -> bool:
        """Caller audio detected. Returns True if it interrupted the agent."""
        if self.state is TurnState.SPEAKING and self.allow_barge_in:
            self.state = TurnState.INTERRUPTED
            self.interrupted_at = len(self.spoken_chunks)
            log.info("voice.barge_in", chunks_spoken=len(self.spoken_chunks))
            return True
        return False

    def end(self) -> None:
        self.state = TurnState.ENDED
        log.info("voice.turn.completed",
                 total_seconds=round(self.budget.total_seconds, 3),
                 within_budget=self.budget.within_budget,
                 worst_stage=self.budget.worst_stage(),
                 **{f"stage_{k}": v for k, v in self.budget.breakdown().items()})


def split_for_streaming(text: str, min_chars: int = 40) -> list[str]:
    """Split a reply into sentence-ish chunks for incremental TTS.

    Short fragments are merged: synthesising "Yes." on its own costs a full
    provider round trip for almost no audio, which is a net latency loss.
    """
    if not text:
        return []
    parts: list[str] = []
    current = ""
    for token in text.replace("!", ".").replace("?", ".").split("."):
        token = token.strip()
        if not token:
            continue
        current = f"{current} {token}".strip() if current else token
        if len(current) >= min_chars:
            parts.append(current + ".")
            current = ""
    if current:
        parts.append(current + ".")
    return parts
