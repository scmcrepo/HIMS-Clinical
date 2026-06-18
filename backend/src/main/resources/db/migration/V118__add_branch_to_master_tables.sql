-- V101__add_branch_to_master_tables.sql (PostgreSQL 16)
-- Adds missing tenant_id and branch_id to tables that extend AuditableEntity but were missed in V096/V098.

DO $$
DECLARE
    t text;
    missed_tables text[] := ARRAY[
        -- Missed entirely (no tenant_id, no branch_id)
        'insurances',
        -- Missed branch_id (but have tenant_id)
        'specimens', 'molecules', 'categories', 'units_of_measure', 'taxes', 'frequencies', 'suppliers', 'payors', 'account_units'
    ];
BEGIN
    FOREACH t IN ARRAY missed_tables LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = current_schema() AND table_name = t) THEN
            
            -- 1. Add tenant_id (if not exists)
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id)', t);
            
            -- Back-fill tenant_id to the default tenant
            EXECUTE format('UPDATE %I SET tenant_id = ''00000000-0000-0000-0000-000000000001'' WHERE tenant_id IS NULL', t);
            
            -- Enforce NOT NULL on tenant_id
            EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', t);
            
            -- Create tenant index
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%I_tenant ON %I (tenant_id)', t, t);

            -- 2. Add branch_id (if not exists)
            EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id)', t);
            
            -- Back-fill branch_id to the default branch of its tenant
            EXECUTE format(
                'UPDATE %I x SET branch_id = b.id FROM branches b '
                || 'WHERE b.tenant_id = x.tenant_id AND b.is_default AND x.branch_id IS NULL', t);
            
            -- Create branch index
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%I_branch ON %I (branch_id)', t, t);

        ELSE
            RAISE NOTICE 'Skipping missing table: %', t;
        END IF;
    END LOOP;
END $$;
