-- V077__alter_department_template_fk.sql
-- Alter department_template table to reference case_sheet_templates instead of printing templates

DELETE FROM department_template;

ALTER TABLE department_template 
  DROP CONSTRAINT IF EXISTS department_template_template_id_fkey;

ALTER TABLE department_template 
  ADD CONSTRAINT fk_department_template_casesheet 
  FOREIGN KEY (template_id) REFERENCES case_sheet_templates(id) ON DELETE CASCADE;
