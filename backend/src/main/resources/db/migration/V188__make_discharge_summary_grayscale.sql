DO $$
DECLARE
    t_discharge TEXT;
BEGIN
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

  #{data.dynamicFieldsHtml}

  <div class="end-report">--End of report--</div>

  <div class="footer">
    <div class="fn">Generated: #{system.currentDate}<br/>Returns accepted within 7 days with bill.</div>
    <div class="sig">
      <div class="sig-line">Consultant Signature</div>
    </div>
    <div class="sig">
      <div class="sig-line">Authorised Signatory</div>
    </div>
  </div>
</div></body></html>$T$;

    UPDATE print_templates SET content = t_discharge WHERE document_type = 'DISCHARGE_SUMMARY';

END $$;
