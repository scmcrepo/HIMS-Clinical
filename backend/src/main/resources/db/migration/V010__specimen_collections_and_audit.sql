-- V010__specimen_collections_and_audit.sql
-- Tables required by the final endpoint implementations

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. SPECIMEN_COLLECTIONS (records specimen collection for diagnostic orders)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE specimen_collections (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    diagnostic_id   UUID        NOT NULL,
    specimen_id     UUID,
    sample_number   VARCHAR(40),
    collection_notes TEXT,
    collected_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    collected_by    UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_specimen_collections PRIMARY KEY (id)
);
CREATE INDEX idx_sc_diagnostic ON specimen_collections(diagnostic_id);
CREATE INDEX idx_sc_sample     ON specimen_collections(sample_number);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. GOODS_RETURNS (return of goods to supplier)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS goods_returns (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    supplier_id     UUID,
    department_id   UUID        NOT NULL,
    return_date     DATE        NOT NULL DEFAULT CURRENT_DATE,
    sequence_number VARCHAR(40),
    notes           TEXT,
    status          SMALLINT    NOT NULL DEFAULT 1,
    created_by      UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by     UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_goods_returns PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_gr_date ON goods_returns(return_date);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Extend INVENTORY_ITEMS with molecule and tax FK columns
-- ─────────────────────────────────────────────────────────────────────────────
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'inventory_items' AND column_name = 'molecule_id'
    ) THEN
        ALTER TABLE inventory_items ADD COLUMN molecule_id UUID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'inventory_items' AND column_name = 'tax_id'
    ) THEN
        ALTER TABLE inventory_items ADD COLUMN tax_id UUID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'inventory_items' AND column_name = 'unit_type'
    ) THEN
        ALTER TABLE inventory_items ADD COLUMN unit_type VARCHAR(30);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'inventory_items' AND column_name = 'scheduled_drug_type'
    ) THEN
        ALTER TABLE inventory_items ADD COLUMN scheduled_drug_type VARCHAR(30);
    END IF;
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. PATIENT_PEDIATRIC (pediatric chart JSON — one per patient)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS patient_pediatric (
    patient_id      UUID        NOT NULL,
    pediatric_data  JSONB       NOT NULL DEFAULT '{}',
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_patient_pediatric PRIMARY KEY (patient_id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. BED_TRANSFERS audit table (not strictly required but useful for history)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bed_transfers (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    encounter_id    UUID        NOT NULL,
    from_bed_id     UUID,
    to_bed_id       UUID        NOT NULL,
    transfer_date   DATE        NOT NULL DEFAULT CURRENT_DATE,
    transferred_by  UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_bed_transfers PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bt_encounter ON bed_transfers(encounter_id);

COMMENT ON TABLE specimen_collections IS 'Records lab specimen collection events. sample_number is generated via SAMPLE prefix sequence.';
COMMENT ON TABLE patient_pediatric     IS 'Pediatric growth chart JSON data. One row per patient, upserted on updatePediatric().';
