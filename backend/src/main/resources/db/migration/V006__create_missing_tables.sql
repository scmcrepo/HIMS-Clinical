-- V006__create_missing_tables.sql  (PostgreSQL 16)
-- Tables required by modules added in chunk 5+:
--   purchase_orders, purchase_orders_lines (full PO workflow)
--   stock_adjustment, stock_adjustment_lines (manual stock correction)
--   bill_detail_modified (charge modification audit trail)
--   sms_logs (SMS send history)

-- ─────────────────────────────────────────────────────────
-- 1. PURCHASE_ORDERS
-- ─────────────────────────────────────────────────────────
CREATE TABLE purchase_orders (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    supplier_id     UUID,
    department_id   UUID         NOT NULL,
    order_date      DATE         NOT NULL DEFAULT CURRENT_DATE,
    sequence_number VARCHAR(40),
    order_status    VARCHAR(20)  NOT NULL DEFAULT 'ORDERED'
        CHECK (order_status IN ('ORDERED','PARTIALLY_RECEIVED','RECEIVED')),
    notes           TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    status          SMALLINT     NOT NULL DEFAULT 1,
    CONSTRAINT pk_purchase_orders PRIMARY KEY (id)
);
CREATE INDEX idx_po_date ON purchase_orders(order_date);

CREATE TABLE purchase_orders_lines (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    order_id         UUID           NOT NULL,
    item_id          UUID           NOT NULL,
    quantity         INTEGER        NOT NULL,
    received_quantity INTEGER       NOT NULL DEFAULT 0,
    unit_rate        NUMERIC(12,4),
    CONSTRAINT pk_pol PRIMARY KEY (id),
    CONSTRAINT fk_pol_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────────────────
-- 2. STOCK_ADJUSTMENT
-- ─────────────────────────────────────────────────────────
CREATE TABLE stock_adjustment (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    department_id   UUID        NOT NULL,
    sequence_number VARCHAR(40),
    adjustment_date DATE        NOT NULL DEFAULT CURRENT_DATE,
    notes           TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status          SMALLINT    NOT NULL DEFAULT 1,
    CONSTRAINT pk_stock_adjustment PRIMARY KEY (id)
);
CREATE INDEX idx_sa_date ON stock_adjustment(adjustment_date);

CREATE TABLE stock_adjustment_lines (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    adjustment_id       UUID        NOT NULL,
    inventory_batch_id  UUID        NOT NULL,
    adjustment_qty      INTEGER     NOT NULL,
    adjustment_type     VARCHAR(10) NOT NULL CHECK (adjustment_type IN ('ADD','SUBTRACT')),
    reason              VARCHAR(255),
    CONSTRAINT pk_sal PRIMARY KEY (id),
    CONSTRAINT fk_sal_adj FOREIGN KEY (adjustment_id) REFERENCES stock_adjustment(id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────────────────
-- 3. BILL_DETAIL_MODIFIED  (audit trail for updateCharge)
--    Records old values before a charge line is modified.
-- ─────────────────────────────────────────────────────────
CREATE TABLE bill_detail_modified (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    charge_line_item_id UUID        NOT NULL,
    previous_amount     BIGINT      NOT NULL,
    previous_rate       BIGINT      NOT NULL,
    previous_quantity   INTEGER     NOT NULL,
    reason              VARCHAR(500),
    modified_by         UUID,
    modified_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_bill_detail_modified PRIMARY KEY (id),
    CONSTRAINT fk_bdm_line FOREIGN KEY (charge_line_item_id)
        REFERENCES charge_line_items(id)
);
CREATE INDEX idx_bdm_line ON bill_detail_modified(charge_line_item_id);

-- ─────────────────────────────────────────────────────────
-- 4. SMS_LOGS  (tracks every SMS send attempt)
-- ─────────────────────────────────────────────────────────
CREATE TABLE sms_logs (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    to_number     VARCHAR(20) NOT NULL,
    template_key  VARCHAR(60),
    message_body  TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'SENT',
    error_message TEXT,
    sent_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_sms_logs PRIMARY KEY (id)
);
CREATE INDEX idx_sms_to ON sms_logs(to_number);
CREATE INDEX idx_sms_sent_at ON sms_logs(sent_at);

-- ─────────────────────────────────────────────────────────
-- 5. CUSTOMER  (walk-in pharmacy customers without patient record)
-- ─────────────────────────────────────────────────────────
CREATE TABLE customers (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    address     TEXT,
    contact_no  VARCHAR(20),
    email       VARCHAR(120),
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_customers PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────
-- 6. INSURANCE
-- ─────────────────────────────────────────────────────────
CREATE TABLE insurances (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id      UUID,
    bill_id         UUID,
    encounter_id    UUID,
    insurer_name    VARCHAR(150),
    policy_number   VARCHAR(80),
    pre_auth_type   VARCHAR(40),
    pre_auth_number VARCHAR(80),
    pre_auth_amount BIGINT,
    pre_auth_date   DATE,
    communication   VARCHAR(40),
    insurance_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    rejection_reason VARCHAR(500),
    status          SMALLINT    NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_insurances PRIMARY KEY (id)
);
CREATE INDEX idx_ins_patient ON insurances(patient_id);
