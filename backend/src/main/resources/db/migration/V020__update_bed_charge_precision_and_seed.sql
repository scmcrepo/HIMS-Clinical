-- V020__update_bed_charge_precision_and_seed.sql
-- Update bed_charge columns to support hours (TIMESTAMPTZ)
ALTER TABLE charge_line_items ALTER COLUMN bed_charge_from TYPE TIMESTAMPTZ;
ALTER TABLE charge_line_items ALTER COLUMN bed_charge_to TYPE TIMESTAMPTZ;

-- Use an existing patient to seed sample charges with precise stay duration
DO $$
DECLARE
    v_patient_id UUID := '824cb116-800e-4f8a-8263-cc944eee1b8c';
    v_consultant_id UUID := '8223626c-b566-4fc6-a676-338044e0a0ee';
    v_encounter_id UUID := gen_random_uuid();
    v_bill_id UUID := gen_random_uuid();
    v_hemoglobin_id UUID := 'e228efa5-f067-45ec-94c9-4002da4545a9';
BEGIN
    -- Only seed if the patient exists (safety check)
    IF EXISTS (SELECT 1 FROM patients WHERE id = v_patient_id) THEN
        -- Create an Inpatient Encounter (started 50 hours ago)
        INSERT INTO clinical_encounters (id, patient_id, primary_provider_id, encounter_type, encounter_status, started_at, created_at)
        VALUES (v_encounter_id, v_patient_id, v_consultant_id, 1, 1, NOW() - INTERVAL '50 hours', NOW());

        -- Create a Bill
        INSERT INTO bills (id, patient_id, encounter_id, primary_provider_id, bill_status, bill_type, encounter_type, created_at)
        VALUES (v_bill_id, v_patient_id, v_encounter_id, v_consultant_id, 0, 0, 1, NOW());

        -- Add a Diagnostic Charge (Manual)
        INSERT INTO charge_line_items (id, bill_id, service_catalog_item_id, item_name, amount, unit_rate, quantity, created_at)
        VALUES (gen_random_uuid(), v_bill_id, v_hemoglobin_id, 'Hemoglobin (Manual Seeding)', 50000, 50000, 1, NOW());

        -- Add a Bed Charge (Manual with 50-hour duration)
        INSERT INTO charge_line_items (id, bill_id, service_catalog_item_id, item_name, amount, unit_rate, quantity, bed_charge_from, bed_charge_to, created_at)
        VALUES (gen_random_uuid(), v_bill_id, v_hemoglobin_id, 'General Ward Bed (Precise Stay)', 300000, 100000, 3, NOW() - INTERVAL '50 hours', NOW(), NOW());
    END IF;
END $$;
