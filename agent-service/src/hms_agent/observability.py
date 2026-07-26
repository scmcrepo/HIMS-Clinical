"""Structured logging and metrics.

Emits the same JSON shape as the Java backend's logstash encoder, so one Grafana
panel can follow a patient interaction across both processes. A different shape
here would mean two dashboards and a manual join, which in practice means nobody
looks.

Correlation context lives in a ContextVar rather than a global, so concurrent
runs in the same process do not bleed into each other's log lines.
"""

from __future__ import annotations

import json
import logging
import sys
import time
import uuid
from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from typing import Any

from prometheus_client import Counter, Gauge, Histogram

from .pii import redact

# Default is None, not {}: a mutable ContextVar default is shared across every
# context that never sets it, so one run could mutate another run's fields.
_context: ContextVar[dict[str, str] | None] = ContextVar("hms_agent_context", default=None)


def new_correlation_id() -> str:
    return str(uuid.uuid4())


@contextmanager
def correlation_context(**fields: str | None) -> Iterator[dict[str, str]]:
    """Bind correlation fields for the duration of a block.

    Restores the previous context on exit, including on exception, so a failed
    run cannot leak its ids onto whatever executes next.
    """
    current = dict(_context.get() or {})
    current.update({k: str(v) for k, v in fields.items() if v is not None})
    token = _context.set(current)
    try:
        yield current
    finally:
        _context.reset(token)


def get_context() -> dict[str, str]:
    return dict(_context.get() or {})


class JsonFormatter(logging.Formatter):
    """Matches the backend's logstash-logback-encoder output."""

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "@timestamp": time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(record.created))
                          + f".{int(record.msecs):03d}Z",
            "level": record.levelname,
            "logger_name": record.name,
            "thread_name": record.threadName,
            "message": record.getMessage(),
        }
        payload.update(get_context())
        extra = getattr(record, "fields", None)
        if extra:
            payload.update(redact(extra))
        if record.exc_info:
            # Exception text routinely contains identifiers nobody meant to log.
            payload["stack_trace"] = redact(self.formatException(record.exc_info))
        return json.dumps(payload, default=str)


def configure_logging(level: str = "INFO") -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level)


class EventLogger:
    """Logs stable event names rather than prose.

    Message text gets edited during refactors and every Loki query built on it
    silently breaks. `event` is the contract.
    """

    def __init__(self, name: str) -> None:
        self._log = logging.getLogger(name)

    def _emit(self, level: int, event: str, **fields: Any) -> None:
        self._log.log(level, event, extra={"fields": {"event": event, **fields}})

    def info(self, event: str, **fields: Any) -> None:
        self._emit(logging.INFO, event, **fields)

    def warning(self, event: str, **fields: Any) -> None:
        self._emit(logging.WARNING, event, **fields)

    # Alias. `warning` is the canonical name, matching stdlib logging, so that
    # linters and readers treating this as a logger are not surprised.
    warn = warning

    def error(self, event: str, **fields: Any) -> None:
        self._emit(logging.ERROR, event, **fields)

    def debug(self, event: str, **fields: Any) -> None:
        self._emit(logging.DEBUG, event, **fields)


# ── Metrics ──────────────────────────────────────────────────────────────────
# Labels stay low-cardinality. Never label by patient, correlation or run id:
# that is how a metrics backend falls over.

AGENT_RUNS = Counter(
    "hms_agent_runs_total", "Agent graph executions", ["entry_channel", "outcome"])
NODE_TRANSITIONS = Counter(
    "hms_agent_node_transitions_total", "Graph node transitions", ["from_node", "to_node"])
TOOL_INVOCATIONS = Counter(
    "hms_agent_tool_invocations_total", "HMS tool calls", ["tool", "outcome"])
TOOL_DURATION = Histogram(
    "hms_agent_tool_duration_seconds", "HMS tool call duration", ["tool"])
CONFIDENCE = Histogram(
    "hms_agent_confidence", "Intent confidence at routing time",
    buckets=(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0))
HITL_ESCALATIONS = Counter(
    "hms_agent_hitl_escalations_total", "Escalations to a human", ["reason"])
HITL_PENDING = Gauge(
    "hms_agent_hitl_pending", "Runs currently awaiting a human")
SHADOW_PROPOSALS = Counter(
    "hms_agent_shadow_proposals_total", "Actions proposed but not executed", ["tool"])
LLM_TOKENS = Counter(
    "hms_agent_llm_tokens_total", "Model tokens consumed", ["model", "direction"])
