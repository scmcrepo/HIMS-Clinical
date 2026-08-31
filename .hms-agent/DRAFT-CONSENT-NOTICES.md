# Draft Consent Notices — for legal review

| | |
|---|---|
| **Version** | Draft 1.0 — **NOT APPROVED, NOT IN USE** |
| **Date** | 2026-08-30 |
| **Author** | Engineering |
| **Seeded by** | `V211__draft_consent_notices.sql`, as `notice_state = 'DRAFT'` |
| **Replaces** | The v1.0 placeholders from V205/V207, which were enum labels |

---

## Why this document exists

The seven notices currently in the system are UI labels — "Creating or linking
your ABHA health account" and similar. They state no purpose detail, no retention
period, no recipients and no withdrawal method, and exist only in English in a
Tamil Nadu deployment. Consent captured against them is not informed consent, and
this is rated **R3 — HIGH residual** in the DPIA, one of only two risks not
improving.

These drafts are written so counsel can **edit rather than start blank**. They are
not legal advice and are not approved. They remain `DRAFT` in the registry, so
`hms_consent_notice_draft_served_total` keeps counting every time one is shown —
the metric does not stop until someone marks them `ACTIVE`.

## What still needs to be supplied

Bracketed placeholders mark facts I do not have. Each must be filled before
approval:

- `[RETENTION]` — the actual retention period. I know what the code does (nothing
  deletes patient data; see DPIA R6) rather than what policy says it should.
- `[HOSPITAL]` — tenant name, substituted per hospital.
- `[CONTACT]` — from `compliance_contacts`, which currently has no rows for any
  tenant (card J-006).

## Drafting principles applied

**Plain language.** A notice the reader cannot understand has not informed
anyone. Short sentences, no defined terms, no cross-references.

**Honest about withdrawal.** `TREATMENT` says plainly that clinical records
cannot be deleted on request while retention obligations apply. A notice that
implies otherwise sets up a refusal the patient will experience as a broken
promise.

**Recipients named by category, not buried.** "Your insurer" is more use than
"third parties as required".

**Each notice covers one purpose.** Bundling is what makes consent
non-specific.

---

## 1. TREATMENT — *required for care*

> **How we use your health information**
>
> [HOSPITAL] keeps a record of your care. This includes your name, contact
> details, date of birth, and the details of your visits, diagnoses, tests,
> prescriptions and procedures.
>
> We use it to treat you, to plan your care, and to keep your medical history
> accurate for the doctors and nurses who see you.
>
> **Who sees it:** the clinicians and staff treating you at this hospital, our
> laboratory and pharmacy, and anyone you ask us to share it with.
>
> **How long we keep it:** [RETENTION]. Medical records must be kept for a
> minimum period under Indian medical-records rules, and for longer if there is a
> legal claim.
>
> **Can you say no:** you can, but we cannot treat you without a medical record.
> If you later ask us to delete your records, we will delete what we are allowed
> to and tell you clearly what we must keep and why.
>
> **Questions or complaints:** [CONTACT]. You can also complain to the Data
> Protection Board of India.

*Note for counsel: the withdrawal paragraph is deliberately blunt. The system's
erasure receipt tells the patient exactly which stores were retained and why, so
the notice and the behaviour match.*

---

## 2. AGENT_MESSAGING — *optional*

> **Automated messages about your care**
>
> We would like to send you WhatsApp or SMS messages about your appointments,
> test results being ready, and reminders about your treatment. Some of these are
> sent automatically by a computer system rather than by a person.
>
> To do this we use your mobile number and the details of your appointments and
> treatment.
>
> **Who sees it:** our messaging provider handles the delivery. They can see your
> mobile number and the message.
>
> **How long we keep it:** [RETENTION].
>
> **Can you say no:** yes. This is entirely optional and refusing will not affect
> your care in any way. You can stop these messages at any time by telling any
> member of our staff, or through the patient portal. Stopping is as easy as
> starting.
>
> **Questions or complaints:** [CONTACT]. You can also complain to the Data
> Protection Board of India.

---

## 3. AGENT_VOICE — *optional*

> **Automated phone calls, and recording**
>
> We would like to call you about your appointments and care using an automated
> system. **These calls are recorded, and the recording is converted into text so
> a computer can understand what you said.** A member of our staff may listen to
> the recording or read the text.
>
> This means a recording of your voice discussing your health will be stored.
>
> **Who sees it:** our staff, and the technology providers who convert the
> recording into text and help the system understand it. These providers operate
> within India.
>
> **How long we keep it:** [RETENTION].
>
> **Can you say no:** yes. This is entirely optional and refusing will not affect
> your care. If you say no, we will call you in the ordinary way instead. You can
> stop at any time by telling any member of our staff.
>
> **Questions or complaints:** [CONTACT]. You can also complain to the Data
> Protection Board of India.

*Note for counsel: this is the highest-risk purpose in the DPIA (R4). The draft
leads with the recording rather than mentioning it in passing, because a patient
who does not realise they are being recorded has not consented to being recorded.
The residency claim is accurate — the agent service fails startup if an endpoint
is outside the India allowlist.*

---

## 4. INSURANCE_CLAIM — *optional*

> **Sharing your details with your insurer**
>
> If you want us to claim from your insurance, we need to send your details to
> your insurance company or third-party administrator. This includes your name,
> policy number, your diagnosis, the treatment you are receiving, and the cost.
>
> **Your diagnosis is part of what we send.** Insurers need it to decide a claim.
>
> **Who sees it:** your insurance company or TPA, and the National Health Claims
> Exchange which carries the message between us.
>
> **How long we keep it:** [RETENTION]. Claim records are kept for accounting and
> audit even after treatment ends.
>
> **Can you say no:** yes. If you say no, we will still treat you — you would pay
> us directly and claim from your insurer yourself.
>
> **Questions or complaints:** [CONTACT]. You can also complain to the Data
> Protection Board of India.

---

## 5. ABHA_LINKAGE — *optional*

> **Linking your ABHA health account**
>
> ABHA is your national health account. Linking it lets your health records from
> this hospital be shared with other hospitals and clinics you choose, and lets us
> see records they hold — but only where you have separately approved that sharing
> through the ABHA app.
>
> To link it we use your ABHA number or ABHA address, your name and your date of
> birth.
>
> **Who sees it:** the national ABDM system, and any hospital or clinic you
> separately approve through ABHA.
>
> **How long we keep it:** [RETENTION]. If you ask us to delete your records, the
> link and anything we received through it are deleted.
>
> **Can you say no:** yes. This is entirely optional and refusing will not affect
> your care at this hospital.
>
> **Questions or complaints:** [CONTACT]. You can also complain to the Data
> Protection Board of India.

*Note for counsel: the second sentence distinguishes this consent from the
separate ABDM consent artifact the patient grants in the ABHA app. Two different
consents govern this and conflating them would misdescribe what the patient is
agreeing to here.*

---

## 6. PORTAL_SELF_ACCESS — *optional*

> **Using the patient portal**
>
> The patient portal lets you see your own appointments, test results and bills,
> and ask for corrections to your records.
>
> To give you access we use your mobile number to send you a one-time password,
> and we keep a record of when you signed in.
>
> **Note:** when you enter your mobile number, the portal shows you which
> hospitals on this platform hold a record for that number, so you can choose the
> right one.
>
> **How long we keep it:** sign-in records are kept for [RETENTION].
>
> **Can you say no:** yes. If you would rather not use the portal, you can ask for
> your records at the hospital reception instead. Your right to see your own
> records does not depend on using the portal.
>
> **Questions or complaints:** [CONTACT]. You can also complain to the Data
> Protection Board of India.

*Note for counsel: the "which hospitals hold a record" paragraph describes
`PortalPatientLookupRepository`'s deliberate cross-tenant lookup. This is the
processing where the platform appears to act as **Fiduciary in its own right**
(DPIA question 2), and the notice should not hide it. If the portal is
restructured so the patient's relationship is with each hospital, this notice
changes substantially.*

---

## 7. MARKETING — *optional*

> **Updates and offers**
>
> We would like to send you information about health check-up packages, camps and
> services at [HOSPITAL].
>
> To do this we use your name and contact details. **We do not use your medical
> records to decide what to send you.**
>
> **Who sees it:** our messaging and email providers, who handle delivery.
>
> **How long we keep it:** until you tell us to stop, or [RETENTION], whichever is
> sooner.
>
> **Can you say no:** yes. This has nothing to do with your care and refusing will
> not affect it. You can stop at any time.
>
> **Questions or complaints:** [CONTACT]. You can also complain to the Data
> Protection Board of India.

*Note for counsel: the sentence about not using medical records to target
marketing is a commitment, not a description. It reflects the current system —
there is no profiling — and should be removed if that changes, rather than
quietly becoming untrue.*

---

## Approval checklist

Before any of these is marked `ACTIVE`:

- [ ] Counsel has reviewed and edited the text
- [ ] `[RETENTION]` filled with actual periods per purpose
- [ ] `[CONTACT]` resolves — requires J-006, publishing a contact per tenant
- [ ] Tamil translation by a qualified translator (E-005); Hindi if required
- [ ] A decision on whether the portal notice survives the restructuring question
- [ ] `UPDATE consent_notices SET notice_state = 'ACTIVE'` for the approved rows

Until the last step, `hms_consent_notice_draft_served_total` continues to count
every consent captured against placeholder text.
