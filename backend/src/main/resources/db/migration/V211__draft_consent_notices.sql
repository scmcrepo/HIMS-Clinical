-- ---------------------------------------------------------------------------
--  V211 — Draft consent notices, version v2.0-draft  (WO-030 / K-004)
--
--  WHY THIS EXISTS
--
--  The v1.0 notices seeded by V205 and V207 were enum labels lifted out of
--  ConsentPurpose: "Creating or linking your ABHA health account" and similar.
--  They state no purpose detail, no retention period, no recipients and no
--  withdrawal method. Consent captured against them is not informed consent,
--  which the DPIA rates R3 — HIGH residual, and one of only two risks that is
--  not improving.
--
--  These replacements are written so counsel can EDIT rather than start from
--  nothing. Full text and drafting rationale: .hms-agent/DRAFT-CONSENT-NOTICES.md
--
--  ── They stay DRAFT ─────────────────────────────────────────────────────────
--
--  Deliberately inserted as notice_state = 'DRAFT', not 'ACTIVE'. They are an
--  engineer's draft, not approved wording, and marking them ACTIVE would silence
--  hms_consent_notice_draft_served_total — the one metric that currently shows
--  how many patients are consenting against inadequate text. Losing that signal
--  by improving the text would be the wrong trade.
--
--  ConsentService.activeNotice prefers ACTIVE over DRAFT and orders DRAFT rows by
--  effective_from DESC, so v2.0-draft is served ahead of v1.0 immediately. Better
--  text reaches patients now; the metric keeps counting until a human approves.
--
--  ── Placeholders ────────────────────────────────────────────────────────────
--
--  [RETENTION] and [CONTACT] are left in the text on purpose. Substituting a
--  guess would produce a notice that is confidently wrong, which is worse than
--  one that is visibly incomplete — a reviewer skims past a plausible number and
--  stops at a bracket. [CONTACT] additionally cannot resolve until J-006 seeds
--  compliance_contacts, which currently has no rows for any tenant.
--
--  ROLLBACK
--    DELETE FROM consent_notices WHERE version = 'v2.0-draft';
--  Safe: v1.0 rows are untouched and become the served notice again.
-- ---------------------------------------------------------------------------

INSERT INTO consent_notices (id, tenant_id, purpose, version, language, body_text, notice_state)
SELECT gen_random_uuid(), t.id, v.purpose, 'v2.0-draft', 'en', v.body, 'DRAFT'
FROM tenants t
CROSS JOIN (VALUES

('TREATMENT', $notice$How we use your health information

[HOSPITAL] keeps a record of your care. This includes your name, contact details, date of birth, and the details of your visits, diagnoses, tests, prescriptions and procedures.

We use it to treat you, to plan your care, and to keep your medical history accurate for the doctors and nurses who see you.

Who sees it: the clinicians and staff treating you at this hospital, our laboratory and pharmacy, and anyone you ask us to share it with.

How long we keep it: [RETENTION]. Medical records must be kept for a minimum period under Indian medical-records rules, and for longer if there is a legal claim.

Can you say no: you can, but we cannot treat you without a medical record. If you later ask us to delete your records, we will delete what we are allowed to and tell you clearly what we must keep and why.

Questions or complaints: [CONTACT]. You can also complain to the Data Protection Board of India.$notice$),

('AGENT_MESSAGING', $notice$Automated messages about your care

We would like to send you WhatsApp or SMS messages about your appointments, test results being ready, and reminders about your treatment. Some of these are sent automatically by a computer system rather than by a person.

To do this we use your mobile number and the details of your appointments and treatment.

Who sees it: our messaging provider handles the delivery. They can see your mobile number and the message.

How long we keep it: [RETENTION].

Can you say no: yes. This is entirely optional and refusing will not affect your care in any way. You can stop these messages at any time by telling any member of our staff, or through the patient portal. Stopping is as easy as starting.

Questions or complaints: [CONTACT]. You can also complain to the Data Protection Board of India.$notice$),

('AGENT_VOICE', $notice$Automated phone calls, and recording

We would like to call you about your appointments and care using an automated system. These calls are recorded, and the recording is converted into text so a computer can understand what you said. A member of our staff may listen to the recording or read the text.

This means a recording of your voice discussing your health will be stored.

Who sees it: our staff, and the technology providers who convert the recording into text and help the system understand it. These providers operate within India.

How long we keep it: [RETENTION].

Can you say no: yes. This is entirely optional and refusing will not affect your care. If you say no, we will call you in the ordinary way instead. You can stop at any time by telling any member of our staff.

Questions or complaints: [CONTACT]. You can also complain to the Data Protection Board of India.$notice$),

('INSURANCE_CLAIM', $notice$Sharing your details with your insurer

If you want us to claim from your insurance, we need to send your details to your insurance company or third-party administrator. This includes your name, policy number, your diagnosis, the treatment you are receiving, and the cost.

Your diagnosis is part of what we send. Insurers need it to decide a claim.

Who sees it: your insurance company or TPA, and the National Health Claims Exchange which carries the message between us.

How long we keep it: [RETENTION]. Claim records are kept for accounting and audit even after treatment ends.

Can you say no: yes. If you say no, we will still treat you — you would pay us directly and claim from your insurer yourself.

Questions or complaints: [CONTACT]. You can also complain to the Data Protection Board of India.$notice$),

('ABHA_LINKAGE', $notice$Linking your ABHA health account

ABHA is your national health account. Linking it lets your health records from this hospital be shared with other hospitals and clinics you choose, and lets us see records they hold — but only where you have separately approved that sharing through the ABHA app.

To link it we use your ABHA number or ABHA address, your name and your date of birth.

Who sees it: the national ABDM system, and any hospital or clinic you separately approve through ABHA.

How long we keep it: [RETENTION]. If you ask us to delete your records, the link and anything we received through it are deleted.

Can you say no: yes. This is entirely optional and refusing will not affect your care at this hospital.

Questions or complaints: [CONTACT]. You can also complain to the Data Protection Board of India.$notice$),

('PORTAL_SELF_ACCESS', $notice$Using the patient portal

The patient portal lets you see your own appointments, test results and bills, and ask for corrections to your records.

To give you access we use your mobile number to send you a one-time password, and we keep a record of when you signed in.

Note: when you enter your mobile number, the portal shows you which hospitals on this platform hold a record for that number, so you can choose the right one.

How long we keep it: sign-in records are kept for [RETENTION].

Can you say no: yes. If you would rather not use the portal, you can ask for your records at the hospital reception instead. Your right to see your own records does not depend on using the portal.

Questions or complaints: [CONTACT]. You can also complain to the Data Protection Board of India.$notice$),

('MARKETING', $notice$Updates and offers

We would like to send you information about health check-up packages, camps and services at [HOSPITAL].

To do this we use your name and contact details. We do not use your medical records to decide what to send you.

Who sees it: our messaging and email providers, who handle delivery.

How long we keep it: until you tell us to stop, or [RETENTION], whichever is sooner.

Can you say no: yes. This has nothing to do with your care and refusing will not affect it. You can stop at any time.

Questions or complaints: [CONTACT]. You can also complain to the Data Protection Board of India.$notice$)

) AS v(purpose, body)
ON CONFLICT (tenant_id, purpose, version, language) DO NOTHING;
