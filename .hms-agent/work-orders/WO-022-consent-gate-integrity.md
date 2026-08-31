# WO-022 — Consent gate integrity: remove self-granting, capture consent for real

| | |
|---|---|
| **Roadmap phase** | Phase 6 — Compliance hardening (DPDP remediation track) |
| **Status** | AWAITING CONFIRMATION |
| **Author** | hms-agentic-delivery agent |
| **Date** | 2026-08-30 |
| **Depends on** | V179 (consent_records), WO-011/P-003 (ConsentService) |
| **Blocks** | WO-023 (compliance API), WO-024 (data principal rights) |

## 1. Objective

Make `ConsentService.requireConsent` capable of failing. Today three services
grant the consent they are about to check, so the DPDP consent gate is
decorative and every consent record it produces is fabricated evidence. After
this work order, consent is captured from a real human who attested that a real
patient was shown a real notice, and an action without consent is refused.

Beneficiary: the hospital tenant, who currently carries statutory liability for
consent records its staff never created.

## 2. Scope

### In scope

- Remove the `if (!hasConsent) grant(...)` block from `AbhaService`,
  `PolicyDiscoveryService` (two sites) and `PreAuthService`.
- Add an explicit consent attestation to the three request paths, so the desk
  can capture consent at the moment of the action without a separate screen.
- `provenance` column on `consent_records`; backfill existing rows as
  `SYSTEM_INFERRED`; exclude that provenance from `hasConsent`.
- Notice registry table so `notice_text_hash` points at reproducible text.
- Frontend: consent checkbox + notice display on the three existing forms.
- Metrics and alerting for consent refusals and for any remaining inferred grants.

### Explicitly out of scope

- Standalone consent management UI (view all purposes, withdraw, history) —
  **WO-023**. The desk can capture consent inline after this WO; managing it is
  a separate surface.
- Erasure and correction endpoints — **WO-024**.
- Portal/mobile self-registration consent (`PORTAL_SELF_ACCESS`) — **WO-023**,
  because it needs a new `ConsentPurpose` member and its own notice text.
- Agent-initiated consent capture over WhatsApp/voice — not planned this phase.
- The 71 HIGH scanner findings — **WO-028**.

## 3. Current state

Verified by reading the repo at commit `fdc4452`.

**Exists and is sound:**

- `application/compliance/ConsentService` — `hasConsent`, `requireConsent`,
  `grant`, `withdraw`, `historyFor`, `expireLapsedConsents` (cron `0 15 2 * * *`).
  Correctly refuses a minor without `guardianVerified`, refuses a blank
  `noticeVersion`, supersedes rather than duplicates on re-grant, logs patient id
  only.
- `application/compliance/ConsentPurpose` — 6 purposes; only `TREATMENT` is
  `requiredForCare`.
- `infrastructure/persistence/compliance/ConsentRecordEntity` extends
  `AuditableEntity`; 15 columns incl. `capture_channel`, `captured_by`,
  `notice_version`, `notice_text_hash`, `is_minor`, `guardian_verified`.
- `V179__dpdp_consent_and_erasure.sql` — `consent_records` with
  `uq_consent_active` partial unique index on
  `(tenant_id, patient_id, purpose) WHERE state = 'GRANTED'`, plus
  `ck_consent_minor`. Feature keys `CONSENT_MANAGE` / `ERASURE_MANAGE` seeded
  per tenant; `TenantService:131-133` provisions them for future tenants.

**The defect** — four call sites, identical shape:

| File | Lines | Purpose |
|---|---|---|
| `application/abha/AbhaService.java` | 76-81 | `ABHA_LINKAGE` |
| `application/policy/PolicyDiscoveryService.java` | 73-78 | `INSURANCE_CLAIM` |
| `application/policy/PolicyDiscoveryService.java` | 186 | `INSURANCE_CLAIM` |
| `application/claims/PreAuthService.java` | 78-83 | `INSURANCE_CLAIM` |

```java
if (!consent.hasConsent(patientId, ConsentPurpose.ABHA_LINKAGE)) {
    consent.grant(patientId, ConsentPurpose.ABHA_LINKAGE, "v1.0", "en",
                  ConsentPurpose.ABHA_LINKAGE.getNoticeSummary(), "VERBAL_IN_PERSON",
                  null, false, false, null);   // capturedBy = null
}
consent.requireConsent(patientId, ConsentPurpose.ABHA_LINKAGE);
```

`requireConsent` cannot throw. Each pass writes a row asserting verbal in-person
consent captured by nobody, with `notice_text_hash` = SHA-256 of the enum's own
one-line summary rather than of anything a patient saw.

**Relevant request DTOs** (all `api/<module>/request/`, Java records):

- `StartAbhaEnrolmentRequest(UUID patientId, OtpChannel channel, String loginId)`
- `DiscoveryOtpRequest(UUID patientId, String identifier)`
- `ConfirmDiscoveryRequest(UUID patientId, String correlationId, String otp)`
- `PreAuthService.submitPreAuth(...)` is called from the insurance desk; the
  controller path needs confirming during decomposition.

**Current user id** is available via `SpringSecurityAuditorAware` /
`SecurityContextHolder`; `capturedBy` should come from there, not from the
request body.

## 4. Design

### 4.1 The attestation, not a new screen

Rejected alternative: a separate consent-capture endpoint the desk must call
first. It doubles the round trips, and any flow that forgets it breaks at the
gate — which trains staff to work around the gate.

Chosen: the existing request DTOs gain an optional `ConsentAttestation`. When
consent is already on file the field is omitted and nothing changes. When it is
absent, the service throws `ConsentRequiredException`, the frontend shows the
notice text and a checkbox, and the request is retried with the attestation
populated. Consent is then recorded with `capturedBy` = the authenticated user,
`captureChannel` = `IN_PERSON`, `provenance` = `STAFF_ATTESTED`.

```
desk submits ─► service ─► hasConsent? ── yes ──► proceed
                              │
                              no
                              ▼
                    attestation present?
                       │            │
                       no           yes
                       ▼            ▼
             409 CONSENT_REQUIRED   grant(capturedBy=currentUser,
             + notice text          provenance=STAFF_ATTESTED)
                                    then proceed
```

The gate can now fail, which is the whole point.

### 4.2 Provenance and the existing rows

New column `provenance VARCHAR(20) NOT NULL DEFAULT 'STAFF_ATTESTED'`, values
`STAFF_ATTESTED | PATIENT_DIGITAL | SYSTEM_INFERRED | IMPORTED`.

Existing rows backfill to `SYSTEM_INFERRED` — they were produced by the defect
and cannot be relied on. `hasConsent` excludes `SYSTEM_INFERRED`, so those
patients are re-asked at next contact.

The rows are **not deleted**. Deleting them destroys the record that the system
once asserted consent, which is exactly what an inquiry would want to see. This
is the recommendation from the previous session's analysis and is flagged for
override in §9.

### 4.3 Notice registry

`consent_notices(id, tenant_id, purpose, version, language, body_text,
effective_from, effective_to, status, …)`, unique on
`(tenant_id, purpose, version, language)`.

`notice_text_hash` currently hashes `ConsentPurpose.getNoticeSummary()` — a
developer-authored English fragment. A hash is only useful if the text it hashes
can be produced on demand; today it cannot. `ConsentService.grant` will resolve
the active notice from this table and hash *that*.

Seeded with `v1.0` English text per purpose, carried over from the enum
summaries so nothing breaks on day one. Tenants may supersede with their own
counsel-approved wording and other languages.

### 4.4 API contracts

No new endpoints. Three existing paths gain an optional request field and one
new error response.

| Method | Path | Feature key | Tenant-scoped | Change |
|---|---|---|---|---|
| POST | `/api/abha/enrolment` | `ABHA_MANAGE` | yes | `+consent` |
| POST | `/api/policy/discovery/otp` | `POLICY_DISCOVERY` | yes | `+consent` |
| POST | `/api/policy/discovery/confirm` | `POLICY_DISCOVERY` | yes | `+consent` |
| POST | insurance desk pre-auth submit | *(confirm at Stage 2)* | yes | `+consent` |

```java
public record ConsentAttestation(
    @NotBlank String noticeVersion,
    @NotBlank String noticeLanguage,
    @AssertTrue(message = "Patient must be shown the notice and agree")
    boolean patientAgreed,
    boolean minor,
    boolean guardianVerified) {}
```

New error, HTTP **409**:

```json
{ "success": false,
  "message": "Patient has not consented to: Creating or linking your ABHA health account",
  "data": { "code": "CONSENT_REQUIRED",
            "purpose": "ABHA_LINKAGE",
            "noticeVersion": "v1.0",
            "noticeLanguage": "en",
            "noticeText": "<resolved from consent_notices>" } }
```

409 rather than 403: the caller is authorised, the *patient* has not agreed.
`ConsentRequiredException` already carries the purpose; it needs a handler in the
global advice that emits this shape.

### 4.5 Data model

| Table | Change | PII? | Encrypted? | Search token? |
|---|---|---|---|---|
| `consent_records` | `+provenance VARCHAR(20) NOT NULL DEFAULT 'STAFF_ATTESTED'` | no | n/a | no |
| `consent_records` | backfill existing rows → `SYSTEM_INFERRED` | — | — | — |
| `consent_notices` | new table | no — notice text is hospital copy, not patient data | no | no |

- Flyway version: **V205** (verified free; directory ends at V204).
- Rollback: `ALTER TABLE consent_records DROP COLUMN provenance;`
  `DROP TABLE consent_notices;`. The backfill is not reversible — the original
  rows carried no provenance, so `SYSTEM_INFERRED` is strictly more information
  than existed before. Documented, accepted.
- No `ddl-auto` reliance; prod is `none`, Flyway owns it.

### 4.6 Frontend changes

`frontend/src/features/abha/` and the policy-discovery and insurance-desk forms:
intercept the 409, render `noticeText` in a modal with an explicit checkbox
(unticked by default — a pre-ticked box is not consent), resubmit with the
attestation. One shared `ConsentGateModal` component rather than three copies.

Not added to `sonar.coverage.exclusions`.

## 5. Compliance impact

**Personal data touched, field by field:** none newly. `consent_records` gains a
non-personal enum column. `consent_notices` holds hospital-authored notice copy,
not patient data. `patient_id` continues to be the only identifier stored.

**New consent purpose:** none. `PORTAL_SELF_ACCESS` is deferred to WO-023.

**Cross-border data flow:** none. No new external calls; this WO only gates
existing ones.

**Audit records written:** consent grants and refusals already write to
`consent_records` and to structured logs. Refusals become genuinely
distinguishable for the first time. `AuditableEntity` supplies created/modified
by and when.

**Erasure and correction reachability:** `consent_records` is `RETAIN` in
`ErasureService.TARGETS` and stays that way. `consent_notices` is not
patient-linked so erasure does not apply. Adding `provenance` does not create a
new copy of patient data anywhere, so the erasure registry is unchanged — checked
deliberately, since the registry silently missing a new store is the documented
failure mode.

**Retention:** unchanged. `consent_records` are the audit trail and are kept per
policy; retention scheduling is WO-025.

**Fiduciary/Processor:** per the classification confirmed 2026-08-30, tenant
clinical data is processed as a **Processor** on behalf of each hospital. This
work order therefore builds the instrument the hospital uses to capture consent
under its own lawful basis — it does not assert a platform lawful basis. The
notice text is per tenant for exactly that reason.

## 6. Observability plan

**Log events** (JSON via `logstash-logback-encoder`, patient id only, never
notice text):

| Event | Level | Fields |
|---|---|---|
| `consent.granted` | INFO | `patientId`, `purpose`, `channel`, `noticeVersion`, `provenance` |
| `consent.refused` | WARN | `patientId`, `purpose`, `action` |
| `consent.inferred.blocked` | WARN | `patientId`, `purpose` — a `SYSTEM_INFERRED` row was ignored |
| `consent.notice.missing` | ERROR | `tenantId`, `purpose`, `language` — no active notice; grant cannot proceed |

**Metrics** (`hms_<domain>_<thing>_<unit>`, labelled `tenant`, `branch`):

- `hms_consent_checks_total{purpose,outcome}` — exists; `outcome` gains `inferred_ignored`
- `hms_consent_grants_total{purpose,provenance}` — `provenance` label is new
- `hms_consent_refusals_total{purpose,action}` — new
- `hms_consent_inferred_remaining` — gauge, count of live `SYSTEM_INFERRED` rows.
  Should trend to zero as patients are re-consented; if it doesn't, re-consent
  isn't happening in practice.

**Traces:** span `consent.check` with attributes `purpose`, `outcome`,
`provenance`, nested under the existing request span. No patient identifiers as
span attributes.

**Alerts:**

| Condition | Threshold | Why a human |
|---|---|---|
| `consent.notice.missing` fires | any occurrence | Desk is hard-blocked; a tenant has no notice seeded |
| `hms_consent_refusals_total` rate spike | >3× 7-day baseline over 1h | Usually a broken capture step, not patients changing their minds |
| `hms_consent_inferred_remaining` flat | no decrease over 30d | Re-consent is not reaching patients |

**Dashboard:** new "DPDP consent" row on the existing Grafana board — grants by
provenance, refusals by purpose, inferred-remaining burndown.

## 7. Acceptance criteria

1. Given a patient with no consent record, when `startEnrolment` is called with
   no attestation, then `ConsentRequiredException` is thrown and **no**
   `consent_records` row is written.
2. Given the same, when called **with** a valid attestation, then exactly one row
   is written with `provenance='STAFF_ATTESTED'`, `captured_by` = the
   authenticated user id (not null), and `notice_text_hash` = SHA-256 of the
   `consent_notices` body actually resolved.
3. Given a pre-existing row with `provenance='SYSTEM_INFERRED'`, `hasConsent`
   returns **false** and `hms_consent_checks_total{outcome="inferred_ignored"}`
   increments.
4. A static test asserts no source file in `application/` contains a `grant(`
   call inside an `if (!...hasConsent(` block — the defect cannot silently return.
5. Tenant B cannot read, resolve or hash tenant A's `consent_notices` row;
   proven by a test authenticating as tenant B.
6. V205 applies cleanly to a DB at V204 and on a full replay from V001; the
   backfill sets `SYSTEM_INFERRED` on exactly the rows present before migration.
7. The 409 body carries `noticeText`; the frontend modal renders it with the
   checkbox unticked, and submitting unticked is rejected client- and server-side.
8. `hms_consent_refusals_total` increments on every refusal path, asserted
   against `/actuator/prometheus`.

## 8. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Removing the auto-grant blocks a live desk mid-shift | High | High | Attestation ships in the same release; §9 Q2 covers a staged switch |
| A tenant has no seeded notice → hard block | Medium | High | V205 seeds v1.0 for every existing tenant; `TenantService` wired for new tenants; `consent.notice.missing` alerts on any occurrence |
| Re-consent never happens; patients stay blocked | Medium | Medium | `hms_consent_inferred_remaining` gauge + 30-day flat alert |
| `uq_consent_active` collides when re-granting over an inferred row | Medium | Medium | `grant()` already supersedes the live row to `WITHDRAWN` first; needs a test with an inferred row specifically |
| 4th call site (pre-auth) reached from a path not yet mapped | Medium | Medium | Stage 2 confirms the insurance-desk controller before the card is written |
| Backfill mislabels genuine staff-captured rows as inferred | Low | Medium | Every existing row was written by the defective path with `captured_by IS NULL`; backfill predicated on that, not blanket |

## 9. Open questions for the user

1. **Disposition of existing fabricated rows.** Recommendation: mark
   `SYSTEM_INFERRED`, exclude from `hasConsent`, retain forever, re-consent at
   next contact. Because deleting them destroys evidence the system once asserted
   consent, and an inquiry will ask what the system believed and when. Tradeoff:
   patients already processed are re-asked, which reception will find repetitive,
   and the gauge stays non-zero for months. Alternative if counsel prefers: also
   notify affected patients proactively that a consent record was recorded in
   error. **Confirm or override.**

2. **Cutover shape.** Recommendation: ship gate + attestation + frontend in one
   release, no feature flag. Because a flag that disables the gate is a flag
   someone will leave off, and the whole defect is a gate that doesn't gate.
   Tradeoff: a larger single release, and if the frontend lags the backend the
   desk hard-blocks. Alternative: a two-week `consent.enforcement=warn` mode that
   logs and meters refusals without throwing, so you can see the volume before it
   bites. That is the safer operational choice and the weaker compliance
   position. **Confirm or override.**

3. **Who authors the v1.0 notice text?** Recommendation: seed the current enum
   summaries so nothing breaks, and flag them as placeholder in the table
   (`status` = draft) pending counsel-approved wording per tenant. Because
   "Creating or linking your ABHA health account" is a UI label, not a DPDP
   notice — it states no purpose, retention, recipients or withdrawal method.
   Tradeoff: the system is technically consent-gated but the notice is not yet
   adequate, so this WO does not by itself close the notice obligation.
   **Confirm, and tell me who supplies the real text.**

4. **Tamil and Hindi notice text.** The registry is language-keyed and the
   deployment is Tamil Nadu. Recommendation: ship English only in this WO, add
   languages in WO-023 once the real text exists. Tradeoff: consent captured in a
   language the patient may not read is weak consent, so this is a real gap for
   the interim. **Confirm or override.**

## 10. Estimate

Six task cards, each a vertical slice leaving the build green.

| Card | Slice | Est | Depends |
|---|---|---|---|
| C-001 | V205: `provenance` + backfill + `consent_notices` + seed + `TenantService` wiring | M | — |
| C-002 | `ConsentService`: provenance-aware `hasConsent`, notice resolution, `grant` signature | M | C-001 |
| C-003 | `ConsentAttestation` record, 409 handler in global advice, `AbhaService` cut over + tests | M | C-002 |
| C-004 | `PolicyDiscoveryService` (2 sites) + `PreAuthService` cut over + tests | M | C-003 |
| C-005 | Metrics, gauge, alerts, dashboard row; assert against `/actuator/prometheus` | S | C-004 |
| C-006 | `ConsentGateModal` + wiring into the three frontend forms | M | C-003 |

C-006 can run in parallel with C-004/C-005 once the 409 contract lands in C-003.

**Verification note:** the previous session recorded that Gradle cannot bootstrap
in the sandbox (Maven Central blocked), so backend cards may land
IMPLEMENTED-BUT-UNVERIFIED. Per the standing rule those stay IN_PROGRESS until
`./gradlew test` runs on a machine that can build Java. Cards touching consent
are exactly the ones where a passing build lies most convincingly, so tests are
written first.

---

*Gate 1: this work order requires explicit user confirmation before decomposition
or implementation begins.*
