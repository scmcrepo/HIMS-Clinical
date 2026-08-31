# DPDP Gap Register — HIMS-Clinical

| | |
|---|---|
| **Assessed** | 2026-08-30 |
| **Commit** | `fdc4452` |
| **Method** | Source read of 671 backend Java files, 192 Flyway migrations, agent-service, frontend; plus `check_conventions.py` (568 findings, 71 HIGH) |
| **Assessor** | hms-agentic-delivery agent — engineering assessment, not legal advice |

> This register is engineering input to a legal review. Items marked **[LEGAL]**
> cannot be closed by code alone and need counsel sign-off.

## 0. Regulatory baseline

The DPDP Rules, 2025 were notified by MeitY on 14 November 2025, operationalising
the DPDP Act, 2023 under an 18-month phased timeline. Three gazetted enforcement
dates: 14 November 2025, 14 November 2026, 14 May 2027. Substantive provisions
bite at the 18-month mark. The Data Protection Board is already operational and
complaints can be filed. Penalties reach ₹250 crore for security-safeguard
failures, imposed per violation.

Sources report the final date as both 13 and 14 May 2027 depending on whether
they count the gazette or publication date. **[LEGAL]** — confirm the operative
date with counsel.

Notable changes from the January 2025 draft: hard data localisation was dropped
in favour of a blacklist-based cross-border model, and a 90-day maximum
grievance-resolution timeline was set. ABDM and sectoral health-record rules
impose their own residency constraints regardless.

## 1. Role classification

Confirmed with the client 2026-08-30, pending counsel ratification:

| Data | Determines purpose & means | Role |
|---|---|---|
| Patient clinical records, claims, ABDM records | Hospital tenant | **Processor** |
| Portal identity, cross-tenant lookup, self-registration | Platform | **Fiduciary** |
| Staff accounts, sessions, subscription billing | Platform | **Fiduciary** |
| Agent messaging / voice decisions | Unsettled — depends who configures | **Decide deliberately** |

**[LEGAL]** The portal is the arguable part. `PortalDirectoryController` serves
`/portal/hospitals` ("Hospitals on the platform") and
`PortalPatientLookupRepository` runs a deliberately tenant-unfiltered query
across every tenant to answer "which hospitals hold a record for this mobile
number?". No individual hospital defined that purpose. Counsel should also
consider whether the portal ought to be restructured so the patient's
relationship is with each hospital rather than with the platform — that would be
a larger change than any work order below.

**[LEGAL]** Section 8(2): a Fiduciary may engage a Processor only under a valid
contract. Tenant agreements need processing terms covering security, sub-
processors, audit, erasure, and breach reporting timelines before May 2027.

## 2. What is already sound

Recorded so remediation does not regress it.

| Control | Evidence |
|---|---|
| Patient PII encrypted at rest | `Patient` — first/last name, contact number, email, blood group, address all `@Convert(EncryptedStringConverter.class)`; `contact_number_token` blind index |
| ABDM consent artifacts modelled correctly | V195 keeps `abdm_consent_requests` / `abdm_consent_artifacts` / `external_health_records` distinct from `consent_records`; records outside consented hi_types or date range are dropped; validity re-checked live, not cached |
| PHI disclosure audited | `pii_disclosure_audit` (V193); index and per-record open audited separately; DENIED outcomes recorded |
| Data residency enforced in agent layer | `agent-service/config.py:86` `assert_residency()` fails startup if LLM/STT/TTS host is off the India allowlist |
| Cross-tenant portal lookup contained | Returns ids only, no decryption, OTP-gated; `PortalRepositoryConventionTest` pins it as the only native query in portal packages |
| Erasure design correct in principle | `ErasureService.TARGETS` ordered derived→primary; ERASED/ANONYMISED/RETAINED distinction; `consent_records` swept last |
| Local log files clean | `backend/logs/*.log` untracked and contain no PII on inspection |

## 3. Findings

Severity: **S1** active liability now · **S2** required before May 2027 · **S3** hardening.

### S1-01 — Consent gate cannot fail; consent records are fabricated

**Where:** `AbhaService:76-81`, `PolicyDiscoveryService:73-78` and `:186`,
`PreAuthService:78-83`.

Each site runs `if (!hasConsent(...)) grant(...)` immediately before
`requireConsent(...)`. The gate can never throw. Every row written asserts
`capture_channel='VERBAL_IN_PERSON'` with `captured_by = NULL` — verbal consent
captured by nobody. `notice_text_hash` hashes the `ConsentPurpose` enum's own
one-line summary, not anything shown to a patient.

This is not a missing control; it manufactures documentary evidence that consent
was obtained. `consent_records` is precisely what a Board inquiry would pull, and
every ABHA-linkage and insurance-claim row in it is false. Under Processor
status it is worse: the platform fabricates consent inside each hospital's
tenant, and the hospital carries the liability for records its staff never
created and cannot see.

It also contradicts `ConsentService`'s own docstring, which states there is
deliberately no "assume yes if unknown" branch — indicating a regression.

**Act/Rules:** Ss. 4, 6 (consent must be free, specific, informed, unambiguous).
**Remediation:** WO-022. **[LEGAL]** disposition of existing rows.

### S1-02 — `SmtpConfigService` logs recipient email addresses

`SmtpConfigService:113, 223, 225` interpolate `toEmail` into INFO and ERROR logs,
including the password-reset flow. Promtail scrapes these into Loki. Pre-existing
debt, confirmed real (most other `pii.in-logs` hits are false positives — masking
helpers and column names).

**Act/Rules:** Rule 6. **Remediation:** WO-029.

### S2-01 — No compliance API exists *(PARTIALLY REMEDIATED — WO-024, unverified)*

`api/compliance/` was an empty directory. V179 seeded `CONSENT_MANAGE` and
`ERASURE_MANAGE` and `TenantService:131-133` provisioned them for new tenants —
permissions guarding endpoints that were never written.

WO-024 added the rights surface (7 endpoints). WO-023 added the consent surface
(history, status, grant, withdraw, notice) plus `PORTAL_SELF_ACCESS`. **Still
missing:** Tamil and Hindi notice text, which is blocked on counsel rather than
engineering.

**Act/Rules:** Ss. 6(4)–(6), 12. **Remediation:** WO-023 for the consent half.

### S2-02 — `ErasureService` is orphaned *(REMEDIATED — WO-024, unverified)*

Referenced only by its own test and entity. `sweep()` was never called from any
production path.

**Reading its SQL against the real schema turned up three further defects**, none
of which a unit test could have caught, because the SQL had never once executed:

1. `agent_idempotency_keys` has **no `patient_id`**, yet the sweep ran
   `DELETE ... WHERE patient_id = :pid`. That throws, the target is recorded
   FAILED, and the cached tool responses — `response_body` is free text and
   routinely carries patient detail — survive the erasure.
2. `hitl_escalations` has no `patient_id` either, and its anonymisation read
   `run_id IN (SELECT run_id FROM hitl_escalations WHERE tenant_id = :tid)`.
   That subquery matches **every run in the tenant**. One patient exercising
   erasure would have destroyed every other patient's transcript in that
   hospital. This is the more dangerous failure of the two: over-deletion, not
   under-deletion.
3. The registry covered 6 stores. The schema has 23 tables carrying
   `patient_id`, plus the `patients` row itself.

**Act/Rules:** S. 12, Rule 8. **Remediation:** WO-024 — registry now 24 stores,
SQL corrected, V206 makes the two agent stores reachable, verification gate added
before any sweep runs.

### S2-03 — Portal self-registration records no consent *(REMEDIATED — WO-023, unverified)*

`PortalRegistrationService:118` logs `consent_version` and discards it.
`PORTAL_SELF_ACCESS` appears in `PortalProperties:72` but is not a member of
`ConsentPurpose`. The mobile app collects a consent version that reaches a log
line and nothing else. This sits in the **Fiduciary** half of the split, so the
obligation is the platform's own.

**Act/Rules:** Ss. 5, 6. **Remediation:** WO-023 — `PORTAL_SELF_ACCESS` added to `ConsentPurpose`, V207 seeds its notice, and registration now writes a `PATIENT_DIGITAL` consent record.

### S2-04 — Log retention 30 days against a 1-year floor

Rule 6 requires log retention for at least one year, in a bucket separate from
the personal-data retention engine. Current state: all four logback appenders
`maxHistory 30`; Prometheus `--storage.tsdb.retention.time=30d`; Loki has no
retention configuration at all.

**Act/Rules:** Rule 6(e). **Remediation:** WO-025.

### S2-05 — No breach detection or notification capability *(REMEDIATED — WO-026, unverified)*

Rule 7 requires informing affected individuals promptly in plain language —
nature, consequences, remedial steps, contact details — plus Board notification.
Nothing in the codebase implements detection, notification, or a Board report
path. Under the split, the platform detects and notifies the hospital, who
notifies the Board and patients; both halves need building.

**Act/Rules:** S. 8(6), Rule 7. **Remediation:** WO-026 — V209 incident register
with both Rule 7 clocks tracked separately, `SecurityIncidentService` with a
notice generator covering all four mandatory elements, `CrossTenantAccessDetector`
auto-raising incidents, 7 alert rules, and a 9-endpoint API.

**Found while building it:** the `CrossTenantAccessException` handler called
`ex.printStackTrace()` — stderr, outside the structured pipeline Promtail
scrapes. The tenant isolation guard worked and nothing counted when it fired.
The same handler logged `ex.getMessage()`, which can put identifiers from two
tenants into one line. Identical bug in the `AccessDenied` handler. Both fixed.

### S2-06 — No grievance mechanism or DPO/contact surface *(REMEDIATED — WO-027, unverified)*

No intake, no SLA tracking, nothing published. Rule 13 requires SDFs to publish
DPO contact details; all Fiduciaries need a designated contact.

**Act/Rules:** Ss. 8(9), 13; Rule 13. 90-day maximum resolution.

**Remediation:** WO-027 — V210 grievance register with three clocks
(acknowledge 3d, internal target 30d, statutory 90d), `GrievanceService`,
9 endpoints, 4 alert rules, and an unauthenticated
`GET /compliance/grievances/contact/public`.

**Still open:** V210 creates the contact table but seeds no rows — the details
are per-hospital and cannot be invented. Until each tenant publishes one, that
hospital has no published contact and s. 8(9) is unmet for it. Card J-006.

### S2-07 — No personal-data retention engine *(REMEDIATED — WO-025, unverified, INERT BY DEFAULT)*

`ConsentService.expireLapsedConsents` (cron `0 15 2 * * *`) expires consent rows.
The data those grants authorised is never scheduled for deletion or
anonymisation. Nothing enforces storage limitation, and medico-legal retention
overrides are not modelled.

**Act/Rules:** S. 8(7), Rule 8.

**Remediation:** WO-025 — V213 adds a per-tenant policy table, a run log, and six
seeded policies. `RetentionService` applies them on a nightly schedule behind four
independent safety brakes.

**Important:** every seeded policy is `enabled=false, dry_run=true`. **Nothing is
deleted until a human arms a policy**, and s. 8(7) remains unmet until then. The
periods are engineering defaults; clinical records are deliberately excluded
because medico-legal retention is not something this project can encode. Card
L-005 is the legal review.

**Caught before shipping:** the first draft seeded `appointments` (no `tenant_id`,
so the tenant-scoped statement could not be written) and `agent_tool_invocations`
anonymising `patient_id` (that table uses `target_entity_id`). Both found by
validating the seeds against the real schema rather than trusting them.

### S2-08 — Unencrypted JSONB columns on `Patient` *(REMEDIATED — WO-029, unverified)*

`pediatric_data` and `template_data` are `Map<String,Object>` → JSONB with no
converter. Contents are whatever a tenant configures, so they can hold arbitrary
clinical or identifying data. Paediatric data is the category Rule 12 treats most
carefully.

**Act/Rules:** Rule 6(a).

**Remediation:** WO-029 — `EncryptedJsonMapConverter`, V214 converts both columns
to TEXT, `PiiMigrationRunner.migratePatientJsonColumns` backfills existing rows.
The converter reads plaintext JSON as well as ciphertext, so there is no flag
day — the V208 mistake corrected in advance rather than repaired afterwards.

**Capability lost, deliberately:** these columns are no longer queryable with the
JSON operators. Verified first that nothing uses `->>` or `@>` against them. A
blind index is the escape hatch if one key ever needs to be searchable.

### S2-09 — Notice text is a hash pointing at nothing *(REGISTRY DONE; TEXT DRAFTED, AWAITING COUNSEL)*

`ConsentService.sha256` hashes `ConsentPurpose.getNoticeSummary()` — a
developer-authored English UI label. There is no notice registry table, so the
text cannot be reproduced on demand. A hash whose preimage is unavailable proves
nothing.

Separately, the summaries are not adequate notices: they state no purpose detail,
retention, recipients, or withdrawal method, and exist only in English despite a
Tamil Nadu deployment.

**Act/Rules:** S. 5; Rule 3.

**Remediation:** WO-022 built the registry. WO-030/K-004 drafted replacement text
for all seven purposes covering purpose, data, recipients, retention, withdrawal
and contact — seeded by V211 as `v2.0-draft`, deliberately `DRAFT` so
`hms_consent_notice_draft_served_total` keeps counting until counsel approves.
Better text now reaches patients while the signal survives.

**Still [LEGAL]:** approval, the `[RETENTION]` and `[CONTACT]` placeholders, and
Tamil translation. Review copy: `.hms-agent/DRAFT-CONSENT-NOTICES.md`.

### S2-10 — No MFA for privileged users

Rule 6 names multi-factor authentication for privileged users explicitly. The
codebase has password-reset OTP (`PasswordResetOtpEntity`) but no MFA on login.
Sessions are cookie-based (`VSSID`, 15m).

**Act/Rules:** Rule 6(b).

**Remediation:** WO-029 card M-002, **deliberately deferred.** MFA rewires the
authentication path — the highest blast radius in the system — and cannot be
verified in this environment. It is Rule 6 hardening rather than a substantive
DPDP gap, and shipping it unverified alongside 40 other unverified cards would
add risk without reducing any. **Still open.**

### S2-11 — No DPIA; SDF status unassessed *(PARTIALLY REMEDIATED — WO-030)*

Volume plus sensitivity plus risk-of-harm under Rule 13 makes SDF designation
likely for mid-to-large hospitals and health-tech platforms, bringing annual
DPIAs, independent audits and a named India-based DPO.

Note the classification interaction: as **Processor** for tenant clinical data,
each hospital's volume is assessed against that hospital rather than the
platform aggregate. This materially reduces platform SDF exposure and is a
reason the Processor reading matters commercially, not only legally.

**Act/Rules:** S. 10, Rule 13. **[LEGAL]**

**Remediation:** WO-030 — `DATA-INVENTORY-ROPA.md` (the factual register, derived
from source) and `DPIA-DRAFT.md` (10 risks, inherent/residual/trend). Both need
legal review; the SDF determination itself remains open and is question 1 of the
DPIA.

**Found while writing the inventory:** `grievances` and
`incident_affected_principals` carry `patient_id` and were absent from
`ErasureService.TARGETS`. A sweep would have reported success while leaving both
untouched. Both now RETAIN. Caught by `build_inventory.py`, which is the argument
for having a regeneration script rather than a hand-maintained register.

### S3-01 — Scanner backlog requiring triage

`check_conventions.py`: 568 findings, 71 HIGH.

| Rule | Count | Note |
|---|---|---|
| `rbac.feature-key` | 402 | Mostly informational — asks for confirmation of seeding |
| `tenant.table-not-scoped` | 24 | Triage; some legitimately platform-level |
| `pii.in-exception` | 20 | Real risk — exception text reaches logs |
| `tenant.entity-not-scoped` | 18 | Triage |
| `pii.in-logs` | 18 | Mostly false positives (masking helpers); `SmtpConfigService` real — S1-02 |
| `tenant.native-query` | 16 | Includes the intentional portal lookup |
| `pii.unencrypted-column` | 15 | Several false positives — `contactNumberToken` is a blind index and must stay deterministic |
| `migration.destructive` | 15 | Review rollback documentation |
| `migration.not-idempotent` | 14 | Review |
| `tenant.webhook-no-context` | 4 | Real risk — ThreadLocal does not survive a webhook thread |
| `obs.no-correlation-id` | 3 | |
| `integration.no-timeout` | 3 | |
| `tenant.threadlocal-hop` | 2 | Real risk |
| `rbac.missing-preauthorize` | 1 | Real risk |

Per the codebase map, this must not be a side quest — it is its own gated work
order. **Remediation:** WO-028.

## 4. Remediation plan

| WO | Scope | Sev addressed | Gate status |
|---|---|---|---|
| **WO-022** | Consent gate integrity — remove self-granting, attestation, provenance, notice registry | S1-01, S2-09 | **IN_PROGRESS 4/6 cards, unverified** |
| **WO-023** | Compliance API — consent grant/withdraw/history, `PORTAL_SELF_ACCESS`, notice languages | rest of S2-01, S2-03 | **IN_PROGRESS 4/5, unverified** |
| **WO-024** | Data principal rights — erasure/correction endpoints, wire `sweep()`, requester verification, retention override notification | S2-02, part of S2-01 | **IN_PROGRESS 4/6 cards, unverified** |
| WO-025 | Retention engine + Rule 6 log retention, two separate clocks | S2-04, S2-07 | Not drafted |
| **WO-026** | Breach detection, Board and patient notification, drill | S2-05 | **IN_PROGRESS 5/6, unverified** |
| **WO-027** | Grievance intake, 90-day SLA tracking, DPO/contact surface | S2-06 | **IN_PROGRESS 4/6, unverified** |
| **WO-028** | Scanner backlog triage — adjudicate all 71 HIGH | S3-01 | **IN_PROGRESS, adjudication DONE; see WO-028-ADJUDICATION.md** |
| **WO-029** | Rule 6 hardening — JSONB encryption done; **MFA and key rotation deliberately deferred** | S2-08 (S1-02 done in WO-022) | **IN_PROGRESS 1/3, unverified** |
| **WO-030** | SDF readiness — inventory, DPIA, contract register | S2-11 | **3/5 DONE — documents complete, DPA register outstanding** |

WO-022 → WO-023 → WO-024 is the critical path and should complete before the
others start.

## 5. Open items for counsel

1. Ratify the Processor/Fiduciary split in §1, with specific attention to the
   portal identity layer.
2. Confirm the operative full-compliance date (13 vs 14 May 2027).
3. Direct the disposition of existing fabricated consent records (WO-022 §9 Q1) —
   retain-and-mark versus delete, and whether affected patients must be told a
   consent record was created in error.
4. Supply or approve DPDP notice text per purpose, in English and Tamil at
   minimum. Current enum summaries are placeholders and are not adequate notices.
5. Determine whether tenant contracts currently contain S. 8(2) processing terms,
   and draft them if not.
6. Advise on SDF likelihood for the platform under the Processor reading.
7. Advise whether the portal should be restructured so the patient's relationship
   is with each hospital rather than the platform.
8. **Confirm the response deadline for rights requests.** WO-024 applies the
   90-day grievance ceiling to erasure and correction as the conservative
   reading; the Rules do not state a separate period. If a shorter period
   applies, `DataPrincipalRightsService.STATUTORY_WINDOW` is the single constant
   to change.
9. **Approve the retention strategy per store.** `ErasureService.TARGETS` encodes
   a judgement about which records survive an erasure request — clinical records
   retained under medico-legal obligation, financial records anonymised,
   consent records retained as the audit trail. Those are legal calls currently
   made in code.
