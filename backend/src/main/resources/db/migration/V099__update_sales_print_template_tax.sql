UPDATE print_templates
SET content = '<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url(''https://fonts.googleapis.com/css2?family=Inconsolata:wght@400;500;600;700&display=swap'');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:''Inconsolata'',monospace;font-size:11px;color:#111;background:#fff}
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
    <tr>
      <th class="th" style="width:5%">#</th>
      <th class="th" style="width:35%">Item</th>
      <th class="th" style="width:10%;text-align:center">Qty</th>
      <th class="th r" style="width:12%">Rate</th>
      <th class="th r" style="width:12%">Tax %</th>
      <th class="th r" style="width:13%">Tax Val</th>
      <th class="th r" style="width:13%">Amt</th>
    </tr>
    #{data.saleLines}
  </table>
  <hr>
  <div class="trow"><span>Sub Total</span><span>&#8377; #{data.totalAmount}</span></div>
  <div class="trow"><span>Discount</span><span>&minus; &#8377; #{data.discountAmount}</span></div>
  <div class="trow"><span>SGST</span><span>&#8377; #{data.sgstAmount}</span></div>
  <div class="trow"><span>CGST</span><span>&#8377; #{data.cgstAmount}</span></div>
  <div class="trow"><span>Mode: #{data.paymentMode}</span><span>&#8377; #{data.paidAmount}</span></div>
  <div class="grand-row"><span>Total</span><span>&#8377; #{data.totalAmount}</span></div>
  <hr>
  <div class="foot">
    <div>#{numberToString} Only</div>
    <div style="margin-top:2px">Returns accepted within 7 days with bill</div>
    <div style="margin-top:4px;font-size:8px">#{date}</div>
  </div>
  <div class="barcode">|| #{data.sequenceNumber} ||</div>
</div></body></html>'
WHERE document_type = 'SALES';
