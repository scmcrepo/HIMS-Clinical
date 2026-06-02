-- V049__add_scmc_master_fields.sql
-- Add missing fields from SCMC legacy CSVs to HMS master tables.

-- 1. Patient
ALTER TABLE patients ADD COLUMN IF NOT EXISTS patient_type VARCHAR(50);

-- 2. Consultant
ALTER TABLE consultants ADD COLUMN IF NOT EXISTS department_id UUID;
ALTER TABLE consultants ADD CONSTRAINT fk_consultants_department FOREIGN KEY (department_id) REFERENCES departments(id);

-- 3. User
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_no VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS salutation VARCHAR(10);
