-- V022__fix_patient_sequence_configuration.sql
-- Fixes the bug in V014 where document_type=1 (RECEIPT) was updated instead of document_type=12 (PATIENT)

-- 1. Ensure the PATIENT (12) generator exists and is active with the correct prefix
INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'SCMCP-', 12, 0, TRUE, COALESCE((SELECT COUNT(*) FROM patients), 0) + 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 12);

-- 2. Update PATIENT (12) generator if it exists (fix prefix and activation)
UPDATE sequence_generators
SET prefix_string = 'SCMCP-',
    is_activated = TRUE,
    current_counter = GREATEST(current_counter, COALESCE((SELECT COUNT(*) FROM patients), 0) + 1)
WHERE document_type = 12;

-- 3. Correct the RECEIPT (1) generator which was accidentally modified by V014
-- We restore a standard prefix if it was changed to 'SCMCP-'
UPDATE sequence_generators
SET prefix_string = 'RCP-' 
WHERE document_type = 1 AND prefix_string = 'SCMCP-';
