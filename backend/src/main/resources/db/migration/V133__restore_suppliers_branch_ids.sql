-- V132__restore_suppliers_branch_ids.sql (PostgreSQL 16)
-- Assign any NULL branch_id in suppliers to the default/first branch of their tenant.

UPDATE suppliers s
SET branch_id = (
    SELECT id FROM branches b
    WHERE b.tenant_id = s.tenant_id
    ORDER BY b.is_default DESC, b.created_at ASC
    LIMIT 1
)
WHERE s.branch_id IS NULL;
