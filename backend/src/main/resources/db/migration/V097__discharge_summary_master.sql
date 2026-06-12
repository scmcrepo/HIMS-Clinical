-- V097__discharge_summary_master.sql
-- Create database schema for Discharge Summary Templates and Records

CREATE TABLE discharge_summary_templates (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(120) NOT NULL,
    specialization  VARCHAR(60)  NOT NULL,
    description     TEXT,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_dst PRIMARY KEY (id),
    CONSTRAINT uq_dst_name_spec UNIQUE (name, specialization)
);

CREATE TABLE discharge_summary_template_fields (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    template_id    UUID         NOT NULL,
    field_key      VARCHAR(80)  NOT NULL,
    label          VARCHAR(120) NOT NULL,
    field_type     VARCHAR(30)  NOT NULL,
    section        VARCHAR(80),
    display_order  INTEGER      NOT NULL DEFAULT 0,
    is_required    BOOLEAN      NOT NULL DEFAULT FALSE,
    placeholder    VARCHAR(200),
    help_text      VARCHAR(300),
    options        JSONB,
    validation     JSONB,
    default_value  VARCHAR(200),
    is_visible     BOOLEAN      NOT NULL DEFAULT TRUE,
    status         SMALLINT     NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_dstf PRIMARY KEY (id),
    CONSTRAINT fk_dstf_template FOREIGN KEY (template_id) REFERENCES discharge_summary_templates(id) ON DELETE CASCADE,
    CONSTRAINT uq_dstf_key UNIQUE (template_id, field_key)
);

CREATE TABLE discharge_summary_records (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    encounter_id UUID        NOT NULL,
    template_id  UUID        NOT NULL,
    data         JSONB       NOT NULL DEFAULT '{}',
    recorded_by  UUID,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status       SMALLINT    NOT NULL DEFAULT 1,
    created_by   UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by  UUID,
    modified_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_dsr           PRIMARY KEY (id),
    CONSTRAINT fk_dsr_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_encounters(id),
    CONSTRAINT fk_dsr_template  FOREIGN KEY (template_id)  REFERENCES discharge_summary_templates(id),
    CONSTRAINT uq_dsr_enc_tmpl  UNIQUE (encounter_id, template_id)
);
CREATE INDEX idx_dsr_encounter ON discharge_summary_records(encounter_id);
CREATE INDEX idx_dsr_template  ON discharge_summary_records(template_id);

DO $$
DECLARE
  v_old_template_id UUID;
  v_new_template_id UUID := gen_random_uuid();
BEGIN
  -- Find the old template ID
  SELECT id INTO v_old_template_id FROM case_sheet_templates WHERE name = 'DISCHARGE' LIMIT 1;
  
  IF v_old_template_id IS NOT NULL THEN
    -- Copy the template
    INSERT INTO discharge_summary_templates(id, name, specialization, description, is_default, status)
    SELECT v_new_template_id, name, specialization, description, is_default, status
    FROM case_sheet_templates
    WHERE id = v_old_template_id;
    
    -- Copy the fields
    INSERT INTO discharge_summary_template_fields
      (template_id, field_key, label, field_type, section, display_order, is_required, placeholder, help_text, options, validation, default_value, is_visible, status)
    SELECT v_new_template_id, field_key, label, field_type, section, display_order, is_required, placeholder, help_text, options, validation, default_value, is_visible, status
    FROM case_sheet_template_fields
    WHERE template_id = v_old_template_id;

    -- Deactivate the old template
    UPDATE case_sheet_templates SET status = 2 WHERE id = v_old_template_id;
  ELSE
    -- If no existing DISCHARGE template, seed a default one
    INSERT INTO discharge_summary_templates(id, name, specialization, description, is_default)
    VALUES(v_new_template_id, 'DISCHARGE', 'GENERAL', 'Default Inpatient Discharge Summary Template', TRUE);

    INSERT INTO discharge_summary_template_fields
      (template_id, field_key, label, field_type, section, display_order, is_required, placeholder, help_text)
    VALUES
      (v_new_template_id, 'diagnosis', 'DIAGNOSIS', 'TEXTAREA', 'Discharge Summary Details', 10, FALSE, 'Enter diagnosis details...', 'Diagnosis details'),
      (v_new_template_id, 'complaints', 'COMPLAINTS', 'TEXTAREA', 'Discharge Summary Details', 20, FALSE, 'Enter patient complaints...', 'Patient complaints'),
      (v_new_template_id, 'examination', 'ON EXAMINATION', 'TEXTAREA', 'Discharge Summary Details', 30, FALSE, 'Enter examination findings...', 'Examination findings'),
      (v_new_template_id, 'opinion', 'OPINION', 'TEXTAREA', 'Discharge Summary Details', 40, FALSE, 'Enter clinical opinion...', 'Clinical opinion'),
      (v_new_template_id, 'treatment', 'TREATMENT', 'TEXTAREA', 'Discharge Summary Details', 50, FALSE, 'Enter treatment given...', 'Treatment given');
  END IF;
END $$;
