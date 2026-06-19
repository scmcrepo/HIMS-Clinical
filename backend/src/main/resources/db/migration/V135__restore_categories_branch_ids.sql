-- V135__restore_categories_branch_ids.sql (PostgreSQL 16)
-- Assign any NULL branch_id in categories to the default/first branch of their tenant.

UPDATE categories c
SET branch_id = (
    SELECT id FROM branches b
    WHERE b.tenant_id = c.tenant_id
    ORDER BY b.is_default DESC, b.created_at ASC
    LIMIT 1
)
WHERE c.branch_id IS NULL;
