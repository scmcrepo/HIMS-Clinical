# Production Deployment Guide

Everything added by the agentic delivery work: what exists, what you must
configure, and the order to bring it up.

Companion documents: `AGENT_ROLLOUT_RUNBOOK.md` covers day-2 operations and the
kill switch. Read that before the first patient, this one before the first
deploy.

---

## 1. What was implemented

### 1.1 Database — 4 migrations, 10 tables

| Migration | Adds |
|---|---|
| `V176__agent_gateway.sql` | `agent_api_tokens`, `agent_tool_invocations`, `agent_idempotency_keys` |
| `V177__hitl_escalations.sql` | `hitl_escalations` |
| `V178__abha_and_nhcx.sql` | `abha_linkages`, `nhcx_transactions` |
| `V179__dpdp_consent_and_erasure.sql` | `consent_records`, `erasure_requests`, `erasure_targets`, `agent_rollout` |

All four are purely additive. No existing table is altered, so rollback is
data-loss-free for pre-existing data. Each file documents its own rollback SQL in
the header.

### 1.2 Backend capabilities

**Agent gateway** — a stateless `/agent/v1/**` security chain authenticating
machine clients by scoped bearer token. The token's scopes become
`HmsUserDetails.featureKeys`, so tenant filters, RBAC and audit apply to agent
actions exactly as to a receptionist's, with no change to the existing 176
controllers.

**Tool surface** — four hospital capabilities exposed as agent tools, plus a
schema endpoint generated from the live OpenAPI document so the Python side
cannot drift from the Java contract.

**Idempotency** — mandatory `X-Idempotency-Key` on `book-slot`, using the unique
constraint as concurrency control rather than check-then-act.

**Human-in-the-loop** — an escalation queue with per-item deadlines, a Copilot
dashboard, and a sweep that marks unanswered escalations `TIMED_OUT`.

**ABDM / ABHA** — OTP-based enrolment client. No Aadhaar is stored anywhere.

**NHCX** — FHIR R4 bundle builders (eligibility, pre-auth, claim), JWS/JWE
transport, an asynchronous callback endpoint that verifies signatures, and a
sweep that escalates submissions the payer never answered.

**DPDP compliance** — per-purpose consent records with notice versioning and
withdrawal, plus an erasure sweep with an explicit registry of every store
holding patient data.

**Observability** — correlation ids across MDC and async boundaries, Prometheus
metrics, provisioned Grafana datasources.

### 1.3 Agent service (Python)

LangGraph orchestrator: supervisor routing with confidence thresholds, four
sub-agents (scheduling, billing, ABHA, claims), consent gate, distress detection,
HITL interrupts, shadow mode with agreement scoring, WhatsApp and voice channel
handling, and a Playwright RPA worker for TPA portals not on NHCX.

**It never opens a database connection.** Every read and write goes through the
HMS REST API.

### 1.4 Endpoints added

| Path | Auth | Purpose |
|---|---|---|
| `GET /api/agent/v1/tools/slot-availability` | agent token, `AGENT_SCHEDULING_READ` | slot lookup |
| `POST /api/agent/v1/tools/book-slot` | agent token, `AGENT_SCHEDULING_WRITE` | booking (idempotent) |
| `GET /api/agent/v1/tools/bed-occupancy` | agent token, `AGENT_BED_READ` | occupancy counts |
| `GET /api/agent/v1/tools/billing-ledger` | agent token, `AGENT_BILLING_READ` | ledger |
| `GET /api/agent/v1/tools/schema` | agent token, `AGENT_TOOLS_READ` | tool definitions |
| `POST /api/agent/v1/hitl/escalations` | agent token, `AGENT_HITL_RAISE` | agent asks for a human |
| `GET/POST /api/hitl/escalations...` | session, `HITL_MANAGE` | Copilot queue |
| `POST/GET/DELETE /api/agent/tokens` | session, `AGENT_TOKEN_MANAGE` | credential management |
| `POST /api/nhcx/callback/**` | **public** — JWS signature | payer responses |
| `GET /api/actuator/prometheus` | **public** | metrics scrape |

### 1.5 Feature keys added

`AGENT_SCHEDULING_READ`, `AGENT_SCHEDULING_WRITE`, `AGENT_BILLING_READ`,
`AGENT_BED_READ`, `AGENT_TOOLS_READ`, `AGENT_HITL_RAISE`, `AGENT_ABHA_WRITE`,
`AGENT_CLAIMS_READ`, `AGENT_TOKEN_MANAGE`, `HITL_MANAGE`, `ABHA_MANAGE`,
`NHCX_CLAIMS`, `CONSENT_MANAGE`, `ERASURE_MANAGE`, `ROLLOUT_MANAGE`.

Seeded per tenant by the migrations **and** wired into
`TenantService.seedRbac()` so tenants onboarded later also receive them.

---

## 2. Configuration

### 2.1 Backend — pre-existing

| Variable | Notes |
|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | PostgreSQL 16 |
| `HMS_ENCRYPTION_KEY` | **base64, 32 bytes.** AES-256-GCM key for PII at rest |
| `HMS_SEARCH_TOKEN_KEY` | blind-index key. Changing it invalidates every search token |
| `LOG_PATH` `ATTACHMENT_PATH` `JASPER_PATH` | filesystem paths |
| `TWILIO_ACCOUNT_SID` `TWILIO_AUTH_TOKEN` `TWILIO_FROM_NUMBER` | existing SMS |

Losing `HMS_ENCRYPTION_KEY` means losing every encrypted column irrecoverably.
It belongs in a secrets manager with a documented custodian, not in a `.env`.

### 2.2 Backend — new (all default to empty)

Nothing below has a working default. That is deliberate: a default pointing at a
sandbox is how test credentials reach production. When unset, the corresponding
feature fails with a named error (`ABDM_NOT_CONFIGURED`, `NHCX_NOT_CONFIGURED`)
and the agent escalates to a human rather than failing obscurely.

**ABDM** — obtain from the ABDM sandbox, then production after certification.

```
HMS_GOV_ABDM_BASE_URL          https://dev.abdm.gov.in  (sandbox)
HMS_GOV_ABDM_CLIENT_ID
HMS_GOV_ABDM_CLIENT_SECRET
HMS_GOV_ABDM_FACILITY_ID       your Health Facility Registry id
```

**NHCX** — obtain during NHCX participant onboarding.

```
HMS_GOV_NHCX_BASE_URL
HMS_GOV_NHCX_PARTICIPANT_CODE
HMS_GOV_NHCX_CLIENT_ID
HMS_GOV_NHCX_CLIENT_SECRET
HMS_GOV_NHCX_SIGNING_KEY_REF      path to your private signing key (JWK)
HMS_GOV_NHCX_ENCRYPTION_KEY_REF   path to your private encryption key (JWK)
HMS_GOV_NHCX_CALLBACK_URL         public HTTPS URL of /api/nhcx/callback
```

The two key refs are **filesystem paths to mounted secrets**, not key values.
Payer public keys are resolved by convention as
`<encryption-key-ref directory>/<participant-code>.jwk` — one file per payer,
because each payer has its own certificate.

### 2.3 Agent service (prefix `HMS_AGENT_`)

```
HMS_AGENT_ENVIRONMENT              dev | staging | prod
HMS_AGENT_HMS_BASE_URL             https://hms.internal/api
HMS_AGENT_HMS_AGENT_TOKEN          issued from Settings → Agent API tokens
HMS_AGENT_HMS_TIMEOUT_SECONDS      default 10
HMS_AGENT_HMS_MAX_RETRIES          default 2

HMS_AGENT_LLM_PROVIDER
HMS_AGENT_LLM_MODEL
HMS_AGENT_LLM_ENDPOINT             must be India-region (see §2.4)
HMS_AGENT_LLM_API_KEY

HMS_AGENT_WHATSAPP_PHONE_NUMBER_ID
HMS_AGENT_WHATSAPP_ACCESS_TOKEN
HMS_AGENT_WHATSAPP_APP_SECRET      webhook signature verification
HMS_AGENT_WHATSAPP_VERIFY_TOKEN    Meta subscription handshake

HMS_AGENT_EXOTEL_ACCOUNT_SID
HMS_AGENT_EXOTEL_API_KEY
HMS_AGENT_EXOTEL_API_TOKEN
HMS_AGENT_STT_ENDPOINT             Bhashini or self-hosted Whisper
HMS_AGENT_STT_API_KEY
HMS_AGENT_TTS_ENDPOINT
HMS_AGENT_TTS_API_KEY

HMS_AGENT_CONFIDENCE_THRESHOLD     default 0.80
HMS_AGENT_SHADOW_MODE              default true — leave true until §5
HMS_AGENT_HITL_TIMEOUT_SECONDS     default 1800
HMS_AGENT_MAX_TURNS_PER_RUN        default 25
HMS_AGENT_ENFORCE_DATA_RESIDENCY   default true — do not disable in production
```

Leaving the LLM variables blank is a legitimate running mode: the service falls
back to the rule-based classifier, which is honest about its own uncertainty and
routes ambiguous input to a human.

### 2.4 Data residency

`enforce_data_residency` refuses to start when `llm_endpoint`, `stt_endpoint` or
`tts_endpoint` resolves to a host outside the India allowlist in
`config.INDIA_REGION_HOSTS`.

This is not decoration. Patient utterances are health data, and DPDP plus ABDM
require them to stay in India. The failure it prevents is silent — everything
works, the data just quietly leaves the country. Extend the allowlist only with
evidence of an in-country deployment.

---

## 3. Infrastructure prerequisites

| Component | Requirement |
|---|---|
| PostgreSQL | 16, in an Indian region, encrypted at rest, PITR backups |
| JVM | Java 21 |
| Python | 3.11+ for the agent service |
| Secrets manager | for `HMS_ENCRYPTION_KEY`, NHCX keys, portal credentials |
| Prometheus + Grafana + Loki | `docker-compose.logging.yml` is a starting point, not a production topology |
| Public HTTPS ingress | for the NHCX callback and WhatsApp webhook only |

### 3.1 Ingress rules — get these right

Three endpoints are unauthenticated by design and must be constrained at the
network edge, because the application layer cannot do it alone:

| Path | Restriction |
|---|---|
| `/api/actuator/prometheus` | monitoring network only. Carries no patient data but reveals traffic volumes and endpoint names |
| `/api/nhcx/callback/**` | NHCX published source ranges. Signature verification is the authentication; IP restriction is the rate limiting |
| `/webhooks/whatsapp` (agent service) | Meta published ranges. Signature is verified, but restrict anyway |

Everything else — the HMS UI, `/api/agent/v1/**` — should not be publicly
reachable. The agent service talks to the HMS over the internal network.

---

## 4. Deployment sequence

### Step 1 — Build and test

```bash
cd backend  && ./gradlew clean build test
cd frontend && npm ci && npm run type-check && npx vitest run && npm run build
cd agent-service && pip install -e ".[dev]" && pytest -q && ruff check src tests
```

The Testcontainers integration tests need Docker; they skip automatically without
it, so a green run on a machine without Docker is weaker than it looks.

### Step 2 — Migrate

```bash
# Back up first. V176-V179 are additive, but this is a clinical database.
pg_dump -Fc "$DB_NAME" > pre-agentic-$(date +%F).dump

cd backend && ./gradlew flywayMigrate    # or let the app migrate on boot
```

Verify afterwards:

```sql
SELECT version, description, success FROM flyway_schema_history
 WHERE version::int >= 176 ORDER BY version::int;

-- Every tenant must have every agent feature. A tenant missing one 403s on
-- every agent call, and the symptom looks like a bug in the agent.
SELECT feature_key, COUNT(*) AS tenants
  FROM features WHERE module IN ('AGENT','ABDM','CLAIM','COMPLIANCE')
 GROUP BY feature_key ORDER BY feature_key;

-- Every tenant starts in shadow.
SELECT stage, COUNT(*) FROM agent_rollout GROUP BY stage;
```

### Step 3 — Deploy the backend

Confirm before proceeding:

```bash
curl -s https://hms.internal/api/actuator/health          # UP
curl -s https://hms.internal/api/actuator/prometheus | head
```

### Step 4 — Issue an agent token

Settings → Agent API tokens → Issue. Grant only the scopes the deployment needs;
start with the read scopes plus `AGENT_HITL_RAISE`.

**The plaintext appears once.** There is no recovery path, only reissue.

### Step 5 — Deploy the agent service

```bash
uvicorn hms_agent.app:create_app --factory --host 0.0.0.0 --port 8090
curl -s http://agent:8090/ready     # {"ready": true, "problems": []}
```

`/ready` names missing configuration explicitly. Do not proceed while it lists
problems.

### Step 6 — Wire the channels

WhatsApp webhook → `https://<public>/webhooks/whatsapp`, using
`HMS_AGENT_WHATSAPP_VERIFY_TOKEN` for the subscription handshake.
Voice → `https://<public>/webhooks/voice/incoming`.

Register approved WhatsApp templates in `TemplateRegistry`. Approval takes days,
and outside the 24-hour customer service window only templates may be sent.

### Step 7 — Staff the Copilot

Grant `HITL_MANAGE` to the front-desk roles who will actually work the queue.
**Confirm they are staffed for the hours the channel is open.** A 24/7 voice line
behind a 9-to-5 escalation desk fails every night, and it fails silently from the
hospital's side — only the patient notices.

---

## 5. Going live

The system starts in `shadow` for every tenant: it reads, proposes, and executes
nothing. That is the correct starting state and it should stay there until the
evidence bars in `AGENT_ROLLOUT_RUNBOOK.md` §2 are met.

Promotion is one stage at a time and the system refuses to skip. Skipping means
enabling a channel whose agreement rate was never measured, which defeats the
purpose of a phased rollout.

Compliance items that must be true before the first real patient:

- [ ] Consent capture live in registration, in the patient's language, with a
      recorded notice version
- [ ] An erasure request run end to end in a test tenant, with **every** target
      reporting a terminal outcome — a `PENDING` target means incomplete erasure
- [ ] Retention periods agreed and documented (`agent_tool_invocations` defaults
      to 7 years, matching clinical records)
- [ ] Kill switch tested by someone who was not told how it works
- [ ] Alert routing verified by an actual page at an inconvenient hour

---

## 6. Known limitations

**Not yet integrated, by design.** ABDM and NHCX require credentials and, for
ABDM, a certification cycle that is calendar time rather than engineering time.
Until configured, those paths escalate to a human.

**Verify ABDM and NHCX field mappings** against the current implementation guides
before go-live. Both specifications have been revised repeatedly; a bundle that
validates against base FHIR R4 can still be rejected by the gateway.

**FHIR is hand-built rather than HAPI.** No new dependency, and adequate for the
bundles in use. If you want profile validation before transmission, add
`hapi-fhir-validation` and keep these builders as the mapping layer —
`FhirR4`'s javadoc documents the path.

**WhatsApp deduplication is in-memory.** Correct for a single process, wrong for
a scaled one: two replicas would each process a retry once. Move it to Redis or
the HMS idempotency table before running more than one instance.

**The frontend test suite is red on `main`** — `LoginPage.test.tsx` has two
pre-existing failures unrelated to this work. Worth fixing before it masks a real
regression.

**ESLint cannot start** — v9 is installed against a legacy `.eslintrc`. Either
migrate the config or pin ESLint 8.
