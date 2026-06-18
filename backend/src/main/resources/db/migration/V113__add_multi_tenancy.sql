-- V096__add_multi_tenancy.sql  (PostgreSQL 16)
-- ============================================================================
-- Enables multi-tenancy by introducing a `tenants` table and a `tenant_id`
-- column on every tenant-scoped table.
--
-- SAFETY / ROLLOUT NOTES:
--   * tenant_id is added as NULLABLE here so this migration can be applied to a
--     RUNNING single-tenant instance without breaking inserts before the new
--     application code is deployed.
--   * All existing rows are back-filled to the default tenant.
--   * The NOT NULL tightening is deferred to V097 (run AFTER the tenant-aware
--     application build is live). This matches the implementation order: schema
--     first, code second, constraints last.
--   * users.tenant_id stays NULLABLE permanently (SUPERADMIN = platform user).
-- ============================================================================

-- 1. Tenants table ----------------------------------------------------------
CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(60)  NOT NULL UNIQUE,    -- e.g. "apollo-chennai"
    name        VARCHAR(120) NOT NULL,
    status      SMALLINT     NOT NULL DEFAULT 1, -- 1=active, 0=inactive
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 2. Default tenant for existing single-tenant data -------------------------
INSERT INTO tenants (id, slug, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Hospital');

-- 3. Helper: add nullable tenant_id + FK + index + backfill -----------------
--    We use a DO block so the table list lives in one place and stays DRY.
DO $$
DECLARE
    t text;
    tenant_scoped_tables text[] := ARRAY[
        -- Core clinical
        'patients','visits','clinical_encounters','appointments','appointment_slots',
        'consultants','departments','staff','areas',
        -- Billing
        'bills','charge_line_items','payments',
        -- Inventory / Pharmacy
        'inventory_items','inventory_batches','stock_adjustments','purchase_orders',
        'purchase_receipts','pharmacy_sales','sales_returns','stock_indents','stock_issues',
        'stock_returns','stock_consumptions','goods_returns',
        -- Diagnostics
        'diagnostic_orders','diagnostic_reports','specimen_collections',
        -- Settings / master data
        'roles','features','beds','bed_types','room_categories','bed_occupancies',
        'service_catalog_items','service_categories','case_sheet_templates','case_sheet_records',
        'print_templates','diagnostic_templates','lab_template_details','order_sets','charges',
        'tariffs','molecules','items','categories','units_of_measure','taxes','tax_categories',
        'frequencies','specimens','suppliers','payors','account_units','attachments','referrals',
        'scheduled_drugs'
    ];
BEGIN
    FOREACH t IN ARRAY tenant_scoped_tables LOOP
        -- Skip tables that may not exist in every environment (defensive).
        IF EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = current_schema() AND table_name = t) THEN
            EXECUTE format(
                'ALTER TABLE %I ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id)', t);
            EXECUTE format(
                'UPDATE %I SET tenant_id = ''00000000-0000-0000-0000-000000000001'' WHERE tenant_id IS NULL', t);
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%I_tenant ON %I (tenant_id)', t, t);
        ELSE
            RAISE NOTICE 'Skipping missing table: %', t;
        END IF;
    END LOOP;
END $$;

-- 4. users.tenant_id (NULLABLE forever — null => SUPERADMIN platform user) ---
ALTER TABLE users ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
-- Back-fill existing users to the default tenant; flip SUPERADMINs back to NULL.
UPDATE users SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE users u SET tenant_id = NULL
  WHERE EXISTS (
    SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
    WHERE ur.user_id = u.id AND r.name = 'SUPERADMIN');
CREATE INDEX IF NOT EXISTS idx_users_tenant ON users (tenant_id);

-- 5. Re-scope previously-global UNIQUE constraints to be per-tenant ----------
--    NOTE: constraint names follow the PostgreSQL default (<table>_<col>_key).
--    Adjust the DROP names if your existing constraints were named explicitly.

-- roles.name : unique per tenant
ALTER TABLE roles DROP CONSTRAINT IF EXISTS uq_roles_name;
CREATE UNIQUE INDEX IF NOT EXISTS uq_roles_tenant_name ON roles (tenant_id, name);

-- features.feature_key : unique per tenant
ALTER TABLE features DROP CONSTRAINT IF EXISTS uq_features_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_features_tenant_key ON features (tenant_id, feature_key);

-- users.username : INTENTIONALLY KEPT GLOBALLY UNIQUE across the whole platform.
-- Login does not take a tenant/branch (per product requirement: "users do not select a
-- hospital at login"); the account is resolved by username alone and its tenant + branch are
-- read from the row. A globally-unique username is what makes that lookup unambiguous and safe.
-- The original global unique (constraint uq_users_username from V001) is LEFT IN PLACE.
-- Nothing to do here — we simply do NOT drop it.

-- join tables (user_roles, user_departments) are implicitly tenant-scoped via
-- the tenant-scoped rows they reference; no tenant_id needed there.

-- Audit / sequence tables (sequence_generators, number_sequences, system_setting)
-- are intentionally left GLOBAL per the design constraints.
