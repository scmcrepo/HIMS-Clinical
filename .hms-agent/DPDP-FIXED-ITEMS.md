# DPDP Remediation — Fixed Items

| | |
|---|---|
| **Session** | 2026-08-30 |
| **Work orders** | WO-022 (6/6), WO-023 (4/5), WO-024 (6/6) — IMPLEMENTED; only the YAML is VERIFIED |
| **Build status** | **NOT COMPILED, NOT TYPECHECKED** — no Gradle (Maven Central blocked), no `node_modules` |
| **Static verification** | 921 Java files parsed, 0 syntax errors, all `com.hms` imports resolve; `alerts-dpdp.yml`, `prometheus.yml`, `docker-compose.logging.yml` parse as valid YAML |

> **Read the verification column before treating anything here as done.** No Java
> was compiled and no test was executed. Nothing in this list is verified in the
> sense the project's own definition of done requires.

## 1. Closed in code

### S1-01 — Consent gate could not fail *(the headline finding)*

| Change | File |
|---|---|
| Self-grant block removed | `application/abha/AbhaService.java` |
| Self-grant block removed | `application/policy/PolicyDiscoveryService.java` |
| Self-grant block removed | `application/claims/PreAuthService.java` |
| `ConsentGate` — single enforcement point, reads capturer from security context | `application/compliance/ConsentGate.java` *(new)* |
| `ConsentProvenance` — STAFF_ATTESTED / PATIENT_DIGITAL / SYSTEM_INFERRED / IMPORTED | `application/compliance/ConsentProvenance.java` *(new)* |
| `hasConsent` requires live **and** reliable provenance; null fails closed | `application/compliance/ConsentService.java` |
| `grant()` refuses SYSTEM_INFERRED; refuses STAFF_ATTESTED without a capturer | `application/compliance/ConsentService.java` |
| `ConsentAttestation` request record | `api/shared/ConsentAttestation.java` *(new)* |
| 409 CONSENT_REQUIRED handler carrying notice text | `api/shared/GlobalExceptionHandler.java` |
| `provenance` column, CHECK constraint, conservative backfill | `V205__consent_provenance_and_notices.sql` *(new)* |
| Attestation threaded through 3 DTOs and 3 controllers | `abha`, `policy`, `preauth` |

**Correction to WO-022:** the work order said four call sites. There were three.
`PolicyDiscoveryService:186` (`checkCoverage`) only ever called `requireConsent`
with no self-grant preceding it; it was routed through `ConsentGate` anyway for
consistency.

**What makes the defect hard to reintroduce:** `grant()` refuses a
STAFF_ATTESTED record with a null `capturedBy`, and no service has a user to
hand — only `ConsentGate` does, via `AuditorAware`. A service that tried to
rebuild the old pattern would fail at runtime on its first call.

**Existing fabricated rows:** backfilled to `SYSTEM_INFERRED`, predicated on
`captured_by IS NULL` rather than applied blanket, so a genuine capture cannot be
mislabelled. Rows retained, not deleted. `hasConsent` ignores them, so affected
patients are re-asked at next contact.

### S2-09 — Notice text hash pointed at nothing

`consent_notices` table (per tenant, purpose, version, language), entity,
repository, and resolution in `ConsentService.activeNotice`. The hash is now
computed over registry text the hospital can produce on demand, and over the
**server's** copy rather than anything the client sent.

Partial unique index permits one live ACTIVE notice per tenant/purpose/language,
so "which text did we show?" cannot become ambiguous.

### S1-02 — PII in logs and exception messages

- 3 log statements in `SmtpConfigService` now mask via `PiiMasking.email(...)`
- 2 `RuntimeException` messages narrowed to the exception class name — JavaMail
  puts the offending recipient address into its message, which reached the error
  log and then Loki
- `security/encryption/PiiMasking.java` *(new)* — the single masking utility the
  compliance reference asks for and which did not previously exist anywhere:
  `phone`, `email`, `aadhaar`, `abhaNumber`, `freeText`

### S2-04 — Log retention below the Rule 6(e) one-year floor

| Component | Before | After |
|---|---|---|
| logback (4 appenders) | `maxHistory 30` | `maxHistory 400` |
| Loki | no retention configured at all | `retention-period=8760h`, deletes enabled |
| Prometheus | `retention.time=30d` | `retention.time=400d` |

Loki previously had *no* retention policy, which reads as "keep forever" but in
practice means "until the volume fills".

## 2. Tests written *(not executed)*

| File | Cases | Covers |
|---|---|---|
| `ConsentGateIntegrityTest` | 14 | Refusal writes no record; attestation attributes to real user; hash over registry text; SYSTEM_INFERRED does not authorise; null provenance fails closed; SYSTEM_INFERRED cannot be created; unknown notice version refused; warn mode; supersede-on-regrant |
| `ConsentSelfGrantConventionTest` | 3 | Source-shape guard: no `if (!hasConsent(` anywhere in `application/`; no direct `grant(` outside the compliance package; every `ConsentPurpose` seeded in V205 |

The convention test exists because a behavioural test cannot catch this defect's
return — a service that grants its own consent passes every test you would think
to write. The only reliable signal is the shape of the source.

## 2b. WO-024 — Data principal rights *(added this session)*

### S2-02 — `ErasureService` was orphaned **and broken**

Wiring it up meant reading its SQL against the real schema for the first time.
Three defects, none of which a unit test could have caught, because the SQL had
never executed:

| Defect | Consequence |
|---|---|
| `agent_idempotency_keys` has no `patient_id`, but the sweep ran `DELETE ... WHERE patient_id = :pid` | Throws; target recorded FAILED; cached tool responses (free-text `response_body`) survive the erasure |
| `hitl_escalations` anonymisation used `run_id IN (SELECT run_id FROM hitl_escalations WHERE tenant_id = :tid)` | The subquery matches **every run in the tenant**. One patient's erasure would have destroyed every other patient's transcript in that hospital |
| Registry listed 6 stores | Schema has 23 tables carrying `patient_id`, plus `patients` |

The second is the serious one: over-deletion, not under-deletion. It would have
looked like a successful erasure.

### What was built

| Change | File |
|---|---|
| `patient_id` on the two unreachable PHI stores; request lifecycle columns; `ERASURE_REQUEST` key; DB CHECK forbidding completion without verification | `V206__erasure_reachability_and_lifecycle.sql` *(new)* |
| Registry expanded 6 → 24 stores with per-table strategy; all SQL corrected; `patients` anonymised not deleted so retained clinical rows don't orphan | `ErasureService.java` *(rewritten)* |
| Intake, verification gate, execution, refusal-with-reason, 90-day clock, overdue job | `DataPrincipalRightsService.java` *(new)* |
| 7 endpoints — the **first ever** in `api/compliance` | `DataPrincipalRightsController.java` *(new)* |
| Request/response DTOs carrying no decrypted patient data | `api/compliance/request`, `api/compliance/response` *(new)* |
| Repositories | `ErasureRequestJpaRepository`, `ErasureTargetJpaRepository` *(new)* |
| `patientId` field added | `HitlEscalationEntity`, `AgentIdempotencyKeyEntity` |
| `ERASURE_REQUEST` wired for new tenants | `TenantService.java` |

**Permission split:** `ERASURE_REQUEST` (reception — intake and read) is separate
from `ERASURE_MANAGE` (verify, execute, reject). Taking a request and running an
irreversible sweep are different risks, and merging them would mean whoever can
answer a phone can erase a patient. There is deliberately **no bulk erasure
endpoint**.

**The verification gate** is enforced twice — in `ErasureService.sweep` and by a
database CHECK constraint — because erasing on an unverified request destroys
data on a stranger's say-so and denies the real patient their own history.

### Tests written *(not executed)*

| File | Cases | Notable |
|---|---|---|
| `ErasureRegistryCompletenessTest` | 5 | Parses the migration directory and fails when a new `patient_id` column appears with no erasure strategy. Simulated against the real schema: 0 missing, 0 stale. Also asserts the `hitl_escalations` self-referential subquery never returns |
| `DataPrincipalRightsServiceTest` | 14 | Mostly assert something does *not* happen — no sweep unverified, no sweep on CORRECTION, no second sweep on retry, no refusal without a reason |

### Found by the project's own scanner

`ERASURE_REQUEST` was seeded in V206 for existing tenants but **not** wired into
`TenantService` for new ones — so every hospital onboarded after this release
would have had a rights queue nobody could open. Fixed.

## 2c. Frontend integration *(added this session)*

The product is now wired end to end. Before this, the backend returned a 409 no
screen could handle and exposed a rights API with no UI.

| Change | File |
|---|---|
| Consent types, attestation, 409 payload, rights and receipt shapes | `types/compliance.ts` *(new)* |
| Rights API client + `asConsentRequired` 409 discriminator | `services/compliance/complianceApi.ts` *(new)* |
| Notice display and attestation capture | `features/compliance/components/ConsentGateModal.tsx` *(new)* |
| The call → 409 → attest → retry cycle, once | `features/compliance/hooks/useConsentGate.ts` *(new)* |
| Rights queue with state tabs and overdue highlighting | `features/compliance/components/RightsQueuePage.tsx` *(new)* |
| Per-store erasure receipt | `features/compliance/components/ErasureReceiptModal.tsx` *(new)* |
| Route `/admin/data-rights` gated on `ERASURE_REQUEST` | `router/AppRouter.tsx` |
| `patientId` on the HITL raise path | `RaiseEscalationRequest`, `HitlService` |
| Patient-aware idempotency overload | `AgentIdempotencyService` |

Three decisions worth knowing about:

- **The consent checkbox starts unticked and is never defaulted.** A pre-ticked
  box is not consent under the Act, and defaulting it would recreate the
  self-granting defect in the interface layer instead of the service layer.
- **The notice text is rendered from the 409 payload, never from a constant in
  the frontend.** Each tenant supplies its own wording, and the hash stored
  against the consent record is computed over the server's copy — a notice
  hard-coded in the client could drift from the one the record claims was shown.
- **The rights queue shows no patient names.** A list of people who asked to be
  forgotten should not also be a directory. Operators work from the patient id
  and open the record separately, where that access is audited.

Verify, execute and refuse are separate actions mirroring the separate server
permissions, and erasure sits behind its own confirmation. Merging verify and
execute would let one misclick both assert a requester's identity and
irreversibly clear their record.

## 2d. WO-023 — Consent management surface *(added this session)*

### S2-03 — Portal self-registration recorded no consent **(closed)**

`PortalRegistrationService` read a `consentVersion` off the request, wrote it to
a log line, and dropped it. `PORTAL_SELF_ACCESS` was referenced in
`PortalProperties` but was never a member of `ConsentPurpose`, so the patient
agreed to something the system never stored — no record consent was given, and
nothing for them to withdraw.

Now writes a real record with provenance `PATIENT_DIGITAL` and `capturedBy` null,
which is correct: the patient ticked the box themselves and no staff member
attested to anything. Wrapped so a consent-write failure logs ERROR and meters
rather than rolling back a completed registration.

Under the Fiduciary/Processor split, this is a purpose the platform holds as
**Fiduciary in its own right**.

### S2-01 — Compliance API **(now closed)**

| Change | File |
|---|---|
| `PORTAL_SELF_ACCESS` as a seventh purpose | `ConsentPurpose.java` |
| Notice seed, `CONSENT_VIEW` key, role grants, history index | `V207__consent_management_surface.sql` *(new)* |
| history · status · grant · withdraw · notice | `ConsentController.java` *(new)* |
| Request and response DTOs | `api/compliance/request`, `api/compliance/response` |
| `CONSENT_VIEW` for new tenants | `TenantService.java` |
| Consent panel with one-click withdrawal | `PatientConsentPanel.tsx` *(new)* |
| `consentApi`, provenance and status types | `complianceApi.ts`, `types/compliance.ts` |

**`CONSENT_VIEW` is separate from `CONSENT_MANAGE` and granted wide** — a
clinician about to send an automated reminder needs to check whether they may,
and that read shouldn't require the ability to record agreement on the patient's
behalf.

**Withdrawal is one click, no confirmation, no reason field.** Consent harder to
withdraw than to give is not freely given, and an "are you sure?" on withdrawal
but not on granting would be exactly that asymmetry.

SYSTEM_INFERRED records surface in the UI as "needs re-consent" rather than being
hidden or shown as valid.

### C-005 — Alerting **(the only verified work here)**

`alerts-dpdp.yml`: 10 rules across consent, rights and Rule 6 safeguards, wired
into `prometheus.yml` and mounted in the compose file. Every rule fires on an
absence or a silent failure, because that's the shape compliance defects take —
nobody reports a consent that was never asked for.

Added `hms_consent_enforcement_warn_mode` so an alert catches a gate left
switched off for a week. All three YAML files parse; **this is the only part of
the DPDP work that is genuinely verified**, because YAML needs no toolchain.

Grafana dashboard JSON still not written. Alert rules matter more — a panel needs
someone looking at it.

### D-005 **(now complete)**

Only one idempotency call site existed, and `BookSlotToolRequest` already carried
`patientId`. One-line wire-up; verified by grep that no 5-argument caller remains.

## 3. Not done — and why

### Remaining in WO-023

| Card | Status | Consequence |
|---|---|---|
| E-005 Tamil and Hindi notice text | **not started — blocked on legal, not engineering** | The registry is language-keyed and V207 seeds English only. Consent captured in a language the patient cannot read is weak consent, in a Tamil Nadu deployment |

WO-022 and WO-024 are now fully implemented. Only the Grafana dashboard JSON
remains outstanding from C-005, and alert rules cover the need.



### Not started at all

WO-025 (retention engine) · WO-026 (breach notification) · WO-027 (grievance +
DPO) · WO-028 (scanner backlog, 71 HIGH) · WO-029 (MFA, JSONB encryption, key
rotation) · WO-030 (DPIA / SDF readiness).

WO-026 has the longest lead time: breach notification needs a detection
capability, a workflow and a drill, and none of it exists.

### Cannot be fixed in code

- **Notice text.** V205 seeds `DRAFT` placeholders carried over from the enum
  summaries. They are UI labels, not DPDP notices: no retention period, no
  recipients, no withdrawal method, English only in a Tamil Nadu deployment.
  `hms_consent_notice_draft_served_total` counts every time one is served.
- **Disposition of the fabricated rows** — retain-and-mark was implemented as the
  safest reversible option. Whether affected patients must be told a consent
  record was created in error is a legal call.
- **Fiduciary/Processor ratification**, tenant contract terms under S. 8(2),
  DPIA and SDF determination.
- **The rights-request deadline.** WO-024 applies the 90-day grievance ceiling to
  erasure and correction as the conservative reading; the Rules state no separate
  period. `DataPrincipalRightsService.STATUTORY_WINDOW` is the one constant to
  change.
- **The retention strategy per store.** `ErasureService.TARGETS` encodes a legal
  judgement — clinical records retained under medico-legal obligation, financial
  records anonymised, consent records retained as the audit trail. Currently that
  judgement lives in code and has not been reviewed by a lawyer.

## 4. Scanner delta

568 / 71 HIGH → 569 (WO-022) → 577 (WO-024) → **582 findings / 71 HIGH** (WO-023).

The one new finding is `migration.destructive` on V205, triggered by
`DROP CONSTRAINT IF EXISTS`. Adjudicated as expected; rollback is documented in
the migration header.

`SmtpConfigService` still shows `pii.in-logs` because the linter matches the word
`email` in `PiiMasking.email(toEmail)`. Adjudicated false positive — the value is
masked at that call site.

The 8 new findings are 7 `rbac.feature-key` prompts on the new controller
(informational — asking that `ERASURE_REQUEST` be seeded and provisioned, which
it now is) and 1 `migration.destructive` on V206 for its `DROP CONSTRAINT IF
EXISTS`, rollback documented in the file header.

HIGH count unchanged across both work orders: none of the 71 belong to WO-022 or
WO-024's scope. They are WO-028's backlog.

## 5. Required next step

Run on a machine with a real toolchain:

```bash
cd backend  && ./gradlew test
cd frontend && npm ci && npx tsc --noEmit && npm test
```

The frontend was written without `node_modules`, so it has never been
typechecked. Relative imports were verified to resolve to real files and the
`ApiResponse`, `Modal` and `toast` contracts were read from source, but that is
not the same as compiling.

Sixteen cards (C-001…C-006, D-001…D-006, E-001…E-004) are
IMPLEMENTED-BUT-UNVERIFIED and remain IN_PROGRESS in the ledger. Consent and erasure code is precisely where a passing
build lies most convincingly, so expect minor import and signature fixes — these
files were written without a compiler.

Then apply V205 and V206 to a seeded database and replay from V001 clean.

**Test the erasure sweep against a populated database before it ever runs on real
data.** Every one of the three defects WO-024 found was invisible until the SQL
met the actual schema. There is no reason to assume the rewrite is the first
version to get that right on the first attempt.
