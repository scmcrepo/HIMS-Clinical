-- V172__update_receipt_print_templates_minimal.sql

DO $$
DECLARE
    t_receipt TEXT;
    t_refund TEXT;
BEGIN
    -- 1. Minimal OP/IP Receipt Template (no charges tables, formatted for A5 Portrait, with inline base64 logo)
    t_receipt := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:4mm 5mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:10px;border-bottom:1px solid #d1d5db}
.header-table td{padding:4px 0;vertical-align:middle;border:none}
.logo-td{width:70px;padding-right:10px;vertical-align:middle;border:none}
.logo-img{max-height:40px;max-width:70px;display:block;object-fit:contain}
.h-name{font-size:13px;font-weight:700;text-transform:uppercase}
.h-sub{font-size:8px;color:#4b5563;line-height:1.4;margin-top:2px}
.bill-title{text-align:right}
.bill-title h1{font-size:12px;font-weight:700;text-transform:uppercase;color:#111827}
.bill-no{font-family:'DM Mono',monospace;font-size:8.5px;color:#4b5563;margin-top:2px}
.details-table{width:100%;margin-bottom:8px;border:none}
.details-table td{padding:3px;font-size:9.5px;vertical-align:top;border:none}
.details-table td label{font-size:7px;font-weight:700;text-transform:uppercase;color:#6b7280;display:block;margin-bottom:1px}
.details-table td span{font-size:10px;font-weight:600}
.sh{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#374151;border-bottom:1px solid #d1d5db;padding-bottom:2px;margin-bottom:5px;margin-top:5px}
table.receipt-table{width:100%;border-collapse:collapse;margin-bottom:8px}
table.receipt-table thead tr{background:#f9fafb;border-bottom:1px solid #d1d5db}
table.receipt-table thead th{padding:4px 5px;font-size:7.5px;font-weight:700;text-transform:uppercase;color:#374151;text-align:left}
table.receipt-table thead th.r{text-align:right}
table.receipt-table tbody tr{border-bottom:1px solid #e5e7eb}
table.receipt-table tbody td{padding:5px;font-size:9px;color:#111827;vertical-align:middle}
table.receipt-table tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
.words{font-size:8.5px;color:#4b5563;font-style:italic;margin-top:4px;padding:4px 6px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:4px}
.end-report{text-align:center;margin:15px 0 5px;font-size:8.5px;color:#9ca3af;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:6px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7px;color:#9ca3af;line-height:1.4}
.sig{text-align:right;font-size:8px}
.sig-line{border-top:1px solid #d1d5db;width:100px;margin-left:auto;padding-top:2px;color:#6b7280}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td>
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> #{profile.contactNo}</div>
      </td>
      <td class="bill-title" style="width:160px">
        <h1>Payment Receipt</h1>
        <div class="bill-no">Bill No: #{data.billNumber} <br/> Date: #{data.billDate}</div>
      </td>
    </tr>
  </table>

  <table class="details-table">
    <tr>
      <td style="width:50%"><label>Patient Name</label><span>#{data.patient.fullName}</span></td>
      <td style="width:50%"><label>Patient ID</label><span>#{data.patient.patientNumber}</span></td>
    </tr>
    <tr>
      <td><label>Gender</label><span>#{data.patient.gender}</span></td>
      <td><label>Consultant</label><span>#{data.consultant.name}</span></td>
    </tr>
    <tr>
      <td><label>Receipt No</label><span style="font-family:'DM Mono',monospace">#{data.receiptNumber}</span></td>
      <td><label>Receipt Date</label><span>#{data.paymentDate}</span></td>
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
        <td class="r" style="font-family:'DM Mono',monospace;font-weight:700;color:#dc2626">&#8377; #{data.balance}</td>
      </tr>
    </tbody>
  </table>

  <div class="words">Received Amount in Words: #{numberToString} Only</div>
  
  <div class="end-report">-- End of report --</div>

  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div><div>This is a computer-generated receipt.</div></div>
    <div class="sig"><div class="sig-line">Authorised Signatory</div><div style="font-size:7px;color:#9ca3af;margin-top:2px">#{profile.name}</div></div>
  </div>
</div></body></html>$T$;

    -- 2. Minimal Refund Template (formatted for A5 Portrait, with inline base64 logo)
    t_refund := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:10px;color:#111827;background:#fff;padding:4mm 5mm}
.page{display:flex;flex-direction:column;min-height:100%}
.header-table{width:100%;border-collapse:collapse;margin-bottom:10px;border-bottom:1px solid #d1d5db}
.header-table td{padding:4px 0;vertical-align:middle;border:none}
.logo-td{width:70px;padding-right:10px;vertical-align:middle;border:none}
.logo-img{max-height:40px;max-width:70px;display:block;object-fit:contain}
.h-name{font-size:13px;font-weight:700;text-transform:uppercase}
.h-sub{font-size:8px;color:#4b5563;line-height:1.4;margin-top:2px}
.bill-title{text-align:right}
.bill-title h1{font-size:12px;font-weight:700;text-transform:uppercase;color:#dc2626}
.bill-no{font-family:'DM Mono',monospace;font-size:8.5px;color:#4b5563;margin-top:2px}
.details-table{width:100%;margin-bottom:8px;border:none}
.details-table td{padding:3px;font-size:9.5px;vertical-align:top;border:none}
.details-table td label{font-size:7px;font-weight:700;text-transform:uppercase;color:#6b7280;display:block;margin-bottom:1px}
.details-table td span{font-size:10px;font-weight:600}
.sh{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#374151;border-bottom:1px solid #d1d5db;padding-bottom:2px;margin-bottom:5px;margin-top:5px}
.row-container{margin-bottom:8px;border:1px solid #e5e7eb;border-radius:6px;overflow:hidden}
.row{display:flex;justify-content:space-between;padding:6px 10px;border-bottom:1px solid #f3f4f6;font-size:9.5px}
.row label{color:#6b7280}.row span{font-weight:600;font-family:'DM Mono',monospace}
.grand{display:flex;justify-content:space-between;padding:10px;font-size:12px;font-weight:700;background:#fef2f2;color:#dc2626;border-top:1.5px solid #fca5a5}
.words{font-size:9px;color:#4b5563;font-style:italic;margin-top:4px;padding:5px 8px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:4px}
.end-report{text-align:center;margin:15px 0 5px;font-size:9px;color:#9ca3af;font-weight:bold;letter-spacing:1px}
.footer{margin-top:auto;padding-top:6px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:7px;color:#9ca3af;line-height:1.4}
.sig{text-align:right;font-size:8px}
.sig-line{border-top:1px solid #d1d5db;width:100px;margin-left:auto;padding-top:2px;color:#6b7280}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <table class="header-table">
    <tr>
      <td class="logo-td">
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display='none'" />
      </td>
      <td>
        <div class="h-name">#{profile.name}</div>
        <div class="h-sub">#{profile.address} <br/> #{profile.contactNo}</div>
      </td>
      <td class="bill-title" style="width:160px">
        <h1>Refund Receipt</h1>
        <div class="bill-no">Bill No: #{data.billNumber} <br/> Date: #{date}</div>
      </td>
    </tr>
  </table>

  <table class="details-table">
    <tr>
      <td style="width:50%"><label>Patient Name</label><span>#{data.patient.fullName}</span></td>
      <td style="width:50%"><label>Patient ID</label><span>#{data.patient.patientNumber}</span></td>
    </tr>
    <tr>
      <td colspan="2"><label>Refund No</label><span style="font-family:'DM Mono',monospace">#{data.refundNumber}</span></td>
    </tr>
  </table>

  <div class="sh">Refund Information</div>
  <div class="row-container">
    <div class="row"><label>Refund Method</label><span>#{data.paymentMode}</span></div>
    <div class="row"><label>Reason for Refund</label><span style="font-family:inherit">#{data.reason}</span></div>
    <div class="grand"><span>Refund Amount Issued</span><span>&#8377; #{data.amount}</span></div>
  </div>

  <div class="words">Refund Amount in Words: #{numberToString} Only</div>
  
  <div class="end-report">-- End of report --</div>

  <div class="footer">
    <div class="fn"><div>Generated: #{date}</div><div>This is a computer-generated receipt.</div></div>
    <div class="sig"><div class="sig-line">Authorised Signatory</div><div style="font-size:7px;color:#9ca3af;margin-top:2px">#{profile.name}</div></div>
  </div>
</div></body></html>$T$;

    -- Update print template configuration properties to A5 Portrait
    UPDATE print_templates 
    SET page_size = 'A5', 
        width = '148mm', 
        height = '210mm', 
        margin_top = '6mm', 
        margin_bottom = '6mm', 
        margin_left = '8mm', 
        margin_right = '8mm',
        content = t_receipt 
    WHERE document_type IN ('OP_RECEIPT', 'IP_RECEIPT');
    
    -- Update refund templates properties to A5 Portrait
    UPDATE print_templates 
    SET page_size = 'A5', 
        width = '148mm', 
        height = '210mm', 
        margin_top = '6mm', 
        margin_bottom = '6mm', 
        margin_left = '8mm', 
        margin_right = '8mm',
        content = t_refund 
    WHERE document_type IN ('REFUND_RECEIPT', 'ADVANCE_REFUND_RECEIPT');

END $$;
