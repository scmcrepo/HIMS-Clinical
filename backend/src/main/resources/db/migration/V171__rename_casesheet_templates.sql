-- V171__rename_casesheet_templates.sql
-- Rename case sheet templates: remove " Default" suffix

UPDATE case_sheet_templates SET name = 'General OP'        WHERE name = 'General OP Default';
UPDATE case_sheet_templates SET name = 'Ophthal'           WHERE name = 'Ophthal Default';
UPDATE case_sheet_templates SET name = 'Orthopaedics OP'   WHERE name = 'Orthopaedics OP Default';
