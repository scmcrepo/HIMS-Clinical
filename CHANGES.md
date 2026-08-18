# Insurance desk — WO-020 & WO-021

Everything added for the VitalSoft manual TPA insurance desk requirement.
Read `.hms-agent/HANDOFF.md` first — it is the live state of the campaign.

## Read these before touching the code

| File | Why |
|---|---|
| `.hms-agent/HANDOFF.md` | Current state, what to do next, in priority order |
| `.hms-agent/work-orders/WO-020-manual-tpa-insurance-desk.md` | The desk flow: design, decisions D-1…D-8, compliance, acceptance criteria |
| `.hms-agent/work-orders/WO-021-insurance-reports.md` | The ten reports |
| `.hms-agent/ledger.json` | Per-task status and evidence |

## Verification status — read this before trusting anything

**Frontend: verified.** `tsc --noEmit` clean, 86/86 tests green across the
insurance feature, 258/260 overall (the 2 failures are pre-existing
`LoginPage.test.tsx` ones present before this work).

**Backend: written, never compiled.** Gradle could not bootstrap in the
authoring environment (`services.gradle.org` and Maven Central were blocked), so
no backend code here has been through a compiler and no backend test has run.
Expect to fix import paths and signatures in the three test classes.

```bash
cd backend && ./gradlew test          # the gate on everything backend
./gradlew flywayMigrate               # V199 + V200
```

The isolation test additionally needs Docker (Testcontainers); it skips itself
automatically when Docker is absent — check it actually ran.

## New files

### Backend — schema
- `db/migration/V199__manual_tpa_insurance_desk.sql` — 7-stage columns,
  `insurance_cheque_receipts`, `REPORT_INSURANCE` feature. Additive only.
- `db/migration/V200__insurance_desk_print_templates.sql` — Letter of
  Acceptance, Enhancement Request.

### Backend — domain
- `domain/insurance/model/InsuranceWorkflowStage.java` — monotonic state machine
- `domain/insurance/model/{ModeOfCommunication,ModeOfDispatch,CourierVendor,TpaDecision}.java`
- `domain/insurance/model/InsuranceChequeReceipt.java`
- `infrastructure/persistence/insurance/InsuranceChequeReceiptJpaRepository.java`

### Backend — application & API
- `application/insurance/InsuranceDeskService.java` — the seven stages
- `api/insurance/request/*.java` — 7 stage request DTOs
- `api/insurance/response/{InsuranceDeskResponse,InsuranceChequeResponse,InsuranceStageTimestamps}.java`
- `application/report/modules/InsuranceReportDataService.java` — 10 scoped queries
- `application/report/modules/InsuranceReportService.java`
- `api/report/InsuranceReportController.java`

### Backend — tests (none have run)
- `domain/insurance/InsuranceWorkflowStageTest.java` — ~23 cases
- `application/insurance/InsuranceDeskServiceTest.java` — ~30 cases
- `application/report/modules/InsuranceReportTenantIsolationTest.java` — the
  cross-tenant guard; **the most important test in this changeset**

### Frontend (all verified)
- `features/insurance/insuranceDesk.ts` + `.test.ts` — 50 cases
- `features/insurance/insuranceReports.ts` + `.test.ts` — 26 cases
- `features/insurance/components/` — timeline, 7 stage forms, desk modal,
  bill-link modal, form primitives, `StageTimeline.test.tsx` (10 cases)
- `features/insurance/pages/InsurancePage.tsx` — rewritten worklist

## Modified files

- `Insurance.java` — 7 stages of fields, `effectiveApprovedLimit()`,
  `isCardExpired()`, `advanceStage()`
- `InsuranceController.java` — 7 stage endpoints; **two stubs fixed**:
  `updateBillId` never persisted, `getByDateRange` ignored both dates
- `InsuranceJpaRepository.java` — date-range and claim-token queries
- `TenantService.java` — `REPORT_INSURANCE` in the catalogue + HOSPITAL_ADMIN
- `PrintServiceImpl.java` — two model builders + dispatch cases
- `InsuranceReportsTab.tsx` — 10 cards replacing the single generic one
- `reportApi.ts` — routing for the 10 reports
- `insuranceApi.ts` — desk endpoints and command types

## Corrections made to the requirement documents

- **V197 was already taken** (patient portal); V198 reserved by WO-018. Used V199/V200.
- **`roles` has no `role_key` column** — it is `name`. The spec's grant SQL would have failed.
- **409 → 400** for `INSURANCE_BILL_NOT_LINKED`: `GlobalExceptionHandler` maps
  `BusinessRuleViolationException` to 400, and a 409 would need new shared plumbing.

## Decisions most likely to warrant your override

- **D-1** — cheque receipts are a real table, not the spec's `cheque_list` JSONB.
  Money that reports must SUM, needing per-row `created_by`. Cost to reverse: one migration.
- **D-7** — `reason_for_enhancement` and both rejection reasons are **encrypted**.
  They are free text explaining why a patient's treatment cost more, which is
  clinical information. The spec had them as plain `VARCHAR(255)`.
- **D-2** — the manual flow does not reuse `preauth_enhancements` (V196). That
  table is keyed by `nhcx_transaction_id` with states driven by async callbacks.

## Known gaps, separate from this work

- The repo audits **no** report execution, for any of its 60+ reports. Worth its
  own work order now that insurance reports expose claim values.
- Repo eslint is broken: v9 wants `eslint.config.js` and the repo has none.
