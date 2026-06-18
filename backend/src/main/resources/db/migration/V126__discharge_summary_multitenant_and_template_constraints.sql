-- V126__discharge_summary_multitenant_and_template_constraints.sql
-- Adds missing tenant_id and branch_id to discharge_summary_templates and discharge_summary_records
-- Drops old non-tenant-aware/non-branch-aware unique constraints and replaces them with branch-aware ones.

-- 1. Alter discharge_summary_templates
ALTER TABLE discharge_summary_templates ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
ALTER TABLE discharge_summary_templates ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

-- Backfill existing templates to default tenant and its default branch
UPDATE discharge_summary_templates 
SET tenant_id = '00000000-0000-0000-0000-000000000001'
WHERE tenant_id IS NULL;

UPDATE discharge_summary_templates x 
SET branch_id = b.id 
FROM branches b 
WHERE b.tenant_id = x.tenant_id AND b.is_default AND x.branch_id IS NULL;

-- Make tenant_id NOT NULL
ALTER TABLE discharge_summary_templates ALTER COLUMN tenant_id SET NOT NULL;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_dst_tenant ON discharge_summary_templates(tenant_id);
CREATE INDEX IF NOT EXISTS idx_dst_branch ON discharge_summary_templates(branch_id);

-- Drop old unique constraint
ALTER TABLE discharge_summary_templates DROP CONSTRAINT IF EXISTS uq_dst_name_spec;
DROP INDEX IF EXISTS uq_dst_name_spec;

-- Create branch-aware unique index
CREATE UNIQUE INDEX IF NOT EXISTS uq_dst_branch_name_spec ON discharge_summary_templates (
    tenant_id, 
    COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'), 
    name, 
    specialization
);

-- 2. Alter discharge_summary_records
ALTER TABLE discharge_summary_records ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
ALTER TABLE discharge_summary_records ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

-- Backfill existing records to default tenant and its default branch
UPDATE discharge_summary_records 
SET tenant_id = '00000000-0000-0000-0000-000000000001'
WHERE tenant_id IS NULL;

UPDATE discharge_summary_records x 
SET branch_id = b.id 
FROM branches b 
WHERE b.tenant_id = x.tenant_id AND b.is_default AND x.branch_id IS NULL;

-- Make tenant_id NOT NULL
ALTER TABLE discharge_summary_records ALTER COLUMN tenant_id SET NOT NULL;

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_dsr_tenant ON discharge_summary_records(tenant_id);
CREATE INDEX IF NOT EXISTS idx_dsr_branch ON discharge_summary_records(branch_id);

-- 3. Refactor case_sheet_templates unique constraint to be branch-aware
-- Drop old constraint
ALTER TABLE case_sheet_templates DROP CONSTRAINT IF EXISTS uq_cst_tenant_name_spec;
DROP INDEX IF EXISTS uq_cst_tenant_name_spec;

-- Create branch-aware unique index
CREATE UNIQUE INDEX IF NOT EXISTS uq_cst_branch_name_spec ON case_sheet_templates (
    tenant_id, 
    COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'), 
    name, 
    specialization, 
    visit_type
);
