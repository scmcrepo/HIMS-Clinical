-- =============================================================================
--  V194 — Benefit verification acknowledgment template  (WO-013 / Screen 2.2)
--
--  The printed sheet the patient signs at admission, confirming what the insurer
--  said would be covered. It exists because the hospital's exposure is the gap
--  between what the patient believed and what the payer actually agreed to, and
--  a signed acknowledgment is the only thing that closes it.
--
--  Uses the existing #{placeholder} substitution and the repo's data.* / profile.*
--  key convention, so PrintServiceImpl.buildModel populates it unchanged.
--
--  Amounts arrive already formatted as rupee strings. Formatting in the template
--  would mean a second rounding rule living in SQL, and the one in
--  CoverageResponseParser is the one that has tests.
--
--  Deliberately prints "Not stated by insurer" rather than a zero or a blank
--  where a benefit is absent. On a document the patient signs, a blank room-rent
--  cap reads as "no limit" and a zero reads as "nothing covered" — and the
--  truth is neither.
--
--  ROLLBACK:
--    DELETE FROM print_templates WHERE document_type = 'BENEFIT_ACKNOWLEDGMENT';
-- =============================================================================

INSERT INTO print_templates (id, name, document_type, content, is_default, tenant_id)
SELECT
    gen_random_uuid(),
    'Insurance Benefit Verification',
    'BENEFIT_ACKNOWLEDGMENT',
    $TPL$
<html>
<head>
<meta charset="utf-8">
<style>
  @page { size: A4; margin: 14mm; }
  body { font-family: "IBM Plex Sans", Arial, sans-serif; font-size: 11pt; color: #1a1a1a; }
  .page { width: 100%; }
  .top-bar { display: flex; justify-content: space-between; align-items: flex-start;
             border-bottom: 2px solid #1a1a1a; padding-bottom: 8px; margin-bottom: 14px; }
  .hospital { font-size: 15pt; font-weight: 700; }
  .doc-title { font-size: 12pt; font-weight: 600; text-transform: uppercase;
               letter-spacing: 0.5px; }
  .meta { font-size: 9.5pt; color: #555; }
  table.kv { width: 100%; border-collapse: collapse; margin-bottom: 14px; }
  table.kv td { padding: 4px 6px; vertical-align: top; }
  table.kv td.label { width: 32%; color: #555; }
  table.benefits { width: 100%; border-collapse: collapse; margin-bottom: 14px; }
  table.benefits th { text-align: left; background: #f2f2f2; padding: 6px;
                      border: 1px solid #d0d0d0; font-size: 10pt; }
  table.benefits td { padding: 6px; border: 1px solid #d0d0d0; }
  td.amount { text-align: right; font-variant-numeric: tabular-nums; }
  .status { display: inline-block; padding: 2px 8px; border-radius: 10px;
            font-size: 9.5pt; font-weight: 600; border: 1px solid #999; }
  .exclusions li { margin-bottom: 3px; }
  .ack { border: 1px solid #1a1a1a; padding: 10px; margin-top: 10px; font-size: 10pt; }
  .sign { margin-top: 26px; display: flex; justify-content: space-between; }
  .sign div { width: 45%; border-top: 1px solid #1a1a1a; padding-top: 4px; font-size: 10pt; }
  .end-report { margin-top: 16px; font-size: 8.5pt; color: #777; text-align: center; }
</style>
</head>
<body>
<div class="page">

  <div class="top-bar">
    <div>
      <div class="hospital">#{profile.hospitalName}</div>
      <div class="meta">#{profile.address}</div>
    </div>
    <div style="text-align:right">
      <div class="doc-title">Insurance Benefit Verification</div>
      <div class="meta">Verified on #{data.checkedAt}</div>
    </div>
  </div>

  <table class="kv">
    <tr><td class="label">Patient</td><td>#{data.patientName} (#{data.patientNumber})</td></tr>
    <tr><td class="label">Insurer / TPA</td><td>#{data.payerName} #{data.tpaName}</td></tr>
    <tr><td class="label">Policy number</td><td>#{data.policyNumberMasked}</td></tr>
    <tr><td class="label">Policy status</td>
        <td><span class="status">#{data.policyStatus}</span></td></tr>
  </table>

  <table class="benefits">
    <tr><th>Benefit</th><th style="width:30%">Amount</th></tr>
    <tr><td>Total sum insured</td><td class="amount">#{data.sumInsured}</td></tr>
    <tr><td>Utilised to date</td><td class="amount">#{data.utilised}</td></tr>
    <tr><td><strong>Balance available</strong></td>
        <td class="amount"><strong>#{data.balance}</strong></td></tr>
    <tr><td>Room rent limit per day</td><td class="amount">#{data.roomRentCap}</td></tr>
    <tr><td>Eligible room category</td><td class="amount">#{data.roomCategory}</td></tr>
    <tr><td>ICU limit per day</td><td class="amount">#{data.icuCap}</td></tr>
    <tr><td>Co-payment borne by patient</td><td class="amount">#{data.coPay}</td></tr>
    <tr><td>Deductible</td><td class="amount">#{data.deductible}</td></tr>
    <tr><td>Pre-existing disease waiting period</td>
        <td class="amount">#{data.pedWaiting}</td></tr>
  </table>

  <div><strong>Exclusions and restrictions notified by the insurer</strong></div>
  <ul class="exclusions">#{data.exclusionsHtml}</ul>

  <div class="ack">
    I have been informed of the room category and daily limits my policy covers, and of the
    co-payment, deductible and excluded items listed above. I understand that any amount the
    insurer does not settle — including charges above these limits and any item listed as
    excluded — is payable by me. I understand these figures are as stated by the insurer on
    the date shown and may change when the claim is finally adjudicated.
  </div>

  <div class="sign">
    <div>Patient / attendant signature</div>
    <div>Insurance desk — #{data.staffName}</div>
  </div>

  <div class="end-report">
    Generated #{dateTime} &nbsp;|&nbsp; NHCX reference #{data.correlationId}
  </div>

</div>
</body>
</html>
    $TPL$,
    TRUE,
    t.id
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM print_templates p
    WHERE p.tenant_id = t.id AND p.document_type = 'BENEFIT_ACKNOWLEDGMENT'
);
