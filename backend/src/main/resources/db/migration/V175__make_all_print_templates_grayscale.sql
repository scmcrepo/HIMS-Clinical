-- V175__make_all_print_templates_grayscale.sql
-- Updates all print templates in the database to use a minimal grayscale aesthetic.

DO $$
DECLARE
    t_bill TEXT;
    t_receipt TEXT;
    t_ip_receipt TEXT;
    t_ip_bill TEXT;
    t_sales TEXT;
    t_lab TEXT;
    t_radiology TEXT;
    t_diag_order TEXT;
    t_discharge TEXT;
    t_refund TEXT;
    t_advance_refund TEXT;
    t_patient_id TEXT;
BEGIN

    -- ============================================================
    -- 1. BILL (Default OP Bill / Provisional Bill)
    -- ============================================================
    t_bill := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:6mm 8mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:12px;border-bottom:1.5px solid #111827}
.header-table td{padding:6px 0;vertical-align:middle;border:none}
.logo-td{width:80px;padding-right:15px;vertical-align:middle;border:none}
.logo-img{max-height:55px;max-width:80px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:16px;font-weight:700;text-transform:uppercase;color:#111827;letter-spacing:0.5px}
.h-sub{font-size:9px;color:#4b5563;line-height:1.4;margin-top:3px}
.document-title{text-align:center;font-size:13px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:10px 0 15px 0;letter-spacing:1px}
.details-table{width:100%;margin-bottom:12px;border-collapse:collapse;border:none}
.details-table td{padding:4px 6px;font-size:10px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:90px}
.sh{font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#111827;border-bottom:1.5px solid #111827;padding-bottom:3px;margin:15px 0 8px 0}
table.items-table{width:100%;border-collapse:collapse;margin-bottom:10px}
table.items-table thead tr{background:#f3f4f6;border-top:1px solid #d1d5db;border-bottom:1px solid #d1d5db}
table.items-table thead th{padding:6px 8px;font-size:8.5px;font-weight:700;text-transform:uppercase;color:#111827;text-align:left}
table.items-table thead th.r{text-align:right}
table.items-table tbody tr{border-bottom:1px solid #e5e7eb}
table.items-table tbody td{padding:6px 8px;font-size:9.5px;color:#111827;vertical-align:middle}
table.items-table tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
table.items-table tbody td.muted{color:#6b7280;font-size:8.5px;font-family:'DM Mono',monospace}
.totals-table{margin-left:auto;width:240px;border:1px solid #d1d5db;border-radius:6px;border-collapse:collapse;overflow:hidden;margin-top:10px;margin-bottom:10px}
.totals-table td{padding:6px 10px;font-size:10px;border-bottom:1px solid #f3f4f6}
.totals-table td.lbl{color:#4b5563;text-align:left}
.totals-table td.val{font-family:'DM Mono',monospace;font-weight:600;text-align:right}
.totals-table tr.grand-total{font-weight:700;border-top:1px solid #111827}
.totals-table tr.grand-total td{color:#111827;border-bottom:none;font-size:10px}
.words{font-size:9px;color:#4b5563;font-style:italic;margin-top:8px;padding:5px 8px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:4px}
.end-report{text-align:center;margin:25px 0 10px;font-size:9.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7.5px;color:#9ca3af;line-height:1.5}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:150px;margin-left:auto;padding-top:4px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">PROVISIONAL BILL</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Patient No</span>: #{data.patient.patientNumber}</td>
      <td style="width: 50%"><span class="lbl">Patient</span>: #{data.patient.fullName}</td>
    </tr>
    <tr>
      <td><span class="lbl">Consultant</span>: #{data.consultant.name}</td>
      <td><span class="lbl">Bill No / Date</span>: #{data.billNumber} &nbsp;|&nbsp; #{data.billDate}</td>
    </tr>
    <tr>
      <td><span class="lbl">Encounter</span>: #{data.encounterType} &nbsp;|&nbsp; #{data.billType}</td>
      <td><span class="lbl">Status</span>: #{data.status}</td>
    </tr>
  </table>

  <div class="sh">Charges</div>
  <table class="items-table">
    <thead>
      <tr>
        <th style="width: 8%">S.No</th>
        <th>Service / Item</th>
        <th class="r" style="width: 15%">Rate (&#8377;)</th>
        <th class="r" style="width: 10%">Qty</th>
        <th class="r" style="width: 15%">Discount (&#8377;)</th>
        <th class="r" style="width: 20%">Amount (&#8377;)</th>
      </tr>
    </thead>
    <tbody>
      #{data.chargeLines}
    </tbody>
  </table>

  #{data.paymentsTable}

  <table class="totals-table">
    <tr>
      <td class="lbl">Gross Total</td>
      <td class="val">&#8377; #{data.billAmount}</td>
    </tr>
    <tr>
      <td class="lbl">Discount</td>
      <td class="val">&minus; &#8377; #{data.discountTotal}</td>
    </tr>
    <tr>
      <td class="lbl">Paid</td>
      <td class="val">&#8377; #{data.paymentTotal}</td>
    </tr>
    <tr class="grand-total">
      <td class="lbl">Balance Due</td>
      <td class="val">&#8377; #{data.dueAmount}</td>
    </tr>
  </table>

  <div class="words">Amount in Words: #{numberToString} Only</div>

  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div><div>This is a computer-generated invoice.</div></div>
    <div class="sig">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 25px;">For #{profile.name}</div>
      <div class="sig-line">Authorised Signatory</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 2. OP_RECEIPT (Default OP Receipt)
    -- ============================================================
    t_receipt := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:6mm 8mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:12px;border-bottom:1.5px solid #111827}
.header-table td{padding:6px 0;vertical-align:middle;border:none}
.logo-td{width:80px;padding-right:15px;vertical-align:middle;border:none}
.logo-img{max-height:55px;max-width:80px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:16px;font-weight:700;text-transform:uppercase;color:#111827;letter-spacing:0.5px}
.h-sub{font-size:9px;color:#4b5563;line-height:1.4;margin-top:3px}
.document-title{text-align:center;font-size:13px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:10px 0 15px 0;letter-spacing:1px}
.details-table{width:100%;margin-bottom:12px;border-collapse:collapse;border:none}
.details-table td{padding:4px 6px;font-size:10px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:90px}
.sh{font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#111827;border-bottom:1.5px solid #111827;padding-bottom:3px;margin:15px 0 8px 0}
table.receipt-table{width:100%;border-collapse:collapse;margin-bottom:10px}
table.receipt-table thead tr{background:#f3f4f6;border-top:1px solid #d1d5db;border-bottom:1px solid #d1d5db}
table.receipt-table thead th{padding:6px 8px;font-size:8.5px;font-weight:700;text-transform:uppercase;color:#111827;text-align:left}
table.receipt-table thead th.r{text-align:right}
table.receipt-table tbody tr{border-bottom:1px solid #e5e7eb}
table.receipt-table tbody td{padding:6px 8px;font-size:9.5px;color:#111827;vertical-align:middle}
table.receipt-table tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
.words{font-size:9px;color:#4b5563;font-style:italic;margin-top:8px;padding:5px 8px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:4px}
.end-report{text-align:center;margin:25px 0 10px;font-size:9.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7.5px;color:#9ca3af;line-height:1.5}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:150px;margin-left:auto;padding-top:4px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">PAYMENT RECEIPT</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Patient Name</span>: #{data.patient.fullName}</td>
      <td style="width: 50%"><span class="lbl">Patient ID</span>: #{data.patient.patientNumber}</td>
    </tr>
    <tr>
      <td><span class="lbl">Gender</span>: #{data.patient.gender}</td>
      <td><span class="lbl">Consultant</span>: #{data.consultant.name}</td>
    </tr>
    <tr>
      <td><span class="lbl">Receipt No</span>: <span style="font-family:'DM Mono',monospace">#{data.receiptNumber}</span></td>
      <td><span class="lbl">Receipt Date</span>: #{data.paymentDate}</td>
    </tr>
    <tr>
      <td><span class="lbl">Bill No / Date</span>: #{data.billNumber} &nbsp;|&nbsp; #{data.billDate}</td>
      <td></td>
    </tr>
  </table>
  
  <div class="sh">Receipt Information</div>
  <table class="receipt-table">
    <thead>
      <tr>
        <th>Receipt Date</th>
        <th>Receipt No</th>
        <th>Mode</th>
        <th class="r">Collected (&#8377;)</th>
        <th class="r">Balance (&#8377;)</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>#{data.paymentDate}</td>
        <td style="font-family:'DM Mono',monospace">#{data.receiptNumber}</td>
        <td>#{data.paymentMode}</td>
        <td class="r" style="font-family:'DM Mono',monospace;font-weight:700">&#8377; #{data.amount}</td>
        <td class="r" style="font-family:'DM Mono',monospace;font-weight:700">&#8377; #{data.balance}</td>
      </tr>
    </tbody>
  </table>

  <div class="words">Received Amount in Words: #{numberToString} Only</div>
  
  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div><div>This is a computer-generated receipt.</div></div>
    <div class="sig">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 25px;">For #{profile.name}</div>
      <div class="sig-line">Authorised Signatory</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 3. IP_RECEIPT (Default IP Receipt)
    -- ============================================================
    t_ip_receipt := t_receipt;

    -- ============================================================
    -- 4. IP_BILL_CONSOLIDATED (Default IP Bill)
    -- ============================================================
    t_ip_bill := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:6mm 8mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:12px;border-bottom:1.5px solid #111827}
.header-table td{padding:6px 0;vertical-align:middle;border:none}
.logo-td{width:80px;padding-right:15px;vertical-align:middle;border:none}
.logo-img{max-height:55px;max-width:80px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:16px;font-weight:700;text-transform:uppercase;color:#111827;letter-spacing:0.5px}
.h-sub{font-size:9px;color:#4b5563;line-height:1.4;margin-top:3px}
.document-title{text-align:center;font-size:13px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:10px 0 15px 0;letter-spacing:1px}
.details-table{width:100%;margin-bottom:12px;border-collapse:collapse;border:none}
.details-table td{padding:4px 6px;font-size:10px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:90px}
.sh{font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#111827;border-bottom:1.5px solid #111827;padding-bottom:3px;margin:15px 0 8px 0}
table.items-table{width:100%;border-collapse:collapse;margin-bottom:10px}
table.items-table thead tr{background:#f3f4f6;border-top:1px solid #d1d5db;border-bottom:1px solid #d1d5db}
table.items-table thead th{padding:6px 8px;font-size:8.5px;font-weight:700;text-transform:uppercase;color:#111827;text-align:left}
table.items-table thead th.r{text-align:right}
table.items-table tbody tr{border-bottom:1px solid #e5e7eb}
table.items-table tbody td{padding:6px 8px;font-size:9.5px;color:#111827;vertical-align:middle}
table.items-table tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
.totals-table{margin-left:auto;width:240px;border:1px solid #d1d5db;border-radius:6px;border-collapse:collapse;overflow:hidden;margin-top:10px;margin-bottom:10px}
.totals-table td{padding:6px 10px;font-size:10px;border-bottom:1px solid #f3f4f6}
.totals-table td.lbl{color:#4b5563;text-align:left}
.totals-table td.val{font-family:'DM Mono',monospace;font-weight:600;text-align:right}
.totals-table tr.grand-total{font-weight:700;border-top:1px solid #111827}
.totals-table tr.grand-total td{color:#111827;border-bottom:none;font-size:10px}
.words{font-size:9px;color:#4b5563;font-style:italic;margin-top:8px;padding:5px 8px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:4px}
.end-report{text-align:center;margin:25px 0 10px;font-size:9.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7.5px;color:#9ca3af;line-height:1.5}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:150px;margin-left:auto;padding-top:4px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">INPATIENT BILL</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Patient Name</span>: #{data.patient.fullName}</td>
      <td style="width: 50%"><span class="lbl">Patient ID</span>: #{data.patient.patientNumber}</td>
    </tr>
    <tr>
      <td><span class="lbl">Admission Date</span>: #{data.admissionDate}</td>
      <td><span class="lbl">Discharge Date</span>: #{data.dischargeDate}</td>
    </tr>
    <tr>
      <td><span class="lbl">Consultant</span>: #{data.consultant.name}</td>
      <td><span class="lbl">Bed / Ward</span>: #{data.bed}</td>
    </tr>
    <tr>
      <td><span class="lbl">Bill No / Date</span>: #{data.billNumber} &nbsp;|&nbsp; #{data.billDate}</td>
      <td><span class="lbl">Status</span>: #{data.status} &nbsp;|&nbsp; #{data.billType}</td>
    </tr>
  </table>

  <div class="sh">All Charges</div>
  <table class="items-table">
    <thead>
      <tr>
        <th style="width: 8%">S.No</th>
        <th>Service</th>
        <th class="r" style="width: 15%">Rate (&#8377;)</th>
        <th class="r" style="width: 10%">Qty</th>
        <th class="r" style="width: 15%">Discount (&#8377;)</th>
        <th class="r" style="width: 20%">Amount (&#8377;)</th>
      </tr>
    </thead>
    <tbody>
      #{data.chargeLines}
    </tbody>
  </table>

  #{data.paymentsTable}

  <table class="totals-table">
    <tr>
      <td class="lbl">Gross Total</td>
      <td class="val">&#8377; #{data.billAmount}</td>
    </tr>
    <tr>
      <td class="lbl">Discount</td>
      <td class="val">&minus; &#8377; #{data.discountTotal}</td>
    </tr>
    <tr>
      <td class="lbl">Paid</td>
      <td class="val">&#8377; #{data.paymentTotal}</td>
    </tr>
    <tr class="grand-total">
      <td class="lbl">Balance Due</td>
      <td class="val">&#8377; #{data.dueAmount}</td>
    </tr>
  </table>

  <div class="words">Amount in Words: #{numberToString} Only</div>

  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div><div>This is a computer-generated inpatient bill.</div></div>
    <div class="sig">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 25px;">For #{profile.name}</div>
      <div class="sig-line">Authorised Signatory</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 5. SALES (Pharmacy POS Receipt)
    -- ============================================================
    t_sales := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:9.5px;color:#111827;background:#fff;padding:4mm 5mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:10px;border-bottom:1.5px solid #111827}
.header-table td{padding:4px 0;vertical-align:middle;border:none}
.logo-td{width:60px;padding-right:10px;vertical-align:middle;border:none}
.logo-img{max-height:40px;max-width:60px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:13px;font-weight:700;text-transform:uppercase;color:#111827}
.h-sub{font-size:8px;color:#4b5563;line-height:1.3;margin-top:2px}
.document-title{text-align:center;font-size:11px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:8px 0 10px 0;letter-spacing:0.5px}
.details-table{width:100%;margin-bottom:8px;border-collapse:collapse;border:none}
.details-table td{padding:3px 4px;font-size:9.5px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:60px}
table.items-table{width:100%;border-collapse:collapse;margin-bottom:8px}
table.items-table thead tr{background:#f3f4f6;border-top:1px solid #d1d5db;border-bottom:1px solid #d1d5db}
table.items-table thead th{padding:4px 5px;font-size:8px;font-weight:700;text-transform:uppercase;color:#111827;text-align:left}
table.items-table thead th.c{text-align:center}
table.items-table thead th.r{text-align:right}
table.items-table tbody tr{border-bottom:1px solid #e5e7eb}
table.items-table tbody td{padding:4px 5px;font-size:9px;color:#111827;vertical-align:middle}
table.items-table tbody td.c{text-align:center}
table.items-table tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
.totals-table{margin-left:auto;width:200px;border:1px solid #d1d5db;border-radius:4px;border-collapse:collapse;overflow:hidden;margin-top:6px;margin-bottom:6px}
.totals-table td{padding:4px 8px;font-size:9px;border-bottom:1px solid #f3f4f6}
.totals-table td.lbl{color:#4b5563;text-align:left}
.totals-table td.val{font-family:'DM Mono',monospace;font-weight:600;text-align:right}
.totals-table tr.grand-total{font-weight:700;border-top:1px solid #111827}
.totals-table tr.grand-total td{color:#111827;border-bottom:none;font-size:10px}
.words{font-size:8px;color:#4b5563;font-style:italic;margin-top:6px;padding:4px 6px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:4px}
.end-report{text-align:center;margin:15px 0 5px;font-size:8.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:6px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7px;color:#9ca3af;line-height:1.4}
.sig{text-align:right;font-size:8px}
.sig-line{border-top:1px solid #d1d5db;width:110px;margin-left:auto;padding-top:2px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">SALES RECEIPT</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Sale No</span>: #{data.sequenceNumber}</td>
      <td style="width: 50%"><span class="lbl">Date</span>: #{data.saleDate}</td>
    </tr>
    <tr>
      <td><span class="lbl">Patient</span>: #{data.patientName}</td>
      <td><span class="lbl">Doctor</span>: #{data.consultantName}</td>
    </tr>
  </table>

  <table class="items-table">
    <thead>
      <tr>
        <th>#</th>
        <th>Item</th>
        <th class="c" style="width: 15%">Qty</th>
        <th class="r" style="width: 20%">Rate (&#8377;)</th>
        <th class="r" style="width: 20%">Amt (&#8377;)</th>
      </tr>
    </thead>
    <tbody>
      #{data.saleLines}
    </tbody>
  </table>

  <table class="totals-table">
    <tr>
      <td class="lbl">Sub Total</td>
      <td class="val">&#8377; #{data.totalAmount}</td>
    </tr>
    <tr>
      <td class="lbl">Discount</td>
      <td class="val">&minus; &#8377; #{data.discountAmount}</td>
    </tr>
    <tr>
      <td class="lbl">Paid (Mode: #{data.paymentMode})</td>
      <td class="val">&#8377; #{data.paidAmount}</td>
    </tr>
    <tr class="grand-total">
      <td class="lbl">Total</td>
      <td class="val">&#8377; #{data.totalAmount}</td>
    </tr>
  </table>

  <div class="words">Amount in Words: #{numberToString} Only</div>

  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div><div>Returns accepted within 7 days with bill.</div></div>
    <div class="sig">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 20px;">For #{profile.name}</div>
      <div class="sig-line">Authorised Signatory</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 6. LAB (Laboratory Report)
    -- ============================================================
    t_lab := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:6mm 8mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:12px;border-bottom:1.5px solid #111827}
.header-table td{padding:6px 0;vertical-align:middle;border:none}
.logo-td{width:80px;padding-right:15px;vertical-align:middle;border:none}
.logo-img{max-height:55px;max-width:80px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:16px;font-weight:700;text-transform:uppercase;color:#111827;letter-spacing:0.5px}
.h-sub{font-size:9px;color:#4b5563;line-height:1.4;margin-top:3px}
.document-title{text-align:center;font-size:13px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:10px 0 15px 0;letter-spacing:1px}
.details-table{width:100%;margin-bottom:12px;border-collapse:collapse;border:none}
.details-table td{padding:4px 6px;font-size:10px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:110px}
.results-table{width:100%;border-collapse:collapse;margin:15px 0}
.results-table thead{background:#f3f4f6;border-top:1.5px solid #111827;border-bottom:1.5px solid #111827}
.results-table th{font-size:9.5px;font-weight:700;color:#111827;padding:8px 6px;text-align:left}
.results-table th.c{text-align:center}
.results-table tbody tr{border-bottom:1px solid #e2e8f0}
.results-table tbody td{padding:8px 6px;font-size:9.5px;vertical-align:middle}
.results-table td.val{text-align:center;font-family:'DM Mono',monospace;font-weight:600;font-size:10px;color:#111827}
.results-table td.unit{text-align:center;font-family:'DM Mono',monospace;color:#4b5563;font-size:9px}
.results-table td.range{text-align:center;color:#4b5563;font-size:9px}
.results-table tbody td[style*="background-color"]{background-color:#f9fafb !important;color:#111827 !important;border-bottom:1px solid #e5e7eb !important;padding:6px 6px !important}
.results-table tbody td[style*="text-decoration:underline"]{color:#111827 !important;padding-top:14px !important;padding-bottom:4px !important;text-decoration:none !important;border-bottom:1.5px solid #111827 !important}
.end-report{text-align:center;margin:25px 0 10px;font-size:9.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7.5px;color:#9ca3af;line-height:1.5}
.sig{text-align:center;font-size:8.5px}
.sig-line{border-top:1px solid #d1d5db;width:130px;margin:0 auto;padding-top:4px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">LABORATORY REPORT</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Patient Name</span>: #{data.patientName}</td>
      <td style="width: 50%"><span class="lbl">Age / Sex</span>: #{data.patientAge} / #{data.patientGender}</td>
    </tr>
    <tr>
      <td><span class="lbl">Patient ID (PID)</span>: #{data.patientNumber}</td>
      <td><span class="lbl">Ref No / Date</span>: #{data.sequenceNumber} &nbsp;|&nbsp; #{data.orderDate}</td>
    </tr>
    <tr>
      <td><span class="lbl">Referred By</span>: #{data.consultantName}</td>
      <td></td>
    </tr>
  </table>

  <table class="results-table">
    <thead>
      <tr class="headers-row">
        <th style="width: 35%">Investigations</th>
        <th style="width: 15%; text-align: center;">Results</th>
        <th style="width: 15%; text-align: center;">Unit</th>
        <th style="width: 35%; text-align: center;">Biological Reference Interval</th>
      </tr>
    </thead>
    <tbody>
      #{data.resultLines}
    </tbody>
  </table>

  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn">
      <div>Queries: #{profile.contactNo}</div>
      <div>* H=High L=Low N=Normal &nbsp;|&nbsp; Report valid 30 days</div>
    </div>
    <div class="sig">
      <div class="sig-line">Lab Technician</div>
      <div style="font-size:7.5px;color:#9ca3af;margin-top:2px">#{profile.name}</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 7. RADIOLOGY (Radiology Report)
    -- ============================================================
    t_radiology := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:6mm 8mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:12px;border-bottom:1.5px solid #111827}
.header-table td{padding:6px 0;vertical-align:middle;border:none}
.logo-td{width:80px;padding-right:15px;vertical-align:middle;border:none}
.logo-img{max-height:55px;max-width:80px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:16px;font-weight:700;text-transform:uppercase;color:#111827;letter-spacing:0.5px}
.h-sub{font-size:9px;color:#4b5563;line-height:1.4;margin-top:3px}
.document-title{text-align:center;font-size:13px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:10px 0 15px 0;letter-spacing:1px}
.details-table{width:100%;margin-bottom:12px;border-collapse:collapse;border:none}
.details-table td{padding:4px 6px;font-size:10px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:110px}
.section{margin-bottom:12px}
.s-hdr{font-size:9.5px;font-weight:700;text-transform:uppercase;letter-spacing:0.5px;color:#111827;border-bottom:1.5px solid #111827;padding-bottom:3px;margin-bottom:6px}
.s-body{font-size:10px;line-height:1.6;color:#1f2937;background:#f9fafb;border-radius:4px;padding:8px;min-height:35px;border-left:3px solid #d1d5db;white-space:pre-wrap}
.imp-body{font-size:10.5px;font-weight:600;line-height:1.6;color:#111827;background:#f3f4f6;border-radius:4px;padding:8px;min-height:35px;border-left:3px solid #111827}
.end-report{text-align:center;margin:25px 0 10px;font-size:9.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7.5px;color:#9ca3af;line-height:1.5}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:150px;margin-left:auto;padding-top:4px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">RADIOLOGY REPORT</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Patient Name</span>: #{data.patientName}</td>
      <td style="width: 50%"><span class="lbl">Age / Sex</span>: #{data.patientAge} / #{data.patientGender}</td>
    </tr>
    <tr>
      <td><span class="lbl">Patient ID</span>: #{data.patientNumber}</td>
      <td><span class="lbl">Report No / Date</span>: #{data.sequenceNumber} &nbsp;|&nbsp; #{date}</td>
    </tr>
    <tr>
      <td><span class="lbl">Referring Doctor</span>: #{data.consultantName}</td>
      <td><span class="lbl">Study / Department</span>: #{data.department}</td>
    </tr>
  </table>

  <div class="section">
    <div class="s-hdr">Findings</div>
    <div class="s-body">#{data.resultLines}</div>
  </div>

  <div class="section">
    <div class="s-hdr">Impression</div>
    <div class="imp-body">To be filled by radiologist.</div>
  </div>

  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn"><div>Report Date: #{date}</div><div>#{profile.name}</div></div>
    <div class="sig">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 25px;">#{data.consultantName}</div>
      <div class="sig-line">Radiologist</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 8. DIAGNOSTIC_ORDER (Diagnostic Order)
    -- ============================================================
    t_diag_order := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:6mm 8mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:12px;border-bottom:1.5px solid #111827}
.header-table td{padding:6px 0;vertical-align:middle;border:none}
.logo-td{width:80px;padding-right:15px;vertical-align:middle;border:none}
.logo-img{max-height:55px;max-width:80px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:16px;font-weight:700;text-transform:uppercase;color:#111827;letter-spacing:0.5px}
.h-sub{font-size:9px;color:#4b5563;line-height:1.4;margin-top:3px}
.document-title{text-align:center;font-size:13px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:10px 0 15px 0;letter-spacing:1px}
.details-table{width:100%;margin-bottom:12px;border-collapse:collapse;border:none}
.details-table td{padding:4px 6px;font-size:10px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:110px}
.sh{font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#111827;border-bottom:1.5px solid #111827;padding-bottom:3px;margin:15px 0 8px 0}
.test-item{display:flex;align-items:center;gap:8px;padding:6px 10px;border:1px solid #e5e7eb;border-radius:4px;margin-bottom:4px;background:#f9fafb}
.tno{border:1px solid #111827;color:#111827;width:18px;height:18px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:9px;font-weight:700;flex-shrink:0}
.tname{font-weight:600;font-size:10px;color:#111827}
.tdept{font-size:8px;color:#6b7280;margin-left:4px}
.spec-tag{background:#f3f4f6;border:1px solid #d1d5db;border-radius:3px;padding:1px 5px;font-size:8px;font-weight:600;color:#374151;margin-left:auto}
.end-report{text-align:center;margin:25px 0 10px;font-size:9.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7.5px;color:#9ca3af;line-height:1.5}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:150px;margin-left:auto;padding-top:4px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">DIAGNOSTIC ORDER</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Patient Name</span>: #{data.patientName}</td>
      <td style="width: 50%"><span class="lbl">Age / Sex</span>: #{data.patientAge} / #{data.patientGender}</td>
    </tr>
    <tr>
      <td><span class="lbl">Patient ID</span>: #{data.patientNumber}</td>
      <td><span class="lbl">Order No / Date</span>: #{data.sequenceNumber} &nbsp;|&nbsp; #{data.orderDate}</td>
    </tr>
  </table>

  <div class="sh">Ordered Tests</div>
  <div class="tests-container">
    #{data.orderLines}
  </div>

  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn"><div>Order Date: #{data.orderDate}</div></div>
    <div class="sig">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 25px;">For #{profile.name}</div>
      <div class="sig-line">Authorised Signatory</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 9. DISCHARGE_SUMMARY (Discharge Summary)
    -- ============================================================
    t_discharge := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:6mm 8mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:12px;border-bottom:1.5px solid #111827}
.header-table td{padding:6px 0;vertical-align:middle;border:none}
.logo-td{width:80px;padding-right:15px;vertical-align:middle;border:none}
.logo-img{max-height:55px;max-width:80px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:16px;font-weight:700;text-transform:uppercase;color:#111827;letter-spacing:0.5px}
.h-sub{font-size:9px;color:#4b5563;line-height:1.4;margin-top:3px}
.document-title{text-align:center;font-size:13px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:10px 0 15px 0;letter-spacing:1px}
.details-table{width:100%;margin-bottom:12px;border-collapse:collapse;border:none}
.details-table td{padding:4px 6px;font-size:10px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:110px}
.section{margin-bottom:12px}
.s-title{background:#f3f4f6;color:#111827;padding:5px 10px;font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:0.5px;border-radius:3px 3px 0 0}
.s-body{border:1px solid #d1d5db;border-top:none;padding:8px;font-size:9.5px;line-height:1.6;min-height:30px;white-space:pre-wrap;border-radius:0 0 3px 3px;color:#1f2937}
.end-report{text-align:center;margin:25px 0 10px;font-size:9.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7.5px;color:#9ca3af;line-height:1.5}
.sig{text-align:center;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:130px;margin:0 auto;padding-top:4px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">DISCHARGE SUMMARY</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Patient Name</span>: #{data.patient.fullName}</td>
      <td style="width: 50%"><span class="lbl">Age / Sex</span>: #{data.patient.age} / #{data.patient.gender}</td>
    </tr>
    <tr>
      <td><span class="lbl">Patient ID</span>: #{data.patient.patientNumber}</td>
      <td><span class="lbl">Blood Group</span>: #{data.patient.bloodGroup}</td>
    </tr>
    <tr>
      <td><span class="lbl">Date of Admission</span>: #{data.admissionDate}</td>
      <td><span class="lbl">Date of Discharge</span>: #{data.dischargeDate}</td>
    </tr>
    <tr>
      <td><span class="lbl">Ward / Bed</span>: #{data.ward} / #{data.bedNumber}</td>
      <td><span class="lbl">Consultant</span>: #{data.consultant.name}</td>
    </tr>
  </table>

  <div class="section"><div class="s-title">Diagnosis</div><div class="s-body">#{data.diagnosis}</div></div>
  
  <table style="width:100%; border:none; margin: 0; border-collapse: collapse;">
    <tr>
      <td style="width: 50%; padding: 0 5px 0 0; border: none; vertical-align: top;">
        <div class="section"><div class="s-title">Chief Complaints</div><div class="s-body">#{data.complaints}</div></div>
      </td>
      <td style="width: 50%; padding: 0 0 0 5px; border: none; vertical-align: top;">
        <div class="section"><div class="s-title">Past History</div><div class="s-body">#{data.pastHistory}</div></div>
      </td>
    </tr>
  </table>

  <div class="section"><div class="s-title">Treatment</div><div class="s-body">#{data.treatment}</div></div>
  <div class="section"><div class="s-title">Condition on Discharge</div><div class="s-body">#{data.conditionOnDischarge}</div></div>
  <div class="section"><div class="s-title">Advice / Follow-Up</div><div class="s-body">#{data.adviceOnDischarge}</div></div>

  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div></div>
    <div class="sig" style="width: 50%; text-align: left;">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 25px;">#{data.consultant.name}</div>
      <div class="sig-line" style="margin-left: 0;">Consultant Signature</div>
    </div>
    <div class="sig" style="width: 50%; text-align: right;">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 25px;">For #{profile.name}</div>
      <div class="sig-line" style="margin-left: auto;">Authorised Signatory</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 10. REFUND_RECEIPT (Refund Receipt)
    -- ============================================================
    t_refund := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:6mm 8mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:12px;border-bottom:1.5px solid #111827}
.header-table td{padding:6px 0;vertical-align:middle;border:none}
.logo-td{width:80px;padding-right:15px;vertical-align:middle;border:none}
.logo-img{max-height:55px;max-width:80px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:16px;font-weight:700;text-transform:uppercase;color:#111827;letter-spacing:0.5px}
.h-sub{font-size:9px;color:#4b5563;line-height:1.4;margin-top:3px}
.document-title{text-align:center;font-size:13px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:10px 0 15px 0;letter-spacing:1px}
.details-table{width:100%;margin-bottom:12px;border-collapse:collapse;border:none}
.details-table td{padding:4px 6px;font-size:10px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:90px}
.sh{font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#111827;border-bottom:1.5px solid #111827;padding-bottom:3px;margin:15px 0 8px 0}
table.receipt-table{width:100%;border-collapse:collapse;margin-bottom:10px}
table.receipt-table thead tr{background:#f3f4f6;border-top:1px solid #d1d5db;border-bottom:1px solid #d1d5db}
table.receipt-table thead th{padding:6px 8px;font-size:8.5px;font-weight:700;text-transform:uppercase;color:#111827;text-align:left}
table.receipt-table thead th.r{text-align:right}
table.receipt-table tbody tr{border-bottom:1px solid #e5e7eb}
table.receipt-table tbody td{padding:6px 8px;font-size:9.5px;color:#111827;vertical-align:middle}
table.receipt-table tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
.words{font-size:9px;color:#4b5563;font-style:italic;margin-top:8px;padding:5px 8px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:4px}
.end-report{text-align:center;margin:25px 0 10px;font-size:9.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7.5px;color:#9ca3af;line-height:1.5}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:150px;margin-left:auto;padding-top:4px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">REFUND RECEIPT</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Patient Name</span>: #{data.patient.fullName}</td>
      <td style="width: 50%"><span class="lbl">Patient ID</span>: #{data.patient.patientNumber}</td>
    </tr>
    <tr>
      <td><span class="lbl">Refund No</span>: <span style="font-family:'DM Mono',monospace">#{data.refundNumber}</span></td>
      <td><span class="lbl">Bill No</span>: #{data.billNumber}</td>
    </tr>
    <tr>
      <td><span class="lbl">Refund Mode</span>: #{data.paymentMode}</td>
      <td><span class="lbl">Refund Date</span>: #{date}</td>
    </tr>
  </table>

  <div class="sh">Refund Information</div>
  <table class="receipt-table">
    <thead>
      <tr>
        <th>Reason</th>
        <th class="r">Refund Amount (&#8377;)</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>#{data.reason}</td>
        <td class="r" style="font-family:'DM Mono',monospace;font-weight:700">&#8377; #{data.amount}</td>
      </tr>
    </tbody>
  </table>

  <div class="words">Refund Amount in Words: #{numberToString} Only</div>
  
  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div></div>
    <div class="sig">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 25px;">For #{profile.name}</div>
      <div class="sig-line">Authorised Signatory</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 11. ADVANCE_REFUND_RECEIPT (Advance Refund Receipt)
    -- ============================================================
    t_advance_refund := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:6mm 8mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:12px;border-bottom:1.5px solid #111827}
.header-table td{padding:6px 0;vertical-align:middle;border:none}
.logo-td{width:80px;padding-right:15px;vertical-align:middle;border:none}
.logo-img{max-height:55px;max-width:80px;display:block;object-fit:contain}
.hospital-info-td{text-align:left;vertical-align:middle;border:none}
.h-name{font-size:16px;font-weight:700;text-transform:uppercase;color:#111827;letter-spacing:0.5px}
.h-sub{font-size:9px;color:#4b5563;line-height:1.4;margin-top:3px}
.document-title{text-align:center;font-size:13px;font-weight:bold;text-transform:uppercase;text-decoration:underline;color:#111827;margin:10px 0 15px 0;letter-spacing:1px}
.details-table{width:100%;margin-bottom:12px;border-collapse:collapse;border:none}
.details-table td{padding:4px 6px;font-size:10px;vertical-align:top;border:none;color:#111827}
.details-table td .lbl{font-weight:700;color:#4b5563;display:inline-block;min-width:90px}
.sh{font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#111827;border-bottom:1.5px solid #111827;padding-bottom:3px;margin:15px 0 8px 0}
table.receipt-table{width:100%;border-collapse:collapse;margin-bottom:10px}
table.receipt-table thead tr{background:#f3f4f6;border-top:1px solid #d1d5db;border-bottom:1px solid #d1d5db}
table.receipt-table thead th{padding:6px 8px;font-size:8.5px;font-weight:700;text-transform:uppercase;color:#111827;text-align:left}
table.receipt-table thead th.r{text-align:right}
table.receipt-table tbody tr{border-bottom:1px solid #e5e7eb}
table.receipt-table tbody td{padding:6px 8px;font-size:9.5px;color:#111827;vertical-align:middle}
table.receipt-table tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
.words{font-size:9px;color:#4b5563;font-style:italic;margin-top:8px;padding:5px 8px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:4px}
.end-report{text-align:center;margin:25px 0 10px;font-size:9.5px;color:#4b5563;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7.5px;color:#9ca3af;line-height:1.5}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:150px;margin-left:auto;padding-top:4px;color:#4b5563;font-weight:600}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td class="hospital-info-td">
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> Contact : #{profile.contactNo}</div>
      </td>
    </tr>
  </table>
  
  <div class="document-title">ADVANCE REFUND RECEIPT</div>

  <table class="details-table">
    <tr>
      <td style="width: 50%"><span class="lbl">Patient Name</span>: #{data.patient.fullName}</td>
      <td style="width: 50%"><span class="lbl">Patient ID</span>: #{data.patient.patientNumber}</td>
    </tr>
    <tr>
      <td><span class="lbl">Refund No</span>: <span style="font-family:'DM Mono',monospace">#{data.refundNumber}</span></td>
      <td><span class="lbl">Bill No</span>: #{data.billNumber}</td>
    </tr>
    <tr>
      <td><span class="lbl">Refund Mode</span>: #{data.paymentMode}</td>
      <td><span class="lbl">Refund Date</span>: #{date}</td>
    </tr>
  </table>

  <div class="sh">Refund Information</div>
  <table class="receipt-table">
    <thead>
      <tr>
        <th>Reason</th>
        <th class="r">Refund Amount (&#8377;)</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>#{data.reason}</td>
        <td class="r" style="font-family:'DM Mono',monospace;font-weight:700">&#8377; #{data.amount}</td>
      </tr>
    </tbody>
  </table>

  <div class="words">Refund Amount in Words: #{numberToString} Only</div>
  
  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div></div>
    <div class="sig">
      <div style="font-size: 8px; color: #4b5563; margin-bottom: 25px;">For #{profile.name}</div>
      <div class="sig-line">Authorised Signatory</div>
    </div>
  </div>
</div></body></html>$T$;

    -- ============================================================
    -- 12. PATIENT_ID card (86x55mm)
    -- ============================================================
    t_patient_id := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&display=swap');
@import url('https://fonts.googleapis.com/css2?family=Libre+Barcode+39&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Outfit',sans-serif;background:#fff}
.card{width:80mm;height:49mm;padding:3mm;background:#fff;color:#111827;border:1px solid #111827;border-radius:3mm;overflow:hidden;position:relative}
.top{display:flex;align-items:center;margin-bottom:2mm;border-bottom:1px solid #e5e7eb;padding-bottom:1.5mm}
.logo-img{height:20px;max-width:30px;object-fit:contain;margin-right:2mm}
.h-name{font-size:8px;font-weight:700;text-transform:uppercase;color:#111827}
.h-sub{font-size:5px;color:#4b5563;line-height:1.2}
.id-badge{background:#f3f4f6;border-radius:1.5mm;padding:0.5mm 1.5mm;font-size:5.5px;font-weight:600;letter-spacing:0.5px;text-transform:uppercase;margin-left:auto;color:#111827}
.p-name{font-size:11px;font-weight:800;margin:0.5mm 0;letter-spacing:-.2px}
.pid{font-size:8.5px;font-family:monospace;background:#f3f4f6;display:inline-block;padding:0.5mm 1.5mm;border-radius:1mm;margin-bottom:0.5mm;color:#111827}
.info-row{display:flex;gap:3mm;margin-top:0.5mm}
.ii label{font-size:5px;font-weight:700;text-transform:uppercase;letter-spacing:.3px;color:#4b5563;display:block}
.ii span{font-size:8px;font-weight:600;color:#111827}
.bottom{position:absolute;bottom:2mm;left:3mm;right:3mm;border-top:1px solid #e5e7eb;padding-top:1mm;display:flex;justify-content:space-between;align-items:center}
.contact{font-size:5px;color:#4b5563;line-height:1.2}
.bc-container{display:inline-flex;align-items:center;background:#fff;padding:0 4px;border-radius:0.6mm;height:6mm;justify-content:center;flex-shrink:0;overflow:hidden;border:0.5px solid #d1d5db}
.bc-barcode{font-family:'Libre Barcode 39',sans-serif;font-size:20px;color:#000;line-height:1;margin:0;padding:0;white-space:nowrap;display:block}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body>
<div class="card">
  <div class="top">
    <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.style.display='none'" />
    <div>
      <div class="h-name">#{profile.name}</div>
      <div class="h-sub">#{profile.address}</div>
    </div>
    <div class="id-badge">Patient</div>
  </div>
  <div class="p-name">#{data.fullName}</div>
  <div class="pid">#{data.patientNumber}</div>
  <div class="info-row">
    <div class="ii"><label>Age</label><span>#{data.age}</span></div>
    <div class="ii"><label>Gender</label><span>#{data.gender}</span></div>
    <div class="ii"><label>Blood</label><span>#{data.bloodGroup}</span></div>
  </div>
  <div class="bottom">
    <div class="contact">Contact: #{profile.contactNo}</div>
    <div class="bc-container">
      <div class="bc-barcode">*#{data.patientNumber}*</div>
    </div>
  </div>
</div>
</body></html>$T$;

    -- ── Update print templates content ───────────────────────────────────────────
    UPDATE print_templates SET content = t_bill WHERE document_type = 'BILL';
    UPDATE print_templates SET content = t_receipt WHERE document_type = 'OP_RECEIPT';
    UPDATE print_templates SET content = t_ip_receipt WHERE document_type = 'IP_RECEIPT';
    UPDATE print_templates SET content = t_ip_bill WHERE document_type = 'IP_BILL_CONSOLIDATED';
    UPDATE print_templates SET content = t_sales WHERE document_type = 'SALES';
    UPDATE print_templates SET content = t_lab WHERE document_type = 'LAB';
    UPDATE print_templates SET content = t_radiology WHERE document_type = 'RADIOLOGY';
    UPDATE print_templates SET content = t_diag_order WHERE document_type = 'DIAGNOSTIC_ORDER';
    UPDATE print_templates SET content = t_discharge WHERE document_type = 'DISCHARGE_SUMMARY';
    UPDATE print_templates SET content = t_refund WHERE document_type = 'REFUND_RECEIPT';
    UPDATE print_templates SET content = t_advance_refund WHERE document_type = 'ADVANCE_REFUND_RECEIPT';
    UPDATE print_templates SET content = t_patient_id WHERE document_type = 'PATIENT_ID';

END $$;
