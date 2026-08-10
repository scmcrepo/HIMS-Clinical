# Requirements Traceability — ABHA_Screens + ABHA_Case_Sheet flow document

_Produced 2026-08-10. Every "Exists" claim below was checked against files on
disk in this repo, not against the ledger's own notes._

## How to read the status column

| Status | Meaning |
|---|---|
| **NONE** | Nothing on disk. Not started. |
| **SKELETON** | A class or column exists but the capability does not. |
| **PARTIAL** | Real logic exists, but the requirement asks for materially more. |
| **UNVERIFIED** | Code exists but has **never been compiled or run**. |

Every backend item below is at best UNVERIFIED. See the blocker section.

---

## Module 1 — Patient identification, ABHA & policy linking (front desk)

### Screen 1.1 — ABHA verification & creation modal

| Requirement line | Repo reality | Status |
|---|---|---|
| ABHA ID search field (@abdm address or 14-digit number) | `AbdmClient.abhaAddressExists()` | UNVERIFIED |
| OTP generation & mobile verification | `AbdmClient.requestMobileOtp()`, `verifyOtpAndEnrol()` | UNVERIFIED |
| Aadhaar demo auth fallback | `requestAadhaarOtp()` exists; *demo auth* is a different ABDM API and is absent | PARTIAL |
| Verified ABHA badge on patient master | No linkage → patient join surfaced anywhere; `abha_linkages` table exists (V178) | SKELETON |
| ABHA card download & print | Nothing. No ABDM card fetch call, no print template | **NONE** |
| **REST surface for any of the above** | `application/abha/` is an **empty directory**. No `AbhaService`. No `api/abha` controller. | **NONE** |

> The frontend has nothing to call. This is the single biggest gap in Module 1 —
> the ABDM client exists but is not reachable from a browser.

### Screen 1.2 — Patient policy search & digital retrieval (NHCX)

| Requirement line | Repo reality | Status |
|---|---|---|
| Discover policies by ABHA ID / mobile from NHCX registry | `NhcxClient` exposes only `submitEligibility`, `submitPreAuth`, `submitClaim` | **NONE** |
| Insurer name dropdown | No payer registry table or endpoint | **NONE** |
| OTP popup authorising hospital policy lookup | No patient-authorisation flow | **NONE** |
| Discovered policies list (policy no., type, payer, TPA, period, primary insured, relationship) | `Insurance` entity has `insurerName`, `policyNumber` only | **NONE** |
| Policy type incl. Family Floater / PM-JAY / Group | No policy-type enum | **NONE** |
| "Link policy to patient encounter" | `Insurance.encounterId` exists; no link-from-discovery path | SKELETON |

### Screen 1.3 — Manual insurance policy registration

| Requirement line | Repo reality | Status |
|---|---|---|
| Policy number, insurer name | `CreateInsuranceRequest` has both | UNVERIFIED (pre-existing, likely working) |
| Member ID / Card ID | No column | **NONE** |
| TPA name | No column | **NONE** |
| Policy copy / health card upload | `application/attachment/` exists and is reusable; not wired to insurance | PARTIAL |

---

## Module 2 — Policy coverage & eligibility breakdown (insurance desk)

### Screen 2.1 — Real-time coverage & eligibility (NHCX)

| Requirement line | Repo reality | Status |
|---|---|---|
| FHIR R4 `CoverageEligibilityRequest` to NHCX | `ClaimBundleBuilder.coverageEligibilityRequest()` | UNVERIFIED |
| `POST /api/v1/coverageeligibility/check` | `NhcxClient.submitEligibility()` | UNVERIFIED |
| Webhook `/nhcx/callback/on-check` | `NhcxCallbackController` maps `/coverageeligibility/on_check` — **path differs from the spec's `on-check`** | UNVERIFIED + discrepancy |
| Live status banner ACTIVE/EXPIRED/LAPSED/SUSPENDED | No policy-status field | **NONE** |
| Total sum insured | No column | **NONE** |
| Utilised amount to date | No column | **NONE** |
| Remaining available balance | No column | **NONE** |
| Daily room rent cap | No column | **NONE** |
| ICU daily capping | No column | **NONE** |
| Co-pay percentage | No column | **NONE** |
| Deductible amount | No column | **NONE** |
| PED waiting-period indicator | No column | **NONE** |
| Package exclusions & restrictions list | No table | **NONE** |

> `nhcx_transactions` stores `approved_amount` and `outcome_code` and nothing
> else financial. Every benefit field this screen displays needs new storage.

### Screen 2.2 — Benefit verification print / PDF

| Requirement line | Repo reality | Status |
|---|---|---|
| Printable patient acknowledgment (room eligibility, co-pay) | Nothing. **But** `application/print/` + the print-template migrations (V181–V190) are a working, extensible mechanism | **NONE** (clear path) |

---

## Module 3 — ABDM medical history & consent flow (doctor)

| Requirement line | Repo reality | Status |
|---|---|---|
| Consent request modal, record-type checkboxes | Nothing | **NONE** |
| Date range + purpose-of-request dropdown | Nothing | **NONE** |
| Send request to patient mobile | No ABDM **Consent Manager** client. `AbdmClient` is enrolment-only | **NONE** |
| Consent status PENDING_APPROVAL → GRANTED → EXPIRED | Nothing | **NONE** |
| Consent artifact presented to Hospital A (HIP) | No HIU role implemented at all | **NONE** |
| Encrypted FHIR R4 data streaming from HIP | Nothing | **NONE** |
| External records viewer tab in case sheet | `features/casesheet` exists to host it; no tab | **NONE** |
| Document previewer for external PDFs | Nothing | **NONE** |
| "Import into current case sheet" | Nothing | **NONE** |

> **Module 3 is entirely greenfield.** Note the internal `ConsentService` in
> `application/compliance/` is a *DPDP* consent record — a different thing from
> an ABDM consent artifact. Do not conflate them; they have different lifecycles,
> different legal bases, and the ABDM artifact is issued by the Consent Manager,
> not by us.

---

## Module 4 — Cashless pre-authorisation & enhancement

| Requirement line | Repo reality | Status |
|---|---|---|
| Pre-auth form, policy auto-fill from 1.2/2.1 | `PreAuthRequest` exists but is a *manual record* of a received pre-auth, not an NHCX submission | PARTIAL |
| Diagnosis via ICD-10 search | No ICD-10 catalogue in repo | **NONE** |
| Planned procedure, expected LOS, room type | No columns | **NONE** |
| Itemised estimate (room, OT, implants, consumables) | No estimate model | **NONE** |
| Attachment manager auto-attaching case sheet & orders | Attachment infra exists, not wired | **NONE** |
| Submit pre-auth to NHCX | `NhcxClient.submitPreAuth()` + `claimRequest(use="preauthorization")` | UNVERIFIED |
| Status SUBMITTED with correlation ID | `nhcx_transactions.correlation_id`, `state` | UNVERIFIED |
| Status APPROVED with approved amount | `approved_amount`, mapped in `NhcxCallbackService` | UNVERIFIED |
| Status QUERY_RAISED + insurer query notes | No state, no notes storage | **NONE** |
| Status REJECTED with reason code | `outcome_code` mapped to REJECTED | UNVERIFIED |
| Insurer query response modal + document upload | Nothing | **NONE** |
| Enhancement request (approved vs revised, justification) | Nothing | **NONE** |

---

## Module 5 — Final claim settlement & bank payment tracking

| Requirement line | Repo reality | Status |
|---|---|---|
| Final bill vs approved pre-auth vs patient co-pay | No co-pay field anywhere | **NONE** |
| Discharge summary attachment preview | Discharge summary templates exist (V187/V188) | PARTIAL |
| Pharmacy & diagnostic ledger attachments | Ledgers exist in billing/sales | PARTIAL |
| Submit final claim to NHCX | `NhcxClient.submitClaim()` + `claimRequest(use="claim")` | UNVERIFIED |
| Control tower at `/insurance/claims` | `InsurancePage.tsx` is 306 lines and does none of this; no route registered | **NONE** |
| CLAIM_SUBMITTED | `nhcx_transactions.state` default `SUBMITTED` | UNVERIFIED |
| CLAIM_APPROVED / ADJUDICATED | mapped | UNVERIFIED |
| PAYMENT_INITIATED | **No state** | **NONE** |
| AMOUNT_RECEIVED_IN_BANK | **No state** | **NONE** |
| CLAIM_DISPUTED / DEDUCTION + disallowed breakdown | **No state, no deduction model** | **NONE** |
| 5 financial metric cards (claimed / approved / received / pending / disallowed) | Nothing | **NONE** |
| NHCX **PaymentNotice** callback | `NhcxCallbackController` maps only eligibility, preauth, claim. **No PaymentNotice route** | **NONE** |
| Bank UTR number | No column | **NONE** |
| Payment date/time of transfer | No column | **NONE** |
| Net disbursed amount | No column | **NONE** |
| TDS amount & penalty/deduction breakdown | No column | **NONE** |
| "Mark reconciled & credit hospital bank ledger" | No hospital bank ledger concept in billing | **NONE** |

> **Module 5 is the largest genuinely new domain.** The flow document's Section 2
> defines a five-status financial lifecycle; the repo models two of the five.

---

## Flow document — cross-cutting requirements

| Requirement | Repo reality | Status |
|---|---|---|
| JWE / JWS transport | `NhcxPayloadCodec` | UNVERIFIED |
| `correlationId` tracked end-to-end in DB | `nhcx_transactions.correlation_id` unique per tenant | UNVERIFIED |
| Bank payment advice linked to NHCX claim ID | No payment advice entity to link | **NONE** |
| Patient OTP authorisation before policy lookup | Nothing | **NONE** |

---

## Summary count

Across both documents, **73 discrete requirement lines**:

| Status | Count |
|---|---|
| NONE — nothing on disk | **48** |
| SKELETON / PARTIAL | **11** |
| UNVERIFIED — written, never compiled | **14** |
| Verified working | **0** |

---

## The blocker that governs everything

The backend has **never been compiled**. Not once, across the whole campaign.

- 22 ledger tasks sit at `IMPLEMENTED, NOT COMPILED` / `NOT VERIFIED`.
- ~60 new main files and 6 test files have never been through `javac`.
- This sandbox has a **JRE only** (no `javac`), no Gradle distribution, and
  Maven Central returns **HTTP 403** through the egress proxy. `./gradlew test`
  cannot run here — same finding as the 2026-07-26 session, re-confirmed today.

The frontend *is* buildable here. Baseline established this session:

```
npm ci            → ok
npx vitest run    → 4 files, 41 tests: 39 passed, 2 FAILED
```

The 2 failures are in `src/features/auth/pages/LoginPage.test.tsx`, are
pre-existing on main, and are unrelated to this campaign.

### Why this changes the plan

Writing five more modules of Java on top of 22 unverified tasks would take the
unverified surface from ~60 files to ~150 and make the eventual first compile
a debugging exercise nobody can scope. The requirement documents describe
new **money-handling** logic — co-pay splits, TDS deductions, bank
reconciliation — which is precisely the category the delivery workflow says to
test first, because a passing build lies most convincingly about tenant scope,
PII, and money.

**The correct next action is not more code. It is `./gradlew test` on a machine
with Maven Central access**, then fixing the first-compile fallout, then
building Modules 1–5 on a foundation that is known to work.
