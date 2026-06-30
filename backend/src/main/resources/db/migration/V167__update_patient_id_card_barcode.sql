-- V167__update_patient_id_card_barcode.sql
-- Update all existing print templates for PATIENT_ID card to use Libre Barcode 39 font and container.

UPDATE print_templates
SET content = '<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url(''https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700;800&display=swap'');
@import url(''https://fonts.googleapis.com/css2?family=Libre+Barcode+39&display=swap'');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:''Outfit'',sans-serif;background:#fff}
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
.bc-container{display:inline-flex;align-items:center;background:#fff;padding:0 6px;border-radius:0.8mm;height:8mm;justify-content:center;flex-shrink:0;overflow:hidden}
.bc-barcode{font-family:''Libre Barcode 39'',sans-serif;font-size:26px;color:#000;line-height:1;margin:0;padding:0;white-space:nowrap;display:block}
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
  <div class="bottom">
    <div class="contact">#{profile.contactNo}</div>
    <div class="bc-container">
      <div class="bc-barcode">*#{data.patientNumber}*</div>
    </div>
  </div>
</div>
</body></html>'
WHERE document_type = 'PATIENT_ID';
