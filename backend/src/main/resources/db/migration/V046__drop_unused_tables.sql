-- V046__drop_unused_tables.sql

-- 1. Drop foreign keys referencing the tables we are about to drop
ALTER TABLE patients DROP CONSTRAINT IF EXISTS fk_patients_area;
ALTER TABLE clinical_encounters DROP CONSTRAINT IF EXISTS fk_ce_code;
ALTER TABLE visits DROP CONSTRAINT IF EXISTS fk_visits_diagnosis;

-- 2. Drop the columns that reference these tables
ALTER TABLE patients DROP COLUMN IF EXISTS area_id;
ALTER TABLE clinical_encounters DROP COLUMN IF EXISTS diagnosis_code_id;
ALTER TABLE visits DROP COLUMN IF EXISTS diagnosis_code_id;

-- 3. Drop the tables themselves
DROP TABLE IF EXISTS areas CASCADE;
DROP TABLE IF EXISTS clinical_codes CASCADE;
DROP TABLE IF EXISTS order_set_items CASCADE;
DROP TABLE IF EXISTS order_sets CASCADE;
