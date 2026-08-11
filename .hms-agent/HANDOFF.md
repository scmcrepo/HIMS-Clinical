# HANDOFF — HMS Agentic Delivery

_Written 2026-08-11T12:14:27+00:00_

Read this after `ledger.py status`. Then read
`references/codebase-map.md` before touching code.

## What happened last session

Session 2026-08-10m: MC-006 Module 3 screens. ALL FIVE MODULES NOW HAVE SCREENS. Screen count 10 of 13 built. ConsentRequestModal + ExternalRecordsViewer mounted as an encounter tab (lazy chunk confirmed in the build). vite build succeeds; tsc clean; 172 tests pass; token guard covers 11 component files across 5 features.

## What to do next

REMAINING: (1) PD-006 remainder - the PrintService BENEFIT_ACKNOWLEDGMENT handler for Screen 2.2; (2) mount the 4 still-unreachable components - AbhaVerificationModal + AbhaVerifiedBadge into the patient master, CoveragePanel + PolicyDiscoveryPanel into the encounter or insurance flow, following the encounter-tab pattern just established; (3) PA-005 needs the OFFICIAL ICD-10 release. Backend STILL never compiled: ~38 tasks unverified, 6 migrations (V191-V196) never replayed, two of which alter nhcx_transactions which holds live data.

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
      last note: JAVA TESTS NOW WRITTEN (still not compiled). Added AgentGatewayIsolationIntegrationTest (14 cases, Testcontainers): auth accept/reject/revoke, NO Set-Cookie assertion for statelessness, scope 403 on write tool, cross-tenant ABSENCE of a token, per-tenant feature seeding, AGENT role tenant-wide, idempotency index per-tenant. Plus AgentTokenServiceTest (14) and AgentPrincipalFactoryTest (6).
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

### WO-005 — Sub-agents: ABHA reception, scheduling/triage, TPA claims  (COMPLETE, 4/4 tasks)
- [x] S-001 Scheduling/triage sub-agent
- [x] S-002 Billing sub-agent
- [x] S-003 ABHA reception sub-agent (full flow against AbdmClient)
- [x] S-004 TPA claims sub-agent (full flow against NhcxClient)

### WO-006 — WhatsApp channel  (COMPLETE, 2/2 tasks)
- [x] W-001 Webhook signature verification, dedupe, 24h window rules
- [x] W-002 BSP transport + FastAPI webhook endpoint + template registry

### WO-007 — Voice pipeline: streaming STT/TTS, barge-in, latency budget  (COMPLETE, 2/2 tasks)
- [x] V-001 Latency budget accounting + barge-in state machine
- [x] V-002 Telephony ingress + streaming STT/TTS providers

### WO-008 — NHCX native claims track  (IN_PROGRESS, 0/3 tasks)
- [>] N-001 NHCX client: signed/encrypted transport + correlation persistence
      last note: CODEC NOW IMPLEMENTED (not compiled). NhcxPayloadCodec no longer throws: real JWS(RS256)-then-JWE(RSA-OAEP-256/A256GCM) via nimbus-jose-jwt, per-payer public keys resolved by participant code, key caching. decryptAndVerify THROWS on signature failure - a payload that decrypts but does not verify is an unauthenticated claim response.
- [>] N-002 Eligibility + preauth + claim bundle builders
      last note: IMPLEMENTED via ClaimBundleBuilder (see F-002).
- [>] N-003 Async callback endpoint + idempotent status handling
      last note: CALLBACK NOW IMPLEMENTED (not compiled). NhcxCallbackController (permitAll - NHCX has no session with us; auth is the JWS signature) + NhcxCallbackService + repository. Duplicate callbacks are no-ops via isAwaitingCallback. Tenant resolved from the correlation id AFTER verification, then set explicitly and cleared in finally. expireStalled escalates submissions the payer never answered. Always 202 on well-formed requests so a downstream error cannot cause an NHCX retry storm.

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
      last note: JAVA TESTS NOW WRITTEN. ErasureServiceTest asserts every patient-data store is in the erasure registry and that consent_records is swept last so a partial failure cannot destroy the audit trail. ConsentPurposeTest pins that only TREATMENT is requiredForCare.
- [x] P-004 Phased rollout switches + runbook

### WO-012 — Module 1 — ABHA verification/creation REST surface + patient badge + card print  (IN_PROGRESS, 2/6 tasks)
- [>] AB-001 ABHA application service + repository + REST controller + DTOs (Screen 1.1 backend)
      last note: IMPLEMENTED, NOT COMPILED. Created AbhaLinkageJpaRepository (blind-index lookups only; deliberately NO findByAbhaNumber since the column is non-deterministically encrypted and equality would silently return nothing), AbhaService (DPDP ABHA_LINKAGE consent gate fires BEFORE any gateway call; duplicate-linkage guard; failure recorded as exception TYPE NAME not message because ABDM error bodies echo Aadhaar), AbhaController @PreAuthorize hasPermission('ABHA_MANAGE','') - feature key ALREADY seeded by V178 so NO new migration needed, AbhaLinkageResponse masks to XX-XXXX-XXXX-nnnn, 2 request DTOs, and AbhaServiceTest (14 cases incl. Aadhaar-never-persisted, consent-before-gateway, mask correctness). STATIC VERIFICATION: tree-sitter parsed 778 repo java files, 0 syntax errors; all com.hms imports in the 7 new files resolve against the repo symbol table; AuditableEntity.setId/PiiSearchTokenService.token/ConsentRequiredException(purpose) signatures cross-checked. NEEDS ./gradlew test - no javac and Maven Central 403 in sandbox.
- [x] AB-002 ABHA frontend domain types, validation, badge/duplicate guards + API client
- [x] AB-003 ABHA verification modal component + patient-master badge wiring
- [ ] AB-004 ABHA card download/print endpoint + template (separately permissioned, audited)
- [ ] AB-005 Aadhaar demo-auth fallback path in AbdmClient
- [>] AB-006 Screen 1.3 manual policy: memberId/tpaName/policyType wired through entity, DTO, service, frontend cmd
      last note: IMPLEMENTED, NOT COMPILED (backend half). Added memberId (encrypted) + memberIdToken (blind index) + tpaName + policyType to the Insurance entity, CreateInsuranceRequest and InsuranceService; extended CreateInsuranceCmd on the frontend (tsc clean). AVOIDED A REGRESSION: I first added a server-side rule requiring either a policy number or a member id. Checked the callers and found InsurancePage.tsx enables Create Record on insurerName alone - the existing screen legitimately creates a record while paperwork is still being chased, so the new rule would have broken a screen in current use as a side effect of adding a new one. Reverted it; the either-identifier rule lives in validateManualPolicy (Screen 1.3 form) where the requirement actually applies. No Java caller constructs CreateInsuranceRequest directly so the record arity change is safe.

### WO-013 — Module 1/2 — NHCX policy discovery, OTP authorisation, coverage & benefit storage  (IN_PROGRESS, 3/6 tasks)
- [>] PD-001 V191 migration: discovered_policies, patient_policy_coverages, exclusions, manual-policy columns, POLICY_DISCOVERY feature
      last note: IMPLEMENTED, NOT MIGRATED. V191__policy_discovery_and_coverage.sql. NEXT FREE VERSION IS V191 (repo shipped to V190 since July; V180 would have collided). Adds discovered_policies (payer assertion, kept separate from insurances until a human links it), patient_policy_coverages (13 benefit fields, append-only snapshots so the admission-time answer survives a later dispute), policy_benefit_exclusions, plus member_id/member_id_token/tpa_name/policy_type on insurances for Screen 1.3. MONEY IN PAISE BIGINT; co-pay in BASIS POINTS not percent because retail policies carry 7.5%. CHECK constraints reject negative balances and co-pay outside 0-10000bp. POLICY_DISCOVERY feature seeded per tenant and granted to whichever roles already hold NHCX_CLAIMS. Verified role_features has pk (role_id,feature_id) so ON CONFLICT DO NOTHING is valid, and features has uq_features_tenant_key. NEEDS Testcontainers replay.
- [x] PD-002 CoverageResponseParser: FHIR benefits to paise/basis-points, missing-vs-zero
- [>] PD-003 PolicyDiscoveryService + NhcxClient discovery/OTP + REST controller + DTOs
      last note: IMPLEMENTED, NOT COMPILED. PolicyDiscoveryService (OTP required before registry lookup - querying a person's insurance holdings without authorisation is a DPDP problem regardless of what the gateway allows; INSURANCE_CLAIM consent gate; discovery idempotent on correlationId so a gateway retry does not double the desk's list), NhcxClient +discoverPolicies/requestDiscoveryOtp/confirmDiscoveryOtp, PolicyDiscoveryController @PreAuthorize POLICY_DISCOVERY, 2 request + 2 response DTOs. Responses mask policy number and member id to ****nnnn. Amounts stay in paise to the browser: one rounding point, not two. Endpoints return correlationId not answers - NHCX is async.
- [x] PD-004 Frontend policy types: benefit formatting, co-pay split, admission guards, manual form
- [x] PD-005 NHCX callback wiring for discovery + on_check into PolicyDiscoveryService
- [>] PD-006 Screen 1.2/2.1 React components + Screen 2.2 benefit print template
      last note: PARTIALLY DONE. Frontend VERIFIED: services/policy/policyApi.ts, features/policy/components/CoveragePanel.tsx (Screen 2.1) and PolicyDiscoveryPanel.tsx (Screen 1.2). tsc exit 0; full suite 121 pass / 2 pre-existing fail; token guard widened to cover the policy components and now asserts it finds >=4 files so a silently-empty file list cannot make it pass while checking nothing. Screen 2.1 renders every amount through formatPaise so an unstated benefit shows an em-dash, never a zero - the desk admits patients on that difference. Status banner uses statusTone so UNKNOWN is amber not red: a payer outage must not read as a dead policy. Screen 1.2 polls rather than awaits (NHCX answers on a callback) and stops polling after 60s instead of forever; the Link button is disabled without an insuranceId. V194 seeds the BENEFIT_ACKNOWLEDGMENT print template for Screen 2.2 - verified the repo uses #{placeholder} syntax (PrintServiceImpl line 84) not {{ }}, and that print_templates carries tenant_id via V113. NOT COMPILED: the V194 seed and the print wiring have not been replayed. REMAINING: a PrintService document-type handler that assembles the benefit payload, and route registration for both panels.

### WO-014 — Module 3 — ABDM Consent Manager (HIU): consent artifact, data streaming, external records viewer  (IN_PROGRESS, 4/6 tasks)
- [>] MC-001 V195 migration: abdm_consent_requests, abdm_consent_artifacts, external_health_records, 2 feature keys
      last note: IMPLEMENTED, NOT MIGRATED. V195. THREE tables kept distinct on purpose: abdm_consent_requests (what we asked), abdm_consent_artifacts (what the CM signed and granted, one per granting HIP), external_health_records (what came back, encrypted, carrying the artifact id that authorised it so a revoked consent traces to everything it let in). CRITICAL: these are NOT consent_records - that is the hospital's DPDP register of its own lawful basis. An ABDM artifact is CM-issued, CM-signed, scoped to hi_types + a date range, and EXPIRES. Conflating them means either treating DPDP consent as authority to pull a stranger's records, or letting an expired artifact look like standing permission. CHECK constraint rejects a reversed date range which would silently fetch nothing. Feature keys ABDM_CONSENT_REQUEST + ABDM_RECORDS_VIEW granted to clinicians only.
- [x] MC-002 ConsentArtifactRules: expiry/revocation/scope gating (dependency-free)
- [>] MC-003 AbdmConsentClient (HIU) + AbdmConsentService + controller + DTOs + 3 entities/repos
      last note: IMPLEMENTED, NOT COMPILED. AbdmConsentClient kept SEPARATE from AbdmClient (enrolment) - different gateway surface and lifecycle; merging them means an identity-creation change can break record retrieval. AbdmConsentService: validates locally BEFORE calling the CM because the CM forwards nonsense to the patient and the hospital gets one approval interaction per request; recordGrant idempotent on artifact id; recordRevocation logs how many records the artifact had already admitted since that is the question asked immediately after and cannot be reconstructed later; fetchRecords DROPS anything outside the consented hi_types or date range because a HIP that over-shares is not authority to keep what it sent, and counts the drops as a warning metric; recordsFor filters against LIVE artifact validity not stored state so a time-boxed grant actually stops showing records. Every read audited to pii_disclosure_audit (reusing V193 as planned) incl. DENIED outcomes. openRecord audited separately from the index - that is when PHI is actually read. ExternalRecordResponse OMITS the payload so a 30-record list does not ship 30 decrypted clinical bundles and bypass per-open auditing.
- [x] MC-004 Frontend abdm types: consent form validation, expiry derivation, record grouping
- [x] MC-005 ABDM callback routes for on-notify/on-fetch wired to AbdmConsentService
- [x] MC-006 Screen 3.1 consent modal + Screen 3.2 records viewer tab in case sheet

### WO-015 — Module 4 — Cashless pre-auth submission, query response, enhancement  (IN_PROGRESS, 3/6 tasks)
- [>] PA-001 V196 migration: estimate lines, query thread, enhancements, ICD-10 table, preauth columns
      last note: IMPLEMENTED, NOT MIGRATED. V196. Estimate stored as LINES not a total: an insurer approving 80k against a 100k estimate has disallowed something specific, and without lines the Screen 4.4 enhancement becomes 'send more money' rather than 'the implant was costed at 40k and you allowed 20k' - the lines ARE the argument. Queries are a THREAD (unique on txn+round) because insurers raise multiple rounds and one column would overwrite the first question with the second. CHECK constraint ck_query_response forces responded_at and response_text to be set together, otherwise nobody can tell whether the insurer is still waiting on us. ck_enh_increase rejects an enhancement below what is already approved - that is a data entry error and the correct action is a claim. icd10_codes is DELIBERATELY EMPTY: ICD-10 is WHO/MoHFW published and a hand-written partial list would look authoritative while silently missing the diagnosis a clinician needs; search degrades to 'no matches' until the official release is loaded (PA-005). ALSO added insurance_id to nhcx_transactions - it was missing, so a claim could be filed with no recorded link to the coverage it relied on.
- [x] PA-002 PreAuthEstimateCalculator: line extension, room shortfall, patient liability, enhancement delta
- [>] PA-003 PreAuthService + entities/repositories + controller + 6 DTOs
      last note: Callback dispatch now reaches PreAuthService.recordQuery and recordEnhancementOutcome (previously unreachable). A query outcome maps to recordQuery, NOT to a rejection: closing a pre-auth the insurer is still considering makes the hospital resubmit instead of answering, restarting the clock on an admitted patient. ALSO FIXED A CONVENTION/RISK DEFECT FROM THE PREVIOUS SESSION: I had written PreAuthJpaRepositories and AbdmConsentJpaRepositories as nested interfaces inside a final class. NO other repository in this codebase uses that shape - all 40+ are top-level - and Spring Data scanning of nested repository interfaces was an unnecessary risk on top of the convention break. Flattened into 6 top-level interfaces and updated all references. Verified no injection cycle: none of ClaimPaymentService, PreAuthService or PolicyDiscoveryService depends back on NhcxCallbackService.
- [x] PA-004 Frontend preauth types: estimate maths, form validation, query thread helpers
- [ ] PA-005 ICD-10 dataset loader + search endpoint (table seeded from the official release)
- [x] PA-006 Screens 4.1-4.4 React: estimate builder, status tracker, query modal, enhancement form

### WO-016 — Module 5 — Final claim, PaymentNotice, UTR/TDS bank reconciliation, control tower  (IN_PROGRESS, 5/7 tasks)
- [>] CP-001 V192 migration: financial_state lifecycle, claim_payment_advices (UTR/TDS), claim_deduction_lines, CLAIM_PAYMENTS feature
      last note: IMPLEMENTED, NOT MIGRATED. V192__claim_payments_and_reconciliation.sql. Adds financial_state as a NEW column rather than widening state: state tracks the NHCX exchange, financial_state tracks whether the hospital has been paid, and a claim can be exchange-complete and financially unpaid for weeks. All 5 statuses from the flow doc now exist incl. the 3 that were missing. claim_payment_advices keeps the payer assertion (net_disbursed_amount) SEPARATE from the hospital confirmation (bank_credited_amount) because comparing them is the entire purpose of Screen 5.3. UNIQUE (tenant_id, utr_number) so a duplicate advice cannot credit the ledger twice. claim_deduction_lines itemised so billing can dispute a specific line. Backfills financial_state for existing CLAIM/PREAUTH rows so the control tower is not empty on day one. NEEDS Testcontainers replay.
- [x] CP-002 ClaimSettlementCalculator: disallowance, co-pay split, net payable, reconciliation gap, state machine
- [>] CP-003 ClaimPaymentService + entities/repositories + control tower REST controller + DTOs
      last note: IMPLEMENTED, NOT COMPILED. ClaimPaymentService (recordPaymentAdvice idempotent on UTR - gateways deliver at least once and a duplicate crediting the ledger twice overstates receipts by the payment value; rejects an advice with no UTR since it would be unmatchable to a bank line; reconcile() records a mismatch rather than refusing it, moving the claim to CLAIM_DISPUTED so someone chases it, because the money DID arrive just not the advised amount), 2 entities, 2 repositories, ClaimPaymentController guarded by CLAIM_PAYMENTS not NHCX_CLAIMS so the person filing claims is not also the person certifying payment, 3 DTOs. CAUGHT AND FIXED A REAL DEFECT: I had invented com.hms.infrastructure.security.CurrentUser which does not exist; the import-resolution check caught it and it now uses SpringSecurityAuditorAware, the same rule that stamps created_by. NOTE: verifier reports one false-positive unresolved import (nested record ClaimSettlementCalculator.Split) - javac accepted that exact import in the harness, the checker just does not resolve nested types.
- [x] CP-004 Frontend control tower types: 5 metric cards, reconciliation, attention queue
- [x] CP-005 PaymentNotice callback route wired to ClaimPaymentService.recordPaymentAdvice
- [x] CP-006 Screen 5.1/5.2/5.3 React components + final claim submission UI
- [x] CB-001 NhcxCallbackRouter: dependency-free routing resolver for all NHCX callback payloads
