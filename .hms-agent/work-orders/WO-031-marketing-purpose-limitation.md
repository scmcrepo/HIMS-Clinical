# WO-031 — Purpose limitation on the marketing patient export

**Phase:** 6 — DPDP remediation
**Status:** DRAFT — awaiting Gate 1
**Raised:** 2026-08-31, during build reconciliation
**Supersedes as priority:** the `patient_pediatric` encryption item (see §7)

---

## 1. The finding

Two endpoints on `PatientController` are permissioned `hasPermission('MARKETING','')`
and neither passes through `ConsentGate`:

| Endpoint | Returns |
|---|---|
| `GET /patient/getPatientsForMaketing` | up to 1000 full `PatientResponse` records |
| `GET /patient/getPatientBySearch` | up to 1000 CSV rows: name, gender, contact number |

`PatientResponse` carries `firstName`, `lastName`, `fullName`, `dateOfBirth`,
`age`, `contactNumber`, `email`, `bloodGroup` and `address`. Every one of those
is an `EncryptedStringConverter` column on `Patient`, so the response is a bulk
**decryption** of the patient base for a purpose that is not care.

`MARKETING` is a declared `ConsentPurpose` with `requiredForCare = false`. It is
seeded as a purpose by V205 and has draft notice text in V211. The consent
machinery for it exists. Nothing consults it.

`ConsentGate` is wired into exactly three services — `AbhaService`,
`PolicyDiscoveryService`, `PreAuthService` — which is card C-004's scope as
written. These two endpoints were never in that scope, and no card covers them.

### Second defect in the same methods

Both endpoints accept `area`, `consultant`, `minAge`, `maxAge` and `gender` and
**ignore all of them**. Both call `searchPatients("")` — an empty query — and
return the first 1000 rows. A caller who asks for one area's over-60s receives
an unfiltered dump of the tenant's patient base and has no way to tell from the
response that the filter did not apply.

This makes the consent problem worse rather than being a separate bug: the
narrowing that a marketing user believes they applied is what would have kept
the export proportionate.

### Why this outranks what we were about to build

The intended next item was encrypting `patient_pediatric.pediatric_data`.
Investigation showed that table is written by nothing: its only writer,
`PatientController.updatePediatric`, is a stub that returns
`"Pediatric data updated"` and performs no update. Whether that table needs
encrypting, dropping, or a legacy sweep depends on whether it holds rows in
production — one query, recorded in §7 — and the honest answer is that we do
not know yet. This finding needs no such question: the endpoints are live, the
data is decrypted, and the purpose is marketing.

## 2. Scope

1. Gate both endpoints on `MARKETING` consent, per patient, filtering out
   non-consenting patients rather than failing the whole request.
2. Make the declared filters actually filter, or remove them from the signature.
   A parameter that is accepted and ignored is worse than one that is absent.
3. Cap and paginate. 1000 hardcoded rows is not a considered limit.
4. Emit an audit event and a metric per export, with a row count.
5. Return the minimum fields marketing needs, not the full `PatientResponse`.

## 3. Explicit non-scope

- **Not** touching `hasPermission('MARKETING','')` or the RBAC model. The
  permission is orthogonal to consent: a user may be authorised to run marketing
  and still have no lawful basis for a given patient.
- **Not** fixing `updatePediatric`, `updateClinicalFlag` or `parsePatientCsv`,
  the three other stubs in this controller that return success while doing
  nothing. Logged in §7; they are a separate work order.
- **Not** building a marketing consent capture UI. Consent is captured through
  the existing surface built in WO-023.
- **Not** deciding the `patient_pediatric` question.

## 4. Compliance impact

DPDP s. 6 requires consent to be specific to the purpose it was given for.
Consent to treatment is not consent to marketing, and `ConsentPurpose` already
encodes that distinction with `requiredForCare = false`. The current behaviour
processes personal data for a secondary purpose with no lawful basis recorded
against any of the affected principals.

Note the interaction with `ConsentService`'s provenance work (C-002): a
`SYSTEM_INFERRED` row must not satisfy this gate. `hasConsent` already ignores
inferred provenance, so gating correctly inherits that, but the acceptance
criteria must pin it — an export that silently accepted manufactured consent
would reintroduce the defect WO-022 was raised to fix.

## 5. Observability

- `hms_marketing_export_total{tenant, endpoint}` — counter
- `hms_marketing_export_patients_total{tenant, outcome="included"|"excluded_no_consent"}`
  — the excluded count is the number that matters at audit
- `event=marketing.export` structured log: tenant id, user id, requested filters,
  rows returned, rows excluded. **Surrogate ids only, no patient fields.**
- Alert if a single export returns more than a threshold of rows, or if
  `excluded_no_consent` is zero across a window — zero exclusions means the gate
  is not firing, which is how the WO-022 defect stayed invisible.

## 6. Acceptance criteria

1. A patient with no `MARKETING` consent record does not appear in either
   response. Test asserts by patient id, not by count.
2. A patient whose only `MARKETING` row is `SYSTEM_INFERRED` does not appear.
3. A patient who granted and then withdrew does not appear.
4. Passing `gender=FEMALE` returns only female patients — or the parameter is
   gone from the signature. No third option.
5. Tenant B running the export receives no tenant A patient, asserted with an
   authenticated cross-tenant test.
6. `hms_marketing_export_patients_total{outcome="excluded_no_consent"}`
   increments when a patient is filtered out.
7. No patient field appears in any log line emitted by these methods.

## 7. Risks and open questions

- **Consent will be near-universally absent.** Nobody has ever been asked for
  `MARKETING` consent, so on day one these endpoints will return close to zero
  rows. That is the correct behaviour and it will look like an outage to whoever
  runs marketing. It needs saying before deploy, not after. Recommend shipping
  behind the existing warn/enforce switch that C-002 established, running in warn
  mode first so the excluded count is visible before the export actually shrinks.
- **Is anything calling these endpoints?** No frontend reference was found.
  If they are dead, deleting them is cheaper and safer than gating them, and
  deletion removes a bulk decryption path entirely. **Needs the user to confirm
  against access logs before we build.**
- **`patient_pediatric` row count**, carried over — run against production:
  `SELECT COUNT(*) FROM patient_pediatric;` Zero means drop the table. Non-zero
  means unencrypted paediatric data with no reader and no writer, which is
  retained-but-unreachable PHI and a Rule 12 problem.
- Three further stubs in `PatientController` return success while doing nothing
  (`updatePediatric`, `updateClinicalFlag`, `parsePatientCsv`). A caller cannot
  distinguish these from a real write. Separate work order; recorded here so it
  is not lost.

## 8. Proposed cards

| Card | Description |
|---|---|
| B-001 | `ConsentGate` batch check + filter in the marketing query path, warn/enforce aware |
| B-002 | Make the filters filter, or remove them; cap and paginate; narrow the response DTO |
| B-003 | Audit event, two metrics, alert rules |
| B-004 | Tests: the seven acceptance criteria above, cross-tenant case written first |

`B-` is free campaign-wide. (`N-` is not, despite the session handover — it is
WO-008's.)
