UPDATE print_templates
SET content = '<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url(''https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap'');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:''DM Sans'',sans-serif;font-size:9.5px;color:#111827;background:#fff;padding:4mm 5mm}
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
table.items-table tbody td.r{text-align:right;font-family:''DM Mono'',monospace;font-weight:600}
.totals-table{margin-left:auto;width:fit-content;min-width:140px;border:1px solid #d1d5db;border-radius:4px;border-collapse:collapse;overflow:hidden;margin-top:6px;margin-bottom:6px}
.totals-table td{padding:3px 8px;font-size:9px;border-bottom:1px solid #f3f4f6}
.totals-table td.lbl{color:#4b5563;text-align:right;padding-right:12px}
.totals-table td.val{font-family:''DM Mono'',monospace;font-weight:600;text-align:right;width:70px}
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
        <img class="logo-img" src="#{profile.logoDataUrl}" onerror="this.parentNode.style.display=''none''" />
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
        <th>S.NO</th>
        <th>Item</th>
        <th class="c" style="width: 10%">Qty</th>
        <th class="r" style="width: 15%">RATE (&#8377;)</th>
        <th class="r" style="width: 15%">DISCOUNT (&#8377;)</th>
        <th class="r" style="width: 15%">AMOUNT (&#8377;)</th>
      </tr>
    </thead>
    <tbody>
      #{data.saleLines}
    </tbody>
  </table>

  <table class="totals-table">
    <tr>
      <td class="lbl">Sub Total</td>
      <td class="val">&#8377; #{data.subTotal}</td>
    </tr>
    <tr>
      <td class="lbl">Discount</td>
      <td class="val">&minus; &#8377; #{data.discountAmount}</td>
    </tr>
    <tr>
      <td class="lbl">SGST</td>
      <td class="val">&#8377; #{data.sgstAmount}</td>
    </tr>
    <tr>
      <td class="lbl">CGST</td>
      <td class="val">&#8377; #{data.cgstAmount}</td>
    </tr>
    <tr>
      <td class="lbl">Paid (#{data.paymentMode})</td>
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
</div></body></html>'
WHERE document_type = 'SALES';
