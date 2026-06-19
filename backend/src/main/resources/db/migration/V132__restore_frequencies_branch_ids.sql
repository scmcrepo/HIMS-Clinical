-- V131__restore_frequencies_branch_ids.sql (PostgreSQL 16)
-- Assign any NULL branch_id in frequencies to the default/first branch of their tenant.

UPDATE frequencies f
SET branch_id = (
    SELECT id FROM branches b
    WHERE b.tenant_id = f.tenant_id
    ORDER BY b.is_default DESC, b.created_at ASC
    LIMIT 1
)
WHERE f.branch_id IS NULL;
