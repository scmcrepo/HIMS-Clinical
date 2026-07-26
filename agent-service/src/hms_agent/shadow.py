"""Shadow mode.

Phase 6 of the roadmap treats shadow mode as a pre-launch testing step. It is
built here, in the foundation, because retrofitting it later means retrofitting
it into every node that performs an action — and by then the temptation is to
skip it and launch on confidence rather than evidence.

In shadow mode every write is recorded as a *proposal* and never executed. Reads
still happen, because a proposal built on stale data proves nothing. Comparing
proposals against what staff actually did is what turns "we think it works" into
a measured agreement rate.
"""

from __future__ import annotations

import json
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

from .observability import SHADOW_PROPOSALS, EventLogger
from .pii import redact

log = EventLogger(__name__)


@dataclass(slots=True)
class Proposal:
    """An action the agent would have taken."""
    run_id: str
    correlation_id: str
    tenant_id: str
    tool: str
    arguments: dict[str, Any]
    fingerprint: str
    proposed_at: float = field(default_factory=time.time)
    # Filled in later by the comparison job, once a human has acted.
    human_action: dict[str, Any] | None = None
    agreed: bool | None = None

    def redacted(self) -> dict[str, Any]:
        d = asdict(self)
        d["arguments"] = redact(d["arguments"])
        return d


class ProposalStore:
    """Append-only JSONL store.

    JSONL rather than a table because proposals are write-once, read-in-bulk by
    an offline scoring job, and keeping them out of the HMS schema avoids
    entangling experiment data with Flyway migrations.

    Arguments are redacted before they hit disk: this file is analysis data, not
    a clinical record, and it should never become a second copy of patient PII.
    """

    def __init__(self, path: str | Path) -> None:
        self._path = Path(path)
        self._path.parent.mkdir(parents=True, exist_ok=True)

    def record(self, proposal: Proposal) -> None:
        with self._path.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(proposal.redacted(), default=str) + "\n")
        SHADOW_PROPOSALS.labels(tool=proposal.tool).inc()
        log.info("agent.shadow.proposed", tool=proposal.tool,
                 fingerprint=proposal.fingerprint)

    def read_all(self) -> list[dict[str, Any]]:
        if not self._path.exists():
            return []
        with self._path.open(encoding="utf-8") as fh:
            return [json.loads(line) for line in fh if line.strip()]

    def agreement_rate(self, tool: str | None = None) -> float | None:
        """Share of scored proposals where the agent matched the human.

        Returns None when nothing has been scored yet — deliberately not 0.0,
        because "no evidence" and "always wrong" should not look the same on a
        go-live dashboard.
        """
        rows = [r for r in self.read_all() if r.get("agreed") is not None]
        if tool:
            rows = [r for r in rows if r.get("tool") == tool]
        if not rows:
            return None
        return sum(1 for r in rows if r["agreed"]) / len(rows)


class ShadowGuard:
    """Decides whether an action executes or is merely recorded."""

    def __init__(self, store: ProposalStore | None = None) -> None:
        self._store = store

    def intercept(self, state: dict[str, Any], tool: str,
                  arguments: dict[str, Any], fingerprint: str) -> bool:
        """Return True if the caller should execute for real.

        When shadowing, records the proposal and returns False.
        """
        if not state.get("shadow_mode", True):
            return True
        proposal = Proposal(
            run_id=state.get("run_id", ""),
            correlation_id=state.get("correlation_id", ""),
            tenant_id=state.get("tenant_id", ""),
            tool=tool,
            arguments=arguments,
            fingerprint=fingerprint,
        )
        if self._store:
            self._store.record(proposal)
        else:
            SHADOW_PROPOSALS.labels(tool=tool).inc()
            log.info("agent.shadow.proposed", tool=tool, fingerprint=fingerprint)
        return False
