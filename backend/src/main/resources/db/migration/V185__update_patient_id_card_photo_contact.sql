-- V184: Update PATIENT_ID card to include patient photo and contact number
DO $$ 
DECLARE
    t_patient_id TEXT;
BEGIN
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
.content-row{display:flex;gap:2.5mm;align-items:flex-start}
.photo-box{width:16mm;height:18mm;border-radius:1.5mm;border:1px solid #d1d5db;overflow:hidden;flex-shrink:0;background:#f3f4f6;display:flex;align-items:center;justify-content:center}
.photo-box img{width:100%;height:100%;object-fit:cover}
.photo-placeholder{width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:#e5e7eb}
.photo-placeholder svg{width:10mm;height:10mm;opacity:0.4}
.details{flex:1;min-width:0}
.p-name{font-size:11px;font-weight:800;margin:0 0 0.3mm 0;letter-spacing:-.2px;line-height:1.2}
.pid{font-size:8.5px;font-family:monospace;background:#f3f4f6;display:inline-block;padding:0.5mm 1.5mm;border-radius:1mm;margin-bottom:0.5mm;color:#111827}
.info-row{display:flex;gap:3mm;margin-top:0.5mm}
.ii label{font-size:5px;font-weight:700;text-transform:uppercase;letter-spacing:.3px;color:#4b5563;display:block}
.ii span{font-size:8px;font-weight:600;color:#111827}
.bottom{position:absolute;bottom:2mm;left:3mm;right:3mm;border-top:1px solid #e5e7eb;padding-top:1mm;display:flex;justify-content:space-between;align-items:center}
.contact{font-size:5.5px;color:#111827;line-height:1.2}
.contact-label{font-weight:700;color:#4b5563}
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
  <div class="content-row">
    <div class="photo-box">
      <img src="#{data.photoUrl}" onerror="this.style.display='none';this.parentNode.querySelector('.photo-placeholder').style.display='flex'" />
      <div class="photo-placeholder" style="display:none">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="#9ca3af"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/></svg>
      </div>
    </div>
    <div class="details">
      <div class="p-name">#{data.fullName}</div>
      <div class="pid">#{data.patientNumber}</div>
      <div class="info-row">
        <div class="ii"><label>Age</label><span>#{data.age}</span></div>
        <div class="ii"><label>Gender</label><span>#{data.gender}</span></div>
        <div class="ii"><label>Blood</label><span>#{data.bloodGroup}</span></div>
      </div>
    </div>
  </div>
  <div class="bottom">
    <div class="contact"><span class="contact-label">Contact:</span> #{data.contactNumber}</div>
    <div class="bc-container">
      <div class="bc-barcode">*#{data.patientNumber}*</div>
    </div>
  </div>
</div>
</body></html>$T$;

    UPDATE print_templates SET content = t_patient_id WHERE document_type = 'PATIENT_ID';

END $$;
