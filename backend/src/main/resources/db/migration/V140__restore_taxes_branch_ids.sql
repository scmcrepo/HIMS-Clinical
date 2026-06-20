-- V140__restore_taxes_branch_ids.sql (PostgreSQL 16)
-- Assign any NULL branch_id in taxes to the default/first branch of their tenant.

UPDATE taxes t
SET branch_id = (
    SELECT id FROM branches b
    WHERE b.tenant_id = t.tenant_id
    ORDER BY b.is_default DESC, b.created_at ASC
    LIMIT 1
)
WHERE t.branch_id IS NULL;
