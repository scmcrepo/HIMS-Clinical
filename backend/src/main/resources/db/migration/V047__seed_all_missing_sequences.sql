-- V047__seed_all_missing_sequences.sql
-- Ensures all sequence generators are seeded and activated by default

-- Activate any existing sequences that were accidentally left inactive
UPDATE sequence_generators SET is_activated = TRUE;

-- Seed missing sequences with their default prefixes if they don't exist
INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'SCMCB-', 0, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 0);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'SCMCR-', 1, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 1);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'SCMCD-', 2, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 2);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'REF-', 3, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 3);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'SCML-', 4, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 4);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'SCMCR-', 5, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 5);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'SAM-', 6, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 6);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'PS-', 7, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 7);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'PAY-', 8, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 8);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'GRN', 9, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 9);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'PR-', 10, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 10);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'PO-', 11, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 11);

-- PATIENT (12) is already handled in V022 but included for safety
INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'SCMCP-', 12, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 12);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'REP-', 13, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 13);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'IS-', 14, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 14);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'CON-', 15, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 15);

INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'ADREF-', 16, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 16);

-- PURCHASE_REQUEST (17) is already handled in V035
INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'PRQ-', 17, 0, TRUE, 1, NOW() WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 17);
