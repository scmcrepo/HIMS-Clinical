-- V009__create_remaining_master_tables.sql
-- Remaining master data tables for the 7 newly built controllers

-- 1. MOLECULES (skipped — already in V004)

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. STAFF (non-consultant clinical staff)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE staff (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    staff_type  VARCHAR(30),
    contact     VARCHAR(20),
    email       VARCHAR(120),
    designation VARCHAR(100),
    status      SMALLINT    NOT NULL DEFAULT 1,
    created_by  UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_staff PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. TAXES + TAX_CATEGORIES (GST master for pharmacy)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE taxes (
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(60)    NOT NULL,
    tax_type    VARCHAR(30),
    total_rate  NUMERIC(6, 2)  NOT NULL DEFAULT 0,
    status      SMALLINT       NOT NULL DEFAULT 1,
    created_by  UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_taxes PRIMARY KEY (id)
);

CREATE TABLE tax_categories (
    id      UUID          NOT NULL DEFAULT gen_random_uuid(),
    tax_id  UUID          NOT NULL REFERENCES taxes(id) ON DELETE CASCADE,
    name    VARCHAR(60)   NOT NULL,
    rate    NUMERIC(6, 2) NOT NULL DEFAULT 0,
    CONSTRAINT pk_tax_categories PRIMARY KEY (id)
);
CREATE INDEX idx_tc_tax ON tax_categories(tax_id);

-- 4. DIAGNOSTIC_TEMPLATES (skipped — already in V005)

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. INVENTORY_ITEMS — add missing columns if table already exists
--    (table was created in V004 but may be missing newer columns)
-- ─────────────────────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'inventory_items' AND column_name = 'requires_prescription'
    ) THEN
        ALTER TABLE inventory_items ADD COLUMN requires_prescription BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'inventory_items' AND column_name = 'requires_batch'
    ) THEN
        ALTER TABLE inventory_items ADD COLUMN requires_batch BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. BED_TYPES view — alias for room_categories so legacy queries still work
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW bed_types AS
    SELECT
        id,
        name,
        billing_cycle AS bed_type,   -- DAILY=0 (TWENTY_FOUR_HOURS), HOURLY=1
        service_catalog_item_id,
        status,
        created_by, created_at,
        modified_by, modified_at
    FROM room_categories;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. DATA_API table (for DataAPIController stored queries)
-- ─────────────────────────────────────────────────────────────────────────────
-- Stored queries live in system_settings (type=DATA_API) already.
-- This table is the legacy equivalent if a separate table is ever needed.
-- Skipped — system_settings approach is cleaner.

COMMENT ON TABLE diagnostic_templates IS 'Lab report templates with reference ranges and result entry structure';
COMMENT ON TABLE molecules IS 'Drug molecule/generic name master consumed by ItemService and DataImport';
COMMENT ON TABLE staff IS 'Non-consultant clinical staff (nurses, technicians, pharmacists)';
