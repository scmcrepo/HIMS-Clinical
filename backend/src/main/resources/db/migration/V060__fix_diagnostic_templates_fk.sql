-- V060__fix_diagnostic_templates_fk.sql
-- Fixes foreign key constraint to point to global departments table instead of diagnostic_departments

-- Drop the old constraint
ALTER TABLE diagnostic_templates DROP CONSTRAINT IF EXISTS fk_dt_department;

-- Clear out any department_ids that don't exist in the new global departments table
UPDATE diagnostic_templates 
SET department_id = NULL 
WHERE department_id IS NOT NULL 
AND department_id NOT IN (SELECT id FROM departments);

-- Add the corrected constraint
ALTER TABLE diagnostic_templates 
ADD CONSTRAINT fk_dt_department FOREIGN KEY (department_id) REFERENCES departments(id);
