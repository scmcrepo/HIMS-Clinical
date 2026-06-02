-- ============================================================
-- V075 — Clinical Masters: Frequency + Order Sets
-- ============================================================

-- 1. Frequency master (dosing frequencies for prescriptions)
CREATE TABLE IF NOT EXISTS frequencies (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL,
    value        INTEGER      NOT NULL DEFAULT 1,
    status       SMALLINT     NOT NULL DEFAULT 1,    -- EntityStatus ordinal: 0=INACTIVE, 1=ACTIVE, 2=DELETED
    created_by   UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by  UUID,
    modified_at  TIMESTAMPTZ
);

-- Seed common frequencies
INSERT INTO frequencies (name, value, status) VALUES
  ('OD (Once Daily)',      1, 1),
  ('BD / BID (Twice Daily)',   2, 1),
  ('TDS (Thrice Daily)',   3, 1),
  ('QID (Four Times Daily)', 4, 1),
  ('HS (Bedtime)',         1, 1),
  ('SOS (As Needed)',      1, 1),
  ('1-0-1',                2, 1),
  ('1-1-1',                3, 1),
  ('1-0-0',                1, 1),
  ('0-0-1',                1, 1),
  ('Stat (Immediately)',   1, 1)
ON CONFLICT DO NOTHING;

-- 2. Order Sets
CREATE TABLE IF NOT EXISTS order_sets (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    description   VARCHAR(500),
    set_type      VARCHAR(30)  NOT NULL DEFAULT 'BOTH',   -- PRESCRIPTION | DIAGNOSTICS | BOTH
    is_outpatient BOOLEAN      NOT NULL DEFAULT TRUE,
    is_favorite   BOOLEAN      NOT NULL DEFAULT FALSE,
    scope         VARCHAR(20)  NOT NULL DEFAULT 'GLOBAL', -- GLOBAL | DEPARTMENT | CONSULTANT
    consultant_id UUID,
    department_id UUID,
    status        SMALLINT     NOT NULL DEFAULT 1,
    created_by    UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ
);

-- 3. Order Set Items
CREATE TABLE IF NOT EXISTS order_set_items (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_set_id            UUID        NOT NULL REFERENCES order_sets(id) ON DELETE CASCADE,
    item_type               VARCHAR(30) NOT NULL DEFAULT 'PHARMACY',  -- PHARMACY | DIAGNOSTIC | PROCEDURE
    service_catalog_item_id UUID,
    item_name               VARCHAR(200),
    diagnostic_type         VARCHAR(20),
    quantity                INTEGER     NOT NULL DEFAULT 1,
    instruction             VARCHAR(200),
    frequency               VARCHAR(50),
    duration                VARCHAR(50),
    route_label             VARCHAR(50)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_frequencies_status    ON frequencies(status);
CREATE INDEX IF NOT EXISTS idx_order_sets_status     ON order_sets(status);
CREATE INDEX IF NOT EXISTS idx_order_sets_is_fav     ON order_sets(is_favorite, consultant_id);
CREATE INDEX IF NOT EXISTS idx_order_set_items_set   ON order_set_items(order_set_id);
