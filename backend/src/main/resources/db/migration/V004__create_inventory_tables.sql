-- V004__create_inventory_tables.sql  (PostgreSQL 16)
-- Tables: molecules, units_of_measure, suppliers, inventory_items,
--         inventory_batches, purchase_receipts, purchase_receipt_lines,
--         pending_receipts, replenishment_requests, replenishment_request_lines,
--         pharmacy_sales, pharmacy_sale_lines, pharmacy_returns

-- ─────────────────────────────────────────────────────────
-- 1. MOLECULES (drug generic names)
-- ─────────────────────────────────────────────────────────
CREATE TABLE molecules (
    id     UUID         NOT NULL DEFAULT gen_random_uuid(),
    name   VARCHAR(150) NOT NULL,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_molecules PRIMARY KEY (id),
    CONSTRAINT uq_molecules_name UNIQUE (name)
);

-- ─────────────────────────────────────────────────────────
-- 2. UNITS_OF_MEASURE
-- ─────────────────────────────────────────────────────────
CREATE TABLE units_of_measure (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(50) NOT NULL,
    symbol      VARCHAR(10),
    status      SMALLINT    NOT NULL DEFAULT 1,
    created_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_units_of_measure PRIMARY KEY (id),
    CONSTRAINT uq_uom_name UNIQUE (name)
);

-- ─────────────────────────────────────────────────────────
-- 3. SUPPLIERS
-- ─────────────────────────────────────────────────────────
CREATE TABLE suppliers (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    contact     VARCHAR(20),
    email       VARCHAR(120),
    address     TEXT,
    gstin       VARCHAR(20),
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_suppliers PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────
-- 4. INVENTORY_ITEMS
-- ─────────────────────────────────────────────────────────
CREATE TABLE inventory_items (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                  VARCHAR(150) NOT NULL,
    molecule_id           UUID,
    unit_of_measure_id    UUID,
    conversion_factor     INTEGER      NOT NULL DEFAULT 1,
    requires_batch        BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_prescription BOOLEAN      NOT NULL DEFAULT FALSE,
    reorder_level         NUMERIC(10,2) NOT NULL DEFAULT 0,
    hsn_code              VARCHAR(20),
    tax_rate              NUMERIC(5,2) NOT NULL DEFAULT 0,
    status                SMALLINT     NOT NULL DEFAULT 1,
    created_by            UUID,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by           UUID,
    modified_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_inventory_items PRIMARY KEY (id),
    CONSTRAINT fk_ii_molecule FOREIGN KEY (molecule_id)        REFERENCES molecules(id),
    CONSTRAINT fk_ii_uom      FOREIGN KEY (unit_of_measure_id) REFERENCES units_of_measure(id)
);

COMMENT ON COLUMN inventory_items.conversion_factor IS 'e.g. 10 strips per box';

CREATE INDEX idx_ii_status ON inventory_items(status);

-- ─────────────────────────────────────────────────────────
-- 5. INVENTORY_BATCHES (replaces stock table)
--    One row = one batch of one item in one department.
--    current_quantity mutated atomically by service layer.
-- ─────────────────────────────────────────────────────────
CREATE TABLE inventory_batches (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    item_id              UUID           NOT NULL,
    department_id        UUID           NOT NULL,
    batch_number         VARCHAR(50),
    current_quantity     INTEGER        NOT NULL DEFAULT 0,
    purchase_rate        NUMERIC(12, 4) NOT NULL,
    maximum_retail_price NUMERIC(12, 4) NOT NULL,
    selling_rate         NUMERIC(12, 4) NOT NULL,
    expiry_date          DATE,
    -- UUID of originating transaction (purchase_receipt, pending_receipt, etc.)
    source_transaction_id UUID          NOT NULL,
    created_by           UUID,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_inventory_batches PRIMARY KEY (id),
    CONSTRAINT fk_ib_item FOREIGN KEY (item_id)       REFERENCES inventory_items(id),
    CONSTRAINT fk_ib_dept FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE INDEX idx_ib_item_dept ON inventory_batches(item_id, department_id);
CREATE INDEX idx_ib_expiry    ON inventory_batches(expiry_date);
CREATE INDEX idx_ib_batch_no  ON inventory_batches(batch_number);
CREATE INDEX idx_ib_source    ON inventory_batches(source_transaction_id);

-- ─────────────────────────────────────────────────────────
-- 6. PURCHASE_RECEIPTS (replaces goods_received table)
-- ─────────────────────────────────────────────────────────
CREATE TABLE purchase_receipts (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    supplier_id     UUID,
    department_id   UUID        NOT NULL,
    receipt_date    DATE        NOT NULL,
    invoice_number  VARCHAR(60),
    invoice_date    DATE,
    sequence_number VARCHAR(40),
    receipt_status  SMALLINT    NOT NULL DEFAULT 0,
    status          SMALLINT    NOT NULL DEFAULT 1,
    notes           TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_purchase_receipts PRIMARY KEY (id),
    CONSTRAINT fk_pr_supplier FOREIGN KEY (supplier_id)   REFERENCES suppliers(id),
    CONSTRAINT fk_pr_dept     FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE INDEX idx_pr_date ON purchase_receipts(receipt_date);
CREATE INDEX idx_pr_status ON purchase_receipts(status);

CREATE TABLE purchase_receipt_lines (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    receipt_id           UUID           NOT NULL,
    item_id              UUID           NOT NULL,
    batch_number         VARCHAR(50),
    quantity             INTEGER        NOT NULL,
    purchase_rate        NUMERIC(12, 4) NOT NULL,
    maximum_retail_price NUMERIC(12, 4) NOT NULL,
    selling_rate         NUMERIC(12, 4) NOT NULL,
    expiry_date          DATE,
    CONSTRAINT pk_purchase_receipt_lines PRIMARY KEY (id),
    CONSTRAINT fk_prl_receipt FOREIGN KEY (receipt_id) REFERENCES purchase_receipts(id) ON DELETE CASCADE,
    CONSTRAINT fk_prl_item    FOREIGN KEY (item_id)    REFERENCES inventory_items(id)
);

-- ─────────────────────────────────────────────────────────
-- 7. PENDING_RECEIPTS (replaces temp_stock)
-- ─────────────────────────────────────────────────────────
CREATE TABLE pending_receipts (
    id            UUID           NOT NULL DEFAULT gen_random_uuid(),
    item_id       UUID           NOT NULL,
    department_id UUID           NOT NULL,
    quantity      INTEGER        NOT NULL,
    purchase_rate NUMERIC(12, 4) NOT NULL,
    selling_rate  NUMERIC(12, 4) NOT NULL,
    expiry_date   DATE,
    is_processed  BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by    UUID,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_pending_receipts PRIMARY KEY (id),
    CONSTRAINT fk_pend_item FOREIGN KEY (item_id)       REFERENCES inventory_items(id),
    CONSTRAINT fk_pend_dept FOREIGN KEY (department_id) REFERENCES departments(id)
);

-- ─────────────────────────────────────────────────────────
-- 8. REPLENISHMENT_REQUESTS (replaces stock_indent)
-- ─────────────────────────────────────────────────────────
CREATE TABLE replenishment_requests (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    from_dept_id    UUID        NOT NULL,
    to_dept_id      UUID        NOT NULL,
    sequence_number VARCHAR(40),
    status          SMALLINT    NOT NULL DEFAULT 0 CHECK (status IN (0, 1, 2)),
    requested_date  DATE        NOT NULL,
    notes           TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_replenishment_requests PRIMARY KEY (id),
    CONSTRAINT fk_rr_from FOREIGN KEY (from_dept_id) REFERENCES departments(id),
    CONSTRAINT fk_rr_to   FOREIGN KEY (to_dept_id)   REFERENCES departments(id)
);

COMMENT ON COLUMN replenishment_requests.status IS '0=REQUESTED, 1=ISSUED, 2=PARTIALLY_ISSUED';

CREATE TABLE replenishment_request_lines (
    id            UUID    NOT NULL DEFAULT gen_random_uuid(),
    request_id    UUID    NOT NULL,
    item_id       UUID    NOT NULL,
    requested_qty INTEGER NOT NULL,
    issued_qty    INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_replenishment_request_lines PRIMARY KEY (id),
    CONSTRAINT fk_rrl_request FOREIGN KEY (request_id) REFERENCES replenishment_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_rrl_item    FOREIGN KEY (item_id)    REFERENCES inventory_items(id)
);

-- ─────────────────────────────────────────────────────────
-- 9. PHARMACY_SALES
-- ─────────────────────────────────────────────────────────
CREATE TABLE pharmacy_sales (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    patient_id      UUID,
    encounter_id    UUID,
    department_id   UUID           NOT NULL,
    sequence_number VARCHAR(40),
    sale_date       DATE           NOT NULL,
    total_amount    NUMERIC(14, 4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14, 4) NOT NULL DEFAULT 0,
    sale_status     SMALLINT       NOT NULL DEFAULT 0 CHECK (sale_status IN (0, 1, 2, 3)),
    status          SMALLINT       NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_pharmacy_sales PRIMARY KEY (id),
    CONSTRAINT fk_ps_patient   FOREIGN KEY (patient_id)   REFERENCES patients(id),
    CONSTRAINT fk_ps_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_encounters(id),
    CONSTRAINT fk_ps_dept      FOREIGN KEY (department_id) REFERENCES departments(id)
);

COMMENT ON COLUMN pharmacy_sales.sale_status IS '0=DRAFT, 1=BILLED, 2=SETTLED, 3=WITH_DUE';

CREATE INDEX idx_ps_date    ON pharmacy_sales(sale_date);
CREATE INDEX idx_ps_patient ON pharmacy_sales(patient_id);
CREATE INDEX idx_ps_status ON pharmacy_sales(status);

CREATE TABLE pharmacy_sale_lines (
    id                 UUID           NOT NULL DEFAULT gen_random_uuid(),
    sale_id            UUID           NOT NULL,
    inventory_batch_id UUID           NOT NULL,
    quantity           INTEGER        NOT NULL,
    unit_rate          NUMERIC(12, 4) NOT NULL,
    amount             NUMERIC(14, 4) NOT NULL,
    discount_amount    NUMERIC(14, 4) NOT NULL DEFAULT 0,
    created_by         UUID,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_pharmacy_sale_lines PRIMARY KEY (id),
    CONSTRAINT fk_psl_sale  FOREIGN KEY (sale_id)            REFERENCES pharmacy_sales(id) ON DELETE CASCADE,
    CONSTRAINT fk_psl_batch FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id)
);

-- ─────────────────────────────────────────────────────────
-- 10. PHARMACY_RETURNS
-- ─────────────────────────────────────────────────────────
CREATE TABLE pharmacy_returns (
    id            UUID           NOT NULL DEFAULT gen_random_uuid(),
    sale_id       UUID           NOT NULL,
    return_date   DATE           NOT NULL,
    refund_amount NUMERIC(14, 4) NOT NULL DEFAULT 0,
    reason        VARCHAR(500),
    created_by    UUID,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_pharmacy_returns PRIMARY KEY (id),
    CONSTRAINT fk_ret_sale FOREIGN KEY (sale_id) REFERENCES pharmacy_sales(id)
);
