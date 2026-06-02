# HIMS — Outpatient & Inpatient Module Specification

> This document defines the **exact business logic, state machines, API contracts, and UI behavior** for the Outpatient (OP) and Inpatient (IP) modules. Use this as the single source of truth to rebuild these modules in a new project.

---

## PART A: DATA MODELS

### 1. Patient
```
{
  id, patientNo: { value }, salutation, firstName, lastName, fullName,
  sex, age, ageOrDob, contactNo, address, clinicalTrial,
  checkin (boolean), visitTime, consultant, attachment, templateData
}
```

### 2. Visit
```
{
  id, patient: { id }, consultant: { id, fullName },
  date (string "DD/MM/YYYY HH:MM AM/PM"),
  checkedTime (string "HH:MM AM/PM"),
  status (enum — see State Machine below),
  visitType ("OP" | "IP"),
  vitalData: { weight, height, bp, bpBelow, pulseRate, respiratoryRate,
               temperature, headCircumference, spo2, remark, entryTime, status },
  bedNo, lastBed: { id, name }, bedStatus,
  dischargeDate, casesheetCreatedDate, waitedTime,
  templateData, appointment: {}
}
```

### 3. Admission Request
```
{
  id, visit: { id, patient, consultant },
  admissionDate (string "DD/MM/YYYY HH:MM AM/PM"),
  admissionTime (display only — split from admissionDate),
  admissionRequestStatus ("REQUESTED" | "ADMITTED"),
  reason, advice, instruction
}
```

### 4. Bed Allocation
```
{
  id, visit: { id, patient, consultant },
  bed: { id, name, bedStatus ("AVAILABLE"|"ALLOCATED"), bedTypes: [] },
  bedType: { id, name },
  fromDate, fromTime, toDate, toTime,
  transferBed: { id }, transferBedId,
  payorType: { id, name }
}
```

### 5. Discharge Summary (CaseSheet)
```
{
  id, visit: { id }, template: { id, templateName },
  data (HTML template data), html (rendered HTML)
}
```

---

## PART B: OUTPATIENT MODULE

### B1. OP Visit State Machine

This is the **core state machine** that drives the entire OP flow. Every visit progresses through these states in order:

```
CHECKEDIN → VITALSIGNS_ENTERED → CASESHEET_RECORDED → CONSULTED
```

**Rules:**
- A visit is created with status `CHECKEDIN`
- Nurse records vital signs → status becomes `VITALSIGNS_ENTERED`
- Doctor opens casesheet and records clinical data → status becomes `CASESHEET_RECORDED`
- Doctor (or staff) clicks "Mark Consulted" → status becomes `CONSULTED` (terminal state)
- "Mark Consulted" button is only visible when `status !== 'CONSULTED'` AND `casesheetCreatedDate !== null`

### B2. OP Visit Queue Page (`/outpatient`)

**On page load:**
1. Fetch today's date from `GET /config/currentDate`
2. Fetch visit status labels from `GET /visit/getVisitStatusTypes` — returns a map like `{ "CHECKEDIN": "Checked In", "VITALSIGNS_ENTERED": "Vitals Entered", ... }`
3. Fetch visit list: `GET /visit?datesearch={DD/MM/YYYY}` — returns array of Visit objects
4. Calculate waiting time for each visit (difference between checkedTime and current time)
5. Start auto-refresh: re-fetch the visit list every **60 seconds** using a recurring timer
6. Fetch current user's role: `GET /user/getUserRole` — if role is `ROLE_NURSE`, default status filter to `CHECKEDIN`

**Display columns:**
`Patient No | Patient Name | Consultant | Waiting Time | Status | Referral | Admission Req. | VitalSign | Profile`

**Filters:**
- Date picker → re-fetches list on date change
- Consultant dropdown → client-side filter (fetched from `GET /consultant?status=notDeleted`)
- Status dropdown → client-side filter (options come from visitStatusTypes)
- Search textbox → client-side filter on patient firstName or patientNo.value (case-insensitive substring match)

**Waiting time calculation logic:**
```
Parse checkedTime string → extract hours, minutes, AM/PM
Convert to 24hr format
Calculate difference from current time
Display as: "Just In" (0hr 0min) | "X mins" (0hr) | "X hr Y mins"
If visit has waitedTime from server, use that instead
If viewing a past date (not today), show "Not Consulted" instead of waiting time
```

**Role-based column visibility:**
- `ROLE_NURSE`: hides Referral, Admission Req., Profile columns. Only shows VitalSign button.
- All other roles: shows all columns.

### B3. OP Actions (4 action buttons per visit row)

#### Action 1: Referral (Refer To)
- Opens modal with fields: Visit Date, Visit Time, Consultant (dropdown)
- On submit: `POST /visit` with body `{ patient: currentVisit.patient, date: "{date} {time}", checkedTime: "{time}", consultant: { id } }`
- The referred visit appears as a new entry in the queue
- Time format: append `:00` to seconds → `"HH:MM:00 AM/PM"`

#### Action 2: Admission Request
- On click: `GET /admissionRequest/visit/{visitId}` to check if one already exists
- If exists → pre-fill form with existing data, show "Update" button
- If not exists → show empty form with visit data pre-filled, show "Create" button
- Modal fields: Patient Name (readonly), Patient No (readonly), Admission Date, Admission Time, Reason (textarea max 255), Advice to Patient (textarea max 255), Instructions to Nurses (textarea max 255)
- Create: `POST /admissionRequest` with `admissionRequestStatus: "REQUESTED"`, `admissionDate: "{date} {time}"`
- Update: `PUT /admissionRequest` with same format
- Print button visible when `mForm.id exists && status == 'active'` — print with `templateType=ADMISSION_ADVICE&visitId={visitId}`

#### Action 3: Vital Signs
- Opens modal with fields: Weight (kg, decimal, max 6 chars), Height (cm, numeric, max 6), Blood Pressure Systolic/Diastolic (mmHg, numeric, max 3 each), Pulse Rate (bpm, numeric, max 3), Respiratory Rate (bpm, numeric, max 3), Temperature (Fahrenheit, numeric, max 6), Head Circumference (cm, numeric, max 3), SPO2 (%, numeric, max 3), Remark (textarea)
- If visit status is `CONSULTED` → hide the save button (read-only view of existing vitals)
- On submit: `PUT /visit` with `{ ...visit, vitalData: { ...formData, entryTime: currentTime, status: true }, status: "VITALSIGNS_ENTERED", appointment: {} }`
- Refreshes visit list after save

#### Action 4: Profile (Patient Medical Record)
- On click: first perform **duplicate name check** — scan all visits in the current list for patients with the same firstName but different patientNo
- If duplicates found → show selection modal listing all matching patients (Patient Number, Patient Name with age/sex, Address, Select button)
- If no duplicates (or after selection) → `GET /patient/{patientId}` → store in SharedService → navigate to `patientProfile/medHis`
- This opens the Patient Profile page with tabs: Profile, Bills, UnBilled Orders, Medical Record

### B4. OP CaseSheet (`/patientProfile/caseSheet_OP`)

Accessed from Medical Record tab → auto-redirects to `/patientProfile/caseSheet_OP/clinical`.

**Left sidebar menu (icon-based):**
1. Clinical Notes — free-text clinical documentation
2. Prescription — add/save/print prescriptions
3. Diagnostic Order — add/save/print diagnostic test orders
4. Attachment — file upload/camera capture

**Header bar shows:** Consultant name (dropdown if no existing visit), Visit Date (date picker if new), page title.

Each sub-page has its own save/print logic. Menu visibility is controlled by `hasMenuPermission()` per role.

### B5. Mark Consulted
- Visible when: `status !== 'CONSULTED'` AND `casesheetCreatedDate !== null`
- On click: `PUT /visit` with `{ ...visitData, status: "CONSULTED", appointment: {} }`
- Refreshes visit list

---

## PART C: INPATIENT MODULE

### C1. IP Admission Flow (Admission Request Queue → Bed Allocation)

**Admission Request Queue page (`/admissionRequest`):**

On load: `GET /admissionRequest?status=ALL&date={today}` + `GET /admissionRequest/getAdmission?date={today}` for counts.

**Status tabs with counts:**
- ALL (requestCount + admitCount)
- Requested (requestCount) — shows "Allocate Bed" button
- Admitted (admitCount) — shows "View Details" button

**Allocate Bed flow (when status = REQUESTED):**
1. Click bed icon → loads patient/consultant from admission request data
2. `GET /bed/getAvailable` → list of available beds
3. `GET /payerType` → list of payor types
4. Modal fields: Patient Name (readonly), Consultant (readonly), Bed (dropdown of available), Bed Type (auto-populated if bed has only one type, otherwise dropdown), Payor Type, Admission Date (default today), Admission Time (default now)
5. **Date validation:** if selected date > today OR < admission date → show warning "before Admission" or "after Current"
6. On submit: TWO sequential API calls:
   - `POST /bed/allocateBed/` with `{ visit: { patient, consultant, date: fromDate+fromTime }, bed, bedType, fromDate, fromTime, payorType }`
   - `PUT /admissionRequest` with `{ ...admissionDetail, admissionRequestStatus: "ADMITTED", admissionDate: today+currentTime }`
7. Refreshes the admission request list

**View Details (when status = ADMITTED):**
- Shows `GET /visit/active/patient/{patientId}` data in a read-only modal with reason

### C2. InPatient List Page (`/inpatient`)

**On load:** `GET /visit/getDischargeSummaryDetails?visitType=IP&searchDate={today}`

**Display columns:** `Bed No | Patient No | Patient Name | Admission Date | Discharge Date | Primary Consultant | Action`

- Bed No: shows `lastBed.name` if bedNo is null, otherwise shows `bedNo`
- Discharge Date: may be null (patient still admitted)

**Filters:** Date picker, Consultant dropdown (client-side)

**Action button is feature-dependent:**
- If `features.MEDICAL_RECORD && features.IN_PATIENT` → shows CaseSheet button (user icon) → calls `getPatientCaseSheet(data)`:
  1. `GET /patient/{patientId}` → store in SharedService
  2. Store visit data in SharedService
  3. Navigate to `patientProfile/caseSheet_{visitType}` (i.e., `patientProfile/caseSheet_IP`)

- If `features.DISCHARGE_SUMMARY_ONLY && !features.IN_PATIENT` → shows Discharge Summary button (document icon) → calls `getPatientDischarge(data)`:
  1. `GET /patient/{patientId}` → store in SharedService
  2. Store visit data in SharedService
  3. Navigate directly to `patientProfile/caseSheet_IP/dischargeSummary`

**Add Discharge Summary button** (top right, only if `DISCHARGE_SUMMARY_ONLY && !IN_PATIENT`):
- Opens search modal → `GET /patient/search?q={searchText}` → select patient → `GET /visit/active/patient/{id}` → navigate to discharge summary

### C3. IP CaseSheet (`/patientProfile/caseSheet_IP`)

Auto-redirects to `/patientProfile/caseSheet_IP/diag` (Diagnostics first).

**Header bar shows:** Bed No (red highlight), Admission Date, Primary Consultant. If no existing visit, shows date picker + time picker + consultant dropdown for creating a new admission.

**Left sidebar menu (9 items, each controlled by `hasMenuPermission()`):**

| # | Menu Item | Sub-route | Description |
|---|-----------|-----------|-------------|
| 1 | Diagnostics | `/diag` | Order lab/radiology tests via modal |
| 2 | Prescriptions | `/prescrp` | Add prescriptions via modal |
| 3 | Other Charges | `/otherChrg` | Add billable charges via modal |
| 4 | Attachments | `/attach` | Upload files |
| 5 | Discharge Summary | `/dischargeSummary` | Template-based (see C4) |
| 6 | OT Notes | `/otNotes` | Operation theater notes |
| 7 | Vital Signs | `/vitalSign` | Record vitals (History/Report toggle) |
| 8 | Progress Notes | `/progressNotes` | Daily progress documentation |
| 9 | Nurse Notes | `/nurseNotes` | Nursing documentation |

### C4. Discharge Summary Logic

**Load templates:** `GET /template/getTemplatesByType?type=DISCHARGE_SUMMARY`

**Render selected template:** `GET /template/getTemplateDetailsById?templateId={id}` → returns `{ html, data }` → render HTML in an editable container.

**Check existing summary:** `GET /caseSheet/visit/{visitId}` → if data exists, pre-fill template data.

**Save new:** `POST /visit/checkinDischargeSummary` with:
```
{
  consultant: { id }, patient: { id },
  date: admissionDate, dischargeDate,
  bedNo, templateData: { data: summaryData, template: templateId }
}
```

**Update existing:** same endpoint or update function with populated IDs.

**Print:** uses `templateType=DISCHARGE_SUMMARY&id={visitId}` print parameters.

### C5. Bed Management (`/bedManagement`)

**On load:** `GET /bed/getAllocatedDetail` + `GET /bedType` for all bed types.

**Shows counts:** Available beds, Allocated beds (calculated client-side from bedStatus).

**Three operations:**

#### Allocate Bed
1. Search patient → `GET /patient/search?q={text}`
2. Select patient → `GET /visit/active/patient/{patientId}`
3. If patient has an active visit with a bed already allocated (`lastBed.id exists && bedStatus is true`) → show warning "Patient has been already Allocated in {bedName}" and block
4. If patient has active visit without bed → use that visit
5. If no active visit → create `visit = { patient: selectedPatient }`
6. Get available beds: `GET /bed/getAvailable`
7. Select bed → auto-populate bed type if only one, otherwise show dropdown
8. Submit: `POST /bed/allocateBed/` with `{ visit, bed, bedType, fromDate+fromTime, consultant, payorType }`

#### Transfer Bed
1. Get available beds: `GET /bed/getAvailable`
2. Select destination bed, date/time
3. Submit: `POST /bed/transferBed/` with `{ ...currentAllocation, transferBedId: newBed.id, toDate+toTime }`

#### Vacate Bed (Discharge)
1. Enter vacate date/time (defaults to today/now)
2. Submit: `POST /bed/vacateBed/` with `{ ...currentAllocation, toDate+toTime }`
3. Bed becomes AVAILABLE, patient is effectively discharged

**Date validation for all 3 operations:** same logic as admission — cannot be after current date or before admission date.

---

## PART D: BILLING INTEGRATION

### D1. OP Billing
- Accessed from Patient Profile → Bills tab → Create OP Bill
- `POST /bill` with `{ patient: { id }, visit: "OP", type: "CASH", billDetail: [...charges], collection: [{ type, mode, amount }] }`
- Collection types: BILL_AMOUNT, DEPOSIT, PARTIAL_PAYMENT
- Payment modes: CASH, CHEQUE, CARD, TRANSFER

### D2. IP Billing
- Accessed from Patient Profile → Bills tab → Create IP Bill
- Same endpoint but `visit: "IP"`
- IP bills start as Draft with deposits, charges accumulate during stay
- Draft → Generate Bill (finalizes) → Collect Payment → Settled
- Supports: Add/Remove/Edit charges, Discount, Refund, Due Amount collection

### D3. UnBilled Diagnostic Orders
- `GET /diagnostics/getUnbilledDiagnosticOrders?patientId={id}`
- Select orders → converts to bill line items → creates OP bill

---

## PART E: ROLE & FEATURE ACCESS MATRIX

### Roles → Default Landing
| Role | Landing Page | Purpose |
|------|-------------|---------|
| `ROLE_SUPER_ADMIN` | `/reports` | Full system access |
| `ROLE_DOCTOR` | `/outpatient` | Consultation workflow |
| `ROLE_NURSE` | (per module) | Vital signs only in OP |
| `ROLE_RECEPTION` | `/patient` | Registration & check-in |
| `ROLE_BILLING` | `/search` | Billing workflow |
| `ROLE_LAB` | `/diagnostics` | Lab results |
| `ROLE_ADMIN` | `/module` or `/reports` | Dashboard/Reports |

### Feature Flags (loaded from `GET /feature/getFeaturesByCurrentUser?module={module}`)
| Flag | Controls |
|------|----------|
| `PATIENT_PROFILE` | Profile tab in Patient Profile |
| `PATIENT_BILLS` | Bills tab in Patient Profile |
| `UNBILLED_DIAGNOSTIC_ORDERS` | UnBilled Orders tab |
| `MEDICAL_RECORD` | Medical Record tab + IP CaseSheet action button |
| `IN_PATIENT` | Full IP CaseSheet with all 9 sub-modules |
| `DISCHARGE_SUMMARY_ONLY` | Discharge-summary-only mode (limited IP view) |

### OP Queue Role Behavior
| Role | Visible Statuses | Available Actions |
|------|-----------------|-------------------|
| `ROLE_NURSE` | CHECKEDIN only | Vital Signs only |
| All Others | All statuses | Referral, Admission Req, Vital Signs, Profile |

---

## PART F: COMPLETE STATE FLOW SUMMARY

```
PATIENT REGISTRATION (POST /patient, optional checkin: true)
    ↓
OP VISIT CREATED (POST /visit, status: CHECKEDIN)
    ↓
NURSE RECORDS VITALS (PUT /visit, status: VITALSIGNS_ENTERED)
    ↓
DOCTOR OPENS CASESHEET (navigate to patientProfile/caseSheet_OP)
    ├── Records Clinical Notes, Prescriptions, Diagnostics
    ├── Can REFER to another consultant (POST /visit → new CHECKEDIN visit)
    └── Can create ADMISSION REQUEST (POST /admissionRequest, status: REQUESTED)
    ↓
CASESHEET SAVED (status: CASESHEET_RECORDED)
    ↓
MARK CONSULTED (PUT /visit, status: CONSULTED) — OP flow ends here
    ↓
IF ADMISSION REQUESTED:
    ↓
ADMISSION REQUEST QUEUE (GET /admissionRequest)
    ↓
ALLOCATE BED (POST /bed/allocateBed + PUT /admissionRequest → ADMITTED)
    ↓
IP PATIENT LIST (GET /visit/getDischargeSummaryDetails?visitType=IP)
    ↓
IP CASESHEET (9 modules: Diagnostics, Prescriptions, Charges, Attachments,
              Discharge Summary, OT Notes, Vitals, Progress Notes, Nurse Notes)
    ↓
DISCHARGE SUMMARY CREATED (POST /visit/checkinDischargeSummary)
    ↓
FINAL IP BILL GENERATED (POST /bill with visit: "IP")
    ↓
BED VACATED (POST /bed/vacateBed) → PATIENT DISCHARGED
```

> **Key implementation note:** The SharedService is a singleton Angular service that holds `patientData`, `visitData`, `billData`, `templateId`, and `unBilledOrders` across page navigations. In a new project, this translates to a global state store (Redux/Zustand/Context) that persists the currently selected patient and visit across routes.
