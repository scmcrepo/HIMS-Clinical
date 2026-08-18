# WO-018 — Patient Portal data surface: consultants, slots, appointments, visit records

| | |
|---|---|
| **Roadmap phase** | Phase 9 — Patient Self-Service Portal |
| **Status** | CONFIRMED (autonomous; decisions recorded in §4 and §9) |
| **Author** | hms-agentic-delivery agent |
| **Date** | 2026-08-14 |
| **Depends on** | WO-017 (portal principal must exist first) |

## 1. Objective

Everything the patient does after login: browse consultants at their branch, see
real slot availability, book/reschedule/cancel, and read their own visit history
including casesheet, approved lab and radiology results, and attachments. All of
it through the portal principal from WO-017, so tenant and branch filters apply
exactly as they do to staff.

## 2. Scope

### In scope
- `api/portal/` controllers delegating to existing `application/` services.
- A `PortalAccessGuard` that asserts every returned row belongs to the token's
  `patientId` — a second check behind the tenant filter, not instead of it.
- Approved-only filtering of diagnostic reports.
- Attachment streaming with short-TTL signed URLs and a download audit.
- `self_registered` surfaced on the staff patient screen (`frontend/`).

### Explicitly out of scope
- Any new clinical write path. The portal never writes clinical data.
- Prescription rendering, bill payment, teleconsultation → PRD Phase 2/3.
- Family-member linking → PRD Phase 2. The candidate list already covers the
  common case (one number, several patients).
- Push delivery → WO-020.

## 3. Current state

- `api/appointment/AppointmentController` — class-level
  `@PreAuthorize("hasPermission('APPOINTMENT',''))`. Already has
  `GET /provider/{providerId}/availability?date=` returning
  `SlotAvailabilityResponse(slotId, fromTime, toTime, maxPatients, bookedCount,
  availableCount, isAvailable)` — exactly the shape the app's slot grid needs, so
  the portal endpoint is a thin re-projection, not new logic.
- `BookAppointmentRequest(patientId, providerId, slotId, appointmentDate, notes,
  tempPatientName, tempPatientSalutation, tempPatientGender, tempPatientPhone,
  tempPatientAge)` — 10 components. The portal fills the first four and leaves the
  `temp*` walk-in fields null.
- `aspect/SmsNotificationAspect.onAppointmentBooked(AppointmentResponse)` already
  fires SMS on booking. A portal booking inherits confirmation SMS for free — do
  not add a second sender.
- Domain rules already enforced: no reschedule when CANCELLED or CHECKED_IN, no
  cancel after CHECKED_IN. The portal surfaces these as errors; it does not
  re-implement them.
- `api/encounter/`, `api/casesheet/CaseSheetRecordController`,
  `api/diagnostic/DiagnosticReportController`, `api/attachment/AttachmentController`
  exist and are staff-scoped.
- `open-in-view: false` (landmine #5) — every portal response must be assembled
  inside the `application/` transaction. The visit-detail response is the one most
  likely to trip this.

## 4. Design

### 4.1 Two-layer authorisation, deliberately redundant

The tenant filter stops hospital A's data reaching a hospital B token. It does
**not** stop patient X's data reaching patient Y's token inside the same tenant —
nothing in the existing model does, because staff are supposed to see every
patient. So `PortalAccessGuard.assertOwns(patientId, resource)` runs on every
portal read, and every controller method takes the patient id from the **token**,
never from a query parameter.

`GET /portal/appointments?patientId=` as written in the PRD is the exact shape of
an IDOR. The portal path is `GET /portal/appointments` with no patient parameter
at all.

### 4.2 API contracts

All under `/api/portal`, all requiring `PORTAL_PATIENT`, all tenant-scoped by the
token.

| Method | Path | Notes |
|---|---|---|
| GET | `/portal/me` | Profile: name, age, gender, blood group, patient number, photo url |
| GET | `/portal/consultants` | Branch consultants, ACTIVE only; `?q=&departmentId=` |
| GET | `/portal/consultants/{id}/availability?date=` | Delegates to existing availability service |
| POST | `/portal/appointments` | Body has no patientId; taken from token. Idempotency-Key header required |
| GET | `/portal/appointments?scope=upcoming\|past&page=&size=` | |
| PUT | `/portal/appointments/{id}/reschedule` | Guard: owns + domain rules |
| DELETE | `/portal/appointments/{id}` | Guard: owns + ≥2h before slot start (D6) |
| GET | `/portal/visits?page=&size=` | 10/page, most recent first |
| GET | `/portal/visits/{encounterId}` | Guard: encounter.patientId == token.patientId |
| GET | `/portal/visits/{encounterId}/casesheet` | Template fields + recorded values |
| GET | `/portal/visits/{encounterId}/lab-reports` | `diagnosticType=LAB`, `isApproved=true` only |
| GET | `/portal/visits/{encounterId}/diagnostic-reports` | `diagnosticType=RADIOLOGY`, approved only |
| GET | `/portal/visits/{encounterId}/attachments` | Metadata only |
| GET | `/portal/attachments/{id}/download` | 302 to a 5-minute signed URL; audited |

Booking is idempotent on `Idempotency-Key`, reusing the pattern from T-008. A
patient on a flaky mobile connection who taps Confirm twice must not get two
appointments; this is the single most likely real-world defect in the whole app.

### 4.3 Data model

No new tables. One new column already delivered by WO-017 (`self_registered`).
Two new feature keys already seeded there.

`portal_attachment_downloads` — decided against a new table; the existing audit
mechanism from WO-001's `agent_tool_invocations` pattern is reused with an
append-only `portal_access_audit` row instead. **Flyway V198** (verified free),
carrying `portal_access_audit(id, patient_id, tenant_id, resource_type,
resource_id, purpose, correlation_id, occurred_at)`. Append-only, no updates, no
deletes; retention 3 years to match the clinical audit trail.

### 4.4 Approved-only filtering

`DiagnosticReport.isApproved = true` is filtered **in the repository query**, not
in a mapper and not in the client. A patient reading a provisional potassium
result before a clinician has signed it is a clinical-safety event, not a UI bug.
The test for this asserts on the SQL result set, and there is a second test that
an unapproved report id passed directly to the detail endpoint returns 404 rather
than 403 — existence itself is not disclosed.

### 4.5 Frontend changes

`frontend/src/features/patient/` — a "Self-registered" badge on the patient
record so front-desk staff know to verify ID at check-in. That is the entire
point of the flag; without the badge the column is decorative.

## 5. Compliance impact

- **Personal data touched:** the patient's own record, in full, decrypted for
  display. No other patient's data is reachable by construction.
- **New consent purpose:** none beyond `PORTAL_SELF_ACCESS` from WO-017.
- **Cross-border:** none.
- **Audit:** every read of a casesheet, report or attachment writes a
  `portal_access_audit` row with a purpose. This is what lets the hospital answer
  "who saw this record, when" after a device theft.
- **Erasure:** `portal_access_audit` is retained under the audit exemption, keyed
  by `patient_id` so it is enumerable for a DPDP access request.
- **Retention:** audit 3 years; no clinical data is copied by this WO.

## 6. Observability plan

- **Logs:** `portal.visit.viewed`, `portal.report.viewed`,
  `portal.attachment.downloaded`, `portal.appointment.booked`,
  `portal.appointment.cancelled` — all INFO with
  `{patient_id, tenant_id, resource_id, correlation_id}` and **no** patient name,
  no diagnosis text, no file name (file names routinely contain patient names).
- **Metrics:**
  - `hms_portal_requests_total{endpoint,outcome}`
  - `hms_portal_bookings_total{outcome}` — `outcome` in
    `booked|slot_full|duplicate_idempotent|rejected`
  - `hms_portal_ownership_violations_total` — increments whenever
    `PortalAccessGuard` rejects
  - `hms_portal_unapproved_report_blocked_total`
  - `hms_portal_attachment_downloads_total`
- **Traces:** span `portal.visit.detail` with `casesheet.present`,
  `lab.count`, `radiology.count`, `attachment.count`.
- **Alerts:** `hms_portal_ownership_violations_total` > 0 in 5m → page. In a
  correct client this is never non-zero, so any value means either an attack or a
  bug that is about to show one patient another's chart.

## 7. Acceptance criteria

1. A token for patient X requesting encounter E owned by patient Y (same tenant)
   receives 404, an audit row, and `hms_portal_ownership_violations_total` increments.
2. A token for tenant A requesting a tenant-B encounter id receives 404 with no
   database row read outside tenant A — asserted with the filter enabled.
3. An unapproved `DiagnosticReport` never appears in any portal response, and its
   id returns 404 on direct request.
4. Two `POST /portal/appointments` with the same `Idempotency-Key` create one
   appointment and return the same body; `hms_portal_bookings_total{outcome="duplicate_idempotent"}` increments.
5. Booking a slot at capacity returns `SLOT_FULL` and creates nothing.
6. Cancelling within 2h of slot start returns `CANCEL_WINDOW_CLOSED`.
7. Cancelling after CHECKED_IN returns the domain error unchanged.
8. Booking a date beyond the 14-day window returns `BOOKING_WINDOW_EXCEEDED`.
9. Visit detail assembles fully inside the service transaction — asserted by a
   test running with `open-in-view: false` and no `LazyInitializationException`.
10. An attachment signed URL older than 5 minutes returns 403.
11. No portal log statement interpolates a name, diagnosis or file name.

## 8. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| R1 IDOR via a patientId query parameter creeping back in during review | Medium | Severe | Portal DTOs have no patientId field; guard on every read; ownership-violation metric alerts at >0 |
| R2 Casesheet free text contains another person's name (family history) | Medium | Low | Accepted; it is the patient's own record and staff already share it on paper |
| R3 Double booking on flaky mobile networks | High | Medium | Mandatory Idempotency-Key |
| R4 Lazy-load blow-up on visit detail | Medium | Low | Assemble in `application/`; explicit test |
| R5 Slot availability races between two patients | Medium | Medium | Existing capacity check is inside the booking transaction; portal adds no new path |

## 9. Open questions — answered, per autonomous mode

1. **Booking window** (approval doc Q1) → **14 days.** Slots are configured
   day-of-week, so 14 days shows every recurring slot twice without turning the
   calendar into a planning exercise.
2. **Report sharing** (Q2) → **Allowed.** It is the patient's own data and DPDP
   grants them access to it; blocking the OS share sheet would be theatre since
   they can screenshot. Every download is audited instead.
3. **Cancellation window** (Q3) → **2 hours before slot start**, plus the existing
   CHECKED_IN rule. Anytime-cancel wastes the slot; 24h is too rigid for OP care.
4. **Payments in Phase 1** (Q6) → **No.** PRD puts it in Phase 3 and it drags in
   PCI scope.

## 10. Estimate

7 cards: profile + guard; consultants + availability; booking with idempotency;
reschedule/cancel with windows; visit list + detail; casesheet + approved reports;
attachments + signed URLs + audit + staff badge.
