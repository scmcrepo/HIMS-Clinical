-- V087__update_radiology_print_template.sql
UPDATE print_templates 
SET content = $T$<!DOCTYPE html>
<html><head><meta charset="utf-8">
<style>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap');
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:'Plus Jakarta Sans',sans-serif;font-size:11px;color:#1e293b;background:#fff}
.page{padding:14mm 12mm 10mm;min-height:273mm;display:flex;flex-direction:column}
.hdr{display:flex;justify-content:space-between;align-items:flex-start;padding-bottom:12px;margin-bottom:12px;border-bottom:2px solid #4f46e5}
.lh{border-left:4px solid #4f46e5;padding-left:12px}
.hosp-name{font-size:18px;font-weight:800;color:#1e1b4b}.hosp-sub{font-size:8.5px;color:#64748b;text-transform:uppercase;letter-spacing:.5px;margin-top:3px}
.rh{text-align:right}.report-title{font-size:20px;font-weight:800;color:#4f46e5;text-transform:uppercase;letter-spacing:1px}
.report-no{font-family:'JetBrains Mono',monospace;font-size:10.5px;color:#6366f1;font-weight:600;margin-top:3px}
.ps{display:grid;grid-template-columns:repeat(4,1fr);gap:8px 12px;background:#f5f3ff;border:1px solid #ddd6fe;border-radius:8px;padding:10px 14px;margin-bottom:14px}
.ps-i label{font-size:7.5px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:#6d28d9;display:block;margin-bottom:1px}
.ps-i span{font-size:11px;font-weight:600;color:#3b0764}
.section{margin-bottom:14px}
.s-hdr{font-size:9.5px;font-weight:800;text-transform:uppercase;letter-spacing:1px;color:#4f46e5;border-bottom:2px solid #ddd6fe;padding-bottom:4px;margin-bottom:8px}
.s-body{font-size:11.5px;line-height:1.75;color:#334155;padding:2px 0 6px;min-height:30px}
.imp-body{font-size:12px;font-weight:600;line-height:1.75;color:#0f172a;background:#faf5ff;border-radius:6px;padding:12px 16px;min-height:30px;border-left:4px solid #8b5cf6;border-right:1px solid #f3e8ff;border-top:1px solid #f3e8ff;border-bottom:1px solid #f3e8ff}
.foot{margin-top:auto;padding-top:10px;border-top:1px solid #e2e8f0;display:flex;justify-content:space-between;align-items:flex-end}
.sig{text-align:center}
.sig-name{font-size:11px;font-weight:700;border-top:1px solid #1e293b;padding-top:4px;display:inline-block;min-width:130px;color:#1e293b}
.sig-sub{font-size:8px;color:#64748b;margin-top:2px}
.foot-note{font-size:8px;color:#94a3b8;line-height:1.7}
@media print{body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
</style></head>
<body><div class="page">
  <div class="hdr">
    <div class="lh"><div class="hosp-name">#{profile.name}</div><div class="hosp-sub">#{profile.address} &nbsp;|&nbsp; #{profile.contactNo}</div></div>
    <div class="rh"><div class="report-title">Radiology Report</div><div class="report-no">#{data.sequenceNumber}</div><div style="font-size:9.5px;color:#64748b;margin-top:3px;font-weight:500;">Date: #{date}</div></div>
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
</div></body></html>$T$
WHERE document_type = 'RADIOLOGY';
