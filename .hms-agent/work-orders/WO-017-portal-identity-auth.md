# WO-017 — Patient Portal identity: OTP auth, cross-tenant lookup, portal principal

| | |
|---|---|
| **Roadmap phase** | Phase 9 — Patient Self-Service Portal |
| **Status** | CONFIRMED (autonomous; decisions recorded in §4 and §9) |
| **Author** | hms-agentic-delivery agent |
| **Date** | 2026-08-14 |
| **Depends on** | WO-001 (agent stateless chain — pattern reuse only) |

## 1. Objective

Give a patient a way to prove they hold a mobile number and receive a short-lived,
tenant-and-branch-scoped credential that the rest of the portal can trust. After
this work order a patient can log in, see which hospitals hold a record for them,
choose a profile and a branch, and self-register if no record exists — with every
subsequent portal request carrying a principal that the existing
`TenantResolutionFilter` and `HmsPermissionEvaluator` recognise.

## 2. Scope

### In scope
- `V197__patient_portal_identity.sql`: `portal_otp_challenges`, `portal_sessions`,
  `patients.self_registered`, `PORTAL_*` feature keys, `PORTAL_PATIENT` role.
- OTP issue/verify over the existing `NotificationPort` (Twilio adapter).
- Cross-tenant patient lookup by `contact_number_token`, deliberately filter-free,
  through one narrowly-scoped repository method that returns ids and nothing else.
- Portal JWT (access + rotating refresh), `PortalPrincipalFactory`,
  `PortalTokenAuthenticationFilter`, a third `SecurityFilterChain` on `/portal/**`.
- Self-registration endpoint with per-number registration cap.
- Rate limiting on OTP request, OTP verify and registration.

### Explicitly out of scope
- Portal read/write data endpoints (consultants, slots, appointments, visits) → **WO-018**.
- The mobile client itself → **WO-019**.
- Push notification delivery (FCM) → **WO-020**, not yet drafted.
- Fixing the pre-existing unauthenticated `/patients/eRegister*` endpoints. Flagged
  in the checkpoint report; that is its own work order with its own gate.
- ABHA-based login. ABDM linkage exists (WO-003) but tying portal identity to ABHA
  is a separate consent question.

## 3. Current state

Read before drafting:

- `security/SecurityConfig.java` — two chains today: `@Order(1)` stateless
  `/agent/v1/**`, `@Order(2)` session-based everything else. The portal needs a
  third, ordered between them, matched on `/portal/**`.
- `security/agent/AgentPrincipalFactory.java` — builds an `HmsUserDetails` with a
  synthetic username and scope-derived authorities. The portal principal copies
  this shape exactly: `HmsUserDetails` takes 11 constructor args, last two being
  `tenantId` and `branchId`.
- `infrastructure/tenant/TenantResolutionFilter.java` — enables the Hibernate
  `tenantFilter`/`branchFilter` **only** when the principal is an authenticated
  `HmsUserDetails`. This is the whole reason the portal must authenticate into a
  real principal rather than reading a tenant id from a request parameter.
- `domain/patient/model/Patient.java` — `contactNumberToken` (line 101, plain
  `varchar(64)`, HMAC via `PiiSearchTokenService`); `firstName`, `lastName`,
  `contactNumber`, `email`, `bloodGroup`, `address` all
  `@Convert(EncryptedStringConverter.class)`. **No `selfRegistered` column exists.**
- `infrastructure/persistence/patient/PatientJpaRepository.findByContactNumberToken`
  — exists, but runs under whatever filters the session has, so it is tenant-scoped
  when a session exists. The portal lookup needs the opposite and must not reuse it.
- `infrastructure/notification/TwilioSmsAdapter` implements `NotificationPort`,
  `@Async`, disabled by default via `hms.sms.provider`.
- `application/patient/PatientManagementService` — maintains `contactNumberToken`
  on save; `registerPatient(RegisterPatientRequest)` already allocates the patient
  number through the prefix/sequence system. Extend it; do not reimplement.

## 4. Design

### 4.0 Decision D1 — OTP is required. This overrides the PRD.

Both requirement documents say "No OTP verification in current scope — mobile
number serves as login identifier."

I am not building that, and this is the one decision in this campaign most worth
the user's attention.

The portal returns, to whoever completes login: encrypted-at-rest name and
address, `ClinicalEncounter.diagnosis`, approved lab and radiology results,
casesheet free text, and downloadable attachments. The proposed credential is a
10-digit number that appears on hospital forms, courier labels, WhatsApp
profiles and in every leaked marketing database in the country. It is an
identifier, not a secret. Worse, §3 of the PRD makes the lookup *cross-tenant* —
so one guessed number returns the patient's records at every hospital on the
platform simultaneously.

Under the DPDP Act the hospital is the Data Fiduciary and this is unauthorised
disclosure of health data at scale, with no technical control standing in the
way and no way to un-disclose it afterwards. `references/autonomy.md` §2 says
build auth with the stricter default and flag it; that is what this is.

The cost is small: `NotificationPort` and a working Twilio adapter already exist,
so this is one table, one service, one extra screen. Note also that the approval
document's own §3.3 argues self-registration is safe *because* the mobile is
"OTP-verified" — the requirement contradicts itself, and I have resolved it in
the safe direction.

Config: `hms.portal.otp.required`, default `true`. Setting it `false` logs a WARN
at startup naming the risk and increments `hms_portal_otp_disabled` so it is
visible on a dashboard. It exists so a developer can work offline, not so the
decision can be made quietly in a properties file.

### 4.1 Flow

```
POST /portal/auth/otp/request  {mobile}
        │  rate limit 3/number/10min, 10/IP/10min
        │  HMAC(mobile) -> contact_number_token
        │  cross-tenant existence check (ids only)
        ├─ found or not found: SAME response shape, SAME latency band
        │  (never reveal whether a number is registered — that is itself PHI)
        └─ SMS 6-digit code, 5 min TTL, bcrypt-hashed at rest, 5 attempts

POST /portal/auth/otp/verify   {mobile, code}
        └─ 200 {identityToken, candidates:[{tenantId,tenantName,logoUrl,
                                            patients:[{patientId,name,age,gender,
                                                       patientNumber}],
                                            branches:[...]}]}
           identityToken: 10 min, scope=PORTAL_IDENTITY only. Proves possession
           of the number. Cannot read any clinical data.

POST /portal/auth/session      {patientId, tenantId, branchId}   (identityToken)
        │  server re-verifies patientId is in the verified number's candidate set
        └─ 200 {accessToken 15min, refreshToken 7d}
           accessToken: scope=PORTAL_PATIENT, claims {patientId,tenantId,branchId}

POST /portal/auth/refresh      rotating; reuse of a consumed refresh token
                               revokes the whole chain and alerts
```

The two-token split is the load-bearing part. The identity token proves "you hold
this number"; the access token asserts "you are this patient at this hospital".
Selecting a profile is therefore a server-side authorisation decision re-checked
against the OTP-verified number, not a client-side choice the server trusts. A
single token would let a client that captured any candidate list swap `patientId`
and read a sibling's records.

### 4.2 Cross-tenant lookup

One method, one purpose, native query, no Hibernate filter:

```java
// PortalPatientLookupRepository — the ONLY filter-free query in the portal.
@Query(value = """
    SELECT p.id, p.tenant_id
    FROM patients p
    WHERE p.contact_number_token = :token
      AND p.status = 0
    """, nativeQuery = true)
List<Object[]> findIdsByContactNumberTokenAcrossTenants(@Param("token") String token);
```

It returns identifiers only. Names, ages and hospital details are fetched in a
second pass with the tenant filter explicitly set per candidate tenant, so the
decrypting read path stays inside normal tenant scope. The method is annotated,
commented, and covered by a test that asserts it is the only `nativeQuery` in the
portal package.

### 4.3 API contracts

| Method | Path | Feature key | Tenant-scoped | Purpose |
|---|---|---|---|---|
| POST | `/api/portal/auth/otp/request` | — (public) | no | Issue OTP |
| POST | `/api/portal/auth/otp/verify` | — (public) | no | Verify, return candidates + identityToken |
| POST | `/api/portal/auth/session` | `PORTAL_IDENTITY` | resolved | Exchange for patient-scoped tokens |
| POST | `/api/portal/auth/refresh` | — (token-bearing) | resolved | Rotate |
| POST | `/api/portal/auth/logout` | `PORTAL_PATIENT` | yes | Revoke chain |
| GET | `/api/portal/hospitals` | `PORTAL_IDENTITY` | no | Active tenants, for registration |
| GET | `/api/portal/hospitals/{tenantId}/branches` | `PORTAL_IDENTITY` | no | Active branches |
| POST | `/api/portal/patients/register` | `PORTAL_IDENTITY` | target tenant | Self-register |

Error envelope matches the agent chain: `{message, data:{code, retryable}}`.
Codes: `OTP_RATE_LIMITED`, `OTP_INVALID`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXCEEDED`,
`IDENTITY_TOKEN_REQUIRED`, `PATIENT_NOT_IN_CANDIDATE_SET`, `REGISTRATION_CAP_REACHED`.

### 4.4 Data model

| Table | Change | PII? | Encrypted? | Search token? |
|---|---|---|---|---|
| `portal_otp_challenges` | new: id, contact_number_token, code_hash, attempts, expires_at, consumed_at, created_ip_hash | number is PII | stored only as HMAC token; **no plaintext mobile column** | n/a |
| `portal_sessions` | new: id, patient_id, tenant_id, branch_id, refresh_token_hash, parent_id, issued_at, expires_at, revoked_at, device_label | no | hash only | n/a |
| `patients` | add `self_registered boolean not null default false` | no | no | no |
| `features` | 2 keys × all tenants: `PORTAL_IDENTITY`, `PORTAL_PATIENT` | — | — | — |
| `roles` | `PORTAL_PATIENT` role, tenant-wide (branch_id NULL) | — | — | — |

- Flyway version: **V197** — verified free (directory ends at V196).
- Also wire both feature keys and the role into `application/tenant/TenantService`
  so tenant number 12 gets them. This is landmine #3 and it is the single most
  repeated bug in this campaign.
- Rollback: drop the two new tables; `ALTER TABLE patients DROP COLUMN self_registered`.
  No existing data is rewritten, so rollback is clean.
- Retention: `portal_otp_challenges` purged at 24h by a scheduled job (they are
  authentication artefacts, not records). `portal_sessions` purged 30 days after
  expiry — long enough to investigate a credential-theft report, short enough not
  to be a standing device-history archive.

### 4.5 Frontend changes

None in `frontend/` — this is a mobile-only surface. Hospital staff see
`self_registered` in a later card of WO-018.

## 5. Compliance impact

- **Personal data touched:** mobile number (never stored in plaintext by this WO —
  only its HMAC token in the challenge row); patient name/age/gender/patient number
  returned in the candidate list *after* OTP verification only.
- **New consent purpose:** yes — "self-service portal access to my own health
  records". Captured at first successful session against the existing DPDP consent
  model from P-003, with purpose `PORTAL_SELF_ACCESS`, versioned text, withdrawable
  from the app's settings screen. It is not a boolean on the patient row.
- **Cross-border flow:** none. SMS via the hospital's existing Indian gateway.
- **Audit:** every OTP request, verification outcome, session issuance, profile
  selection and self-registration is an append-only audit row carrying
  `patient_id`, `tenant_id`, outcome and correlation id — never the mobile number.
- **Erasure:** `portal_sessions` and `portal_otp_challenges` join on `patient_id`
  and `contact_number_token`; both are reachable from the P-003 erasure job and
  must be added to its table list in this work order, not later.
- **Children's data:** a parent's number legitimately maps to a child's record.
  The candidate list therefore intentionally exposes minors' records to the number
  holder. Recorded as an accepted risk (R4) because it matches how the front desk
  already works.

## 6. Observability plan

- **Logs** (`event` field, JSON encoder, never the mobile number or the code):
  - `portal.otp.requested` INFO `{contact_token_prefix(8), candidate_tenants, correlation_id}`
  - `portal.otp.verified` INFO `{outcome, attempts, correlation_id}`
  - `portal.session.issued` INFO `{patient_id, tenant_id, branch_id, correlation_id}`
  - `portal.session.refresh_reuse_detected` ERROR `{patient_id, session_chain_id}`
  - `portal.registration.created` INFO `{patient_id, tenant_id, self_registered:true}`
- **Metrics:**
  - `hms_portal_otp_requests_total{outcome}` counter
  - `hms_portal_otp_verify_total{outcome}` counter — `outcome` in
    `success|invalid|expired|attempts_exceeded`
  - `hms_portal_sessions_active` gauge
  - `hms_portal_refresh_reuse_total` counter
  - `hms_portal_lookup_seconds` timer, labels `{found}` — watched for a timing
    oracle between found and not-found
  - `hms_portal_otp_disabled` gauge, 1 when `otp.required=false`
- **Traces:** span `portal.auth.otp.verify` with `otp.outcome`; span
  `portal.lookup.cross_tenant` with `candidate.count` — deliberately named so a
  filter-free query is visible in the trace view.
- **Alerts:**
  - `hms_portal_refresh_reuse_total` > 0 in 5m → page. Refresh reuse is either a
    stolen token or a broken client; both need a human tonight.
  - `hms_portal_otp_verify_total{outcome="invalid"}` > 100/5m → warn. Enumeration.
  - `hms_portal_otp_disabled` == 1 for > 1h → warn.
  - Divergence > 150ms between `hms_portal_lookup_seconds{found="true"}` and
    `{found="false"}` p95 → warn.

## 7. Acceptance criteria

1. Given a number with records in tenants A and B, when OTP is verified, then the
   candidate list contains both, and the response is byte-identical in shape to
   the not-found case before verification.
2. Given an identity token for number X, when `/portal/auth/session` is called
   with a `patientId` belonging to number Y, then 403 `PATIENT_NOT_IN_CANDIDATE_SET`
   and an audit row is written.
3. Given a valid portal access token for tenant A, when any `/portal/**` request
   is served, then `TenantContext.get()` equals A and the Hibernate tenant filter
   is enabled — asserted by a test that queries a tenant-B patient by id and gets
   empty.
4. Given a consumed refresh token, when it is presented again, then the whole
   chain is revoked, 401 is returned, and `hms_portal_refresh_reuse_total` increments.
5. Given 4 OTP requests for one number inside 10 minutes, the 4th returns
   `OTP_RATE_LIMITED` and no SMS is sent.
6. `portal_otp_challenges` contains no column capable of holding a plaintext
   mobile number — asserted by a schema test.
7. No log statement in `api/portal/**` or `application/portal/**` interpolates
   `contactNumber`, `firstName`, `lastName` or an OTP code — asserted by
   `check_conventions.py --changed-only`.
8. A new tenant created through `TenantService` after V197 has both `PORTAL_*`
   features and the `PORTAL_PATIENT` role.
9. `hms.portal.otp.required=false` logs a WARN naming the risk and sets the gauge.
10. Self-registration attempt 4 for one mobile number returns `REGISTRATION_CAP_REACHED`.

## 8. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| R1 Cross-tenant lookup is filter-free by design; a later edit widens what it returns | Medium | Severe — cross-tenant PHI | Ids only; single method; test asserting it is the only native query in the package; comment explaining why |
| R2 SMS gateway down → nobody can log in | Medium | High | `hms.portal.otp.required` is not the answer; surface a clear error, alert on send failure rate, keep the existing session-based staff path untouched |
| R3 Timing oracle reveals whether a number is registered | Medium | Medium | Identical response shape; latency histogram compared by `found` label; alert on divergence |
| R4 Number reassignment — telco recycles a number, new holder sees old records | Low | Severe | Sessions expire in 7d; `portal.session.issued` audited; recommend an annual re-verification policy — flagged as an open question |
| R5 Parent's number exposes an adult child's records after they turn 18 | Medium | Medium | Out of scope here; needs a front-desk process. Documented, not solved |

## 9. Open questions — answered, per autonomous mode

1. **OTP or not?** → **OTP required.** See §4.0. This is the one to overrule if
   you disagree, and the earlier you do it the cheaper it is.
2. **Access token lifetime?** → 15 min, not the PRD's 30. A phone in a pocket
   re-authenticates silently via refresh; the shorter window only costs a
   background call.
3. **Concurrent devices?** → PRD says 2. Implemented as 2 active refresh chains
   per patient; a third login revokes the oldest. Cheaper to relax than tighten.
4. **Registration cap?** → PRD's 3 per number across all tenants, enforced on the
   HMAC token.
5. **Number re-verification cadence?** → Recommend re-OTP every 90 days even with
   a live refresh token, because of R4. Not implemented in this WO; needs your call
   on the friction tradeoff.

## 10. Estimate

6 cards: V197 + provisioning; OTP issue/verify; cross-tenant lookup; JWT +
principal + filter chain; session exchange + refresh rotation; self-registration
+ caps. Cards 2–6 each leave the build green.
