-- V014__standardize_patient_id_format.sql
-- Standardizes patient ID prefix to SCMCP- and ensures 4-digit zero padding

-- 1. Update the configuration table
UPDATE sequence_generators 
SET prefix_string = 'SCMCP-' 
WHERE document_type = 1;

-- 2. Correct all existing IDs in the storage table
WITH ordered_patients AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY created_at) as row_num
  FROM patients
)
UPDATE number_sequences
SET value = 'SCMCP-' || LPAD(ordered_patients.row_num::text, 4, '0')
FROM ordered_patients
WHERE number_sequences.id = ordered_patients.id;

-- 3. Synchronize the counter so the next registration is correct
UPDATE sequence_generators 
SET current_counter = COALESCE((SELECT COUNT(*) FROM patients), 0) + 1
WHERE document_type = 1;
