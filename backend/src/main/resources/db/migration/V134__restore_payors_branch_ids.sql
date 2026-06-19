-- V134__restore_payors_branch_ids.sql (PostgreSQL 16)
-- Assign any NULL branch_id in payors to the default/first branch of their tenant.

UPDATE payors p
SET branch_id = (
    SELECT id FROM branches b
    WHERE b.tenant_id = p.tenant_id
    ORDER BY b.is_default DESC, b.created_at ASC
    LIMIT 1
)
WHERE p.branch_id IS NULL;
