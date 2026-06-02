import re

file_path = "/home/ssb/Desktop/Hms-complete/backend/src/main/java/com/hms/application/report/ReportDataService.java"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("sequence_numbers", "number_sequences")
content = content.replace(".formatted_value", ".value")
content = content.replace(".entity_id", ".id")

# Fix R06 query specifically
r06_old = """ce.visit_mode                               AS visit_type,
                ce.status                                   AS encounter_status,"""
r06_new = """CASE ce.visit_mode WHEN 0 THEN 'OUTPATIENT' WHEN 1 THEN 'INPATIENT' ELSE 'OTHER' END AS visit_type,
                CASE ce.status WHEN 1 THEN 'ACTIVE' WHEN 2 THEN 'DISCHARGED' ELSE ce.status::text END AS encounter_status,"""
content = content.replace(r06_old, r06_new)

with open(file_path, "w") as f:
    f.write(content)
