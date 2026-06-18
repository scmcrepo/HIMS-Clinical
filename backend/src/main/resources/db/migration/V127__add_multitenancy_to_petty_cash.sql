-- V127__add_multitenancy_to_petty_cash.sql (PostgreSQL 16)
-- Adds missing tenant_id and branch_id to petty_cash table.

-- 1. Add tenant_id (if not exists)
ALTER TABLE petty_cash ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);

-- 2. Add branch_id (if not exists)
ALTER TABLE petty_cash ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

-- 3. Backfill tenant_id and branch_id from users (creator)
UPDATE petty_cash pc
SET tenant_id = u.tenant_id,
    branch_id = u.branch_id
FROM users u
WHERE pc.created_by = u.id AND pc.tenant_id IS NULL;

-- 4. Fallback backfill to default tenant and branch if creator wasn't matched or doesn't have it
UPDATE petty_cash
SET tenant_id = '00000000-0000-0000-0000-000000000001'
WHERE tenant_id IS NULL;

UPDATE petty_cash pc
SET branch_id = b.id
FROM branches b
WHERE b.tenant_id = pc.tenant_id AND b.is_default AND pc.branch_id IS NULL;

-- 5. Enforce NOT NULL on tenant_id
ALTER TABLE petty_cash ALTER COLUMN tenant_id SET NOT NULL;

-- 6. Create indexes
CREATE INDEX IF NOT EXISTS idx_petty_cash_tenant ON petty_cash (tenant_id);
CREATE INDEX IF NOT EXISTS idx_petty_cash_branch ON petty_cash (branch_id);
