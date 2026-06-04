-- V082__create_ophthal_dynamic_casesheet_template.sql
-- Create OPHTHAL case sheet template

DO $$
DECLARE
    v_ophthal UUID := '9aa86594-78b3-4b76-95d6-391d3a72d9b3';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM case_sheet_templates WHERE id = v_ophthal) THEN
        INSERT INTO case_sheet_templates (id, name, specialization, visit_type, description, is_default)
        VALUES (v_ophthal, 'Ophthal Default', 'OPHTHAL', 'OP', 'Ophthalmology case sheet template', TRUE);
    END IF;

    DELETE FROM case_sheet_template_fields WHERE template_id = v_ophthal;

    INSERT INTO case_sheet_template_fields
      (template_id, field_key, label, field_type, section, display_order, is_required)
    VALUES
      -- ── SECTION: Chief Complaints ──────────────────────
      (v_ophthal, 's_chief_complaints', 'Chief Complaints', 'HEADING', 'Chief Complaints', 10, FALSE),
      (v_ophthal, 'complaints', 'Complaints', 'TEXTAREA', 'Chief Complaints', 20, FALSE),
      (v_ophthal, 'allergies', 'Allergies', 'TEXTAREA', 'Chief Complaints', 30, FALSE),

      -- ── SECTION: General Examination ───────────────────
      (v_ophthal, 's_general_examination', 'General Examination', 'HEADING', 'General Examination', 40, FALSE),
      (v_ophthal, 'diabetes', 'Diabetes', 'TEXTAREA', 'General Examination', 50, FALSE),
      (v_ophthal, 'hypertension', 'Hypertension', 'TEXTAREA', 'General Examination', 60, FALSE),
      (v_ophthal, 'cardiac_status', 'Cardiac Status', 'TEXTAREA', 'General Examination', 70, FALSE),
      (v_ophthal, 'renal_status', 'Renal Status', 'TEXTAREA', 'General Examination', 80, FALSE),
      (v_ophthal, 'any_other', 'Any Other', 'TEXTAREA', 'General Examination', 90, FALSE),

      -- ── SECTION: Systemic Examination ──────────────────
      (v_ophthal, 's_systemic_examination', 'Systemic Examination', 'HEADING', 'Systemic Examination', 100, FALSE),
      (v_ophthal, 'present_glass_powers', 'Present Glass Powers', 'TEXTAREA', 'Systemic Examination', 110, FALSE),
      (v_ophthal, 'vision', 'Vision', 'TEXTAREA', 'Systemic Examination', 120, FALSE),
      (v_ophthal, 'flash', 'Flash', 'TEXTAREA', 'Systemic Examination', 130, FALSE),
      (v_ophthal, 'acceptance', 'Acceptance', 'TEXTAREA', 'Systemic Examination', 140, FALSE),
      (v_ophthal, 'addition', 'Addition', 'TEXTAREA', 'Systemic Examination', 150, FALSE),
      (v_ophthal, 'post_dilated_acceptance_pda', 'Post Dilated Acceptance (PDA)', 'TEXTAREA', 'Systemic Examination', 160, FALSE),
      (v_ophthal, 'post_mydriatic_test_pmt', 'Post Mydriatic Test (PMT)', 'TEXTAREA', 'Systemic Examination', 170, FALSE),
      (v_ophthal, 'cover_test', 'CoverTest', 'TEXTAREA', 'Systemic Examination', 180, FALSE),

      -- ── SECTION: Slit Lamp Anterior Segment ────────────
      (v_ophthal, 's_slit_lamp', 'Slit Lamp Anterior Segment', 'HEADING', 'Slit Lamp Anterior Segment', 190, FALSE),
      (v_ophthal, 'od_lids', 'OD Lids', 'TEXT', 'Slit Lamp Anterior Segment', 200, FALSE),
      (v_ophthal, 'os_lids', 'OS Lids', 'TEXT', 'Slit Lamp Anterior Segment', 210, FALSE),
      (v_ophthal, 'od_conj', 'OD Conj', 'TEXT', 'Slit Lamp Anterior Segment', 220, FALSE),
      (v_ophthal, 'os_conj', 'OS Conj', 'TEXT', 'Slit Lamp Anterior Segment', 230, FALSE),
      (v_ophthal, 'od_cornea', 'OD Cornea', 'TEXT', 'Slit Lamp Anterior Segment', 240, FALSE),
      (v_ophthal, 'os_cornea', 'OS Cornea', 'TEXT', 'Slit Lamp Anterior Segment', 250, FALSE),
      (v_ophthal, 'od_ant_ch', 'OD Ant.Ch', 'TEXT', 'Slit Lamp Anterior Segment', 260, FALSE),
      (v_ophthal, 'os_ant_ch', 'OS Ant.Ch', 'TEXT', 'Slit Lamp Anterior Segment', 270, FALSE),
      (v_ophthal, 'od_pupil', 'OD Pupil', 'TEXT', 'Slit Lamp Anterior Segment', 280, FALSE),
      (v_ophthal, 'os_pupil', 'OS Pupil', 'TEXT', 'Slit Lamp Anterior Segment', 290, FALSE),
      (v_ophthal, 'od_lens', 'OD Lens', 'TEXT', 'Slit Lamp Anterior Segment', 300, FALSE),
      (v_ophthal, 'os_lens', 'OS Lens', 'TEXT', 'Slit Lamp Anterior Segment', 310, FALSE),
      (v_ophthal, 'od_iop_by_at', 'OD IOP by AT', 'TEXT', 'Slit Lamp Anterior Segment', 320, FALSE),
      (v_ophthal, 'os_iop_by_at', 'OS IOP by AT', 'TEXT', 'Slit Lamp Anterior Segment', 330, FALSE),
      (v_ophthal, 'gonioscopy', 'Gonioscopy', 'TEXTAREA', 'Slit Lamp Anterior Segment', 340, FALSE),

      -- ── SECTION: Treatment & Plan ──────────────────────
      (v_ophthal, 's_treatment_plan', 'Treatment & Plan', 'HEADING', 'Treatment & Plan', 350, FALSE),
      (v_ophthal, 'provisional_diagnosis', 'Provisional Diagnosis', 'TEXTAREA', 'Treatment & Plan', 360, FALSE),
      (v_ophthal, 'plan_of_care', 'Plan of Care', 'TEXTAREA', 'Treatment & Plan', 370, FALSE),
      (v_ophthal, 'op_procedures_and_treatment', 'OP Procedures & Treatment', 'TEXTAREA', 'Treatment & Plan', 380, FALSE),
      (v_ophthal, 'diet', 'Diet', 'TEXT', 'Treatment & Plan', 390, FALSE),
      (v_ophthal, 'review_and_instructions', 'Review & Instructions', 'TEXTAREA', 'Treatment & Plan', 400, FALSE);
END $$;
