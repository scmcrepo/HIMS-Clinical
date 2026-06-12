-- V098__update_discharge_print_template_label.sql
-- Change Ward / Bed label to Bedtype / Bed and make fields dynamic using #{data.dynamicFieldsHtml} in DISCHARGE_SUMMARY print template

UPDATE print_templates
SET content = $$<!DOCTYPE html>
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
    <div class="pg-cell"><label>Bedtype / Bed</label><span>#{data.ward} / #{data.bedNumber}</span></div>
    <div class="pg-cell"><label>Consultant</label><span>#{data.consultant.name}</span></div>
  </div>
  #{data.dynamicFieldsHtml}
  <div class="foot">
    <div class="sig"><div class="sig-line">Consultant Signature</div><div class="sig-sub">#{data.consultant.name}</div></div>
    <div class="sig"><div class="sig-line">Authorised Signatory</div><div class="sig-sub">#{profile.name}</div></div>
  </div>
</div></body></html>$$
WHERE document_type = 'DISCHARGE_SUMMARY';
