# Session changes — 2026-08-31

Commit `4300770`. Everything below is in this package.

**None of the Java in this commit has been compiled.** No JDK and no Maven
access in the session that produced it. Run `./gradlew test` before merging.

---

## 1. Code changes (two files)

### `ErasureService.countRetained` — live bug fixed

`patient_pediatric` (created in V010) is keyed on `patient_id` alone and has no
`tenant_id` column. The generic predicate
`WHERE patient_id = :pid AND tenant_id = :tid` therefore threw
`column "tenant_id" does not exist` on **every** erasure request.

`sweep()` caught it, recorded the store FAILED, and set the request
`PARTIALLY_COMPLETED`. Consequences: no erasure could produce the clean receipt
the runbook expects, the patient was never told how many paediatric records were
retained, and a permanently-FAILED target teaches operators to read FAILED as
normal — which is how a real failure gets missed.

Fixed by scoping through `patients`:

```sql
SELECT COUNT(*) FROM patient_pediatric pp
  JOIN patients p ON p.id = pp.patient_id
 WHERE pp.patient_id = :pid AND p.tenant_id = :tid
```

Scoped through the join rather than by dropping the tenant predicate: an
unscoped count could report another hospital's rows in a number shown to a data
principal. A dead ternary in the same method (both branches identical) was
removed.

### `PatientController` — two endpoints removed (WO-031, card B-001)

`GET /patient/getPatientsForMaketing` and `GET /patient/getPatientBySearch`.

Both were permissioned `hasPermission('MARKETING','')`, returned up to 1000
patients each — `firstName`, `lastName`, `dateOfBirth`, `contactNumber`,
`email`, `bloodGroup`, `address`, all `EncryptedStringConverter` columns — and
passed through no `ConsentGate`. Each call was a bulk decryption of the patient
base for a purpose that is not care.

`MARKETING` is a declared `ConsentPurpose` with `requiredForCare = false`, seeded
by V205 with draft notice text in V211. The consent machinery existed. These
endpoints never consulted it. DPDP s. 6 requires consent specific to the
purpose.

Both also accepted `area`, `consultant`, `minAge`, `maxAge` and `gender` and
ignored all five, calling `searchPatients("")` and returning the first 1000 rows.

**Deleted rather than gated.** Gating adds a control around a bulk decryption
path; deleting removes the path. No caller exists anywhere in this repository —
backend, frontend, mobile, agent-service, docs or tests.

**Residual risk:** this does not prove no *external* caller exists. An unknown
integration now gets 404. That is visible and revertible; an ungated bulk PII
export is neither. Confirm against access logs.

To restore, rebuild behind `ConsentGate` per WO-031 §5. Do not revert the commit.

## 2. New scripts — `.hms-agent/scripts/`

All three run without a compiler.

| Script | Needs | Result |
|---|---|---|
| `check_erasure_sql.py` | Postgres with migrations replayed | 28/28 statements valid, all tenant-scoped |
| `check_retention_sql.py` | Postgres with migrations replayed | 12/12 valid; parses policies out of V213 so it stays honest if the seed changes |
| `check_marketing_export.py` | source only | exit 0 now; **exit 1 on the pre-change tree**, naming both endpoints |

`check_marketing_export.py` asserts the property, not the absence of two method
names: any endpoint permissioned `MARKETING` returning `PatientResponse` or a
raw `List<String>` without a consent-gate reference fails it. It was validated
in both directions — a guard never shown to fail is not a guard.

## 3. Ledger repairs

**Ten duplicate card ids.** `ledger.py find_task` returns the first match in
work-order order, so every WO-028 and WO-029 card was unreachable and any
command aimed at one hit an older card in WO-002, WO-010 or WO-019 instead —
two of which were already DONE. WO-028 `H-*`/`F-*` renamed to `Q-001`–`Q-007`,
WO-029 `M-*` to `U-001`–`U-003`. 158 cards, zero duplicates. Referencing
documents updated.

The session handover says the next free prefix is `N-`. It is not — WO-008 uses
it. Free single letters now: O, X, Y, Z.

**Stale `repo` path** corrected, which unblocks `ledger.py handoff`.

**WO-031 registered** with cards B-001 to B-003.

**Evidence recorded as notes on 23 cards.** Nothing marked DONE — no compiler
ran, and every DPDP card is a vertical slice whose backend half is unverified.

## 4. What was verified this session

- **Migrations:** all 202, V001–V214, replayed into a fresh Postgres 16.15 with
  `ON_ERROR_STOP=1`. Zero failures, 151 tables, all ten DPDP tables present,
  key columns landing with expected types. **DDL proven; backfill DML not** —
  those ran against empty tables.
- **Frontend:** `tsc --noEmit` gives 7 errors, all in `DoctorCalendarPage.tsx`,
  committed and untouched by this campaign. `vitest` 258 passed / 2 failed, both
  `LoginPage.test.tsx`, last modified 2026-07-15 — six weeks before the campaign.
  Every DPDP frontend file is clean.
- **Static Java:** 955 files parsed, 0 syntax errors, all `com.hms` imports
  resolve. Not a compiler.
- **Conventions:** 604 findings vs a 605 baseline, HIGH unchanged at 60. The
  diff is only the expected removals plus surviving endpoints at shifted lines.

## 5. Open questions this session raised

1. `SELECT COUNT(*) FROM patient_pediatric;` — zero means drop the table.
   Non-zero means unencrypted Rule 12 paediatric data with no reader and no
   writer. Its only writer, `PatientController.updatePediatric`, is a stub that
   returns success and writes nothing.
2. Did anything external call the two removed endpoints? Access logs.
3. `pii_disclosure_audit` identifies its subject as `subject_id`, not
   `patient_id`, so `build_inventory.py` structurally cannot flag it as missing
   an erasure strategy — and it has none. Register it as RETAIN with a stated
   reason, or teach the script the column. Do not just allowlist it.
4. `hitl_escalations.resolved_at` is nullable and is that policy's date column,
   so an escalation never resolved keeps its transcript indefinitely and no
   retention policy reaches it.
5. Three further stubs in `PatientController` return success while doing nothing:
   `updatePediatric`, `updateClinicalFlag`, `parsePatientCsv`. A caller cannot
   distinguish these from a real write.

## 6. Not included

`frontend/package-lock.json` was reverted. `npm install` rewrote it because it is
out of sync with `package.json` — `npm ci` currently fails for that reason. Real
problem, separate change.
