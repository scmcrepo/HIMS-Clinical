-- V035__add_purchase_request_sequence.sql
-- Ensure the PURCHASE_REQUEST (17) generator exists and is active with the prefix 'PR-'
INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'PR-', 17, 0, TRUE, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 17);
