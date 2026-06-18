-- V105__add_missing_tenant_columns.sql
-- Add tenant_id and branch_id to missed tables: stock_adjustment, customers, temp_stock

-- 1. stock_adjustment
ALTER TABLE stock_adjustment ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
ALTER TABLE stock_adjustment ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);
UPDATE stock_adjustment SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_stock_adjustment_tenant ON stock_adjustment (tenant_id);
CREATE INDEX IF NOT EXISTS idx_stock_adjustment_branch ON stock_adjustment (branch_id);

-- 2. customers
ALTER TABLE customers ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);
UPDATE customers SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_customers_tenant ON customers (tenant_id);
CREATE INDEX IF NOT EXISTS idx_customers_branch ON customers (branch_id);

-- 3. temp_stock
ALTER TABLE temp_stock ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
ALTER TABLE temp_stock ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);
UPDATE temp_stock SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_temp_stock_tenant ON temp_stock (tenant_id);
CREATE INDEX IF NOT EXISTS idx_temp_stock_branch ON temp_stock (branch_id);
