# WO-001 — Agent Gateway: authentication, tool surface, and audit

| | |
|---|---|
| **Roadmap phase** | Phase 1 — Core Data & Tooling Layer |
| **Status** | CONFIRMED (2026-07-25) |
| **Author** | hms-agentic-delivery agent |
| **Date** | 2026-07-25 |
| **Depends on** | — (first work order) |

## 1. Objective

Give the future LangGraph orchestrator a way to authenticate to the HMS and call
a small, safe, audited set of hospital capabilities. After this work order, a
machine client can hold a scoped, revocable, per-tenant credential and invoke
`check_slot_availability`, `book_slot`, `fetch_billing_ledger` and
`check_bed_occupancy` — with tenant filters, RBAC, PII encryption and audit
applying to it exactly as they do to a human receptionist.

This is the critical path. Nothing in Phases 2–5 can be built until the agent
has an identity and a tool surface.

## 2. Scope

### In scope
- Scoped API token authentication for machine clients (new filter chain)
- Fix `HmsPermissionEvaluator`'s hardcoded debug-file write (blocking — see §3)
- Agent principal provisioning: per-tenant, scope-limited, revocable
- Agent tool surface under `/api/agent/v1/` — 4 read/write tools
- Idempotency for write tools
- Agent action audit trail (append-only)
- Observability: structured logs, Micrometer + Prometheus, correlation ids
- OpenAPI tool schemas consumable by the Python orchestrator

### Explicitly out of scope
- FHIR R4 serialization → **WO-002**
- ABDM / NHCX sandbox onboarding and government token management → **WO-003**
- The LangGraph service itself, supervisor, sub-agents → **WO-004** (Phase 2)
- WhatsApp and voice channels → Phase 3
- HITL dashboard → Phase 5
- Cleaning up the pre-existing PII-in-logs backlog → separate work order if wanted

## 3. Current state — what reading the code changed

Three findings from `SecurityConfig`, `TenantResolutionFilter`,
`HmsUserDetails` and `HmsPermissionEvaluator` that alter the plan:

**(a) Session-cookie auth for the agent is not viable.** `SecurityConfig` sets
`.sessionManagement(s -> s.maximumSessions(1).maxSessionsPreventsLogin(true))`.
One active session per user, and a *second* login is **refused** rather than
evicting the first. An agent service holding a session as a normal user could
therefore never run more than one process, and a session lost without a clean
logout would lock the agent out until the registry expired. This eliminates the
"service principal logs in and keeps a cookie" option outright. Scoped tokens on
a stateless filter chain are the answer, not a preference.

**(b) There is a clean integration seam.** `TenantResolutionFilter` runs *after*
`UsernamePasswordAuthenticationFilter` and reads whatever `HmsUserDetails` is in
the `SecurityContext`. `HmsPermissionEvaluator` falls back to checking
`authentication.getAuthorities()` for the raw feature key when the role cache
misses, and `HmsUserDetails.getAuthorities()` maps `featureKeys` straight to
authorities. So a token filter that constructs an `HmsUserDetails` carrying
`tenantId`, `branchId` and the token's scopes as `featureKeys` gets tenant
filtering, branch filtering, RBAC and `AuditableEntity` stamping **for free**,
with no changes to the 176 existing controllers.

**(c) `HmsPermissionEvaluator` has a blocking defect.** On *every* permission
check it executes:

```java
java.nio.file.Files.writeString(
    java.nio.file.Path.of("/home/ssb/Desktop/HIMS-Clinical/backend/evaluator_debug.log"),
    ... username, roles, featureKey, tenantId ...,
    CREATE, APPEND);
```

A synchronous filesystem write on a hardcoded developer path, on the hot
authorization path, logging username and tenant outside the logging framework —
so it is invisible to Promtail/Loki, never rotated, and never masked. The repo
already contains a 255 KB `evaluator_debug.log` as evidence. An agent making
thousands of tool calls per hour multiplies this into a real availability and
DPDP problem. It must go before agent traffic arrives.

Also relevant: `bookAppointment` requires a concrete `slotId`, so an agent
cannot book from natural language directly — it must call slot availability
first and choose. That is why `check_slot_availability` is in this work order
and not deferred. And `BookAppointmentRequest` carries `tempPatientName` /
`tempPatientPhone` for walk-ins, which are unencrypted PII on the existing path;
the agent tool must not widen that exposure.

Existing assets being reused, not reimplemented: `AppointmentSchedulingService`
(`bookAppointment`, `getSlotAvailability`), `BedManagementService`,
`BillingOperationsService`, `PiiEncryptionService`, `FeaturePermissionCacheService`.

## 4. Design

```
  Python orchestrator
        │  Authorization: Bearer <token>
        │  X-Correlation-Id, X-Run-Id, X-Idempotency-Key
        ▼
  ┌──────────────────────────────────────────────┐
  │ SecurityFilterChain @Order(1)  /agent/v1/**  │  STATELESS
  │   AgentTokenAuthenticationFilter             │  → builds HmsUserDetails
  │   TenantResolutionFilter (reused, unchanged) │  → tenant+branch filters on
  └──────────────────┬───────────────────────────┘
                     ▼
  api/agent/v1/  AgentToolController (@PreAuthorize per tool)
                     ▼
  application/agent/  AgentToolService  ── idempotency, audit, metrics
                     ▼
  existing application/ services — unchanged business logic
```

The main `SecurityFilterChain` gets `@Order(2)` and an explicit matcher so the
human/browser path is untouched. Agent requests never create an `HttpSession`,
so `maximumSessions(1)` cannot bite.

### 4.1 API contracts

Base path `/api/agent/v1`. All tenant-scoped. All return the existing
`ApiResponse<T>` envelope.

| Method | Path | Feature key | Idempotent | Purpose |
|---|---|---|---|---|
| GET | `/tools/slot-availability` | `AGENT_SCHEDULING_READ` | n/a | `check_slot_availability(provider_id, date)` |
| POST | `/tools/book-slot` | `AGENT_SCHEDULING_WRITE` | key required | `book_slot(...)` |
| GET | `/tools/billing-ledger` | `AGENT_BILLING_READ` | n/a | `fetch_billing_ledger(patient_id)` |
| GET | `/tools/bed-occupancy` | `AGENT_BED_READ` | n/a | `check_bed_occupancy(branch_id?, ward?)` |
| GET | `/tools/schema` | `AGENT_TOOLS_READ` | n/a | JSON schemas for all tools |

Error envelope, so the agent can decide rather than guess:

```json
{ "message": "Slot is fully booked",
  "data": { "code": "SLOT_FULL", "retryable": false,
            "correlationId": "..." } }
```

`code` is a stable enum. `retryable` distinguishes "try again" from "stop and
escalate" — a bare 500 teaches an LLM nothing except to loop.

`/tools/schema` is generated from the springdoc OpenAPI document at runtime, not
hand-maintained, so the Python tool definitions cannot drift from the Java
contract.

**Write-tool idempotency.** `X-Idempotency-Key` is mandatory on
`POST /tools/book-slot`. A key already seen returns the original response with
`200` and `Idempotency-Replayed: true`. Keys are tenant-scoped and expire after
24h. LLM agents retry; without this, a retried booking double-books a patient.

### 4.2 Data model — Flyway **V176** (verified: highest existing is V175)

| Table | Change | PII? | Encrypted? | Search token? |
|---|---|---|---|---|
| `agent_api_tokens` | new | token hash only | n/a (SHA-256 hash, plaintext never stored) | lookup by hash |
| `agent_tool_invocations` | new — append-only audit | correlation ids only, no patient PII | n/a | no |
| `agent_idempotency_keys` | new | response body may contain PII | **yes**, encrypted converter | by key hash |
| `features` | +5 rows per tenant | no | no | no |
| `roles` | +`AGENT` role per tenant | no | no | no |

`agent_api_tokens`: `id, tenant_id, branch_id (nullable), name, token_hash,
scopes (jsonb), created_by, created_at, expires_at, revoked_at, last_used_at`.
The token is shown **once** at creation and only its SHA-256 hash is stored.

`agent_tool_invocations`: `id, tenant_id, branch_id, token_id, correlation_id,
run_id, tool_name, outcome, error_code, duration_ms, idempotency_key,
target_entity_type, target_entity_id, created_at`. No free text, no patient
identifiers beyond a surrogate entity id. Append-only — no UPDATE or DELETE
grants.

Rollback: `V176` is purely additive (new tables, new feature/role rows). Rollback
is `DROP TABLE agent_api_tokens, agent_tool_invocations, agent_idempotency_keys`
plus deleting the five feature keys and the `AGENT` role. No existing table is
altered, so rollback is data-loss-free for pre-existing data.

### 4.3 Agent-layer changes

None yet — the Python service arrives in WO-004. This work order produces the
contract it will consume.

### 4.4 Frontend changes

Minimal: a token management screen under
`frontend/src/features/settings/agent-tokens/` — create (show once), list,
revoke. Restricted to `HOSPITAL_ADMIN`. Deferred to a task card that can be cut
if you would rather manage tokens by migration for the pilot.

## 5. Compliance impact

**Personal data touched.** The tools read patient names, phone numbers, billing
ledgers and bed assignments. No *new* personal-data columns are introduced.
`agent_idempotency_keys` stores cached response bodies which may contain PII —
hence the encrypting converter and 24h expiry.

**New consent purpose.** Yes, and it matters. A patient consenting to treatment
has not consented to an automated system acting on their record. This work order
does not yet *expose* an agent to patients, so consent capture is deferred to
Phase 3 with the channels — but the audit table is designed now so that when
consent arrives, every agent action is already attributable.

**Cross-border data flow.** None in this work order. No LLM call is made from
the HMS. The residency decision lands in WO-004.

**Audit.** `agent_tool_invocations`, append-only, plus the existing
`AuditableEntity` created/modified stamping which the agent principal inherits
automatically via `HmsUserDetails`.

**Erasure and correction.** Reachable. The audit table holds surrogate ids only,
and idempotency records expire in 24h — so an erasure request against the
patient record does not leave orphaned PII copies.

**Retention.** Idempotency keys 24h. Audit invocations: recommend 7 years to
match clinical record retention; confirm with the hospital's policy.

## 6. Observability plan

**Log events** (JSON, existing logstash encoder), all carrying `correlationId`,
`runId`, `tenantId`, `branchId`, `tokenId` via MDC:

| `event` | Level | Fields |
|---|---|---|
| `agent.auth.succeeded` | INFO | tokenId, scopes |
| `agent.auth.failed` | WARN | reason, sourceIp |
| `agent.tool.invoked` | INFO | tool, idempotencyKey |
| `agent.tool.completed` | INFO | tool, outcome, durationMs |
| `agent.tool.failed` | WARN/ERROR | tool, errorCode, retryable |
| `agent.idempotency.replayed` | INFO | tool, idempotencyKey |
| `agent.token.revoked` | INFO | tokenId, revokedBy |

No patient identifiers in any of them.

**Metrics** — adds `micrometer-registry-prometheus`, exposes
`/actuator/prometheus`, adds Prometheus to `docker-compose.logging.yml` as a
second Grafana datasource:

- `hms_agent_tool_invocations_total{tool,outcome,tenant}`
- `hms_agent_tool_duration_seconds{tool}` (histogram)
- `hms_agent_auth_failures_total{reason}`
- `hms_agent_idempotency_replays_total{tool}`
- `hms_agent_active_tokens` (gauge)

**Traces** — OpenTelemetry starter, spans on the token filter, each tool, each
delegated service call, exported to Tempo. `correlationId` and `runId` as span
attributes.

**Alerts**
- `hms_agent_auth_failures_total` > 10/min → possible credential problem or probe
- agent tool error ratio > 10% over 15m
- p95 `hms_agent_tool_duration_seconds` > 2s (the voice budget depends on this)
- any `tenant.isolation.violation` counter increment → security incident

**Dashboard** — new Grafana panel group "Agent Gateway", provisioned as code
under `grafana/dashboards/`.

## 7. Acceptance criteria

1. A valid scoped token authenticates and reaches a tool endpoint.
2. An expired, revoked, or unknown token receives 401 and increments
   `hms_agent_auth_failures_total`.
3. A token scoped to `AGENT_SCHEDULING_READ` receives **403** on
   `POST /tools/book-slot`.
4. A token issued for tenant A receives 404/403 for tenant B's patient,
   appointment, bed and ledger — verified by a Testcontainers test asserting
   **absence**, not filtering.
5. Agent requests create no `HttpSession`, and two concurrent agent processes
   using the same token both succeed (proving `maximumSessions(1)` is bypassed).
6. `POST /tools/book-slot` replayed with the same `X-Idempotency-Key` returns
   the original response and creates exactly one appointment.
7. Every tool invocation writes exactly one `agent_tool_invocations` row.
8. `HmsPermissionEvaluator` writes nothing to the filesystem; a permission check
   produces no file I/O.
9. `/tools/schema` returns valid JSON Schema for all four tools, generated from
   the live OpenAPI document.
10. `hms_agent_tool_invocations_total` increments on success and failure,
    verified via `/actuator/prometheus`.
11. No log line, metric label or span attribute contains a patient name, phone,
    or address — verified by `check_conventions.py` on changed files.
12. `V176` applies cleanly to a database already at V175 and replays from V001.

## 8. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Two filter chains misordered → agent path falls through to form login | Medium | High | Explicit `@Order` + `securityMatcher`; test asserts no session cookie is issued |
| Removing the evaluator debug write breaks someone's workflow | Low | Low | Replace with a DEBUG-level `log.debug` behind the existing logger |
| Scopes drift from feature keys as modules are added | Medium | Medium | Scopes *are* feature keys; single source of truth |
| Token leaked from orchestrator config | Medium | High | Short expiry, revocation endpoint, `last_used_at` monitoring, secrets manager not env file |
| Idempotency table grows unbounded | Medium | Low | 24h TTL + scheduled purge (with explicit tenant context — see landmine 1) |
| Agent load exposes N+1 queries in reused services | Medium | Medium | Load-test the four tools; `open-in-view: false` means failures are loud |

## 9. Decisions — CONFIRMED 2026-07-25

All six recommendations accepted by the user:

1. **Evaluator debug write** — fix inside this work order. → T-001
2. **Token expiry** — 90 days, overlapping rotation, immediate revocation. → T-004
3. **Token scoping** — per tenant **and** optional branch (`branch_id` nullable). → T-003, T-004
4. **Token-management UI** — in scope. → T-010
5. **Audit retention** — 7 years on `agent_tool_invocations`, matching clinical records. → T-003
6. **Prometheus now, Tempo deferred to WO-004.** → T-002

### Original questions, for the record

## 9a. Open questions (answered above)

1. **Fix the `HmsPermissionEvaluator` debug write inside this work order?**
   Recommendation: **yes**. It is ~10 lines to remove, it is on the hot path for
   every request the agent will make, and it writes unrotated PII to a path that
   does not exist on your servers. The tradeoff is that it is technically
   pre-existing debt, not roadmap work. Confirm or defer.

2. **Token expiry and rotation policy.** Recommendation: 90-day expiry with
   overlapping rotation, `last_used_at` tracked, revocation immediate. Shorter
   is safer but means the pilot hospital's orchestrator config needs touching
   more often. Confirm or override.

3. **Agent tokens per tenant, or per tenant *and* branch?** Recommendation:
   allow both — `branch_id` nullable, so a hospital can scope an agent to one
   location. Costs nothing now; retrofitting is painful.

4. **Ship the token-management UI in this work order, or manage tokens by
   migration for the pilot?** Recommendation: include it — a hospital admin who
   cannot revoke a credential without a DBA is a security problem. But it is the
   most cuttable card if you want the backend sooner.

5. **Audit retention for `agent_tool_invocations`.** Recommendation: 7 years to
   match clinical records. Needs your compliance view.

6. **Do you want Prometheus + Tempo added now, or logs-only for this work
   order?** Recommendation: add Prometheus now (small, and the alerts above
   depend on it), defer Tempo to WO-004 when there are actually two services to
   trace across. Tracing one monolith buys little.

## 10. Estimate

**8 task cards**, roughly 2 of them large. Sequencing:

```
T-001 evaluator fix + observability foundation  ─┐
T-002 V176 schema + feature/role seeding        ─┤─► independent, parallel
T-003 token issuance + hashing service           │
T-004 AgentTokenAuthenticationFilter + chain    ◄─┘  depends T-002,T-003
T-005 tool surface skeleton + audit + metrics   ◄── depends T-004
T-006 the four tools                            ◄── depends T-005
T-007 idempotency                               ◄── depends T-005
T-008 token management UI                       ◄── depends T-003 (cuttable)
```

---

*Gate 1: this work order requires your explicit confirmation before
decomposition or implementation begins.*
