-- V151__scope_patient_sequence_generators_by_branch.sql
-- Remove special tenant-level unique constraints for PATIENT (document_type = 12)
-- and allow them to be scoped by branch just like any other document type.

-- 1. Backfill branch_id to the default branch of the tenant for all PATIENT document types
--    This ensures existing patient sequence generators don't have a null branch_id
UPDATE sequence_generators sg
SET branch_id = b.id
FROM branches b
WHERE b.tenant_id = sg.tenant_id AND b.is_default = true AND sg.document_type = 12 AND sg.branch_id IS NULL;

-- 2. Drop existing constraint indexes
DROP INDEX IF EXISTS uq_sg_tenant_patient_prefix;
DROP INDEX IF EXISTS uq_sg_tenant_patient_active;
DROP INDEX IF EXISTS uq_sg_tenant_branch_prefix;
DROP INDEX IF EXISTS uq_sg_tenant_branch_active;

-- 3. Create unified branch-scoped unique constraints for ALL document types
-- A. All active prefixes are unique per tenant + branch (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS uq_sg_tenant_branch_prefix
    ON sequence_generators (tenant_id, branch_id, LOWER(prefix_string))
    WHERE (is_activated = true);

-- B. Only one active prefix per tenant + branch per document type
CREATE UNIQUE INDEX IF NOT EXISTS uq_sg_tenant_branch_active
    ON sequence_generators (tenant_id, branch_id, document_type)
    WHERE (is_activated = true);

-- 4. Backfill branch_id for existing patients to the default branch
--    This ensures existing patients are visible within the branch scope
UPDATE patients p
SET branch_id = b.id
FROM branches b
WHERE b.tenant_id = p.tenant_id AND b.is_default = true AND p.branch_id IS NULL;
