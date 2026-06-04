-- V078__seed_discharge_summary_template.sql
-- Seed default discharge summary template under GENERAL specialization

DO $$
DECLARE
  v_dc UUID := gen_random_uuid();
BEGIN
  -- Insert the DISCHARGE template
  INSERT INTO case_sheet_templates(id, name, specialization, visit_type, description, is_default)
  VALUES(v_dc, 'DISCHARGE', 'GENERAL', 'IP', 'Default Inpatient Discharge Summary Template', TRUE);

  -- Insert the 5 fields of the template: DIAGNOSIS, COMPLAINTS, ON EXAMINATION, OPINION, TREATMENT
  INSERT INTO case_sheet_template_fields
    (template_id, field_key, label, field_type, section, display_order, is_required, placeholder, help_text, options, validation)
  VALUES
    (v_dc, 'diagnosis', 'DIAGNOSIS', 'TEXTAREA', 'Discharge Summary Details', 10, FALSE, 'Enter diagnosis details...', NULL, NULL, NULL),
    (v_dc, 'complaints', 'COMPLAINTS', 'TEXTAREA', 'Discharge Summary Details', 20, FALSE, 'Enter patient complaints...', NULL, NULL, NULL),
    (v_dc, 'examination', 'ON EXAMINATION', 'TEXTAREA', 'Discharge Summary Details', 30, FALSE, 'Enter examination findings...', NULL, NULL, NULL),
    (v_dc, 'opinion', 'OPINION', 'TEXTAREA', 'Discharge Summary Details', 40, FALSE, 'Enter clinical opinion...', NULL, NULL, NULL),
    (v_dc, 'treatment', 'TREATMENT', 'TEXTAREA', 'Discharge Summary Details', 50, FALSE, 'Enter treatment given...', NULL, NULL, NULL);
END $$;
