-- V098__add_branches.sql  (PostgreSQL 16)
-- ============================================================================
-- Adds the BRANCH level beneath tenant. A tenant (hospital) owns one or more
-- branches (locations); business data is isolated per-branch. Hierarchy:
--   SUPERADMIN  >  HOSPITAL_ADMIN (tenant-wide)  >  BRANCH_ADMIN / staff (one branch)
--
-- SAFETY / ROLLOUT:
--   * branch_id is added NULLABLE so this can run on a live instance before the
--     branch-aware build is deployed.
--   * Every tenant gets exactly one auto-created DEFAULT branch; existing rows are
--     back-filled to their tenant's default branch.
--   * users.branch_id stays NULLABLE permanently (SUPERADMIN and HOSPITAL_ADMIN
--     are not pinned to a branch).
--   * A NOT NULL tightening for business tables can follow later (mirrors V097),
--     once every write path stamps a branch.
-- ============================================================================

-- 1. Branches table ---------------------------------------------------------
CREATE TABLE branches (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    code        VARCHAR(60)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT false,
    status      SMALLINT     NOT NULL DEFAULT 1,   -- 1=active, 0=inactive
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_branches_tenant_code UNIQUE (tenant_id, code)
);
CREATE INDEX IF NOT EXISTS idx_branches_tenant ON branches (tenant_id);
-- At most one default branch per tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_branches_one_default
    ON branches (tenant_id) WHERE is_default;

-- 2. Auto-create a default branch for every existing tenant -----------------
INSERT INTO branches (tenant_id, code, name, is_default)
SELECT t.id, 'MAIN', 'Main Branch', true
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM branches b WHERE b.tenant_id = t.id AND b.is_default);

-- 3. Add nullable branch_id + FK + index + backfill to tenant-scoped tables --
DO $$
DECLARE
    t text;
    branch_scoped_tables text[] := ARRAY[
        'patients','visits','clinical_encounters','appointments','appointment_slots',
        'consultants','departments','staff','areas',
        'bills','charge_line_items','payments',
        'inventory_items','inventory_batches','stock_adjustments','purchase_orders',
        'purchase_receipts','pharmacy_sales','sales_returns','stock_indents','stock_issues',
        'stock_returns','stock_consumptions','goods_returns',
        'diagnostic_orders','diagnostic_reports','specimen_collections',
        'beds','bed_types','room_categories','bed_occupancies',
        'service_catalog_items','service_categories','case_sheet_templates','case_sheet_records',
        'print_templates','diagnostic_templates','lab_template_details','order_sets','charges',
        'tariffs','attachments','referrals','scheduled_drugs'
        -- NOTE: roles & features are tenant-scoped only (shared across a tenant's branches),
        -- so they intentionally do NOT get a branch_id.
    ];
BEGIN
    FOREACH t IN ARRAY branch_scoped_tables LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = current_schema() AND table_name = t) THEN
            EXECUTE format(
                'ALTER TABLE %I ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id)', t);
            -- Back-fill each row to its tenant's default branch.
            EXECUTE format(
                'UPDATE %I x SET branch_id = b.id FROM branches b '
                || 'WHERE b.tenant_id = x.tenant_id AND b.is_default AND x.branch_id IS NULL', t);
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%I_branch ON %I (branch_id)', t, t);
        ELSE
            RAISE NOTICE 'Skipping missing table: %', t;
        END IF;
    END LOOP;
END $$;

-- 4. users.branch_id (NULLABLE — null => SUPERADMIN or HOSPITAL_ADMIN) -------
ALTER TABLE users ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);
CREATE INDEX IF NOT EXISTS idx_users_branch ON users (branch_id);
-- Existing tenant users are left branch-unpinned by default; a hospital admin can
-- assign them to a branch from the Users screen. (We do NOT force every legacy user
-- into the default branch, because that would silently hide tenant-wide admins.)

-- 5. Seed the new admin roles for the default tenant ------------------------
--    Roles are per-tenant (uq_roles_tenant_name after V096). HOSPITAL_ADMIN and
--    BRANCH_ADMIN mirror the platform hierarchy; grants can be configured in the UI.
INSERT INTO roles (id, name, description, status, tenant_id, created_at, modified_at)
SELECT gen_random_uuid(), 'HOSPITAL_ADMIN', 'Tenant-wide administrator (all branches)', 1,
       '00000000-0000-0000-0000-000000000001', now(), now()
WHERE NOT EXISTS (
    SELECT 1 FROM roles r
    WHERE r.name = 'HOSPITAL_ADMIN' AND r.tenant_id = '00000000-0000-0000-0000-000000000001');

INSERT INTO roles (id, name, description, status, tenant_id, created_at, modified_at)
SELECT gen_random_uuid(), 'BRANCH_ADMIN', 'Branch administrator (single branch)', 1,
       '00000000-0000-0000-0000-000000000001', now(), now()
WHERE NOT EXISTS (
    SELECT 1 FROM roles r
    WHERE r.name = 'BRANCH_ADMIN' AND r.tenant_id = '00000000-0000-0000-0000-000000000001');
