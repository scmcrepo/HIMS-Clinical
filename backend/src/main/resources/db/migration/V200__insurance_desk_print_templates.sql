-- =============================================================================
--  V200 — Insurance desk print templates  (WO-020 / ID-005)
--
--  Two documents the manual TPA desk cannot work without:
--
--  LETTER_ACCEPTANCE — the undertaking the patient or attender signs once the
--    TPA has sanctioned a pre-authorisation. A sanction is not a guarantee: the
--    insurer routinely disallows non-medical charges, room-rent excess and
--    consumables at settlement, and without this signature the hospital has no
--    documented basis to recover that balance. It is the only thing standing
--    between a disallowance and a write-off.
--
--  ENHANCEMENT_REQUEST — the formal requisition sent when treatment exceeds the
--    sanctioned limit, carrying a breakdown of the running bill. A TPA
--    assessing an enhancement wants to see where the money went before it will
--    sanction more; sending the request without the breakdown reliably earns a
--    query, and a query costs more days than the breakdown costs to produce.
--
--  Both use the repo's existing #{placeholder} substitution and its data.* /
--  profile.* key convention, so PrintServiceImpl.buildModel populates them with
--  the builders added in the same task card. No new rendering path.
--
--  Amounts arrive already formatted as rupee strings from the model builders.
--  Formatting in the template would put a second rounding rule in SQL, and the
--  one in Java is the one with tests.
--
--  Grayscale, matching V175/V188 — these print on the same office laser
--  printers as everything else on the desk.
--
--  ROLLBACK:
--    DELETE FROM print_templates
--     WHERE document_type IN ('LETTER_ACCEPTANCE', 'ENHANCEMENT_REQUEST');
--  Purely additive.
-- =============================================================================

-- ── Letter of Acceptance ────────────────────────────────────────────────────
INSERT INTO print_templates (id, name, document_type, content, is_default, tenant_id)
SELECT
    gen_random_uuid(),
    'Letter of Acceptance',
    'LETTER_ACCEPTANCE',
    $TPL$
<html>
<head>
<meta charset="utf-8">
<style>
  @page { size: A4; margin: 16mm; }
  body { font-family: "IBM Plex Sans", Arial, sans-serif; font-size: 11pt; color: #1a1a1a; }
  .top-bar { display: flex; justify-content: space-between; align-items: flex-start;
             border-bottom: 2px solid #1a1a1a; padding-bottom: 8px; margin-bottom: 16px; }
  .hospital { font-size: 15pt; font-weight: 700; }
  .addr { font-size: 9pt; color: #444; }
  .doc-title { font-size: 12pt; font-weight: 600; text-transform: uppercase;
               letter-spacing: 0.5px; text-align: center; margin: 14px 0 18px; }
  table.kv { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
  table.kv td { padding: 4px 6px; vertical-align: top; font-size: 10.5pt; }
  table.kv td.label { width: 24%; color: #555; }
  .undertaking { border: 1px solid #999; padding: 12px 14px; margin: 16px 0;
                 line-height: 1.65; font-size: 10.5pt; }
  .limit { font-weight: 700; }
  .sign-row { display: flex; justify-content: space-between; margin-top: 42px; }
  .sign-box { width: 44%; border-top: 1px solid #1a1a1a; padding-top: 6px;
              font-size: 9.5pt; text-align: center; }
  .foot { margin-top: 26px; font-size: 8.5pt; color: #666; text-align: center;
          border-top: 1px solid #ccc; padding-top: 6px; }
</style>
</head>
<body>
  <div class="top-bar">
    <div>
      <div class="hospital">#{profile.hospitalName}</div>
      <div class="addr">#{profile.address}</div>
      <div class="addr">#{profile.phone}</div>
    </div>
    <div class="addr">Date: #{date}</div>
  </div>

  <div class="doc-title">Letter of Acceptance</div>

  <table class="kv">
    <tr><td class="label">Patient Name</td><td>#{data.patient.fullName}</td>
        <td class="label">Patient No</td><td>#{data.patient.patientNumber}</td></tr>
    <tr><td class="label">Bill No</td><td>#{data.billNumber}</td>
        <td class="label">Bed</td><td>#{data.bed}</td></tr>
    <tr><td class="label">Insurer</td><td>#{data.insurerName}</td>
        <td class="label">TPA</td><td>#{data.tpaName}</td></tr>
    <tr><td class="label">Policy No</td><td>#{data.policyNumber}</td>
        <td class="label">Claim No</td><td>#{data.claimNo}</td></tr>
    <tr><td class="label">Admission Date</td><td>#{data.admissionDate}</td>
        <td class="label">Sanction Date</td><td>#{data.preauthDateOfApproval}</td></tr>
  </table>

  <table class="kv">
    <tr><td class="label">Amount Sanctioned</td>
        <td class="limit">#{data.approvedLimit}</td></tr>
  </table>

  <div class="undertaking">
    With reference to the above, I hereby undertake to pay the amount of my /
    my ward's hospitalisation expenses — including both medical and non-medical
    charges — if the above mentioned Corporate / TPA / Insurance Company rejects
    or declines to pay part or full of the amount to the Hospital.
    <br><br>
    I understand that the sanctioned amount shown above is an authorisation from
    the insurer and not a guarantee of payment, and that any amount disallowed at
    settlement — including non-medical items, room rent in excess of the eligible
    category, and charges outside the policy — remains payable by me.
  </div>

  <div class="sign-row">
    <div class="sign-box">Patient / Attender Signature<br>Name &amp; Relationship</div>
    <div class="sign-box">For #{profile.hospitalName}<br>Insurance Desk</div>
  </div>

  <div class="foot">
    This document is generated by the hospital information system and is valid
    only when signed.
  </div>
</body>
</html>
    $TPL$,
    TRUE,
    t.id
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM print_templates pt
    WHERE pt.document_type = 'LETTER_ACCEPTANCE' AND pt.tenant_id = t.id
);

-- ── Enhancement Request ─────────────────────────────────────────────────────
INSERT INTO print_templates (id, name, document_type, content, is_default, tenant_id)
SELECT
    gen_random_uuid(),
    'Enhancement Request',
    'ENHANCEMENT_REQUEST',
    $TPL$
<html>
<head>
<meta charset="utf-8">
<style>
  @page { size: A4; margin: 16mm; }
  body { font-family: "IBM Plex Sans", Arial, sans-serif; font-size: 11pt; color: #1a1a1a; }
  .top-bar { display: flex; justify-content: space-between; align-items: flex-start;
             border-bottom: 2px solid #1a1a1a; padding-bottom: 8px; margin-bottom: 16px; }
  .hospital { font-size: 15pt; font-weight: 700; }
  .addr { font-size: 9pt; color: #444; }
  .doc-title { font-size: 12pt; font-weight: 600; text-transform: uppercase;
               letter-spacing: 0.5px; text-align: center; margin: 14px 0 18px; }
  .to { font-size: 10.5pt; margin-bottom: 14px; }
  table.kv { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
  table.kv td { padding: 4px 6px; vertical-align: top; font-size: 10.5pt; }
  table.kv td.label { width: 24%; color: #555; }
  table.charges { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
  table.charges th { text-align: left; background: #f2f2f2; padding: 6px;
                     border: 1px solid #d0d0d0; font-size: 10pt; }
  table.charges td { padding: 6px; border: 1px solid #d0d0d0; font-size: 10pt; }
  .ask { border: 1px solid #999; padding: 10px 12px; margin: 14px 0; font-size: 10.5pt; }
  .ask .big { font-size: 12pt; font-weight: 700; }
  .reason { border-left: 3px solid #999; padding: 6px 10px; margin: 12px 0;
            font-size: 10.5pt; line-height: 1.55; }
  .sign-row { display: flex; justify-content: flex-end; margin-top: 40px; }
  .sign-box { width: 44%; border-top: 1px solid #1a1a1a; padding-top: 6px;
              font-size: 9.5pt; text-align: center; }
  .foot { margin-top: 24px; font-size: 8.5pt; color: #666; text-align: center;
          border-top: 1px solid #ccc; padding-top: 6px; }
</style>
</head>
<body>
  <div class="top-bar">
    <div>
      <div class="hospital">#{profile.hospitalName}</div>
      <div class="addr">#{profile.address}</div>
      <div class="addr">#{profile.phone}</div>
    </div>
    <div class="addr">Date: #{dateTime}</div>
  </div>

  <div class="doc-title">Request for Enhancement of Pre-Authorisation</div>

  <div class="to">
    To: <b>#{data.tpaName}</b> / <b>#{data.insurerName}</b><br>
    Sub: Enhancement of sanctioned limit — Claim No <b>#{data.claimNo}</b>
  </div>

  <table class="kv">
    <tr><td class="label">Patient Name</td><td>#{data.patient.fullName}</td>
        <td class="label">Patient No</td><td>#{data.patient.patientNumber}</td></tr>
    <tr><td class="label">Policy No</td><td>#{data.policyNumber}</td>
        <td class="label">Bill No</td><td>#{data.billNumber}</td></tr>
    <tr><td class="label">Consultant</td><td>#{data.consultant.name}</td>
        <td class="label">Bed</td><td>#{data.bed}</td></tr>
    <tr><td class="label">Admission Date</td><td>#{data.admissionDate}</td>
        <td class="label">Request Type</td><td>#{data.enhancementType}</td></tr>
  </table>

  <table class="charges">
    <thead>
      <tr><th>Charge</th><th style="text-align:right;width:28%">Amount (INR)</th></tr>
    </thead>
    <tbody>
      #{data.billBreakdown}
    </tbody>
  </table>

  <div class="reason">
    <b>Reason for enhancement:</b><br>
    #{data.reasonForEnhancement}
  </div>

  <div class="ask">
    Previously sanctioned limit: <b>#{data.sanctionedLimit}</b><br>
    Revised amount requested: <span class="big">#{data.requestedAmount}</span><br>
    Request raised on: #{data.enhancementAppliedDate}
  </div>

  <div>
    We request you to kindly consider the above and sanction the revised limit at
    the earliest, so that the patient's treatment continues without interruption.
  </div>

  <div class="sign-row">
    <div class="sign-box">For #{profile.hospitalName}<br>Insurance Desk</div>
  </div>

  <div class="foot">
    Generated by the hospital information system. Supporting documents —
    interim bill, investigation reports and case notes — are enclosed.
  </div>
</body>
</html>
    $TPL$,
    TRUE,
    t.id
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM print_templates pt
    WHERE pt.document_type = 'ENHANCEMENT_REQUEST' AND pt.tenant_id = t.id
);
