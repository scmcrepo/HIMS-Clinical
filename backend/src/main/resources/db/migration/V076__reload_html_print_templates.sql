-- V076: Reloads production-quality HTML print templates with proper dynamic data bindings.
DELETE FROM print_templates WHERE document_type IN (
  'BILL', 'OP_RECEIPT', 'IP_RECEIPT', 'IP_BILL_CONSOLIDATED', 'SALES', 'LAB', 'RADIOLOGY', 'DIAGNOSTIC_ORDER', 'DISCHARGE_SUMMARY', 'REFUND_RECEIPT', 'ADVANCE_REFUND_RECEIPT', 'PATIENT_ID'
);

DO $$
DECLARE t_bill TEXT; t_receipt TEXT; t_ip_receipt TEXT; t_sales TEXT;
        t_lab TEXT; t_radiology TEXT; t_diag_order TEXT; t_discharge TEXT;
        t_ip_bill TEXT; t_refund TEXT; t_advance_refund TEXT; t_patient_id TEXT;
BEGIN

-- ============================================================
-- 1. BILL  (A4)
-- ============================================================
t_bill := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:11px;color:#111827;background:#fff}
.page{padding:14mm 12mm 12mm;min-height:273mm;display:flex;flex-direction:column}
.top-bar{background:#111827;color:#fff;padding:10px 14px;border-radius:6px;display:flex;justify-content:space-between;align-items:center;margin-bottom:10px}
.h-name{font-size:16px;font-weight:700}.h-sub{font-size:8px;color:#9ca3af;margin-top:2px}
.bill-title{text-align:right}.bill-title h1{font-size:20px;font-weight:700}
.bill-no{font-family:'DM Mono',monospace;font-size:10px;color:#d1d5db;margin-top:2px}
.strip{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:6px;padding:8px 12px;margin-bottom:10px}
.pi label{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.4px;color:#6b7280;display:block;margin-bottom:1px}
.pi span{font-size:11px;font-weight:600}
.sh{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#6b7280;border-bottom:1px solid #f3f4f6;padding-bottom:3px;margin-bottom:4px}
table{width:100%;border-collapse:collapse;margin-bottom:8px}
thead tr{background:#f3f4f6}
thead th{padding:6px 8px;font-size:8px;font-weight:700;text-transform:uppercase;color:#374151;text-align:left;border-bottom:1.5px solid #e5e7eb}
thead th.r{text-align:right}
tbody tr{border-bottom:1px solid #f9fafb}
tbody td{padding:5px 8px;font-size:10px;color:#374151;vertical-align:middle}
tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
tbody td.muted{color:#9ca3af;font-size:9px;font-family:'DM Mono',monospace}
.totals{margin-left:auto;width:220px;border:1px solid #e5e7eb;border-radius:6px;overflow:hidden}
.trow{display:flex;justify-content:space-between;padding:5px 10px;border-bottom:1px solid #f3f4f6;font-size:10px}
.trow .lbl{color:#6b7280}.trow .val{font-family:'DM Mono',monospace;font-weight:600}
.trow.grand{background:#111827;color:#fff;border:none;padding:8px 10px;font-size:13px;font-weight:700}
.words{font-size:9px;color:#6b7280;font-style:italic;margin-top:6px;padding:4px 8px;background:#f9fafb;border-radius:4px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:8px;color:#9ca3af;line-height:1.7}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:130px;margin-left:auto;padding-top:4px;color:#6b7280}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="top-bar">
    <div><div class="h-name">#{profile.name}</div><div class="h-sub">#{profile.address} &nbsp;|&nbsp; #{profile.contactNo}</div></div>
    <div class="bill-title"><h1>Tax Invoice</h1><div class="bill-no">#{data.billNumber} &nbsp;|&nbsp; #{data.billDate}</div></div>
  </div>
  <div class="strip">
    <div class="pi"><label>Patient Name</label><span>#{data.patient.fullName}</span></div>
    <div class="pi"><label>Patient ID</label><span>#{data.patient.patientNumber}</span></div>
    <div class="pi"><label>Consultant</label><span>#{data.consultant.name}</span></div>
    <div class="pi"><label>Encounter</label><span>#{data.encounterType}</span></div>
    <div class="pi"><label>Bill Type</label><span>#{data.billType}</span></div>
    <div class="pi"><label>Status</label><span>#{data.status}</span></div>
  </div>
  <div class="sh">Charges</div>
  <table>
    <thead><tr><th style="width:26px">#</th><th>Service / Item</th><th style="width:50px;text-align:center">Qty</th><th class="r" style="width:80px">Rate (&#8377;)</th><th class="r" style="width:80px">Discount (&#8377;)</th><th class="r" style="width:90px">Amount (&#8377;)</th></tr></thead>
    <tbody>#{data.chargeLines}</tbody>
  </table>
  #{data.paymentsTable}
  <div class="totals">
    <div class="trow"><span class="lbl">Gross Total</span><span class="val">&#8377; #{data.billAmount}</span></div>
    <div class="trow"><span class="lbl">Discount</span><span class="val">&minus; &#8377; #{data.discountTotal}</span></div>
    <div class="trow"><span class="lbl">Paid</span><span class="val">&#8377; #{data.paymentTotal}</span></div>
    <div class="trow grand"><span>Balance Due</span><span class="val">&#8377; #{data.dueAmount}</span></div>
  </div>
  <div class="words">Amount in Words: #{numberToString} Only</div>
  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div><div>This is a computer-generated invoice.</div></div>
    <div class="sig"><div class="sig-line">Authorised Signatory</div><div style="font-size:8px;color:#9ca3af;margin-top:2px">#{profile.name}</div></div>
  </div>
</div></body></html>$T$;

-- ============================================================
-- 2. OP_RECEIPT  (A5)
-- ============================================================
t_receipt := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:11px;color:#111827;background:#fff}
.page{padding:14mm 12mm 12mm;min-height:273mm;display:flex;flex-direction:column}
.top-bar{background:#111827;color:#fff;padding:10px 14px;border-radius:6px;display:flex;justify-content:space-between;align-items:center;margin-bottom:10px}
.h-name{font-size:16px;font-weight:700}.h-sub{font-size:8px;color:#9ca3af;margin-top:2px}
.bill-title{text-align:right}.bill-title h1{font-size:20px;font-weight:700}
.bill-no{font-family:'DM Mono',monospace;font-size:10px;color:#d1d5db;margin-top:2px}
.strip{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:6px;padding:8px 12px;margin-bottom:10px}
.pi label{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.4px;color:#6b7280;display:block;margin-bottom:1px}
.pi span{font-size:11px;font-weight:600}
.sh{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#6b7280;border-bottom:1px solid #f3f4f6;padding-bottom:3px;margin-bottom:4px}
table{width:100%;border-collapse:collapse;margin-bottom:8px}
thead tr{background:#f3f4f6}
thead th{padding:6px 8px;font-size:8px;font-weight:700;text-transform:uppercase;color:#374151;text-align:left;border-bottom:1.5px solid #e5e7eb}
thead th.r{text-align:right}
tbody tr{border-bottom:1px solid #f9fafb}
tbody td{padding:5px 8px;font-size:10px;color:#374151;vertical-align:middle}
tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
tbody td.muted{color:#9ca3af;font-size:9px;font-family:'DM Mono',monospace}
.totals{margin-left:auto;width:220px;border:1px solid #e5e7eb;border-radius:6px;overflow:hidden}
.trow{display:flex;justify-content:space-between;padding:5px 10px;border-bottom:1px solid #f3f4f6;font-size:10px}
.trow .lbl{color:#6b7280}.trow .val{font-family:'DM Mono',monospace;font-weight:600}
.trow.grand{background:#111827;color:#fff;border:none;padding:8px 10px;font-size:13px;font-weight:700}
.words{font-size:9px;color:#6b7280;font-style:italic;margin-top:6px;padding:4px 8px;background:#f9fafb;border-radius:4px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:8px;color:#9ca3af;line-height:1.7}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:130px;margin-left:auto;padding-top:4px;color:#6b7280}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="top-bar">
    <div><div class="h-name">#{profile.name}</div><div class="h-sub">#{profile.address} &nbsp;|&nbsp; #{profile.contactNo}</div></div>
    <div class="bill-title"><h1>OP Receipt</h1><div class="bill-no">Bill: #{data.billNumber} &nbsp;|&nbsp; #{data.billDate}</div></div>
  </div>
  <div class="strip">
    <div class="pi"><label>Patient Name</label><span>#{data.patient.fullName}</span></div>
    <div class="pi"><label>Patient ID</label><span>#{data.patient.patientNumber}</span></div>
    <div class="pi"><label>Consultant</label><span>#{data.consultant.name}</span></div>
    <div class="pi"><label>Gender</label><span>#{data.patient.gender}</span></div>
    <div class="pi"><label>Receipt No</label><span style="font-family:'DM Mono',monospace">#{data.receiptNumber}</span></div>
    <div class="pi"><label>Receipt Date</label><span>#{data.paymentDate}</span></div>
  </div>
  <div class="sh">Charges Included</div>
  <table>
    <thead><tr><th style="width:26px">#</th><th>Service / Item</th><th style="width:50px;text-align:center">Qty</th><th class="r" style="width:80px">Rate (&#8377;)</th><th class="r" style="width:80px">Discount (&#8377;)</th><th class="r" style="width:90px">Amount (&#8377;)</th></tr></thead>
    <tbody>#{data.chargeLines}</tbody>
  </table>
  
  <div class="sh">Receipt Information</div>
  <table>
    <thead>
      <tr>
        <th>Receipt Date</th>
        <th>Receipt No</th>
        <th>Mode of Pay</th>
        <th class="r">Previous Paid (&#8377;)</th>
        <th class="r">Amount Collected (&#8377;)</th>
        <th class="r">Balance Due (&#8377;)</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>#{data.paymentDate}</td>
        <td style="font-family:'DM Mono',monospace">#{data.receiptNumber}</td>
        <td>#{data.paymentMode}</td>
        <td class="r" style="font-family:'DM Mono',monospace">&#8377; #{data.previousPaid}</td>
        <td class="r" style="font-family:'DM Mono',monospace;font-weight:700">&#8377; #{data.amount}</td>
        <td class="r" style="font-family:'DM Mono',monospace;font-weight:700;color:#dc2626">&#8377; #{data.balance}</td>
      </tr>
    </tbody>
  </table>

  <div class="words">Received Amount in Words: #{numberToString} Only</div>
  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div><div>This is a computer-generated receipt.</div></div>
    <div class="sig"><div class="sig-line">Authorised Signatory</div><div style="font-size:8px;color:#9ca3af;margin-top:2px">#{profile.name}</div></div>
  </div>
</div></body></html>$T$;

-- ============================================================
-- 3. IP_RECEIPT  (A5)
-- ============================================================
t_ip_receipt := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:11px;color:#111827;background:#fff}
.page{padding:14mm 12mm 12mm;min-height:273mm;display:flex;flex-direction:column}
.top-bar{background:#111827;color:#fff;padding:10px 14px;border-radius:6px;display:flex;justify-content:space-between;align-items:center;margin-bottom:10px}
.h-name{font-size:16px;font-weight:700}.h-sub{font-size:8px;color:#9ca3af;margin-top:2px}
.bill-title{text-align:right}.bill-title h1{font-size:20px;font-weight:700}
.bill-no{font-family:'DM Mono',monospace;font-size:10px;color:#d1d5db;margin-top:2px}
.strip{display:grid;grid-template-columns:repeat(3,1fr);gap:6px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:6px;padding:8px 12px;margin-bottom:10px}
.pi label{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.4px;color:#6b7280;display:block;margin-bottom:1px}
.pi span{font-size:11px;font-weight:600}
.sh{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#6b7280;border-bottom:1px solid #f3f4f6;padding-bottom:3px;margin-bottom:4px}
table{width:100%;border-collapse:collapse;margin-bottom:8px}
thead tr{background:#f3f4f6}
thead th{padding:6px 8px;font-size:8px;font-weight:700;text-transform:uppercase;color:#374151;text-align:left;border-bottom:1.5px solid #e5e7eb}
thead th.r{text-align:right}
tbody tr{border-bottom:1px solid #f9fafb}
tbody td{padding:5px 8px;font-size:10px;color:#374151;vertical-align:middle}
tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
tbody td.muted{color:#9ca3af;font-size:9px;font-family:'DM Mono',monospace}
.totals{margin-left:auto;width:220px;border:1px solid #e5e7eb;border-radius:6px;overflow:hidden}
.trow{display:flex;justify-content:space-between;padding:5px 10px;border-bottom:1px solid #f3f4f6;font-size:10px}
.trow .lbl{color:#6b7280}.trow .val{font-family:'DM Mono',monospace;font-weight:600}
.trow.grand{background:#111827;color:#fff;border:none;padding:8px 10px;font-size:13px;font-weight:700}
.words{font-size:9px;color:#6b7280;font-style:italic;margin-top:6px;padding:4px 8px;background:#f9fafb;border-radius:4px}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:8px;color:#9ca3af;line-height:1.7}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:130px;margin-left:auto;padding-top:4px;color:#6b7280}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="top-bar">
    <div><div class="h-name">#{profile.name}</div><div class="h-sub">#{profile.address} &nbsp;|&nbsp; #{profile.contactNo}</div></div>
    <div class="bill-title"><h1>IP Receipt</h1><div class="bill-no">Bill: #{data.billNumber} &nbsp;|&nbsp; #{data.billDate}</div></div>
  </div>
  <div class="strip">
    <div class="pi"><label>Patient Name</label><span>#{data.patient.fullName}</span></div>
    <div class="pi"><label>Patient ID</label><span>#{data.patient.patientNumber}</span></div>
    <div class="pi"><label>Consultant</label><span>#{data.consultant.name}</span></div>
    <div class="pi"><label>Gender</label><span>#{data.patient.gender}</span></div>
    <div class="pi"><label>Receipt No</label><span style="font-family:'DM Mono',monospace">#{data.receiptNumber}</span></div>
    <div class="pi"><label>Receipt Date</label><span>#{data.paymentDate}</span></div>
  </div>
  <div class="sh">Charges Included</div>
  <table>
    <thead><tr><th style="width:26px">#</th><th>Service / Item</th><th style="width:50px;text-align:center">Qty</th><th class="r" style="width:80px">Rate (&#8377;)</th><th class="r" style="width:80px">Discount (&#8377;)</th><th class="r" style="width:90px">Amount (&#8377;)</th></tr></thead>
    <tbody>#{data.chargeLines}</tbody>
  </table>
  
  <div class="sh">Receipt Information</div>
  <table>
    <thead>
      <tr>
        <th>Receipt Date</th>
        <th>Receipt No</th>
        <th>Mode of Pay</th>
        <th class="r">Previous Paid (&#8377;)</th>
        <th class="r">Amount Collected (&#8377;)</th>
        <th class="r">Balance Due (&#8377;)</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td>#{data.paymentDate}</td>
        <td style="font-family:'DM Mono',monospace">#{data.receiptNumber}</td>
        <td>#{data.paymentMode}</td>
        <td class="r" style="font-family:'DM Mono',monospace">&#8377; #{data.previousPaid}</td>
        <td class="r" style="font-family:'DM Mono',monospace;font-weight:700">&#8377; #{data.amount}</td>
        <td class="r" style="font-family:'DM Mono',monospace;font-weight:700;color:#dc2626">&#8377; #{data.balance}</td>
      </tr>
    </tbody>
  </table>

  <div class="words">Received Amount in Words: #{numberToString} Only</div>
  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div><div>This is a computer-generated receipt.</div></div>
    <div class="sig"><div class="sig-line">Authorised Signatory</div><div style="font-size:8px;color:#9ca3af;margin-top:2px">#{profile.name}</div></div>
  </div>
</div></body></html>$T$;

-- ============================================================
-- 4. SALES — Pharmacy POS receipt  (A5)
-- ============================================================
t_sales := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Inconsolata:wght@400;500;600;700&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Inconsolata',monospace;font-size:11px;color:#111;background:#fff}
.page{padding:8mm}
.hdr{text-align:center;margin-bottom:6px}
.hosp{font-size:14px;font-weight:700}.sub{font-size:9px;color:#777;margin-top:1px}
hr{border:none;border-top:1px dashed #888;margin:5px 0}
.title{text-align:center;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:2px;margin:3px 0}
.meta{display:grid;grid-template-columns:1fr 1fr;gap:2px 8px;margin-bottom:5px}
.mi{font-size:9px}.mi .lbl{color:#888}
table{width:100%;border-collapse:collapse}
.th{font-size:9px;font-weight:700;text-transform:uppercase;border-bottom:1.5px solid #111;padding:3px 4px}
.th.r{text-align:right}
td{padding:3px 4px;font-size:10px;border-bottom:1px dotted #ddd;vertical-align:top}
td.r{text-align:right;font-weight:600}
.trow{display:flex;justify-content:space-between;padding:3px 2px;font-size:10px}
.grand-row{display:flex;justify-content:space-between;padding:6px 2px;font-size:13px;font-weight:700;border-top:2px solid #111;margin-top:2px}
.foot{text-align:center;margin-top:8px;font-size:9px;color:#888}
.barcode{border:1px solid #eee;background:#f9f9f9;text-align:center;padding:4px;margin-top:6px;font-size:8px;color:#bbb;letter-spacing:3px}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="hdr">
    <div class="hosp">#{profile.name}</div>
    <div class="sub">#{profile.address}</div>
    <div class="sub">#{profile.contactNo}</div>
  </div>
  <hr><div class="title">Sale Receipt</div><hr>
  <div class="meta">
    <div class="mi"><span class="lbl">Sale No: </span><strong>#{data.sequenceNumber}</strong></div>
    <div class="mi"><span class="lbl">Date: </span><strong>#{data.saleDate}</strong></div>
    <div class="mi"><span class="lbl">Patient: </span><strong>#{data.patientName}</strong></div>
    <div class="mi"><span class="lbl">Dr: </span><strong>#{data.consultantName}</strong></div>
  </div>
  <hr>
  <table>
    <tr><th class="th">#</th><th class="th">Item</th><th class="th" style="text-align:center">Qty</th><th class="th r">Rate</th><th class="th r">Amt</th></tr>
    #{data.saleLines}
  </table>
  <hr>
  <div class="trow"><span>Sub Total</span><span>&#8377; #{data.totalAmount}</span></div>
  <div class="trow"><span>Discount</span><span>&minus; &#8377; #{data.discountAmount}</span></div>
  <div class="trow"><span>Mode: #{data.paymentMode}</span><span>&#8377; #{data.paidAmount}</span></div>
  <div class="grand-row"><span>Total</span><span>&#8377; #{data.totalAmount}</span></div>
  <hr>
  <div class="foot">
    <div>#{numberToString} Only</div>
    <div style="margin-top:2px">Returns accepted within 7 days with bill</div>
    <div style="margin-top:4px;font-size:8px">#{date}</div>
  </div>
  <div class="barcode">|| #{data.sequenceNumber} ||</div>
</div></body></html>$T$;

-- ============================================================
-- 5. LAB  (A4)
-- ============================================================
t_lab := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Source+Serif+4:wght@400;600;700&family=JetBrains+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Source Serif 4',Georgia,serif;font-size:11px;color:#0f172a;background:#fff}
.page{padding:14mm 12mm 10mm;min-height:273mm}
.hdr{display:flex;justify-content:space-between;align-items:flex-start;padding-bottom:10px;margin-bottom:10px;border-bottom:2px solid #0f172a}
.lh{border-left:4px solid #2563eb;padding-left:10px}
.hosp-name{font-size:17px;font-weight:700}.hosp-sub{font-size:8px;color:#64748b;text-transform:uppercase;letter-spacing:.4px;margin-top:2px}
.rh{text-align:right}.report-title{font-size:20px;font-weight:700;color:#2563eb}
.report-no{font-family:'JetBrains Mono',monospace;font-size:10px;color:#94a3b8;margin-top:2px}
.pc-grid{background:#eff6ff;border:1px solid #bfdbfe;border-radius:7px;padding:9px 13px;margin-bottom:12px;display:grid;grid-template-columns:repeat(4,1fr);gap:7px}
.pc label{font-size:7.5px;font-weight:700;text-transform:uppercase;letter-spacing:.4px;color:#3b82f6;display:block;margin-bottom:1px}
.pc span{font-size:11px;font-weight:600;color:#1e3a8a}
.dept-bar{background:#1e40af;color:#fff;padding:5px 12px;font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:1px;border-radius:4px;margin:10px 0 4px}
table{width:100%;border-collapse:collapse}
thead tr{background:#dbeafe}
thead th{padding:6px 8px;font-size:7.5px;font-weight:700;text-transform:uppercase;color:#1e3a8a;text-align:left}
thead th.c{text-align:center}
tbody tr{border-bottom:1px solid #e0f2fe}
tbody td{padding:6px 8px;font-size:10px}
.tname{font-weight:600;color:#0f172a}
.val{text-align:center;font-family:'JetBrains Mono',monospace;font-weight:700;font-size:11px}
.unit{text-align:center;color:#94a3b8;font-size:9px;font-family:'JetBrains Mono',monospace}
.range{text-align:center;color:#64748b;font-size:9px}
.flag{display:inline-block;width:14px;height:14px;border-radius:50%;font-size:7px;font-weight:700;text-align:center;line-height:14px}
.fn{background:#dcfce7;color:#15803d}.fh{background:#fee2e2;color:#dc2626}.fl{background:#fef3c7;color:#92400e}
.foot{border-top:1px solid #dbeafe;padding-top:10px;margin-top:14px;display:flex;justify-content:space-between;align-items:flex-end}
.foot-note{font-size:8px;color:#94a3b8;line-height:1.7}
.sig{text-align:center}
.sig-line{border-top:1px solid #94a3b8;width:110px;margin:0 auto;padding-top:4px;font-size:9px;color:#64748b}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="hdr">
    <div class="lh"><div class="hosp-name">#{profile.name}</div><div class="hosp-sub">#{profile.address} &nbsp;|&nbsp; #{profile.contactNo}</div></div>
    <div class="rh"><div class="report-title">Lab Report</div><div class="report-no">#{data.sequenceNumber}</div><div style="font-size:9px;color:#94a3b8;margin-top:2px">#{data.orderDate}</div></div>
  </div>
  <div class="pc-grid">
    <div class="pc"><label>Patient</label><span>#{data.patientName}</span></div>
    <div class="pc"><label>Patient ID</label><span>#{data.patientNumber}</span></div>
    <div class="pc"><label>Age / Gender</label><span>#{data.patientAge} / #{data.patientGender}</span></div>
    <div class="pc"><label>Referred By</label><span>#{data.consultantName}</span></div>
    <div class="pc"><label>Order No</label><span>#{data.sequenceNumber}</span></div>
    <div class="pc"><label>Sample Date</label><span>#{data.sampleDate}</span></div>
    <div class="pc"><label>Report Date</label><span>#{date}</span></div>
    <div class="pc"><label>Department</label><span>#{data.department}</span></div>
  </div>
  <div class="dept-bar">Investigation Results</div>
  <table>
    <thead><tr><th>Test Name</th><th class="c">Result</th><th class="c">Unit</th><th class="c">Ref Range</th></tr></thead>
    <tbody>#{data.resultLines}</tbody>
  </table>
  <div class="foot">
    <div class="foot-note"><div>&#42; H=High L=Low N=Normal &nbsp;|&nbsp; Report valid 30 days</div><div>Queries: #{profile.contactNo}</div></div>
    <div class="sig"><div class="sig-line">Lab Technician</div><div style="font-size:8px;color:#94a3b8;margin-top:3px">#{profile.name}</div></div>
  </div>
</div></body></html>$T$;

-- ============================================================
-- 6. RADIOLOGY  (A4)
-- ============================================================
t_radiology := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Syne:wght@400;600;700;800&family=Fira+Code:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Syne',sans-serif;font-size:11px;color:#0f172a;background:#fff}
.page{padding:14mm 12mm 10mm;min-height:273mm}
.top{background:#0f172a;color:#fff;padding:11px 16px;border-radius:7px;display:flex;justify-content:space-between;align-items:center;margin-bottom:11px}
.h-name{font-size:15px;font-weight:700}.h-sub{font-size:8px;color:#94a3b8;margin-top:2px}
.badge{background:#3b82f6;border-radius:6px;padding:6px 12px;text-align:center}
.badge h1{font-size:12px;font-weight:800;text-transform:uppercase;letter-spacing:1px}
.badge .rno{font-family:'Fira Code',monospace;font-size:9px;color:#bfdbfe;margin-top:2px}
.ps{display:grid;grid-template-columns:1fr 1fr;gap:5px 14px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;padding:9px 13px;margin-bottom:11px}
.ps-i label{font-size:7.5px;font-weight:700;text-transform:uppercase;letter-spacing:.4px;color:#94a3b8}
.ps-i span{font-size:11px;font-weight:600;display:block;margin-top:1px}
.section{margin-bottom:9px}
.s-hdr{font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:1px;color:#3b82f6;border-bottom:2px solid #3b82f6;padding-bottom:3px;margin-bottom:5px}
.s-body{font-size:11px;line-height:1.8;color:#334155;background:#f8fafc;border-radius:5px;padding:9px;min-height:30px;border-left:3px solid #e2e8f0}
.imp-body{font-size:12px;font-weight:600;line-height:1.7;color:#0f172a;background:#eff6ff;border-radius:5px;padding:9px;min-height:30px;border-left:3px solid #3b82f6}
.foot{margin-top:10px;padding-top:9px;border-top:1px solid #e2e8f0;display:flex;justify-content:space-between;align-items:flex-end}
.sig{text-align:center}
.sig-name{font-size:11px;font-weight:700;border-top:1px solid #0f172a;padding-top:4px;display:inline-block;min-width:110px}
.sig-sub{font-size:8px;color:#94a3b8}
.foot-note{font-size:8px;color:#94a3b8;line-height:1.7}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="top">
    <div><div class="h-name">#{profile.name}</div><div class="h-sub">#{profile.address} &nbsp;|&nbsp; #{profile.contactNo}</div></div>
    <div class="badge"><h1>Radiology Report</h1><div class="rno">#{data.sequenceNumber}</div></div>
  </div>
  <div class="ps">
    <div class="ps-i"><label>Patient Name</label><span>#{data.patientName}</span></div>
    <div class="ps-i"><label>Patient ID</label><span>#{data.patientNumber}</span></div>
    <div class="ps-i"><label>Age / Gender</label><span>#{data.patientAge} / #{data.patientGender}</span></div>
    <div class="ps-i"><label>Referring Doctor</label><span>#{data.consultantName}</span></div>
    <div class="ps-i"><label>Study</label><span>#{data.department}</span></div>
    <div class="ps-i"><label>Date</label><span>#{date}</span></div>
  </div>
  <div class="section"><div class="s-hdr">Findings</div><div class="s-body">#{data.resultLines}</div></div>
  <div class="section"><div class="s-hdr">Impression</div><div class="imp-body">To be filled by radiologist.</div></div>
  <div class="foot">
    <div class="foot-note"><div>Report: #{date}</div><div>#{profile.name} | #{profile.contactNo}</div></div>
    <div class="sig"><div class="sig-name">#{data.consultantName}</div><div class="sig-sub">Radiologist</div></div>
  </div>
</div></body></html>$T$;

-- ============================================================
-- 7. DIAGNOSTIC_ORDER  (A5)
-- ============================================================
t_diag_order := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Plus Jakarta Sans',sans-serif;font-size:10px;color:#111;background:#fff}
.page{padding:8mm}
.hdr{display:flex;justify-content:space-between;align-items:center;border-bottom:2px solid #111;padding-bottom:6px;margin-bottom:8px}
.h-name{font-size:13px;font-weight:800}.h-sub{font-size:8px;color:#777;margin-top:1px}
.obadge{background:#111;color:#fff;padding:5px 10px;border-radius:5px;text-align:center}
.obadge .lbl{font-size:8px;font-weight:700;letter-spacing:1px;text-transform:uppercase}
.obadge .no{font-size:12px;font-weight:800;font-family:monospace;margin-top:1px}
.pb{display:grid;grid-template-columns:1fr 1fr;gap:4px;border:1.5px solid #e0e0e0;border-radius:6px;padding:7px 10px;margin-bottom:8px}
.pb-i label{font-size:8px;color:#888;font-weight:600;text-transform:uppercase;letter-spacing:.3px}
.pb-i span{font-size:10px;font-weight:700;display:block}
.tests-lbl{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#888;margin-bottom:4px}
.test-item{display:flex;align-items:center;gap:6px;padding:5px 8px;border:1px solid #e8e8e8;border-radius:4px;margin-bottom:3px}
.tno{background:#111;color:#fff;width:18px;height:18px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:9px;font-weight:700;flex-shrink:0}
.tname{font-weight:600;font-size:10px}.tdept{font-size:8px;color:#888}
.spec-tag{background:#fef3c7;border:1px solid #fbbf24;border-radius:3px;padding:1px 5px;font-size:8px;font-weight:600;color:#92400e;margin-left:auto}
.foot{margin-top:8px;font-size:8px;color:#999;display:flex;justify-content:space-between;border-top:1px dashed #ddd;padding-top:5px}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="hdr">
    <div><div class="h-name">#{profile.name}</div><div class="h-sub">#{profile.address}</div></div>
    <div class="obadge"><div class="lbl">Order No</div><div class="no">#{data.sequenceNumber}</div></div>
  </div>
  <div class="pb">
    <div class="pb-i"><label>Patient</label><span>#{data.patientName}</span></div>
    <div class="pb-i"><label>Patient ID</label><span>#{data.patientNumber}</span></div>
    <div class="pb-i"><label>Age / Gender</label><span>#{data.patientAge} / #{data.patientGender}</span></div>
    <div class="pb-i"><label>Date</label><span>#{data.orderDate}</span></div>
  </div>
  <div class="tests-lbl">Ordered Tests</div>
  #{data.orderLines}
  <div class="foot"><span>Order Date: #{data.orderDate}</span><span>#{profile.name}</span></div>
</div></body></html>$T$;

-- ============================================================
-- 8. DISCHARGE_SUMMARY  (A4)
-- ============================================================
t_discharge := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Lora:wght@400;500;600;700&family=Space+Mono&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Lora',Georgia,serif;font-size:11px;color:#1a1a1a;background:#fff}
.page{padding:14mm 12mm 10mm}
.hdr{text-align:center;border-bottom:3px solid #1a1a1a;padding-bottom:10px;margin-bottom:12px}
.h-name{font-size:19px;font-weight:700}.h-sub{font-size:9px;color:#777;margin-top:3px}
.doc-title{font-size:15px;font-weight:700;text-transform:uppercase;letter-spacing:2px;margin-top:8px}
.pg{display:grid;grid-template-columns:1fr 1fr;border:1.5px solid #ccc;border-radius:4px;margin-bottom:12px;overflow:hidden}
.pg-cell{padding:5px 9px;border-bottom:1px solid #e8e8e8;border-right:1px solid #e8e8e8}
.pg-cell:nth-child(even){border-right:none}
.pg-cell label{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.4px;color:#888;display:block;margin-bottom:2px}
.pg-cell span{font-size:11px;font-weight:600}
.section{margin-bottom:9px}
.s-title{background:#1a1a1a;color:#fff;padding:4px 10px;font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:1px;border-radius:3px 3px 0 0}
.s-body{border:1px solid #ddd;border-top:none;padding:7px 9px;font-size:11px;line-height:1.8;min-height:28px;white-space:pre-line;border-radius:0 0 3px 3px}
.two-col{display:grid;grid-template-columns:1fr 1fr;gap:9px}
.foot{border-top:2px solid #1a1a1a;padding-top:10px;margin-top:12px;display:grid;grid-template-columns:1fr 1fr;gap:9px}
.sig{text-align:center;padding-top:22px}
.sig-line{border-top:1px solid #999;padding-top:4px;font-size:10px;font-weight:600;display:inline-block;min-width:110px}
.sig-sub{font-size:8px;color:#888;margin-top:2px}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="hdr">
    <div class="h-name">#{profile.name}</div>
    <div class="h-sub">#{profile.address} &nbsp;|&nbsp; #{profile.contactNo}</div>
    <div class="doc-title">Discharge Summary</div>
  </div>
  <div class="pg">
    <div class="pg-cell"><label>Patient Name</label><span>#{data.patient.fullName}</span></div>
    <div class="pg-cell"><label>Patient ID</label><span>#{data.patient.patientNumber}</span></div>
    <div class="pg-cell"><label>Age / Gender</label><span>#{data.patient.age} / #{data.patient.gender}</span></div>
    <div class="pg-cell"><label>Blood Group</label><span>#{data.patient.bloodGroup}</span></div>
    <div class="pg-cell"><label>Date of Admission</label><span>#{data.admissionDate}</span></div>
    <div class="pg-cell"><label>Date of Discharge</label><span>#{data.dischargeDate}</span></div>
    <div class="pg-cell"><label>Ward / Bed</label><span>#{data.ward} / #{data.bedNumber}</span></div>
    <div class="pg-cell"><label>Consultant</label><span>#{data.consultant.name}</span></div>
  </div>
  <div class="section"><div class="s-title">Diagnosis</div><div class="s-body">#{data.diagnosis}</div></div>
  <div class="two-col">
    <div class="section"><div class="s-title">Chief Complaints</div><div class="s-body">#{data.complaints}</div></div>
    <div class="section"><div class="s-title">Past History</div><div class="s-body">#{data.pastHistory}</div></div>
  </div>
  <div class="section"><div class="s-title">Treatment</div><div class="s-body">#{data.treatment}</div></div>
  <div class="section"><div class="s-title">Condition on Discharge</div><div class="s-body">#{data.conditionOnDischarge}</div></div>
  <div class="section"><div class="s-title">Advice / Follow-Up</div><div class="s-body">#{data.adviceOnDischarge}</div></div>
  <div class="foot">
    <div class="sig"><div class="sig-line">Consultant Signature</div><div class="sig-sub">#{data.consultant.name}</div></div>
    <div class="sig"><div class="sig-line">Authorised Signatory</div><div class="sig-sub">#{profile.name}</div></div>
  </div>
</div></body></html>$T$;

-- ============================================================
-- 9. IP_BILL_CONSOLIDATED  (A4)
-- ============================================================
t_ip_bill := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:11px;color:#1a1a2e;background:#fff}
.page{padding:14mm 12mm 10mm}
.hdr{display:flex;justify-content:space-between;border-bottom:3px solid #1a1a2e;padding-bottom:8px;margin-bottom:10px}
.h-name{font-size:17px;font-weight:700}.h-sub{font-size:8px;color:#666;text-transform:uppercase;letter-spacing:.3px;margin-top:2px}
.bill-info{text-align:right}.bill-type{font-size:17px;font-weight:700}
.bill-no{font-family:'DM Mono',monospace;font-size:10px;color:#888;margin-top:2px}
.abar{background:#f0f0f8;border-radius:6px;padding:8px 12px;margin-bottom:10px;display:grid;grid-template-columns:repeat(4,1fr);gap:7px;border:1px solid #e0e0ee}
.ab label{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.3px;color:#888}
.ab span{font-size:11px;font-weight:600;display:block;margin-top:1px}
.cat{background:#1a1a2e;color:#fff;padding:5px 10px;font-size:9px;font-weight:700;text-transform:uppercase;letter-spacing:.7px;margin-top:7px}
table{width:100%;border-collapse:collapse}
thead th{padding:5px 8px;font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.3px;background:#f8f8fc;border-bottom:1.5px solid #e0e0ee;text-align:left;color:#666}
thead th.r{text-align:right}
tbody td{padding:5px 8px;font-size:10px;border-bottom:1px solid #f0f0f8}
tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
.summary{margin-top:10px;display:flex;justify-content:flex-end}
.s-box{width:220px;border:1px solid #e0e0ee;border-radius:6px;overflow:hidden}
.srow{display:flex;justify-content:space-between;padding:5px 10px;border-bottom:1px solid #f0f0f0;font-size:10px}
.srow .lbl{color:#666}.srow .val{font-family:'DM Mono',monospace;font-weight:600}
.srow.total{background:#1a1a2e;color:#fff;border:none;font-size:14px;font-weight:700;padding:8px 10px}
.foot{margin-top:14px;padding-top:8px;border-top:1px solid #e0e0e0;font-size:8px;color:#999;text-align:center}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="hdr">
    <div><div class="h-name">#{profile.name}</div><div class="h-sub">#{profile.address} | #{profile.contactNo}</div></div>
    <div class="bill-info"><div class="bill-type">Inpatient Bill</div><div class="bill-no">#{data.billNumber}</div><div class="bill-no">#{data.billDate}</div></div>
  </div>
  <div class="abar">
    <div class="ab"><label>Patient</label><span>#{data.patient.fullName}</span></div>
    <div class="ab"><label>Patient ID</label><span>#{data.patient.patientNumber}</span></div>
    <div class="ab"><label>Admitted</label><span>#{data.admissionDate}</span></div>
    <div class="ab"><label>Discharged</label><span>#{data.dischargeDate}</span></div>
    <div class="ab"><label>Consultant</label><span>#{data.consultant.name}</span></div>
    <div class="ab"><label>Bed</label><span>#{data.bed}</span></div>
    <div class="ab"><label>Bill Type</label><span>#{data.billType}</span></div>
    <div class="ab"><label>Status</label><span>#{data.status}</span></div>
  </div>
  <div class="cat">All Charges</div>
  <table>
    <thead><tr><th>#</th><th>Service</th><th style="text-align:center">Qty</th><th class="r">Rate</th><th class="r">Discount</th><th class="r">Amount (&#8377;)</th></tr></thead>
    <tbody>#{data.chargeLines}</tbody>
  </table>
  #{data.paymentsTable}
  <div class="summary"><div class="s-box">
    <div class="srow"><span class="lbl">Gross Total</span><span class="val">&#8377; #{data.billAmount}</span></div>
    <div class="srow"><span class="lbl">Discount</span><span class="val">&minus; &#8377; #{data.discountTotal}</span></div>
    <div class="srow"><span class="lbl">Paid</span><span class="val">&#8377; #{data.paymentTotal}</span></div>
    <div class="srow total"><span>Balance Due</span><span class="val">&#8377; #{data.dueAmount}</span></div>
  </div></div>
  <div class="foot">#{numberToString} Only &nbsp;|&nbsp; Computer-generated &nbsp;|&nbsp; #{date}</div>
</div></body></html>$T$;

-- ============================================================
-- 10. REFUND_RECEIPT  (A5)
-- ============================================================
t_refund := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=IBM+Plex+Mono&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'IBM Plex Sans',sans-serif;font-size:10px;color:#111;background:#fff}
.page{padding:10mm}
.hdr{text-align:center;border-bottom:2px solid #dc2626;padding-bottom:8px;margin-bottom:8px}
.hosp{font-size:15px;font-weight:700}.sub{font-size:8px;color:#888}
h2{font-size:14px;font-weight:700;letter-spacing:1px;text-transform:uppercase;margin:7px 0 6px;text-align:center;color:#dc2626}
.row{display:flex;justify-content:space-between;padding:4px 0;border-bottom:1px dashed #e5e7eb;font-size:10px}
.row label{color:#666}.row span{font-weight:600;font-family:'IBM Plex Mono',monospace}
.grand{display:flex;justify-content:space-between;padding:8px 0 4px;font-size:14px;font-weight:700;border-top:2px solid #dc2626;margin-top:4px;color:#dc2626}
.words{font-size:9px;color:#888;margin-top:6px;font-style:italic}
.note{text-align:center;margin-top:10px;font-size:8px;color:#aaa}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="hdr"><div class="hosp">#{profile.name}</div><div class="sub">#{profile.address} | #{profile.contactNo}</div></div>
  <h2>Refund Receipt</h2>
  <div class="row"><label>Refund No</label><span>#{data.refundNumber}</span></div>
  <div class="row"><label>Date</label><span>#{date}</span></div>
  <div class="row"><label>Patient</label><span>#{data.patient.fullName}</span></div>
  <div class="row"><label>Patient ID</label><span>#{data.patient.patientNumber}</span></div>
  <div class="row"><label>Bill Number</label><span>#{data.billNumber}</span></div>
  <div class="row"><label>Reason</label><span>#{data.reason}</span></div>
  <div class="row"><label>Refund Mode</label><span>#{data.paymentMode}</span></div>
  <div class="grand"><span>Refund Amount</span><span>&#8377; #{data.amount}</span></div>
  <div class="words">#{numberToString} Only</div>
  <div class="note">#{profile.name} &nbsp;|&nbsp; #{date}</div>
</div></body></html>$T$;

-- ============================================================
-- 11. ADVANCE_REFUND_RECEIPT  (A5) — same layout, amber accent
-- ============================================================
t_advance_refund := t_refund;  -- reuse; title text differs but model is same

-- ============================================================
-- 12. PATIENT_ID card  (86x55mm)
-- ============================================================
t_patient_id := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Outfit',sans-serif;background:#fff}
.card{width:80mm;height:49mm;padding:3mm;background:linear-gradient(135deg,#1a1a2e 0%,#16213e 60%,#0f3460 100%);color:#fff;border-radius:3mm;overflow:hidden;position:relative}
.top{display:flex;justify-content:space-between;align-items:center;margin-bottom:2mm}
.h-name{font-size:8px;font-weight:700}.id-badge{background:rgba(255,255,255,.15);border-radius:2mm;padding:1mm 2mm;font-size:6px;font-weight:600;letter-spacing:1px;text-transform:uppercase}
.p-name{font-size:12px;font-weight:800;margin:1mm 0;letter-spacing:-.3px}
.pid{font-size:9px;font-family:monospace;background:rgba(255,255,255,.12);display:inline-block;padding:1mm 2mm;border-radius:1.5mm;margin-bottom:1mm}
.info-row{display:flex;gap:4mm;margin-top:1mm}
.ii label{font-size:5px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:rgba(255,255,255,.6);display:block}
.ii span{font-size:8px;font-weight:600}
.bottom{position:absolute;bottom:2mm;left:3mm;right:3mm;border-top:1px solid rgba(255,255,255,.15);padding-top:1.5mm;display:flex;justify-content:space-between;align-items:center}
.contact{font-size:6px;color:rgba(255,255,255,.6)}
.bc{font-size:6px;color:rgba(255,255,255,.4);font-family:monospace;letter-spacing:2px}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body>
<div class="card">
  <div class="top"><div class="h-name">#{profile.name}</div><div class="id-badge">Patient</div></div>
  <div class="p-name">#{data.fullName}</div>
  <div class="pid">#{data.patientNumber}</div>
  <div class="info-row">
    <div class="ii"><label>Age</label><span>#{data.age}</span></div>
    <div class="ii"><label>Gender</label><span>#{data.gender}</span></div>
    <div class="ii"><label>Blood</label><span>#{data.bloodGroup}</span></div>
  </div>
  <div class="bottom"><div class="contact">#{profile.contactNo}</div><div class="bc">||| #{data.patientNumber} |||</div></div>
</div>
</body></html>$T$;

-- ── Insert all templates ──────────────────────────────────────────────────────
INSERT INTO print_templates (id,name,document_type,print_mode,page_size,height,width,margin_top,margin_bottom,margin_left,margin_right,content,is_default,status) VALUES
  (gen_random_uuid(),'Default OP Bill',           'BILL',                 'HTML','A4',    '297mm','210mm','10mm','10mm','12mm','12mm',t_bill,           true,1),
  (gen_random_uuid(),'Default OP Receipt',        'OP_RECEIPT',           'HTML','A4',    '297mm','210mm','10mm','10mm','12mm','12mm',t_receipt,        true,1),
  (gen_random_uuid(),'Default IP Receipt',        'IP_RECEIPT',           'HTML','A4',    '297mm','210mm','10mm','10mm','12mm','12mm',t_ip_receipt,     true,1),
  (gen_random_uuid(),'Default IP Bill',           'IP_BILL_CONSOLIDATED', 'HTML','A4',    '297mm','210mm','10mm','10mm','12mm','12mm',t_ip_bill,         true,1),
  (gen_random_uuid(),'Default Sales Receipt',     'SALES',                'HTML','A5',    '210mm','148mm','6mm', '6mm', '8mm', '8mm', t_sales,          true,1),
  (gen_random_uuid(),'Default Lab Report',        'LAB',                  'HTML','A4',    '297mm','210mm','10mm','10mm','12mm','12mm',t_lab,             true,1),
  (gen_random_uuid(),'Default Radiology Report',  'RADIOLOGY',            'HTML','A4',    '297mm','210mm','10mm','10mm','12mm','12mm',t_radiology,       true,1),
  (gen_random_uuid(),'Default Diagnostic Order',  'DIAGNOSTIC_ORDER',     'HTML','A5',    '210mm','148mm','6mm', '6mm', '8mm', '8mm', t_diag_order,     true,1),
  (gen_random_uuid(),'Default Discharge Summary', 'DISCHARGE_SUMMARY',    'HTML','A4',    '297mm','210mm','10mm','10mm','12mm','12mm',t_discharge,       true,1),
  (gen_random_uuid(),'Default Refund Receipt',    'REFUND_RECEIPT',       'HTML','A5',    '210mm','148mm','8mm', '8mm', '10mm','10mm',t_refund,          true,1),
  (gen_random_uuid(),'Default Advance Refund',    'ADVANCE_REFUND_RECEIPT','HTML','A5',   '210mm','148mm','8mm', '8mm', '10mm','10mm',t_advance_refund,  true,1),
  (gen_random_uuid(),'Default Patient ID Card',   'PATIENT_ID',           'HTML','Custom','55mm', '86mm', '3mm', '3mm', '3mm', '3mm', t_patient_id,     true,1);

END $$;
