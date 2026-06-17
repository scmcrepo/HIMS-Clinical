-- V107: Add default HTML print template for Purchase Order documents.
-- Uses dollar-quoting to safely embed the HTML without escaping single quotes.
-- Margins are set to 0mm to remove browser default headers/footers.

DELETE FROM print_templates WHERE document_type = 'PURCHASE_ORDER';

DO $$
DECLARE t_po TEXT;
BEGIN

t_po := $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=DM+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'DM Sans',sans-serif;font-size:11px;color:#111827;background:#fff}
.page{padding:12mm 12mm 12mm;min-height:297mm;display:flex;flex-direction:column}
.top-bar{background:#111827;color:#fff;padding:10px 14px;border-radius:6px;display:flex;justify-content:space-between;align-items:center;margin-bottom:10px}
.h-name{font-size:16px;font-weight:700}.h-sub{font-size:8px;color:#9ca3af;margin-top:2px}
.bill-title{text-align:right}.bill-title h1{font-size:20px;font-weight:700;letter-spacing:1px}
.bill-no{font-family:'DM Mono',monospace;font-size:10px;color:#d1d5db;margin-top:2px}
.strip{display:grid;grid-template-columns:1fr 1fr;gap:15px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:6px;padding:12px 14px;margin-bottom:10px}
.col{display:flex;flex-direction:column;gap:6px}
.pi{display:flex;justify-content:space-between;align-items:center;border-bottom:1px dashed #f3f4f6;padding-bottom:4px}
.pi label{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.4px;color:#6b7280;width:120px;flex-shrink:0;text-align:left}
.pi span{font-size:10.5px;font-weight:600;color:#1f2937;text-align:left;flex-grow:1}
.sh{font-size:8px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#6b7280;border-bottom:1px solid #f3f4f6;padding-bottom:3px;margin-bottom:4px}
table{width:100%;border-collapse:collapse;margin-bottom:8px}
thead tr{background:#f3f4f6}
thead th{padding:6px 8px;font-size:8px;font-weight:700;text-transform:uppercase;color:#374151;text-align:left;border-bottom:1px solid #e5e7eb}
thead th.r{text-align:right}
tbody tr{border-bottom:1px solid #f9fafb}
tbody td{padding:5px 8px;font-size:10px;color:#374151;vertical-align:middle}
tbody td.r{text-align:right;font-family:'DM Mono',monospace;font-weight:600}
tbody td.muted{color:#9ca3af;font-size:9px;font-family:'DM Mono',monospace}
.unit-span{color:#9ca3af;font-size:9px;margin-left:2px}
.totals{margin-left:auto;width:220px;border:1px solid #e5e7eb;border-radius:6px;overflow:hidden}
.trow{display:flex;justify-content:space-between;padding:5px 10px;border-bottom:1px solid #f3f4f6;font-size:10px}
.trow .lbl{color:#6b7280}.trow .val{font-family:'DM Mono',monospace;font-weight:600}
.trow.grand{background:#111827;color:#fff;border:none;padding:8px 10px;font-size:13px;font-weight:700}
.words{font-size:9px;color:#6b7280;font-style:italic;margin-top:6px;padding:4px 8px;background:#f9fafb;border-radius:4px}
.notes-sec{margin-top:12px;padding:8px 10px;background:#f9fafb;border:1px solid #e5e7eb;border-radius:6px}
.notes-sec label{font-size:8px;font-weight:700;text-transform:uppercase;color:#6b7280;display:block;margin-bottom:4px}
.notes-sec p{font-size:10px;color:#374151;white-space:pre-wrap}
.footer{margin-top:auto;padding-top:10px;border-top:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:flex-end}
.fn{font-size:8px;color:#9ca3af;line-height:1.7}
.sig{text-align:right;font-size:9px}
.sig-line{border-top:1px solid #d1d5db;width:130px;margin-left:auto;padding-top:4px;color:#6b7280}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="top-bar">
    <div><div class="h-name">#{profile.name}</div><div class="h-sub">#{profile.address} &nbsp;|&nbsp; #{profile.contactNo}</div></div>
    <div class="bill-title"><h1>PURCHASE ORDER</h1><div class="bill-no">#{data.sequenceNumber} &nbsp;|&nbsp; #{data.orderDate}</div></div>
  </div>
  <div class="strip">
    <div class="col">
      <div class="pi"><label>Purchase Order No</label><span>#{data.sequenceNumber}</span></div>
      <div class="pi"><label>Supplier</label><span>#{data.supplier.name}</span></div>
      <div class="pi"><label>Contact Person</label><span>#{data.supplier.contactPerson}</span></div>
    </div>
    <div class="col">
      <div class="pi"><label>Date</label><span>#{data.orderDate}</span></div>
      <div class="pi"><label>Contact No</label><span>#{data.supplier.contactNo}</span></div>
      <div class="pi"><label>Address</label><span>#{data.supplier.address}</span></div>
    </div>
  </div>
  <div class="sh">Order Details</div>
  <table>
    <thead><tr><th style="width:36px">NO</th><th>ITEM NAME</th><th class="r" style="width:80px">MRP</th><th class="r" style="width:80px">PRICE</th><th style="width:80px;text-align:center">QUANTITY</th><th class="r" style="width:90px">SUB TOTAL</th></tr></thead>
    <tbody>#{data.poLines}</tbody>
  </table>
  <div class="totals">
    <div class="trow grand"><span>Total Amount</span><span class="val">#{data.totalAmount}</span></div>
  </div>
  <div class="words">Amount in Words: #{numberToString} Only</div>
  #{data.notesSection}
  <div class="footer">
    <div class="fn"><div>Generated: #{dateTime}</div><div>This is a computer-generated Purchase Order.</div></div>
    <div class="sig"><div class="sig-line">Authorised Signatory</div><div style="font-size:8px;color:#9ca3af;margin-top:2px">#{profile.name}</div></div>
  </div>
</div></body></html>$T$;

INSERT INTO print_templates (id, name, document_type, print_mode, page_size, height, width, margin_top, margin_bottom, margin_left, margin_right, content, is_default, status)
VALUES (gen_random_uuid(), 'Default Purchase Order', 'PURCHASE_ORDER', 'HTML', 'A4', '297mm', '210mm', '0mm', '0mm', '0mm', '0mm', t_po, true, 1);

END $$;
