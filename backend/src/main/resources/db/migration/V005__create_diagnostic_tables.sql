-- V005__create_diagnostic_tables.sql  (PostgreSQL 16)
-- Tables: specimens, diagnostic_templates, diagnostic_template_lines,
--         diagnostic_orders, diagnostic_order_lines

-- ─────────────────────────────────────────────────────────
-- 1. SPECIMENS
-- ─────────────────────────────────────────────────────────
CREATE TABLE specimens (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_specimens PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────
-- 2. DIAGNOSTIC_TEMPLATES (order sets / panels)
-- ─────────────────────────────────────────────────────────
CREATE TABLE diagnostic_templates (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    diagnostic_type SMALLINT     NOT NULL CHECK (diagnostic_type IN (0, 1)),
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    charge_id       UUID,
    specimen_id     UUID,
    reference_range VARCHAR(200),
    unit            VARCHAR(50),
    lab_template_type VARCHAR(30),
    template_html   TEXT,
    CONSTRAINT pk_diagnostic_templates PRIMARY KEY (id)
);

COMMENT ON COLUMN diagnostic_templates.diagnostic_type IS '0=LAB, 1=RADIOLOGY';

CREATE TABLE diagnostic_template_lines (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    template_id             UUID         NOT NULL,
    service_catalog_item_id UUID         NOT NULL,
    instruction             VARCHAR(255),
    CONSTRAINT pk_diag_template_lines PRIMARY KEY (id),
    CONSTRAINT fk_dtl_template FOREIGN KEY (template_id)             REFERENCES diagnostic_templates(id) ON DELETE CASCADE,
    CONSTRAINT fk_dtl_item     FOREIGN KEY (service_catalog_item_id) REFERENCES service_catalog_items(id)
);

-- ─────────────────────────────────────────────────────────
-- 3. DIAGNOSTIC_ORDERS
-- ─────────────────────────────────────────────────────────
CREATE TABLE diagnostic_orders (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    encounter_id    UUID        NOT NULL,
    patient_id      UUID        NOT NULL,
    provider_id     UUID,
    diagnostic_type SMALLINT    NOT NULL CHECK (diagnostic_type IN (0, 1)),
    sequence_number VARCHAR(40),
    order_date      DATE        NOT NULL,
    order_status    SMALLINT    NOT NULL DEFAULT 0,
    status          SMALLINT    NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_diagnostic_orders PRIMARY KEY (id),
    CONSTRAINT fk_do_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_encounters(id),
    CONSTRAINT fk_do_patient   FOREIGN KEY (patient_id)   REFERENCES patients(id)
);

CREATE INDEX idx_do_encounter  ON diagnostic_orders(encounter_id);
CREATE INDEX idx_do_order_date ON diagnostic_orders(order_date);
CREATE INDEX idx_do_status     ON diagnostic_orders(status);

-- ─────────────────────────────────────────────────────────
-- 4. DIAGNOSTIC_ORDER_LINES (replaces diagnostic_detail)
-- ─────────────────────────────────────────────────────────
CREATE TABLE diagnostic_order_lines (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    order_id                UUID         NOT NULL,
    service_catalog_item_id UUID         NOT NULL,
    specimen_id             UUID,
    instruction             VARCHAR(255),
    status                  SMALLINT     NOT NULL DEFAULT 0 CHECK (status IN (0, 1, 2)),
    result_value            TEXT,
    result_unit             VARCHAR(50),
    reference_range         VARCHAR(100),
    result_recorded_at      TIMESTAMPTZ,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_diagnostic_order_lines PRIMARY KEY (id),
    CONSTRAINT fk_dol_order    FOREIGN KEY (order_id)               REFERENCES diagnostic_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_dol_item     FOREIGN KEY (service_catalog_item_id) REFERENCES service_catalog_items(id),
    CONSTRAINT fk_dol_specimen FOREIGN KEY (specimen_id)             REFERENCES specimens(id)
);

COMMENT ON COLUMN diagnostic_order_lines.status IS '0=ORDERED, 1=BILLED, 2=CANCELLED';

CREATE INDEX idx_dol_order ON diagnostic_order_lines(order_id);
