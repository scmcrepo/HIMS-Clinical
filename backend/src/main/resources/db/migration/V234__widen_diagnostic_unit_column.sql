-- ---------------------------------------------------------------------------
--  V234 — Widen unit column in lab_template_details and diagnostic_templates
--
--  Certain clinical lab units contain descriptive multi-analyte or qualitative
--  specifications such as "Qualitative (mg/dL for bilirubin, mg/dL for urobilinogen)"
--  which exceed 50 characters. Widen from VARCHAR(50) to VARCHAR(255).
-- ---------------------------------------------------------------------------

ALTER TABLE lab_template_details ALTER COLUMN unit TYPE VARCHAR(255);
ALTER TABLE diagnostic_templates ALTER COLUMN unit TYPE VARCHAR(255);
