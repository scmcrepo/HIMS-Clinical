# WO-021 — Insurance MIS reports (10 reports)

| | |
|---|---|
| **Roadmap phase** | Phase 10 — VitalSoft parity: manual insurance desk |
| **Status** | CONFIRMED (autonomous) |
| **Author** | hms-agentic-delivery agent |
| **Date** | 2026-08-15 |
| **Depends on** | WO-020 (the stage columns these reports read) |

## 1. Objective

Replace the single generic "Insurance Claim Summary" card with the ten reports
the insurance desk actually runs: pre-auth raised and status, enhancement raised
and status, claim dispatch, disallowance summary and detail, document-pending
worklist, IP outstanding credit bills, and receivables ageing. These are how a
hospital finance team answers "what is stuck, and where is our money".

## 2. Scope

### In scope

- `InsuranceReportDataService` — ten raw-JDBC queries, every one scoped through
  `ReportScope`.
- `InsuranceReportService extends BaseReportService` — report catalogue,
  parameter metadata, custom HTML for the multi-section reports.
- `InsuranceReportController extends BaseReportController` at `/report/insurance`,
  `@PreAuthorize("hasPermission('REPORT_INSURANCE','')")`.
- Frontend: ten report cards in `InsuranceReportsTab.tsx`, filter dialogs, and
  the `REPORT_CATEGORY_PATHS` entries that route them.
- `getAgeingCriteria` corrected to the six brackets the ageing report uses.

### Explicitly out of scope

- **JasperReports / `.jrxml`.** This repo does not use Jasper; it has its own
  `ReportEngine` with HTML/PDF/CSV/XLSX output. Porting Jasper templates would
  add a dependency and a second rendering path to serve the same tables.
- Scheduled email delivery of reports.
- Cross-tenant/platform roll-ups.

## 3. Current state

- `InsuranceReportsTab.tsx` renders exactly one `ReportCard`
  (`insurance_summary`), which `reportApi.ts` routes to the **billing** category
  — so there is no insurance report endpoint at all today.
- The report pattern is well established: `CollectionReportController` is 16
  lines extending `BaseReportController`; `CollectionReportDataService` builds
  SQL with `scope.predicate(alias)` + `scope.args()` and runs it through
  `ReportDbUtil.queryForList`.
- `BaseReportService.executeAsHtml/Json/Binary` all call `decryptQueryResult`,
  which is what makes encrypted columns renderable.

## 4. Design

### 4.0 Decisions

**D-1 — Ten separate report names, not one parameterised report.** Matches the
existing catalogue granularity and lets `ReportCard` show a per-report summary.

**D-2 — Status sub-reports (2 and 4) are one report with a section per status**,
rendered via `buildCustomHtml`, rather than three registered reports each. That
is what the source system's sub-report structure means, and it keeps
"Approved / In process / Rejected" on one page where a desk manager compares them.

**D-3 — Ageing buckets: <31, 31–60, 61–90, 91–120, 121–150, >150 days**, per the
source spec's six brackets — replacing the current five-bucket
`getAgeingCriteria` response, which matches nothing.

**D-4 — Ageing is measured from bill date**, not dispatch date, because the
receivable exists from the moment the credit bill is raised. Dispatch date is
shown as a column so the desk can see the gap it controls.

**D-5 — Every query is tenant-scoped through `ReportScope`, no exceptions.**
Raw JDBC bypasses the Hibernate filters entirely; this is the single sharpest
edge in the whole work order.

### 4.1 API contracts

Inherited from `BaseReportController`:

| Method | Path | Purpose |
|---|---|---|
| GET | `/report/insurance/info` | catalogue |
| GET | `/report/insurance/info/{reportName}` | parameter metadata |
| POST | `/report/insurance/{reportName}?format=HTML\|JSON\|PDF\|CSV\|XLSX` | run |

Report names: `preauth_raised`, `preauth_status`, `enhancement_raised`,
`enhancement_status`, `claim_dispatch`, `disallowance_summary`,
`disallowance_detail`, `document_pending_status`, `ip_outstanding_credit_bills`,
`insurance_ageing_analysis`.

### 4.2 Data model

**No migration.** Reads `insurances`, `insurance_cheque_receipts`, `bills`,
`charge_line_items`, `patients` — all created by WO-020 or already present. The
`REPORT_INSURANCE` feature is seeded by WO-020's V199 (one migration is cheaper
than two, and the reports are worthless without the desk data anyway).

### 4.3 Frontend changes

`InsuranceReportsTab.tsx` gains ten `ReportCard`s with per-report summary
renderers; `reportApi.ts` gains ten `REPORT_CATEGORY_PATHS` entries pointing at
`insurance`.

## 5. Compliance impact

- **Personal data:** reports display patient name, policy/claim numbers and the
  encrypted reason fields. All flow through `decryptQueryResult`, so the
  ciphertext never reaches the browser as ciphertext — and, equally, the
  *plaintext* only reaches users holding `REPORT_INSURANCE`.
- **New consent purpose:** none — internal hospital financial reporting on data
  already held.
- **Cross-border:** none.
- **Audit:** reports are reads. The existing report access path is unchanged; no
  new audit record. *(Noted for the checkpoint: this repo does not currently
  audit report execution at all. That is pre-existing, applies to all 60+
  reports, and is its own work order — not something to fix silently here.)*
- **Erasure:** reports hold no copies; they query live tables.
- **Retention:** n/a.
- **Logs:** report SQL is not logged with parameters.

## 6. Observability plan

- Log event `insurance.report.executed` (INFO) with `reportName`, `format`,
  `rowCount`, `durationMs` — never the parameters, which contain patient ids.
- Metric `hms_insurance_report_executions_total{report,format,outcome}` counter.
- Metric `hms_insurance_report_duration_seconds{report}` histogram — the ageing
  and disallowance-detail queries scan the widest and are the ones that will
  degrade first.
- Alert: p95 duration > 10s for any report — a desk clerk will assume the screen
  is broken and re-run it, doubling the load.

## 7. Acceptance criteria

1. `GET /report/insurance/info` lists exactly ten reports.
2. Each of the ten executes with valid parameters and returns rows or a clean
   empty result (no exception on empty).
3. **Every one of the ten queries carries the `ReportScope` predicate** —
   asserted by a test that runs each report as tenant B and confirms tenant A's
   insurance rows are absent from the result.
4. A user without `REPORT_INSURANCE` gets 403 (non-SUPERADMIN).
5. `preauth_status` renders three sections (Approved / In process / Rejected).
6. `insurance_ageing_analysis` buckets a bill dated 45 days ago into "31 to 60
   days" and one dated 200 days ago into "More than 150 days".
7. `disallowance_detail` totals per bill equal the sum of
   `charge_line_items.disallowed_amount` for that bill.
8. Encrypted columns render as plaintext in HTML output (proves
   `decryptQueryResult` is on the path) and are absent from any log line.
9. `GET /insurance/getAgeingCriteria` returns the six brackets in D-3.

## 8. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| A missing `ReportScope` predicate leaks another hospital's claims | Medium | **Critical** | Test per report as tenant B (AC-3); review every query by hand |
| Positional-arg misalignment when appending the scope predicate | Medium | High | Follow the documented order: predicate at end of WHERE, `args.addAll` at end of args, trailing clauses after |
| Ageing report slow on large bill tables | Medium | Medium | Duration metric + alert; index review before go-live |
| Report shows disallowance from the NHCX track and the manual track double-counted | Low | High | Manual track reads `charge_line_items` only; `claim_deduction_lines` is explicitly not joined |

## 9. Open questions — answered

1. **Jasper or the existing engine?** → Existing engine (§2). The `.jrxml` files
   are the source system's implementation, not the requirement; the requirement
   is the ten reports.
2. **New feature key or reuse `REPORT_BILLING`?** → New `REPORT_INSURANCE`,
   matching the other nine report modules and letting a hospital give finance
   staff insurance reports without billing reports.

## 10. Estimate

2 task cards: backend (data service + service + controller + scoping tests),
frontend (ten cards + routing + filter dialogs).
