# HANDOFF — HMS Agentic Delivery

_Written 2026-07-26T09:08:04+00:00_

Read this after `ledger.py status`. Then read
`references/codebase-map.md` before touching code.

## What happened last session

Built P-002, P-003 and P-004. P-002 (agreement scoring + rollout gating) and P-004 (rollout switches + runbook) are VERIFIED - 22 new Python tests. P-003 (DPDP consent + erasure) is split: the Java half (V179, ConsentService, ErasureService with a per-store target registry) is written not compiled; the Python half (consent gate in the supervisor, fails closed, distress bypasses) is VERIFIED with 7 tests. Python now 183 tests, ruff and mypy clean. Java static: 761 files, zero syntax errors, all imports resolve, 0 HIGH conventions findings. Synced agent-service into the repo BEFORE packaging this time.

## What to do next

./gradlew test - now gates 22 written Java cards. Remaining not-built: S-003 (ABHA sub-agent), S-004 (claims sub-agent), W-002 (BSP transport), V-002 (telephony/STT/TTS) - all four need vendor credentials. Also unfinished inside written cards: NhcxPayloadCodec (needs nimbus-jose-jwt + real keys) and the NHCX callback controller.

## State at handoff

### WO-001 — Agent Gateway: auth, tool surface, audit  (IN_PROGRESS, 1/10 tasks)
- [>] T-001 Remove HmsPermissionEvaluator debug-file write
      last note: IMPLEMENTED, NOT VERIFIED. Removed Files.writeString block from HmsPermissionEvaluator.hasPermission; replaced with guarded log.debug. Added HmsPermissionEvaluatorTest (8 cases) incl. no-file-IO assertion and a case pinning the getAuthorities() fallback that T-005 agent scopes depend on. CORRECTION to card: evaluator_debug.log is NOT git-tracked (.gitignore already covers *.log); no git rm needed, left the local artifact alone. NEEDS: ./gradlew test.
- [>] T-002 Observability foundation: Micrometer, Prometheus, correlation-id MDC
      last note: FINDING: the repo's frontend test suite is RED on main independently of this campaign. src/features/auth/pages/LoginPage.test.tsx has 2 failing tests ('submits login successfully (no branches)', 'handles multiple branches flow'); git status confirms features/auth/ is untouched by us and the file fails when run in isolation. Worth fixing before it masks a real regression.
- [>] T-003 V176 migration: agent tables, feature keys, AGENT role, tenant provisioning
      last note: IMPLEMENTED, NOT VERIFIED. V176__agent_gateway.sql: 3 tables (agent_api_tokens, agent_tool_invocations append-only, agent_idempotency_keys), 6 feature keys x all tenants, AGENT role x all tenants, grants. TenantService wired for future tenants. BUG FOUND AND FIXED DURING IMPLEMENTATION: V176 created AGENT with branch_id NULL but seedRbac would have created it branch-scoped, giving existing vs future tenants different role shapes; added AGENT to tenantWideRoles. Verified ROLE_GRANTS is 9 pairs (Map.of caps at 10) and added a test asserting it. NEEDS: Testcontainers run of the migration against a DB at V175 and a full replay from V001 -- no Postgres in this sandbox.
- [>] T-004 Agent token issuance, hashing, listing and revocation
      last note: STATIC VERIFICATION ADDED (still not compiled). tree-sitter-java parse of all 735 repo files: zero syntax errors, including every new agent file. All com.hms imports in the 34 target files resolve against the repo symbol table. Constructor arities cross-checked against real declarations: BookAppointmentRequest(10) and HmsUserDetails(11) both match. No invented record accessors. REMAINING RISK: external library imports (Spring/Jakarta/Micrometer/springdoc) and type correctness are unchecked - only javac can do that.
- [>] T-005 AgentTokenAuthenticationFilter and the stateless /agent filter chain
      last note: STATIC VERIFICATION ADDED (still not compiled). tree-sitter-java parse of all 735 repo files: zero syntax errors, including every new agent file. All com.hms imports in the 34 target files resolve against the repo symbol table. Constructor arities cross-checked against real declarations: BookAppointmentRequest(10) and HmsUserDetails(11) both match. No invented record accessors. REMAINING RISK: external library imports (Spring/Jakarta/Micrometer/springdoc) and type correctness are unchecked - only javac can do that.
- [>] T-006 Tool surface skeleton, error envelope, audit, metrics, and check_bed_occupancy
      last note: STATIC VERIFICATION ADDED (still not compiled). tree-sitter-java parse of all 735 repo files: zero syntax errors, including every new agent file. All com.hms imports in the 34 target files resolve against the repo symbol table. Constructor arities cross-checked against real declarations: BookAppointmentRequest(10) and HmsUserDetails(11) both match. No invented record accessors. REMAINING RISK: external library imports (Spring/Jakarta/Micrometer/springdoc) and type correctness are unchecked - only javac can do that.
- [>] T-007 Remaining tools: check_slot_availability, book_slot, fetch_billing_ledger
      last note: STATIC VERIFICATION ADDED (still not compiled). tree-sitter-java parse of all 735 repo files: zero syntax errors, including every new agent file. All com.hms imports in the 34 target files resolve against the repo symbol table. Constructor arities cross-checked against real declarations: BookAppointmentRequest(10) and HmsUserDetails(11) both match. No invented record accessors. REMAINING RISK: external library imports (Spring/Jakarta/Micrometer/springdoc) and type correctness are unchecked - only javac can do that.
- [>] T-008 Idempotency for book_slot
      last note: STATIC VERIFICATION ADDED (still not compiled). tree-sitter-java parse of all 735 repo files: zero syntax errors, including every new agent file. All com.hms imports in the 34 target files resolve against the repo symbol table. Constructor arities cross-checked against real declarations: BookAppointmentRequest(10) and HmsUserDetails(11) both match. No invented record accessors. REMAINING RISK: external library imports (Spring/Jakarta/Micrometer/springdoc) and type correctness are unchecked - only javac can do that.
- [>] T-009 Generated tool schema endpoint
      last note: STATIC VERIFICATION ADDED (still not compiled). tree-sitter-java parse of all 735 repo files: zero syntax errors, including every new agent file. All com.hms imports in the 34 target files resolve against the repo symbol table. Constructor arities cross-checked against real declarations: BookAppointmentRequest(10) and HmsUserDetails(11) both match. No invented record accessors. REMAINING RISK: external library imports (Spring/Jakarta/Micrometer/springdoc) and type correctness are unchecked - only javac can do that.
- [x] T-010 Agent token management UI

### WO-002 — FHIR R4 serialization layer  (IN_PROGRESS, 0/2 tasks)
- [>] F-001 FHIR R4 resource builders (Patient, Coverage, Encounter, Organization, Practitioner)
      last note: IMPLEMENTED, NOT COMPILED. FhirR4.java: Patient/Organization/Practitioner/Encounter/Coverage builders + message bundle assembly. DELIBERATE CHOICE: hand-built with Jackson rather than HAPI FHIR. HAPI is the right long-term answer for its validator, but writing against its API without a compiler would surface errors inside an unfamiliar library; these builders are self-contained with zero new dependencies. Upgrade path documented in the class javadoc.
- [>] F-002 Bundle assembly + validation for ABDM and NHCX profiles
      last note: IMPLEMENTED, NOT COMPILED. ClaimBundleBuilder: coverageEligibilityRequest + claimRequest(use=preauthorization|claim). All three share one skeleton. CAVEAT: NHCX tightens cardinalities over time; a bundle valid against base R4 can still be gateway-rejected. Verify against the current implementation guide before go-live.

### WO-003 — ABDM/ABHA integration + central government token auth  (IN_PROGRESS, 0/3 tasks)
- [>] A-001 Government API credential config + central bearer token manager
      last note: IMPLEMENTED, NOT COMPILED. GovApiProperties (all credentials externalised, no working defaults - a default pointing at a sandbox is how test creds reach prod) + GovTokenManager (per-provider caching, refresh skew, double-checked locking, invalidate-on-401). Token refresh failures log only the provider and exception type: the message can contain the client secret.
- [>] A-002 ABDM client: ABHA creation, OTP, existence check, profile
      last note: IMPLEMENTED, NOT COMPILED. AbdmClient: requestAadhaarOtp, requestMobileOtp, verifyOtpAndEnrol, abhaAddressExists. Aadhaar is passed through and never stored, never logged, never in an exception message. Error paths log status code only because ABDM response bodies echo the submitted Aadhaar. CAVEAT: ABDM has revised its API across v1/v2/v3; paths reflect the v3 sandbox shape and are configurable. CLIENT MUST SUPPLY: sandbox credentials + facility id + certification.
- [>] A-003 ABHA linkage persistence + service + agent tool
      last note: IMPLEMENTED, NOT COMPILED. AbhaLinkageEntity + V178. ABHA number/address encrypted with blind-index token columns (encrypted columns are not searchable). No Aadhaar column at all. DPDP consent recorded separately from ABDM's own consent artefact.

### WO-004 — Agent service foundation: LangGraph supervisor, state, HMS client  (COMPLETE, 5/5 tasks)
- [x] G-001 Graph state, supervisor routing, confidence thresholds
- [x] G-002 HMS gateway client: retries, idempotency, correlation
- [x] G-003 PII redaction + structured observability
- [x] G-004 Data-residency guard + settings
- [x] G-005 LLM classifier with rule fallback

### WO-005 — Sub-agents: ABHA reception, scheduling/triage, TPA claims  (IN_PROGRESS, 2/4 tasks)
- [x] S-001 Scheduling/triage sub-agent
- [x] S-002 Billing sub-agent
- [ ] S-003 ABHA reception sub-agent (full flow against AbdmClient)
- [ ] S-004 TPA claims sub-agent (full flow against NhcxClient)

### WO-006 — WhatsApp channel  (IN_PROGRESS, 1/2 tasks)
- [x] W-001 Webhook signature verification, dedupe, 24h window rules
- [ ] W-002 BSP transport + FastAPI webhook endpoint + template registry

### WO-007 — Voice pipeline: streaming STT/TTS, barge-in, latency budget  (IN_PROGRESS, 1/2 tasks)
- [x] V-001 Latency budget accounting + barge-in state machine
- [ ] V-002 Telephony ingress + streaming STT/TTS providers

### WO-008 — NHCX native claims track  (IN_PROGRESS, 0/3 tasks)
- [>] N-001 NHCX client: signed/encrypted transport + correlation persistence
      last note: IMPLEMENTED, NOT COMPILED. NhcxClient (eligibility/preauth/claim submission, NHCX x-hcx-* headers, 4xx non-retryable so a rejected claim is not re-filed forever) + NhcxTransactionEntity persisted BEFORE the HTTP call. NhcxPayloadCodec is DELIBERATELY NOT IMPLEMENTED: JWS/JWE needs nimbus-jose-jwt plus the hospital's real signing key and payer certs. It throws a named error rather than returning the payload unchanged - a silent no-op would mean transmitting unsigned patient claims to a national exchange. CLIENT MUST SUPPLY: NHCX credentials, participant code, signing/encryption keys.
- [>] N-002 Eligibility + preauth + claim bundle builders
      last note: IMPLEMENTED via ClaimBundleBuilder (see F-002).
- [>] N-003 Async callback endpoint + idempotent status handling
      last note: PARTIAL. nhcx_transactions has a unique correlation_id so at-least-once callbacks are idempotent, and expires_at drives escalation when a payer never responds. NOT BUILT: the callback controller itself - it cannot be written safely until NhcxPayloadCodec can verify signatures, because an unverified callback must never be trusted.

### WO-009 — Legacy TPA RPA track (Playwright worker pool)  (COMPLETE, 2/2 tasks)
- [x] R-001 RPA worker: Playwright driver abstraction + credential vault interface
- [x] R-002 TPA portal flow runner with session recording + escalation

### WO-010 — HITL interrupts + Administrative Copilot dashboard  (IN_PROGRESS, 1/5 tasks)
- [>] H-001 V177: hitl_escalations table + HITL_MANAGE feature + provisioning
      last note: IMPLEMENTED, NOT COMPILED. V177__hitl_escalations.sql: table with AuditableEntity columns, expires_at NOT NULL (a paused graph is a patient waiting), partial unique index so one run cannot queue twice. Two feature keys: HITL_MANAGE (HOSPITAL_ADMIN/ADMIN/BRANCH_ADMIN/RECEPTION - front desk must take over without a manager) and AGENT_HITL_RAISE (AGENT role only). Both wired into TenantService for future tenants. Static: parse clean, imports resolve.
- [>] H-002 Escalation entity, repository, service (raise/list/resolve)
      last note: IMPLEMENTED, NOT COMPILED. HitlEscalationEntity (transcript + operator_reply encrypted - transcript is PHI), repository, HitlService with raise/queue/get/resolve. Distress gets a 5-minute deadline vs 30 default. Resolve rejects a second decision on an already-resolved item rather than overwriting. CORRECT/OVERRIDE require a reason, enforced server-side not just in the UI.
- [>] H-003 /api/hitl controller: queue, detail, decision
      last note: IMPLEMENTED, NOT COMPILED. HitlController (/hitl, HITL_MANAGE) queue/detail/decision/count - queue returns summaries WITHOUT transcripts to limit PHI exposure. AgentHitlController (/agent/v1/hitl, AGENT_HITL_RAISE) for filing. Deliberate scope split: an agent can ask for help, never resolve its own request for help. Tenant comes from the token, never the body.
- [x] H-004 Agent-side HITL client + graph wiring
- [>] H-005 Escalation timeout job + stale alerting
      last note: IMPLEMENTED, NOT COMPILED. HitlService.expireOverdue @Scheduled every minute, marks WAITING past expires_at as TIMED_OUT, logs ERROR and increments hms_agent_hitl_timeouts_total. Repository query deliberately tenant-agnostic - the sweep runs with no tenant context and must cover every tenant.

### WO-011 — Shadow mode, compliance hardening, phased rollout  (IN_PROGRESS, 3/4 tasks)
- [x] P-001 Shadow mode: proposal capture + store
- [x] P-002 Agreement scoring job + go-live dashboard
- [>] P-003 DPDP consent model + erasure/retention jobs
      last note: JAVA IMPLEMENTED (not compiled) + PYTHON GATE VERIFIED. V179: consent_records (one row per purpose, unique live grant, withdrawn rows never deleted - they ARE the audit trail), erasure_requests + erasure_targets (one row per store so an incomplete sweep is visible as PENDING). ConsentPurpose enum with requiredForCare - only TREATMENT is, because consent conditioned on receiving care is not freely given. ConsentService grant/hasConsent/withdraw/expire, rejects minor consent without guardian verification and blank notice version. ErasureService with a TARGETS registry naming every store holding patient data, three outcomes (ERASED/ANONYMISED/RETAINED) since erasure is not absolute under statutory retention. PYTHON SIDE VERIFIED: consent gate in the supervisor, 7 tests - fails closed when the check errors, distress bypasses the gate, per-channel purpose mapping.
- [x] P-004 Phased rollout switches + runbook
