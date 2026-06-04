-- V080__update_general_dynamic_casesheet_template.sql
-- Clear old fields and insert new ones for the 'General ' case sheet template

DO $$
DECLARE
    v_general UUID := '8da86594-78b3-4b76-95d6-391d3a72d9b2';
BEGIN
    -- Ensure the template itself exists to avoid foreign key violations
    IF NOT EXISTS (SELECT 1 FROM case_sheet_templates WHERE id = v_general) THEN
        INSERT INTO case_sheet_templates (id, name, specialization, visit_type, description, is_default)
        VALUES (v_general, 'General OP Default', 'GENERAL', 'OP', 'General outpatient case sheet template', TRUE);
    END IF;

    DELETE FROM case_sheet_template_fields WHERE template_id = v_general;

    INSERT INTO case_sheet_template_fields
      (template_id, field_key, label, field_type, section, display_order, is_required)
    VALUES
      -- ── SECTION: Chief Complaints ──────────────────────
      (v_general, 's_chief_complaints', 'Chief Complaints', 'HEADING', 'Chief Complaints', 10, FALSE),
      (v_general, 'chief_complaints', 'Chief Complaints', 'TEXTAREA', 'Chief Complaints', 20, FALSE),

      -- ── SECTION: Medical History ───────────────────────
      (v_general, 's_medical_history', 'Medical History', 'HEADING', 'Medical History', 30, FALSE),
      (v_general, 'previous_history', 'Previous History', 'TEXTAREA', 'Medical History', 40, FALSE),
      (v_general, 'present_history', 'Present History', 'TEXTAREA', 'Medical History', 50, FALSE),
      (v_general, 'present_medication', 'Present Medication', 'TEXTAREA', 'Medical History', 60, FALSE),
      (v_general, 'drug_allergy', 'Drug Allergy', 'TEXTAREA', 'Medical History', 70, FALSE),

      -- ── SECTION: Clinical Findings ─────────────────────
      (v_general, 's_clinical_findings', 'Clinical Findings', 'HEADING', 'Clinical Findings', 80, FALSE),
      (v_general, 'examination_findings', 'Examination Findings', 'TEXTAREA', 'Clinical Findings', 90, FALSE),
      (v_general, 'diagnosis', 'Diagnosis', 'TEXTAREA', 'Clinical Findings', 100, FALSE),
      (v_general, 'advice', 'Advice', 'TEXTAREA', 'Clinical Findings', 110, FALSE),
      (v_general, 'counselling', 'Counselling', 'TEXTAREA', 'Clinical Findings', 120, FALSE),
      (v_general, 'review', 'Review', 'TEXTAREA', 'Clinical Findings', 130, FALSE);
END $$;
