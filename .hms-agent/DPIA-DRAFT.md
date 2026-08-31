# Data Protection Impact Assessment

**HIMS-Clinical multi-tenant Hospital Information Management System**

| | |
|---|---|
| **Version** | 1.0 — **DRAFT for legal review** |
| **Date** | 2026-08-30 |
| **Assessor** | Engineering |
| **Basis** | `DATA-INVENTORY-ROPA.md` v1.0, `DPDP-GAP-REGISTER.md`, `WO-028-ADJUDICATION.md` |
| **Status** | Not approved. No DPO appointed. No legal review has taken place |

---

## 0. What this document is and is not

Rule 13 requires a Significant Data Fiduciary to carry out a periodic Data
Protection Impact Assessment. **Whether this platform or its tenants are SDFs has
not been determined** — that is question 1 in §8 and it belongs to counsel.

This draft exists so that when the determination is made, the assessment is not
started from nothing. It is written by the engineer who built the remediation,
which makes it well-informed about the system and a poor substitute for
independent review. Someone who did not write the code should challenge §5 in
particular.

**Nothing here has been verified by execution.** At the time of writing, 29
remediation cards are implemented and uncompiled. Where this document says a
control exists, it means the code exists — not that it has been observed working.
That distinction matters more in a DPIA than anywhere else, because a DPIA that
credits untested controls understates risk.

---

## 1. The processing

**Nature.** A multi-tenant SaaS hospital information system. Each tenant hospital
registers patients, records clinical encounters, orders diagnostics, dispenses
pharmacy items, bills, submits insurance claims through NHCX, and links patient
records to the national ABDM health-record network. An AI agent layer handles
appointment booking, triage and patient messaging over WhatsApp and voice.

**Scope.** 150 tables, of which 25 carry patient data. 67 encrypted fields.
Patient identity, contact, demographic, clinical, financial and insurance data,
plus paediatric records and voice transcripts.

**Context.** Healthcare in India. Data subjects are patients — including
children, and including people receiving care for conditions they may not wish
disclosed. They cannot meaningfully shop around: the hospital they attend
determines the system that holds their records, and refusing consent to
`TREATMENT` means not receiving care.

That asymmetry is the single most important fact in this assessment. Every risk
below is aggravated by the fact that the data subject cannot walk away.

**Purposes.** Seven enumerated in `ConsentPurpose`: treatment, automated
messaging, automated voice, insurance claims, ABHA linkage, portal self-access,
marketing. Only treatment is required for care.

---

## 2. Necessity and proportionality

| Purpose | Necessary? | Assessment |
|---|---|---|
| `TREATMENT` | Yes | Core. Cannot deliver care without clinical records |
| `INSURANCE_CLAIM` | Yes, where claimed | Cashless treatment requires payer disclosure. Genuinely optional — patients may pay directly |
| `ABHA_LINKAGE` | No | Convenience and policy alignment. Correctly optional |
| `AGENT_MESSAGING` | No | Correctly optional |
| `AGENT_VOICE` | No | **Highest-risk purpose.** Recording and transcription of a patient's voice discussing their health |
| `PORTAL_SELF_ACCESS` | No | Supports the access right |
| `MARKETING` | No | Correctly separated and never bundled |

**Data minimisation — one concern.** `patients.pediatric_data` and
`template_data` are unstructured JSONB whose contents depend on tenant
configuration. Minimisation cannot be assessed for a field that can hold
anything, and paediatric data is the category Rule 12 treats most carefully.
Both are **unencrypted** (S2-08, WO-029, not started).

---

## 3. Risks to data subjects

Assessed as risk to the *person*, not to the business. Likelihood assumes the
controls in §5 work as written — which has not been verified.

### R1 — Cross-tenant disclosure · **HIGH**

One hospital seeing another's patient records. The system is multi-tenant with
Hibernate filter-based isolation, and `TenantFilterAspect` *disables* the filter
when `TenantContext` is null — so any code path without a session runs unscoped.

**Realised once already.** WO-028 found `AbdmConsentCallbackController` accepting
unauthenticated cross-tenant writes: `permitAll`, no signature verification
despite a docstring claiming otherwise, and no tenant context. Whether it was
exploited is unknown.

*Harm:* disclosure of clinical data to an unrelated organisation. Not remediable
after the fact.
*Controls:* HMAC verification (WO-028), `withTenantOf` scoping,
`CrossTenantAccessDetector` (WO-026), 24 scanner findings on unscoped
tables/entities adjudicated as parent-scoped.
*Residual:* **MEDIUM.** The architectural pattern — filter off when context
absent — fails open rather than closed. That is a design property, not a bug, and
it will keep producing this class of defect.

### R2 — Fabricated consent · **HIGH, realised**

Three services granted the consent they then checked. Every resulting record
claimed `VERBAL_IN_PERSON` capture with a null capturer. The gate could not fail.

*Harm:* processing without consent, plus false documentary evidence that consent
existed — worse than an absent record, because it would have survived an audit.
*Controls:* WO-022 removed the pattern; `SYSTEM_INFERRED` provenance excludes the
false rows; a source-shape test prevents return.
*Residual:* **LOW** once verified. **Affected patients have not been re-consented
and have not been told a record was created in error.** That is question 3 in §8.

### R3 — Inadequate notice · **HIGH**

All seven notices are placeholders carried over from enum labels. They state no
retention period, no recipients and no withdrawal method, and exist only in
English in a Tamil Nadu deployment.

*Harm:* consent that is not informed is not consent. Every grant currently being
captured rests on this.
*Controls:* the registry is versioned and language-keyed; DRAFT service is
metered.
*Residual:* **HIGH.** Machinery without content. Not an engineering task.

### R4 — Voice and transcript processing · **HIGH**

Recording a patient describing symptoms, transcribed and passed to an LLM.

*Harm:* the most intimate category in the system, in a form trivially
re-identifiable by voice alone.
*Controls:* separate `AGENT_VOICE` consent; India-region allowlist enforced at
startup in the agent service; transcripts encrypted; erasure reaches
`hitl_escalations`.
*Residual:* **MEDIUM.** The residency guard is real and is the strongest control
in the system. But pre-V206 transcripts carry no `patient_id` and cannot be
reached by a per-patient erasure — they are swept wholesale instead, which is
blunt.

### R5 — Erasure that silently under-delivers · **MEDIUM**

Telling a patient their data is gone while copies remain.

*Harm:* compounded by the false assurance. The patient stops looking.
*Controls:* 26-store registry; `ErasureRegistryCompletenessTest` fails the build
on an unregistered patient-linked table; per-store receipt shown to the patient;
FAILED targets keep the request open.
*Residual:* **MEDIUM.** The sweep has **never been executed against a populated
database.** All three defects found in it were invisible until the SQL met the
real schema, and there is no basis for assuming the rewrite is the first correct
version.

### R6 — Indefinite retention · **MEDIUM**

Nothing deletes or anonymises patient data when its purpose is served.

*Harm:* diffuse but real — the longer data is held, the larger every other risk
on this list becomes.
*Controls:* **none.** WO-025 not started.
*Residual:* **MEDIUM**, rising over time.

### R7 — Breach with no notification capability · **was HIGH, now MEDIUM**

*Controls:* WO-026 register, both Rule 7 clocks, notice generator, detection.
*Residual:* **MEDIUM.** No drill has been run, and there is no UI, so staff
cannot raise an incident. A notification path nobody has exercised is one that
fails on the night.

### R8 — Children's data · **MEDIUM**

*Controls:* `ConsentService` refuses a minor's consent without
`guardianVerified`; the consent modal enforces it client-side too.
*Residual:* **MEDIUM.** `patient_pediatric` is RETAIN under erasure, and
`pediatric_data` JSONB is unencrypted.

### R9 — Third-party processors · **MEDIUM**

Cloud, SMTP, SMS/WhatsApp, LLM/STT/TTS, monitoring.
*Controls:* residency enforcement on the AI endpoints only.
*Residual:* **MEDIUM.** No DPA register exists. The Java-side NHCX and ABDM
egress has no residency check at all, unlike the agent service.

### R10 — Key compromise · **MEDIUM**

Single `HMS_ENCRYPTION_KEY` protecting 67 fields.
*Residual:* **MEDIUM.** `PiiKeyRotationUtil` exists and **has never been run**.
Rotation untested under pressure is rotation you do not have, and a compromise is
not currently detectable.

---

## 4. Risk summary

| Risk | Inherent | Residual | Trend |
|---|---|---|---|
| R1 Cross-tenant disclosure | HIGH | MEDIUM | Improving |
| R2 Fabricated consent | HIGH | LOW* | Fixed, unverified |
| R3 Inadequate notice | HIGH | **HIGH** | Static — blocked on counsel |
| R4 Voice processing | HIGH | MEDIUM | Stable |
| R5 Erasure under-delivery | MEDIUM | MEDIUM | Unverified |
| R6 Indefinite retention | MEDIUM | **MEDIUM** | **Worsening** |
| R7 Breach notification | HIGH | MEDIUM | Improving |
| R8 Children's data | MEDIUM | MEDIUM | Stable |
| R9 Third parties | MEDIUM | MEDIUM | Static |
| R10 Key compromise | MEDIUM | MEDIUM | Static |

\* conditional on the build passing.

**Two risks are not improving.** R3 is blocked entirely on counsel-supplied text.
R6 is actively worsening, because every day of operation adds data that nothing
is scheduled to remove.

---

## 5. Controls relied upon

Grouped by whether they have been observed working.

**Verified:** Prometheus alert rules and retention configuration parse as valid
YAML. That is the complete list.

**Implemented, not executed:** consent gate integrity, provenance, notice
registry, erasure registry and sweep, rights lifecycle, breach register and
notification, grievance redressal, ABDM callback verification, tenant scoping on
the callback path, field encryption on four newly-covered columns, PII masking,
log retention.

**Pre-existing and presumed working** (in production before this work): field
encryption on 63 fields, blind-index search tokens, Hibernate tenant filters,
`pii_disclosure_audit`, ABDM consent artifact scope enforcement, agent-service
residency guard.

**A DPIA that treats the second group as effective would understate risk
throughout.** The residual ratings in §4 assume they work; if the build fails,
those ratings are wrong.

---

## 6. Measures to reduce risk

Ordered by risk reduction per unit of effort.

| # | Measure | Addresses | Owner | Status |
|---|---|---|---|---|
| 1 | **Run the build and test suite** | All | Engineering | Blocked |
| 2 | **Counsel-approved notice text, English + Tamil** | R3 | Legal | Not started |
| 3 | Exercise the erasure sweep against populated data | R5 | Engineering | Not started |
| 4 | Re-consent patients with `SYSTEM_INFERRED` grants | R2 | Ops + Legal | Not started |
| 5 | Retention engine (WO-025) | R6 | Engineering | Not started |
| 6 | Breach drill | R7 | Ops | Not started |
| 7 | Publish a contact for every tenant (J-006) | s. 8(9) | Ops | Not started |
| 8 | DPA register for sub-processors | R9 | Legal | Not started |
| 9 | Exercise key rotation once | R10 | Engineering | Not started |
| 10 | Encrypt `pediatric_data` / `template_data` (WO-029) | R8 | Engineering | Not started |
| 11 | Residency check on Java-side NHCX/ABDM egress | R9 | Engineering | Not started |
| 12 | Incident and grievance UI (I-006, J-005) | R7 | Engineering | Not started |

Measures 2, 4, 6, 7 and 8 need no build and are unblocked today.

---

## 7. Assessment

**The remediation is substantial and the residual risk is materially lower than
at the start.** Six work orders closed the two S1 findings and seven of eleven
S2s. The consent gate, erasure registry, rights lifecycle, breach register and
grievance mechanism did not exist and now do.

**Two things prevent this being a positive assessment.**

First, **almost nothing has been verified.** Twenty-nine cards are implemented
and uncompiled. Every residual rating in §4 rests on code nobody has run. If the
build fails materially, this assessment is void rather than merely optimistic.

Second, **the highest residual risk is not an engineering problem.** R3 —
inadequate notice — cannot be closed by anyone on the build team. Every consent
being captured today rests on placeholder text stating no retention period, no
recipients and no withdrawal method, in a language a large share of the patient
population may not read. The machinery is in place and the content is absent.

**Conclusion:** processing may continue, on the basis that the alternative —
suspending a hospital information system — would harm patients more than the
identified risks. That is not a comfortable conclusion and it should not be
mistaken for approval. It is contingent on measures 1 and 2 progressing, and it
should be revisited if either stalls.

---

## 8. Questions for counsel

1. **Are the platform or its tenants Significant Data Fiduciaries under Rule 13?**
   Determines whether this DPIA is mandatory and periodic, whether a DPO must be
   appointed, and whether independent audit applies. Note the Processor reading
   materially reduces platform exposure by assessing volume per hospital.
2. **Ratify the Fiduciary/Processor split**, especially the portal identity layer
   where the platform appears to act as Fiduciary in its own right.
3. **Disposition of the fabricated consent records.** They are retained and
   marked `SYSTEM_INFERRED`. Must affected patients be proactively told a consent
   record was created in error?
4. **Approve or replace the seven notice texts**, and confirm which languages are
   required for a Tamil Nadu deployment.
5. **Review `ErasureService.TARGETS`.** It encodes judgements about which records
   survive an erasure request — clinical retained, financial anonymised, consent
   and grievance records retained. Those are legal calls currently made in code.
6. **Confirm the statutory periods** assumed throughout: 90 days for rights
   requests and grievances, and 24h/72h for Rule 7 breach notification.
7. **Do tenant agreements contain s. 8(2) processing terms?**
8. **Should the portal be restructured** so the patient's relationship is with
   each hospital rather than the platform? This would be a larger change than any
   work order completed so far, and is better decided before more is built on the
   current shape.

---

## 9. Review

Revisit on: SDF determination · completion of the build and test run · any new
consent purpose · any new external recipient · any breach · otherwise annually.
