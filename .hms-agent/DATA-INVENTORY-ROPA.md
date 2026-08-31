# Data Inventory and Record of Processing Activities

**HIMS-Clinical multi-tenant Hospital Information Management System**

| | |
|---|---|
| **Version** | 1.0 |
| **Date** | 2026-08-30 |
| **Basis** | Source of record at commit state after V210; 150 tables, 942 Java files enumerated programmatically |
| **Author** | Engineering. **Not legal advice** — this is the factual record a lawyer needs to assess compliance |
| **Review cycle** | Whenever a `patient_id` column is added, a new external recipient is introduced, or a consent purpose changes |

---

## 1. How to read this

This is the answer to the first question any auditor, Board inquiry or DPIA asks:
**what personal data do you hold, why, where does it go, and when does it stop
existing?**

Until now that knowledge was distributed across `ErasureService.TARGETS`, the
entity annotations, the Flyway migrations and several people's memory. Nothing
assembled it. A register that cannot be produced on demand is, for practical
purposes, a register that does not exist.

Everything below was extracted from the code rather than recalled, so it is
accurate as of the stated commit and will drift the moment someone adds a column.
Section 9 says how to regenerate it.

---

## 2. Roles and the processing relationship

Confirmed with the client 2026-08-30, **pending counsel ratification**.

| Data | Determines purpose and means | Platform's role |
|---|---|---|
| Patient clinical records, claims, ABDM records | Hospital tenant | **Processor** |
| Portal identity, cross-tenant lookup, self-registration | Platform | **Fiduciary** |
| Staff accounts, sessions, subscription billing | Platform | **Fiduciary** |
| Agent messaging and voice decisions | Unresolved — depends who configures | **Decide deliberately** |

**Consequence for this document:** for the majority of the data below, the
platform is a Processor and each hospital is the Data Fiduciary. The hospital
owes the notice, the erasure decision and the breach notification; the platform
supplies the machinery and is bound by contract under s. 8(2).

**Open:** whether tenant agreements currently contain s. 8(2) processing terms.
If not, that is a contractual gap no code closes.

---

## 3. Categories of personal data held

### 3.1 Patient-linked tables (25)

Every table carrying a `patient_id`. Erasure strategy is from
`ErasureService.TARGETS`, which is the live registry, not a copy.

| Table | Category | Erasure strategy | Notes |
|---|---|---|---|
| `patients` | Identity, contact, demographic | ANONYMISE | Primary record. Anonymised rather than deleted so retained clinical rows do not orphan |
| `clinical_encounters` | **Health** | RETAIN | Medico-legal retention overrides erasure |
| `visits` | **Health** | RETAIN | |
| `diagnostic_orders` | **Health** | RETAIN | |
| `attachments` | **Health** — scans, reports | RETAIN | |
| `patient_pediatric` | **Health, child** | RETAIN | Rule 12 category |
| `appointments` | Scheduling, contact | ANONYMISE | |
| `insurances` | Financial, policy identifiers | ANONYMISE | |
| `bills`, `payments` | Financial | ANONYMISE | Money must reconcile; the person need not be named |
| `pharmacy_sales`, `sales_returns` | Financial, contact | ANONYMISE | |
| `nhcx_transactions` | **Health** + financial | ANONYMISE | Claim payloads |
| `discovered_policies` | Policy identifiers | DELETE | Derived from insurer lookup |
| `patient_policy_coverages` | Policy | DELETE | Derived |
| `abha_linkages` | National health ID | DELETE | |
| `abdm_consent_requests` | Consent metadata | DELETE | |
| `abdm_consent_artifacts` | Consent metadata | DELETE | |
| `external_health_records` | **Health**, third-party | DELETE | Hospital is custodian only |
| `portal_sessions` | Session | DELETE | |
| `agent_idempotency_keys` | Cached tool responses | DELETE | `response_body` carries patient detail |
| `hitl_escalations` | **Health** — transcripts | ANONYMISE | |
| `consent_records` | Consent evidence | RETAIN | The audit trail itself |
| `erasure_requests` | Rights request | *excluded* | Erasing the record of an erasure destroys the evidence it was honoured |
| `grievances` | Complaint, may contain health | RETAIN | Record that a right was exercised |
| `incident_affected_principals` | Breach linkage, ids only | RETAIN | Record that a breach notification was owed and sent |

### 3.2 Non-patient personal data

| Table | Subject | Category |
|---|---|---|
| `users` | Staff | Identity, contact, credentials — platform is **Fiduciary** |
| `staff`, `consultants` | Clinical staff | Identity, contact, registration number |
| `customers`, `suppliers`, `payors`, `referrals` | Business contacts | Identity, contact |
| `compliance_contacts` | Published DPO | Organisational — **deliberately not personal data of a principal** |

---

## 4. Encryption at rest

**67 fields across 23 entities** carry `@Convert(EncryptedStringConverter.class)`.

**Encrypted:** patient names, contact number, email, blood group, address;
clinical diagnosis on both `clinical_encounters` and `visits`; NHCX diagnosis
code and text; ABHA number and address; HITL transcripts and operator replies;
external health record payloads; agent cached responses; grievance body,
resolution, notes and complainant contact; policy and claim identifiers; staff,
consultant, customer, supplier, payor and referral contact details; SMTP password.

**Deliberately not encrypted, with reasons:**

| Field | Why |
|---|---|
| `contact_number_token`, `contact_token`, `abha_number_token` | Blind indexes. Must be deterministic or lookup breaks — encrypting them would break the mechanism that lets the real value stay encrypted |
| `compliance_contacts.email` / `.phone` / `.address` | Published under s. 8(9); served unauthenticated. Organisational, not personal data of a principal |
| `password_reset_otp.email` | **Known gap.** Queried by value, and the converter is non-deterministic, so encrypting silently breaks password reset. Needs a search token — card F-001 |
| `tenants.address`, `branches.address` | Organisational |

**Key management:** single `HMS_ENCRYPTION_KEY`. `PiiKeyRotationUtil` exists;
**rotation has never been exercised.** A key compromise is not currently
detectable.

---

## 5. Where data goes — recipients and transfers

| Recipient | Data | Mechanism | Residency control |
|---|---|---|---|
| **NHCX / insurers** | Claim payloads, diagnosis codes, policy identifiers | `NhcxClient`, JWS-signed | None enforced in code |
| **ABDM Consent Manager** | Consent requests, ABHA identifiers | `AbdmConsentClient` | None enforced in code |
| **ABDM gateway (inbound)** | Consent artifacts, external health records | `AbdmConsentCallbackController` | HMAC verification added WO-028 |
| **SMTP provider** | Recipient email addresses | `SmtpConfigService` | Per-tenant configuration |
| **LLM endpoint** | Patient conversation context | `agent-service` | **Enforced** — India allowlist, fails startup |
| **STT / TTS endpoints** | Voice audio, transcripts | `agent-service` | **Enforced** — same |
| **Loki / Prometheus** | Logs and metrics | Promtail scrape | Deployment-dependent |

**Cross-border position.** The final DPDP Rules dropped hard localisation in
favour of a blacklist model, but ABDM and sectoral health-record rules impose
their own residency constraints. The agent service enforces an India allowlist
and fails startup otherwise; **the Java services do not enforce any equivalent
check** on NHCX or ABDM endpoints. That asymmetry is deliberate to note, not
deliberate by design.

**Sub-processors requiring DPAs:** cloud host, SMTP provider, SMS/WhatsApp
provider, LLM/STT/TTS provider, monitoring stack. **No DPA register exists.**

---

## 6. Lawful basis and consent

Seven enumerated purposes in `ConsentPurpose`:

| Purpose | Required for care | Notice text status |
|---|---|---|
| `TREATMENT` | **Yes** | DRAFT placeholder |
| `AGENT_MESSAGING` | No | DRAFT placeholder |
| `AGENT_VOICE` | No | DRAFT placeholder |
| `INSURANCE_CLAIM` | No | DRAFT placeholder |
| `ABHA_LINKAGE` | No | DRAFT placeholder |
| `PORTAL_SELF_ACCESS` | No | DRAFT placeholder |
| `MARKETING` | No | DRAFT placeholder |

Only `TREATMENT` blocks care. Everything else must be genuinely optional —
consent conditioned on receiving treatment is not freely given.

**All seven notices are placeholders.** They state no retention period, no
recipients and no withdrawal method, and exist only in English in a Tamil Nadu
deployment. `hms_consent_notice_draft_served_total` counts every time one is
shown. **This is the largest open compliance gap and it is not an engineering
task.**

---

## 7. Retention

| Data | Retention | Enforced by |
|---|---|---|
| Security and access logs | 1 year | logback 400d, Loki 8760h, Prometheus 400d |
| Consent records | Indefinite, deliberately | Audit trail |
| Erasure requests | Indefinite, deliberately | Evidence the right was honoured |
| Security incidents | Indefinite, deliberately | Evidence of what was known when |
| Clinical records | Medico-legal period | **Not enforced — no job deletes them** |
| Everything else | **None** | **Nothing** |

**Storage limitation is unimplemented.** `ConsentService.expireLapsedConsents`
expires consent rows; the data those grants authorised lives forever. Seven
`@Scheduled` jobs exist and none of them deletes patient data by policy. This is
WO-025, not started.

---

## 8. Known gaps in this register

Recorded here rather than omitted, because a register that only lists what works
is marketing.

1. **No DPA register** for sub-processors.
2. **Key rotation never exercised.** `PiiKeyRotationUtil` exists and has never
   been run; a key compromise is not currently detectable.
3. **No residency enforcement** on the Java-side NHCX and ABDM egress, unlike the
   agent service which fails startup on an off-allowlist host.
4. **`password_reset_otp.email` plaintext** — F-001.
5. **No published contact for any tenant** — the table exists with no rows —
   J-006. s. 8(9) is unmet for every hospital until this is filled.
6. **Storage limitation unimplemented** — §7. This is the largest structural gap
   after notice text.

**Closed while writing this document:** `grievances` and
`incident_affected_principals` carried `patient_id` and were absent from the
erasure registry, so a sweep would have reported success while leaving both
untouched. Both are now RETAIN — they are the record that a right was exercised,
and destroying the only proof someone complained is worse than retaining it
against their wishes. Found by the regeneration script in §9, which is the
argument for having written it.

---

## 9. Regenerating this document

The schema sections were produced by parsing the migration directory and the
entity annotations. Re-run after any migration that adds a `patient_id` column
or any new `@Convert(EncryptedStringConverter.class)`:

```
python3 .hms-agent/scripts/build_inventory.py
```

`ErasureRegistryCompletenessTest` enforces the same invariant in CI: a new
patient-linked table with no erasure strategy fails the build. That test is the
reason this register can be trusted to stay accurate between reviews — provided
the build is actually run.
