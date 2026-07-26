# hms-agent-service

LangGraph orchestrator for HIMS-Clinical (roadmap Phases 2–6).

## The one architectural rule

This service **never touches the HMS database**. Every read and write goes
through the authenticated agent gateway at `/api/agent/v1` (WO-001), so tenant
filters, RBAC, audit stamping and PII encryption apply to agent actions exactly
as they do to a receptionist's. Direct SQL would bypass all four and become a
second, weaker copy of the system's safety apparatus.

## Layout

```
src/hms_agent/
  config.py         settings + India data-residency guard (fails at boot, not in prod)
  pii.py            masking/redaction — everything logged passes through here
  observability.py  JSON logs matching the backend's shape, Prometheus metrics
  hms_client.py     gateway client: retries, idempotency, correlation propagation
  classifier.py     intent classification (rule-based now, LLM pluggable later)
  state.py          graph state
  shadow.py         shadow mode: propose, never execute, score later
  graph/
    nodes.py        supervisor, sub-agents, HITL interrupt
    builder.py      graph assembly
```

## Running the checks

```bash
pip install -e ".[dev]"
pytest -q          # 81 tests
ruff check src tests
mypy src/hms_agent --ignore-missing-imports
```

## Deliberately not implemented

Three things are blocked rather than stubbed, because a plausible-looking fake
would hide that the integration does not exist:

| What | Blocked on |
|---|---|
| `LlmClassifier` | Model-hosting decision. Patient utterances are health data; DPDP requires they stay in India. |
| ABHA onboarding | ABDM sandbox credentials + certification cycle (WO-003). |
| Claims automation | NHCX gateway credentials (WO-008/009). |

All three escalate to a human at runtime rather than failing silently.

## Shadow mode

On by default (`shadow_mode=True`). Writes are recorded as proposals and never
executed; reads still happen so proposals are built on real data. Compare
proposals against what staff actually did, then turn it off on evidence rather
than confidence.
