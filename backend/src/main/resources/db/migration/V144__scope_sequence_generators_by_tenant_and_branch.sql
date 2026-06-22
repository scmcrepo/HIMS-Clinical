-- V144__scope_sequence_generators_by_tenant_and_branch.sql (PostgreSQL 16)
-- Introduces tenant_id and branch_id to sequence_generators to enable scoping.

-- 1. Add tenant_id (nullable initially)
ALTER TABLE sequence_generators ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);

-- 2. Add branch_id (nullable initially)
ALTER TABLE sequence_generators ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

-- 3. Backfill tenant_id to the default tenant
UPDATE sequence_generators SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;

-- 4. Backfill branch_id to the default branch of the tenant for all non-patient document types (12 = PATIENT)
UPDATE sequence_generators sg
SET branch_id = b.id
FROM branches b
WHERE b.tenant_id = sg.tenant_id AND b.is_default = true AND sg.document_type != 12 AND sg.branch_id IS NULL;

-- 5. Resolve duplicate prefixes before creating unique constraints:
-- A. IP_ORDER (5) and RECEIPT (1) both defaulted to 'SCMCR-'. Change IP_ORDER to 'IPO-'.
UPDATE sequence_generators
SET prefix_string = 'IPO-'
WHERE document_type = 5 AND LOWER(prefix_string) = 'scmcr-';

-- B. PURCHASE_REQUEST (17) and PURCHASE_RETURN (10) both defaulted to 'PR-'. Change PURCHASE_REQUEST to 'PRQ-'.
UPDATE sequence_generators
SET prefix_string = 'PRQ-'
WHERE document_type = 17 AND LOWER(prefix_string) = 'pr-';

-- C. General fallback for any other unexpected duplicate prefixes per tenant/branch:
-- For branch-scoped generators, append document type.
WITH duplicates_branch AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id, branch_id, LOWER(prefix_string) ORDER BY id) as rn
    FROM sequence_generators
    WHERE document_type != 12
)
UPDATE sequence_generators sg
SET prefix_string = sg.prefix_string || '-' || sg.document_type
FROM duplicates_branch d
WHERE sg.id = d.id AND d.rn > 1;

-- For patient-scoped generators (if any duplicates exist per tenant, append generator ID).
WITH duplicates_patient AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id, LOWER(prefix_string) ORDER BY id) as rn
    FROM sequence_generators
    WHERE document_type = 12
)
UPDATE sequence_generators sg
SET prefix_string = sg.prefix_string || '-' || sg.id
FROM duplicates_patient d
WHERE sg.id = d.id AND d.rn > 1;

-- 6. Enforce NOT NULL on tenant_id
ALTER TABLE sequence_generators ALTER COLUMN tenant_id SET NOT NULL;

-- 7. Create indexes for foreign keys
CREATE INDEX IF NOT EXISTS idx_sg_tenant ON sequence_generators (tenant_id);
CREATE INDEX IF NOT EXISTS idx_sg_branch ON sequence_generators (branch_id);

-- 8. Add unique constraints:
-- A. Patient prefixes are unique per tenant (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS uq_sg_tenant_patient_prefix
    ON sequence_generators (tenant_id, LOWER(prefix_string))
    WHERE (document_type = 12);

-- B. Only one active patient prefix per tenant
CREATE UNIQUE INDEX IF NOT EXISTS uq_sg_tenant_patient_active
    ON sequence_generators (tenant_id, document_type)
    WHERE (is_activated = true AND document_type = 12);

-- C. All other prefixes are unique per tenant + branch (case-insensitive)
CREATE UNIQUE INDEX IF NOT EXISTS uq_sg_tenant_branch_prefix
    ON sequence_generators (tenant_id, branch_id, LOWER(prefix_string))
    WHERE (document_type != 12);

-- D. Only one active prefix per tenant + branch for all other document types
CREATE UNIQUE INDEX IF NOT EXISTS uq_sg_tenant_branch_active
    ON sequence_generators (tenant_id, branch_id, document_type)
    WHERE (is_activated = true AND document_type != 12);
