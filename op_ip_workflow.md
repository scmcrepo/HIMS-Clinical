# VitalSoft HIMS — Outpatient & Inpatient Workflow

> [!NOTE]
> These flowcharts are derived directly from the source code in the `outpatient/`, `inpatient/`, `patient/`, `admissionRequest/`, `bedManagement/`, `bill/`, and `medical/casesheet/` modules.

---

## 1. Outpatient (OP) Flow — `/#/outpatient`

**Controller:** `Visit` → [outpatient.js](file:///home/ssb/Downloads/HIMS/vitalsoft/application/src/main/webapp/app/modules/outpatient/outpatient.js)
**Template:** [index.html](file:///home/ssb/Downloads/HIMS/vitalsoft/application/src/main/webapp/app/modules/outpatient/index.html)

### 1.1 High-Level OP Journey

```mermaid
flowchart TD
    A["🏥 Patient Registration\n(/patient)"] --> B["✅ Check-In Visit\n(POST /visit)"]
    B --> C["📋 OP Visit Queue\n(/outpatient)"]
    C --> D{"Status?"}
    D -->|CHECKEDIN| E["📊 Record Vital Signs"]
    D -->|VITALSIGNS_ENTERED| F["🩺 Doctor Consultation"]
    D -->|CASESHEET_RECORDED| G["✔ Mark Consulted"]
    D -->|CONSULTED| H["🏁 Visit Completed"]

    E --> E1["Status → VITALSIGNS_ENTERED"]
    E1 --> F
    F --> F1["Open Patient Profile\n→ Medical Record / CaseSheet OP"]
    F1 --> F2["Clinical Notes / Prescription / Diagnostic / Attachment"]
    F2 --> F3["Status → CASESHEET_RECORDED"]
    F3 --> G
    G --> G1["PUT /visit → status: CONSULTED"]
    G1 --> H

    F1 --> R["🔀 Refer to Another Consultant"]
    F1 --> AR["🛏 Create Admission Request"]

    R --> R1["POST /visit (new visit, different consultant)"]
    R1 --> C

    AR --> AR1["POST /admissionRequest\nstatus: REQUESTED"]
    AR1 --> AR2["Admission Request Queue\n(/admissionRequest)"]
    AR2 --> AR3["Allocate Bed → IP Flow"]

    style A fill:#E3F2FD,stroke:#1565C0
    style C fill:#FFF3E0,stroke:#E65100
    style H fill:#E8F5E9,stroke:#2E7D32
    style AR3 fill:#FCE4EC,stroke:#C62828
```

### 1.2 Detailed OP Step-by-Step Logic

#### Step 1 — Patient Registration & Check-In

| Action | Code Reference | API |
|--------|---------------|-----|
| Create new patient | `PatientCtrl.save()` | `POST /patient` |
| Check-in with consultant | `PatientCtrl.checkIn()` | `POST /visit` |
| Duplicate visit check | `patientVisitEntry()` | `GET /visit/patientVisitEntry/{patientId}` |

> [!IMPORTANT]
> During registration, if the "Check-In" checkbox is enabled, a visit is auto-created with the selected consultant and time. Otherwise the patient is only registered.

#### Step 2 — OP Visit Queue (Outpatient Page)

```mermaid
flowchart TD
    INIT["Page Load\ngetVisitListByDate(todayDate)"] --> FETCH["GET /visit?datesearch={date}"]
    FETCH --> LIST["Display Visit List\n(Patient No, Name, Consultant, Waiting Time, Status)"]
    LIST --> FILTER{"Filter by"}
    FILTER -->|Date| FETCH
    FILTER -->|Consultant| LIST
    FILTER -->|Status| LIST
    FILTER -->|Search Text| LIST
    LIST --> AUTO["Auto-refresh every 60s\n(updateLater → $timeout)"]
    AUTO --> FETCH

    style INIT fill:#E8EAF6,stroke:#283593
```

**Visit Status Types** (from `GET /visit/getVisitStatusTypes`):

| Status | Label | Badge Color |
|--------|-------|-------------|
| `CHECKEDIN` | Checked In | 🟡 Warning (yellow) |
| `VITALSIGNS_ENTERED` | Vitals Entered | 🟢 Success (green) |
| `CASESHEET_RECORDED` | Casesheet Recorded | ⚪ Default (grey) |
| `CONSULTED` | Consulted | ⚪ Default (grey) |

**Role-Based Visibility:**
- **ROLE_NURSE** — sees only `CHECKEDIN` visits; can only record Vital Signs
- **Other roles** — see All statuses; can Refer, create Admission Req, record Vitals, open Profile

#### Step 3 — Vital Signs Recording

```mermaid
flowchart LR
    A["Click 📊 VitalSign Button"] --> B["Modal: vitalsign.html"]
    B --> C["Enter: Weight, Height, BP,\nPulse Rate, Respiratory Rate,\nTemperature, Head Circumference,\nSPO2, Remark"]
    C --> D["createVitalsign(visit, form)"]
    D --> E["PUT /visit\n(status → VITALSIGNS_ENTERED)"]
    E --> F["Refresh Visit List"]

    style A fill:#F3E5F5,stroke:#6A1B9A
```

#### Step 4 — Doctor Consultation (Patient Profile)

```mermaid
flowchart TD
    A["Click 👤 Profile Button"] --> B{"Duplicate Name\nCheck"}
    B -->|"Multiple patients\nwith same name"| C["Show Selection Modal\n(existingPatientSearch.html)"]
    B -->|"Unique patient"| D["GET /patient/{id}"]
    C --> D
    D --> E["Navigate to\npatientProfile/medHis"]
    E --> F["Patient Profile Tabs"]
    F --> F1["Profile View"]
    F --> F2["Bills"]
    F --> F3["UnBilled Orders"]
    F --> F4["Medical Record"]
    F4 --> G["OP CaseSheet\n(patientProfile/caseSheet_OP)"]
    G --> G1["Clinical Notes"]
    G --> G2["Prescription"]
    G --> G3["Diagnostic Order"]
    G --> G4["Attachment"]

    style A fill:#E0F7FA,stroke:#00695C
    style G fill:#FFF9C4,stroke:#F57F17
```

#### Step 5 — Referral (Internal Refer To)

```mermaid
flowchart LR
    A["Click 🔀 Refer Button"] --> B["Modal: visitEntry.html"]
    B --> C["Select: Visit Date, Time,\nConsultant to Refer To"]
    C --> D["createVisit(visit, formData)\nPOST /visit"]
    D --> E["New visit created\nin OP queue"]

    style A fill:#FBE9E7,stroke:#BF360C
```

#### Step 6 — Admission Request (OP → IP Bridge)

```mermaid
flowchart TD
    A["Click 🛏 Admission Req. Button"] --> B["GET /admissionRequest/visit/{visitId}"]
    B --> C{"Existing\nRequest?"}
    C -->|Yes| D["Show existing data\n+ Update button"]
    C -->|No| E["Show empty form\n+ Create button"]
    D --> F["admissionRequest.html Modal"]
    E --> F
    F --> G["Enter: Admission Date/Time,\nReason, Advice to Patient,\nInstructions to Nurses"]
    G -->|New| H["POST /admissionRequest\nstatus: REQUESTED"]
    G -->|Update| I["PUT /admissionRequest"]
    H --> J["Appears in\n/admissionRequest queue"]
    I --> J

    style A fill:#FCE4EC,stroke:#880E4F
    style J fill:#E8F5E9,stroke:#1B5E20
```

---

## 2. Inpatient (IP) Flow — `/#/inpatient`

**Controller:** `DischargeSummary` → [inpatient.js](file:///home/ssb/Downloads/HIMS/vitalsoft/application/src/main/webapp/app/modules/inpatient/inpatient.js)
**Template:** [index.html](file:///home/ssb/Downloads/HIMS/vitalsoft/application/src/main/webapp/app/modules/inpatient/index.html)

### 2.1 High-Level IP Journey

```mermaid
flowchart TD
    A["🛏 Admission Request\n(from OP or Direct)"] --> B["📋 Admission Request Queue\n(/admissionRequest)"]
    B --> C["🏨 Allocate Bed\n(POST /bed/allocateBed)"]
    C --> D["Status → ADMITTED"]
    D --> E["📋 InPatient Queue\n(/inpatient)"]

    E --> F{"Feature Flags"}
    F -->|"MEDICAL_RECORD\n& IN_PATIENT"| G["Open IP CaseSheet\n(patientProfile/caseSheet_IP)"]
    F -->|"DISCHARGE_SUMMARY_ONLY\n& !IN_PATIENT"| H["Open Discharge Summary\nDirectly"]

    G --> G1["Diagnostic Orders"]
    G --> G2["Prescriptions"]
    G --> G3["Other Charges"]
    G --> G4["Attachments"]
    G --> G5["Discharge Summary"]
    G --> G6["OT Notes"]
    G --> G7["Vital Signs"]
    G --> G8["Progress Notes"]
    G --> G9["Nurse Notes"]

    G5 --> I["Save/Print Discharge Summary"]
    H --> I

    I --> J["🏨 Bed Management\n(/bedManagement)"]
    J --> K["Transfer Bed / Vacate Bed"]
    K --> L["📄 Generate Final Bill"]
    L --> M["🏁 Patient Discharged"]

    style A fill:#E3F2FD,stroke:#1565C0
    style E fill:#FFF3E0,stroke:#E65100
    style M fill:#E8F5E9,stroke:#2E7D32
```

### 2.2 Detailed IP Step-by-Step Logic

#### Step 1 — Admission Request Processing

```mermaid
flowchart TD
    A["Admission Request Queue\n(/admissionRequest)"] --> B["GET /admissionRequest?status={status}&date={date}"]
    B --> C["List: Patient, Requested By,\nReason, Status"]
    C --> D{"Status Filter"}
    D -->|ALL| C
    D -->|REQUESTED| E["Show 🛏 Allocate Bed Button"]
    D -->|ADMITTED| F["Show ℹ View Details Button"]

    E --> G["Click Allocate Bed"]
    G --> H["GET /bed/getAvailable"]
    H --> I["Modal: allocateBed.html"]
    I --> J["Select: Bed, Bed Type, Payor Type,\nAdmission Date/Time"]
    J --> K["POST /bed/allocateBed/\n+ PUT /admissionRequest\nstatus → ADMITTED"]
    K --> L["Patient now visible in\n/inpatient page"]

    F --> M["Modal: view.html\nShow visit details"]

    style A fill:#E8EAF6,stroke:#283593
    style L fill:#C8E6C9,stroke:#1B5E20
```

> [!IMPORTANT]
> The Allocate Bed action performs TWO operations: (1) creates a bed allocation record via `POST /bed/allocateBed/`, and (2) updates the admission request status to `ADMITTED` via `PUT /admissionRequest`.

#### Step 2 — InPatient List Page

```mermaid
flowchart TD
    INIT["Page Load"] --> FETCH["GET /visit/getDischargeSummaryDetails\n?visitType=IP&searchDate={date}"]
    FETCH --> LIST["Display IP Patient List"]
    LIST --> COLS["Columns: Bed No, Patient No,\nPatient Name, Admission Date,\nDischarge Date, Primary Consultant"]
    LIST --> FILTER{"Filter by"}
    FILTER -->|Date| FETCH
    FILTER -->|Consultant| LIST

    LIST --> ACT{"Action Button\n(feature-dependent)"}
    ACT -->|"MEDICAL_RECORD +\nIN_PATIENT"| CS["Open IP CaseSheet\ngetPatientCaseSheet()"]
    ACT -->|"DISCHARGE_SUMMARY_ONLY"| DS["Open Discharge Summary\ngetPatientDischarge()"]

    CS --> NAV1["Navigate to\npatientProfile/caseSheet_IP"]
    DS --> NAV2["Navigate to\npatientProfile/caseSheet_IP/dischargeSummary"]

    style INIT fill:#E8EAF6,stroke:#283593
    style NAV1 fill:#FFF9C4,stroke:#F57F17
    style NAV2 fill:#FFF9C4,stroke:#F57F17
```

#### Step 3 — IP CaseSheet (Full Medical Record)

```mermaid
flowchart TD
    A["IP CaseSheet\n(caseSheet_IP)"] --> B["Left Side Menu"]
    B --> B1["🧪 Diagnostic Orders"]
    B --> B2["💊 Prescriptions"]
    B --> B3["💰 Other Charges"]
    B --> B4["📎 Attachments"]
    B --> B5["📄 Discharge Summary"]
    B --> B6["🔪 OT Notes"]
    B --> B7["📊 Vital Signs"]
    B --> B8["📝 Progress Notes"]
    B --> B9["👩‍⚕ Nurse Notes"]

    B1 --> D1["Add Diagnostic Order\n(Modal → POST)"]
    B2 --> D2["Add Prescription\n(Modal → POST)"]
    B3 --> D3["Add Charges to Bill\n(Modal → POST)"]
    B5 --> D5["Select Template → Fill Data\n→ Save / Update / Print"]
    B7 --> D7["Add Vital Signs\n(History / Report view)"]
    B8 --> D8["Add Progress Notes"]
    B9 --> D9["Add Nurse Notes"]

    style A fill:#E1F5FE,stroke:#01579B
```

#### Step 4 — Discharge Summary

```mermaid
flowchart TD
    A["Discharge Summary Tab"] --> B["Select Template\nGET /template/getTemplatesByType?type=DISCHARGE_SUMMARY"]
    B --> C["Render Template HTML\nGET /template/getTemplateDetailsById"]
    C --> D["Fill in Summary Data"]
    D --> E{"Existing\nSummary?"}
    E -->|No| F["SAVE\nPOST /visit/checkinDischargeSummary"]
    E -->|Yes| G["UPDATE\n(updateDischargeSummary)"]
    F --> H["Print Option Available"]
    G --> H

    style A fill:#F3E5F5,stroke:#4A148C
```

#### Step 5 — Add Discharge Summary (Search Existing Visit)

> [!NOTE]
> This button is only visible when `features.DISCHARGE_SUMMARY_ONLY && !features.IN_PATIENT`. It allows adding discharge summaries for patients found via search.

```mermaid
flowchart LR
    A["Click ➕ Add Discharge\nSummary Button"] --> B["Modal: searchExistingVisit.html"]
    B --> C["Search Patient by\nID / Name / Phone"]
    C --> D["GET /patient/search?q={query}"]
    D --> E["Select Patient"]
    E --> F["GET /visit/active/patient/{id}\n(getActiveVisitByPatient)"]
    F --> G["Navigate to\npatientProfile/caseSheet_IP/dischargeSummary"]

    style A fill:#EDE7F6,stroke:#4527A0
```

#### Step 6 — Bed Management

```mermaid
flowchart TD
    A["Bed Management\n(/bedManagement)"] --> B["GET /bed/getAllocatedDetail"]
    B --> C["Bed Grid: Available / Allocated"]

    C --> D["Allocate Bed"]
    C --> E["Transfer Bed"]
    C --> F["Vacate Bed (Discharge)"]

    D --> D1["Search Patient → Check Active Visit"]
    D1 --> D2{"Already\nAllocated?"}
    D2 -->|Yes| D3["Show Warning:\nPatient already allocated"]
    D2 -->|No| D4["Select Bed, Bed Type,\nDate/Time, Consultant"]
    D4 --> D5["POST /bed/allocateBed/"]

    E --> E1["Select Transfer Bed,\nDate/Time"]
    E1 --> E2["POST /bed/transferBed/"]

    F --> F1["Enter Vacate Date/Time"]
    F1 --> F2["POST /bed/vacateBed/"]
    F2 --> F3["Bed → AVAILABLE\nPatient Discharged"]

    style A fill:#EFEBE9,stroke:#3E2723
    style F3 fill:#E8F5E9,stroke:#1B5E20
```

---

## 3. Complete End-to-End Flow (OP → IP → Discharge)

```mermaid
flowchart TD
    R["1. Patient Registration\n(POST /patient)"] --> CI["2. OP Check-In\n(POST /visit)"]
    CI --> OPQ["3. OP Visit Queue\nStatus: CHECKEDIN"]
    OPQ --> VS["4. Nurse: Record Vital Signs\nStatus: VITALSIGNS_ENTERED"]
    VS --> DOC["5. Doctor: Open Profile\n→ OP CaseSheet"]
    DOC --> CL["6a. Clinical Notes\n+ Prescription\n+ Diagnostic Orders"]
    DOC --> REF["6b. Refer to\nAnother Consultant"]
    DOC --> ADM["6c. Admission Request\n(if IP needed)"]
    CL --> MC["7. Mark Consulted\nStatus: CONSULTED"]

    REF --> OPQ
    ADM --> ARQ["8. Admission Request Queue\nStatus: REQUESTED"]
    ARQ --> BED["9. Allocate Bed\n(POST /bed/allocateBed)"]
    BED --> IPQ["10. InPatient Queue\nStatus: ADMITTED"]
    IPQ --> IPCS["11. IP CaseSheet"]
    IPCS --> DIAG["Diagnostics"]
    IPCS --> PRESC["Prescriptions"]
    IPCS --> CHRG["Other Charges"]
    IPCS --> VSGN["Vital Signs"]
    IPCS --> PNOTE["Progress Notes"]
    IPCS --> NNOTE["Nurse Notes"]
    IPCS --> OTN["OT Notes"]
    IPCS --> DS["12. Discharge Summary\n(Select Template → Save)"]
    DS --> BILL["13. Generate Final IP Bill\n(patientProfile/bill_createIP)"]
    BILL --> VAC["14. Vacate Bed\n(POST /bed/vacateBed)"]
    VAC --> DONE["15. ✅ Patient Discharged"]

    style R fill:#E3F2FD,stroke:#1565C0
    style OPQ fill:#FFF3E0,stroke:#E65100
    style IPQ fill:#F3E5F5,stroke:#6A1B9A
    style DONE fill:#E8F5E9,stroke:#2E7D32
```

---

## 4. Key API Endpoints Summary

### Outpatient APIs
| API | Method | Purpose |
|-----|--------|---------|
| `/visit?datesearch={date}` | GET | Fetch OP visit list by date |
| `/visit` | POST | Create new visit (check-in / referral) |
| `/visit` | PUT | Update visit (vital signs / mark consulted) |
| `/visit/getVisitStatusTypes` | GET | Get status type labels |
| `/visit/patientVisitEntry/{patientId}` | GET | Check duplicate visit entry |
| `/admissionRequest` | POST | Create admission request |
| `/admissionRequest` | PUT | Update admission request |
| `/admissionRequest/visit/{visitId}` | GET | Get admission by visit |
| `/referConsultant` | POST | Refer patient to consultant |

### Inpatient APIs
| API | Method | Purpose |
|-----|--------|---------|
| `/visit/getDischargeSummaryDetails` | GET | Fetch IP patient list |
| `/visit/checkinDischargeSummary` | POST | Save discharge summary |
| `/visit/active/patient/{id}` | GET | Get active visit for patient |
| `/admissionRequest?status=&date=` | GET | List admission requests |
| `/admissionRequest/getAdmission?date=` | GET | Get status counts |
| `/bed/getAvailable` | GET | List available beds |
| `/bed/allocateBed/` | POST | Allocate bed to patient |
| `/bed/transferBed/` | POST | Transfer patient between beds |
| `/bed/vacateBed/` | POST | Discharge patient (vacate bed) |
| `/bed/getAllocatedDetail` | GET | Get all bed allocations |
| `/template/getTemplatesByType` | GET | Get discharge summary templates |
| `/caseSheet/visit/{visitId}` | GET | Get casesheet for a visit |

---

## 5. Role & Feature-Based Access

| Feature Flag | Controls |
|-------------|----------|
| `PATIENT_PROFILE` | Profile tab visibility |
| `PATIENT_BILLS` | Bills tab visibility |
| `UNBILLED_DIAGNOSTIC_ORDERS` | UnBilled Orders tab |
| `MEDICAL_RECORD` | Medical Record tab + IP CaseSheet button |
| `IN_PATIENT` | Full IP CaseSheet access |
| `DISCHARGE_SUMMARY_ONLY` | Discharge summary–only mode (no full IP) |

| Role | Default Landing | OP Queue Behavior |
|------|----------------|-------------------|
| `ROLE_SUPER_ADMIN` | `/reports` | Full access to all actions |
| `ROLE_DOCTOR` | `/outpatient` | Full access, auto-set as consultant |
| `ROLE_NURSE` | — | Sees only `CHECKEDIN`; can only record Vital Signs |
| `ROLE_RECEPTION` | `/patient` | Patient registration |
| `ROLE_BILLING` | `/search` | Bill creation |
| `ROLE_ADMIN` | `/module` or `/reports` | Admin dashboard |
