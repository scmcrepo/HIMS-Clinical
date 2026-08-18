# WO-020 — Manual TPA insurance desk: 7-stage progressive workflow

| | |
|---|---|
| **Roadmap phase** | Phase 10 — VitalSoft parity: manual insurance desk |
| **Status** | CONFIRMED (autonomous — open questions answered in §9) |
| **Author** | hms-agentic-delivery agent |
| **Date** | 2026-08-15 |
| **Depends on** | WO-015 (pre-auth entities), WO-016 (claim payments), V191/V192/V196 |
| **Source requirement** | `VITALSOFT_INSURANCE_FLOW.md`, `VITALSOFT_VS_HIMS_INSURANCE_GAPS.md` |

## 1. Objective

Give the hospital insurance desk the progressive, manual TPA workflow it runs
today on VitalSoft: pre-auth request → TPA approval → bill linkage → enhancement
request → enhancement approval → pre-dispatch document checklist → courier
dispatch → disallowance and cheque settlement. After this work order a desk clerk
can drive a private/TPA credit claim end to end inside HIMS, with every stage
timestamped, attributable and attachable, instead of tracking it on paper beside
a flat single-step screen.

This is the **manual, fax-and-courier** track. It sits beside — not on top of —
the NHCX-native digital track built in WO-015/WO-016, which most Indian insurers
still do not support.

## 2. Scope

### In scope

- 7-stage workflow state machine on the existing `insurances` record, with
  monotonic progression and per-stage audit columns.
- Stage 1 pre-auth request: card validity, TPA communication mode + endpoint,
  applied timestamp, requested amount.
- Stage 2 pre-auth approval/rejection: TPA claim number, approved limit,
  rejection reason, TPA's communication mode + endpoint.
- Inter-stage: **working** bill linkage (`PUT /insurance/updateBillId`), plus the
  rule that enhancement is blocked until a bill is linked.
- Stage 3/4 enhancement request and approval — the *manual* variant, recording
  what was faxed to the TPA and what came back.
- Stage 5 pre-dispatch document checklist (JSONB).
- Stage 6 dispatch: mode, courier vendor, POD number, dispatched by, delay reason.
- Stage 7 disallowance and cheque settlement: cheque receipts table, itemised
  per-charge disallowance driven through the **existing**
  `BillingOperationsService.updateDisallowedAmounts`.
- Per-stage attachments via the existing `AttachmentType.INSURANCE` + category.
- Two print templates: `LETTER_ACCEPTANCE`, `ENHANCEMENT_REQUEST`.
- Fixing two stub endpoints discovered while reading the code (§3).
- Frontend: replace the flat `InsurancePage.tsx` table+modal with a left-sidebar
  stage timeline, stage forms, bill-link modal, checklist grid, dispatch form,
  cheque grid and disallowance editor.

### Explicitly out of scope

- **The 10 MIS reports** → WO-021. They are a different beast: raw-JDBC,
  tenant-scoped by `ReportScope`, needing `decryptQueryResult`, and they should
  not hold up the desk flow.
- **Any NHCX wire traffic.** This flow never posts a FHIR bundle. Where a payer
  *is* on NHCX the existing `PreAuthService` path remains the right one.
- **Merging the manual and NHCX enhancement models.** See decision D-2.
- **Migrating existing `insurances` rows into the new stage machine.** Existing
  rows keep `insurance_current_status = NULL`, which the UI reads as "legacy
  record, flat view". A backfill is a separate, reversible decision.
- **Retiring `InsuranceStatus`.** The old enum keeps driving `/pending` and the
  existing screens; the new stage column is additive.

## 3. Current state

Read before drafting. What exists:

| Thing | Where | State |
|---|---|---|
| `Insurance` entity | `domain/insurance/model/Insurance.java` | 15 columns; `policyNumber`, `preAuthNumber`, `memberId` encrypted; `memberIdToken` blind index |
| `InsuranceStatus` | same package | `ACTIVE, PRE_AUTH_REQUESTED, PRE_AUTH_RECEIVED, SETTLED, REJECTED` — a flat lifecycle, not the 7 stages |
| `InsuranceService` | `application/insurance/` | create / receivePreAuth / settle / reject / getters |
| `InsuranceController` | `api/insurance/` | `@PreAuthorize("hasPermission('INSURANCE','')")`; lookup endpoints already exist |
| Attachments | `AttachmentType.INSURANCE` (ordinal 2) + `category` string | **Already sufficient** — all five stage categories work with zero backend change |
| Disallowance | `charge_line_items.disallowed_amount`, `BillingOperationsService.updateDisallowedAmounts`, `PUT /bill/update-details` | Works; unused by any UI |
| Credit bills | `BillingOperationsService.getCurrentMonthBills`, `GET /bill/current-month/{patientId}` | Works |
| NHCX pre-auth | `PreAuthService`, `preauth_estimate_lines`, `preauth_queries`, `preauth_enhancements` (V196) | Digital track — separate |
| Claim payments | `ClaimPaymentService`, `claim_payment_advices`, `claim_deduction_lines` (V192) | Digital track — separate |
| Print engine | `PrintServiceImpl.buildModel` switch on `documentType`, `#{placeholder}` substitution, templates seeded by migration (see V194) | Extend the switch |
| Reports | `BaseReportController` / `BaseReportService` / `ReportScope` / `ReportDbUtil` | Pattern to copy in WO-021 |

### Two stubs found while reading — these are bugs, not gaps

1. `InsuranceController.updateBillId` parses `id` and `billId`, then returns
   `insuranceService.getById(insuranceId)` **without linking anything**. The gap
   document lists this endpoint as "⚠️ Exists"; it is a no-op that returns a
   success message. Fixed here.
2. `InsuranceController.getByDateRange` accepts `searchFromDate` /
   `searchToDate` and ignores both, returning `getPending()`. Fixed here.

## 4. Design

### 4.0 Decisions taken (autonomous mode — override any of these)

**D-1 — Cheque receipts get a real table, not the spec's `cheque_list` JSONB.**
The requirement mirrors VitalSoft's MySQL `insurance.cheque_list JSON`. I am
deviating: cheques carry **money**, need per-row `created_by`/`created_at`, and
Reports 6/9/10 must `SUM` them. Money in JSONB cannot be constrained, cannot be
indexed usefully, and aggregates only through fragile casts. Every other money
path in this repo is a BIGINT paise column in a real table
(`claim_payment_advices` is the closest precedent). Cost of overriding me later:
one migration. Cost of getting it wrong: settlement figures that silently
disagree with the ledger.

**D-2 — The manual desk flow does NOT reuse `preauth_enhancements`.** Superficially
Stage 3/4 and `PreAuthService.requestEnhancement` are the same concept. They are
not the same *record*: the NHCX table is keyed by `nhcx_transaction_id` and its
states are gateway states (`SUBMITTED/APPROVED/REJECTED/QUERY_RAISED`) driven by
async callbacks. The manual flow has no transaction, no correlation id, and its
state changes when a human reads a fax. Forcing them together means a nullable
transaction FK and a state machine that means two different things — and the
first NHCX callback that touches a manually-created row would be a real
incident. They stay separate; §5 of the roadmap can unify the *read* model later.

**D-3 — Disallowance reuses the existing billing path.** Stage 7's itemised
deductions call `BillingOperationsService.updateDisallowedAmounts`, the method
that already owns `charge_line_items.disallowed_amount`. No second write path to
bill money. (`claim_deduction_lines` stays the NHCX payer-advice record.)

**D-4 — All money in paise, BIGINT**, matching the whole repo. VitalSoft's
`INT(11)` rupees are a legacy artefact. Column names follow the requirement
document (`preauth_approved_limit`, not `preauth_approved_limit_paise`) so the
spec stays greppable, with the unit stated in the migration comment, the entity
javadoc and the DTO.

**D-5 — Stage stored as VARCHAR via `@Enumerated(EnumType.STRING)`**, not
VitalSoft's ordinal `INT`. Ordinals in the DB are the reason the source spec has
to carry a "DO NOT reorder" table. The requirement's own SQL sketch already used
`VARCHAR(50)`.

**D-6 — Feature keys: the desk reuses the existing `INSURANCE` key** (already
seeded for ADMIN/BRANCH_ADMIN and grantable), so no endpoint is invisible on day
one. Reports get a **new** `REPORT_INSURANCE` key, matching the other nine report
modules, seeded in V199 for existing tenants and added to `TenantService.FEATURES`
+ `HOSPITAL_ADMIN` grants for future ones.

**D-7 — `reason_for_enhancement` and both rejection-reason columns are
encrypted.** They are free text explaining *why a patient's treatment cost more*
— "extended ICU stay post-op sepsis" is clinical information, and
`references/compliance.md` §4 puts clinical notes under encryption. This is a
deviation from the source spec's plain `VARCHAR(255)` and it costs searchability,
which nothing needs. Reports render them through the existing
`BaseReportService.decryptQueryResult`.

**D-8 — TPA fax numbers and mail ids stay plaintext.** They are the *insurer's*
business contact endpoints, not patient data. `dispatched_by` likewise stays a
plaintext staff name: the reports layer already displays `users.username`
plainly, and encrypting it would break dispatcher grouping in Report 5 for no
patient-privacy gain.

### 4.1 API contracts

All under the existing `InsuranceController`, feature key `INSURANCE`,
tenant-scoped. `ApiResponse.ok(message, data)` wrapper throughout.

| Method | Path | Purpose |
|---|---|---|
| POST | `/insurance/{id}/stages/preauth` | Stage 1 — submit/update pre-auth request |
| POST | `/insurance/{id}/stages/preauth-approval` | Stage 2 — record TPA decision |
| POST | `/insurance/{id}/stages/enhancement` | Stage 3 — request enhancement (409 if no bill linked) |
| POST | `/insurance/{id}/stages/enhancement-approval` | Stage 4 — record TPA decision |
| POST | `/insurance/{id}/stages/checklist` | Stage 5 — document checklist |
| POST | `/insurance/{id}/stages/dispatch` | Stage 6 — courier/email dispatch |
| POST | `/insurance/{id}/stages/disallowance` | Stage 7 — cheques + itemised deductions |
| GET | `/insurance/{id}/desk` | Full desk view: record + stage timestamps + cheques + checklist |
| PUT | `/insurance/updateBillId` | **fixed** — actually links the bill |
| GET | `/insurance?searchFromDate&searchToDate` | **fixed** — actually filters by date |
| GET | `/insurance/courierVendors` | `["PROFESSION_COURIER","FIRST_FLIGHT","ST_COURIER","DTDC","BLUE_DART"]` |
| GET | `/insurance/modeOfCommunication` | **changed** → `["FAX","MAIL"]` |
| GET | `/insurance/modeOfDispatch` | `["COURIER","EMAIL"]` |
| GET | `/insurance/getStatus` | full workflow-stage list as `{id,name}` |

Error cases:

**Correction made during implementation:** this section originally specified 409
for the bill-linkage failure. `GlobalExceptionHandler` maps
`BusinessRuleViolationException` to **400**, and every other domain rule in this
codebase surfaces that way. Introducing a 409 would have meant a new exception
type and a new handler branch in shared code to make one endpoint different from
its neighbours. The error *code* in the message is unchanged and is what the
frontend switches on.

| Condition | Status | Code |
|---|---|---|
| Enhancement requested with no linked bill | 400 | `INSURANCE_BILL_NOT_LINKED` |
| Courier dispatch with no POD number | 400 | `INSURANCE_POD_REQUIRED` |
| Courier dispatch with no vendor | 400 | `INSURANCE_COURIER_REQUIRED` |
| Email dispatch with no destination | 400 | `INSURANCE_DISPATCH_MAIL_REQUIRED` |
| `searchFromDate` after `searchToDate` | 400 | `INSURANCE_INVALID_DATE_RANGE` |
| Stage submitted out of order (would move backwards) | 200 | stage retained, fields updated — monotonic by design |
| `FAX` mode with no fax number / `MAIL` with no mail id | 400 | bean validation |
| Approved status with no approved limit | 400 | `INSURANCE_APPROVED_LIMIT_REQUIRED` |
| Rejected status with no reason | 400 | `INSURANCE_REJECTION_REASON_REQUIRED` |
| Cheque amount ≤ 0 | 400 | bean validation |

Idempotency: stage submissions are **upserts on the insurance row**, naturally
idempotent — resubmitting Stage 2 overwrites Stage 2 fields and re-stamps
`preauth_approval_updated_*`. Cheque receipts are inserts and are *not*
idempotent; the UI submits the full cheque list per save and the service
reconciles by id (D-1 makes this cheap).

### 4.2 Data model

Flyway version **V199** — verified free: the directory ends at V197
(`patient_portal_identity`) and V198 is reserved by WO-018/PS-001.
**The requirement document's "V197" is already taken.**

`ALTER TABLE insurances` — additive only, no column altered or dropped:

| Column group | Columns | PII? | Encrypted? | Token? |
|---|---|---|---|---|
| Stage 1 | `card_validity DATE`, `preauth_communication_to_tpa`, `preauth_fax_no`, `preauth_mail_id`, `preauth_applied_date`, `preauth_requested_amount`, 4× audit | no | no | no |
| Stage 2 | `claim_no` | **yes** (insurance id) | **yes** | `claim_no_token` |
| Stage 2 | `preauth_approval_status`, `preauth_date_of_approval`, `preauth_communication_by_tpa`, `preauth_approve_fax_no`, `preauth_approve_mail_id`, `preauth_approved_limit`, 4× audit | no | no | no |
| Stage 2 | `preauth_rejection_reason` | **yes** (clinical free text) | **yes** | no |
| Stage 3 | `enhancement_type`, `enhancement_applied_date`, `enhancement_requested_amount`, `enhancement_communication_to_tpa`, `enhancement_fax_no`, `enhancement_mail_id`, 4× audit | no | no | no |
| Stage 3 | `reason_for_enhancement` | **yes** (clinical) | **yes** | no |
| Stage 4 | `enhancement_approval_status`, `enhancement_date_of_approval`, `enhancement_communication_by_tpa`, `enhancement_approved_limit`, 4× audit | no | no | no |
| Stage 4 | `enhancement_rejection_reason` | **yes** (clinical) | **yes** | no |
| Stage 5 | `checklist JSONB DEFAULT '{}'`, 4× audit | no | no | no |
| Stage 6 | `mode_of_dispatch`, `courier`, `dispatch_date`, `dispatched_by`, `dispatch_mail_id`, `pod_no`, `reason_for_delay`, 2× audit | no | no | no |
| Stage 7 | `disallowance_created_by/date` | no | no | no |
| Stage | `insurance_current_status VARCHAR(50)` | no | no | no |

New table `insurance_cheque_receipts` (D-1): `id`, `tenant_id`, `branch_id`,
`insurance_id` FK, `cheque_no`, `cheque_date`, `drawn_on`, `payable_at`,
`amount BIGINT` (paise, `CHECK > 0`), `authorised_by`, `status`, 4× audit.
Tenant + branch columns because it extends `AuditableEntity` and must be filtered
like every other business row.

New feature row: `REPORT_INSURANCE` for every existing tenant, granted to
`ADMIN` (via full-access) and `HOSPITAL_ADMIN`.

Encrypted columns are `TEXT` (ciphertext is ~2.4× plaintext + IV/tag; the repo's
own V146 widened `policy_number` to 512 for exactly this reason).

**Rollback** (documented, not automated — Flyway community has no undo):

```sql
DROP TABLE IF EXISTS insurance_cheque_receipts;
ALTER TABLE insurances DROP COLUMN IF EXISTS card_validity, ... ;  -- full list in migration header
DELETE FROM role_features WHERE feature_id IN (SELECT id FROM features WHERE feature_key='REPORT_INSURANCE');
DELETE FROM features WHERE feature_key='REPORT_INSURANCE';
DELETE FROM print_templates WHERE document_type IN ('LETTER_ACCEPTANCE','ENHANCEMENT_REQUEST');
```

Purely additive, so rollback loses only data captured by the new flow.

### 4.3 Agent-layer changes

None. The manual desk is a human workflow. Deliberately not exposed as an agent
tool: an agent that can mark a claim dispatched or record a cheque is an agent
that can move money without a human reading the fax it claims to have received.

### 4.4 Frontend changes

`frontend/src/features/insurance/`:

- `pages/InsurancePage.tsx` — rewritten: date-range + status filter + grid, row
  chevron opens the desk modal.
- `components/StageTimeline.tsx` — left sidebar, 7 steps, completed steps show
  their timestamp, locked steps unclickable.
- `components/stages/*.tsx` — one form per stage.
- `components/LinkBillModal.tsx` — `GET /bill/current-month/{patientId}`.
- `components/ChecklistGrid.tsx`, `ChequeGrid.tsx`, `DisallowanceEditor.tsx`.
- `insuranceDesk.ts` — **pure, tested** logic: stage unlocking, card-expiry
  comparison, fax/mail validation, cheque totals, checklist shortfall.
- `services/insurance/insuranceApi.ts` — extended client.

Card-expiry warning is computed in the pure module and rendered as an amber
banner, not a blocking error: an expired card is a fact the desk must see, not a
reason to refuse to record what the TPA said.

## 5. Compliance impact

Answering `references/compliance.md` §6:

- **Personal data touched:** `claim_no` (encrypted + token), three free-text
  reason fields (encrypted, clinical), plus the already-encrypted
  `policy_number` / `pre_auth_number` / `member_id` read by the print templates.
  Checklist JSONB holds document *names and counts* only — the design forbids
  free-text clinical detail there, and the non-submission reason field is
  operational ("lost by attender"), which the UI labels accordingly.
- **New consent purpose:** none. Sharing claim documents with the patient's own
  insurer is the purpose the patient already consented to at admission for a
  credit/cashless admission. No new party receives data — the *hospital* faxes
  the TPA, exactly as today, and HIMS only records that it happened.
- **Cross-border data flow:** none. No LLM, no external API. Postgres only.
- **Audit records:** every stage writes `<stage>_created_by/_created_date` and
  `<stage>_updated_by/_updated_date` from `SpringSecurityAuditorAware`, plus the
  inherited `AuditableEntity` fields. Cheque receipts are rows with their own
  `created_by`. Stage progression is monotonic so history is never erased by a
  later edit of an earlier stage.
- **Erasure/correction:** all new data lives on `insurances` and one child table,
  both reachable from `patient_id`. No new copy of patient data is created
  outside the primary store, so DPDP erasure needs no new sweep — the existing
  patient erasure path (WO-011/P-003) reaches it. Print output is transient.
- **Retention:** claim records are financial records; India's tax/company law
  retention (8 years) dominates DPDP minimisation. No new retention job — these
  rows follow the bill they are linked to.
- **Logs:** stage events log `insuranceId`, `patientId`, stage name and outcome.
  Never the claim number, never a reason field, never a cheque number.

## 6. Observability plan

**Log events** (INFO unless stated), all carrying `correlationId`, `tenantId`,
`branchId`, `userId`, `insuranceId`, `patientId`:

| `event` | When | Extra fields |
|---|---|---|
| `insurance.desk.stage.submitted` | any stage saved | `stage`, `previousStage`, `advanced` (bool) |
| `insurance.desk.bill.linked` | bill linkage | `billId` |
| `insurance.desk.enhancement.blocked` (WARN) | enhancement with no bill | — |
| `insurance.desk.dispatch.recorded` | Stage 6 | `mode`, `courier` (never `podNo` — it is a tracking id tied to a patient's docket) |
| `insurance.desk.cheque.recorded` | cheque saved | `chequeCount`, `totalPaise` — never `chequeNo`, `drawnOn` |
| `insurance.desk.disallowance.applied` | Stage 7 deductions | `lineCount`, `totalDisallowedPaise` |
| `insurance.desk.card.expired` (WARN) | Stage 1 saved with `card_validity < today` | `daysExpired` |

**Metrics** (Micrometer → `/actuator/prometheus`, per `references/observability.md` §3):

- `hms_insurance_desk_stage_transitions_total{stage,outcome}` — counter
- `hms_insurance_desk_stage_duration_seconds{from_stage,to_stage}` — histogram,
  the desk's real SLA question ("how long do we sit between approval and dispatch")
- `hms_insurance_desk_dispatch_total{mode,courier}` — counter
- `hms_insurance_desk_disallowed_paise_total{}` — counter
- `hms_insurance_desk_cheque_receipts_total{}` — counter

Labels are stage/mode/courier only — nothing patient-scoped, per the
cardinality rule.

**Traces:** span `insurance.desk.stage` with attributes `stage`, `advanced`,
`insuranceId`. No PII attributes.

**Alerts:** `hms_insurance_desk_stage_duration_seconds` p95 for
`preauth_approval → dispatch` exceeding 7 days — a claim sitting undispatched
past TAT is money the hospital will not collect, and nobody currently notices.

**Dashboard:** new Grafana row "Insurance desk" — funnel by stage, dispatch
volume by courier, disallowance rupees per week.

## 7. Acceptance criteria

1. Given an insurance record at `PREAUTHORISATION`, when Stage 2 is submitted
   with status `APPROVED` and a limit, then `insurance_current_status` becomes
   `PREAUTHORISATION_APPROVAL` and `preauth_approved_limit` is persisted.
2. Given a record at `DISPATCH_ENTRY`, when Stage 1 is re-submitted, then the
   Stage 1 fields update and `insurance_current_status` stays `DISPATCH_ENTRY`
   (monotonic).
3. Given an insurance record with `bill_id` null, when Stage 3 is submitted,
   then the response is 400 carrying `INSURANCE_BILL_NOT_LINKED` and no
   enhancement fields are written.
4. Given `PUT /insurance/updateBillId`, when called with a valid bill, then
   `insurances.bill_id` is actually updated (regression test for the stub).
5. Given `GET /insurance?searchFromDate=A&searchToDate=B`, then only records
   created in [A,B] are returned (regression test for the stub).
6. Given a Stage 2 submission with `approvalStatus=APPROVED` and no
   `approvedLimit`, then 400.
7. Given `communicationToTpa=FAX` and a blank `faxNo`, then 400.
8. **Tenant B, authenticated, requesting tenant A's insurance desk view, receives
   404 — the record is absent, not filtered.**
9. **Tenant B cannot read tenant A's cheque receipts**, asserted directly on the
   repository with tenant B's context.
10. A user in a tenant-scoped role **without** the `INSURANCE` feature receives
    403 from every new stage endpoint (asserted with a non-SUPERADMIN user).
11. `claim_no` is stored as ciphertext — a raw JDBC read of the column does not
    equal the plaintext, and `claim_no_token` matches
    `PiiSearchTokenService.token(plaintext)`.
12. `reason_for_enhancement` is stored as ciphertext.
13. Stage 7 with itemised deductions results in
    `charge_line_items.disallowed_amount` updated for exactly those lines.
14. `hms_insurance_desk_stage_transitions_total{stage="DISPATCH_ENTRY"}`
    increments when Stage 6 is submitted.
15. No log statement in the new code interpolates `claimNo`, `chequeNo`,
    `podNo`, or any reason field — asserted by `check_conventions.py` plus a
    reading of every new `log.` call site.
16. `V199` applies cleanly to a database already at V197 and is idempotent on
    replay.
17. Pure frontend module `insuranceDesk.ts`: card expiring today is not expired;
    card expired yesterday is; stage unlocking matches the state table.

## 8. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Two enhancement models (manual + NHCX) confuse a future reader | High | Medium | D-2 recorded here; javadoc on both pointing at each other |
| Column-name drift from the requirement doc | Medium | Low | Spec names kept verbatim; units documented |
| Desk clerk records a cheque against the wrong claim | Medium | High | Cheque grid shows patient + bill number; cheques are rows with `created_by`, so it is correctable and attributable |
| `insurances` row grows to ~70 columns | Certain | Medium | Accepted: it mirrors the source system and keeps the stage flow in one transactional row. Revisit if a second workflow lands |
| Backfilling legacy rows into the stage machine | Medium | Medium | Deliberately not done; null status = legacy view |
| Disallowance edits race with billing edits on the same line | Low | High | Reuses the single existing write path (D-3), which is `@Transactional` |

## 9. Open questions — answered (autonomous)

1. **Cheque list: JSONB per spec, or a table?** → Table. Reasoning in D-1.
   Override cost: one migration.
2. **Reuse `preauth_enhancements`?** → No. Reasoning in D-2.
3. **Is `reason_for_enhancement` clinical data?** → Treated as yes, encrypted
   (D-7), per the §2 autonomy rule "if unsure whether a field is PII, treat it
   as PII".
4. **New feature key for the desk?** → No, reuse `INSURANCE` (D-6). A new key
   would make every desk endpoint 403 for existing users until an admin granted
   it, and this replaces a screen they already have.
5. **Courier vendor list — hardcoded enum or master table?** → Hardcoded enum
   for now, matching the source system. A courier master is a settings feature
   nobody asked for; the enum is one migration away from becoming a table if a
   hospital uses a sixth courier.
6. **Should `GET /insurance` date range default to today?** → Yes, matching
   VitalSoft, but implemented as "last 30 days" when both params are absent —
   defaulting to today on a desk screen shows an empty grid every morning.

## 10. Estimate

7 task cards, mostly sequential (migration → domain → service/API → print →
frontend logic → frontend UI), with the print-template card parallelisable
against the frontend cards.

---

*Autonomous mode: confirmed without a Gate 1 stop. Decisions D-1…D-8 are the
ones most likely to warrant an override; they lead the checkpoint report.*
