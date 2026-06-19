-- V129__restore_diagnostic_branch_ids.sql (PostgreSQL 16)
-- Map all specimens, diagnostic templates, and lab template details that have a NULL branch_id
-- back to the primary/first branch of their tenant.

UPDATE specimens s
SET branch_id = (
    SELECT id FROM branches b
    WHERE b.tenant_id = s.tenant_id
    ORDER BY b.is_default DESC, b.created_at ASC
    LIMIT 1
)
WHERE s.branch_id IS NULL;

UPDATE diagnostic_templates dt
SET branch_id = (
    SELECT id FROM branches b
    WHERE b.tenant_id = dt.tenant_id
    ORDER BY b.is_default DESC, b.created_at ASC
    LIMIT 1
)
WHERE dt.branch_id IS NULL;

UPDATE lab_template_details ltd
SET branch_id = (
    SELECT id FROM branches b
    WHERE b.tenant_id = ltd.tenant_id
    ORDER BY b.is_default DESC, b.created_at ASC
    LIMIT 1
)
WHERE ltd.branch_id IS NULL;
