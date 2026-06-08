-- Seed sequence generator for STOCK_ADJUSTMENT (document_type = 18)
INSERT INTO sequence_generators (id, prefix_string, document_type, reset_policy, is_activated, current_counter, created_at)
SELECT gen_random_uuid(), 'ADJ-', 18, 0, TRUE, 1, NOW() 
WHERE NOT EXISTS (SELECT 1 FROM sequence_generators WHERE document_type = 18);
