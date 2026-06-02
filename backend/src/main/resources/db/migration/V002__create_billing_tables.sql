-- V002__create_billing_tables.sql  (PostgreSQL 16)
-- Tables: service_categories, service_catalog_items, pricing_tiers,
--         payors, referrals, bills, charge_line_items,
--         charge_line_modifications, discount_adjustments,
--         line_item_discounts, payments, bill_audit
--
-- PostgreSQL notes vs. MySQL original:
--   • UUID native type — no BINARY(16) or UNHEX() hacks
--   • BIGINT for money (paise/cents) — unchanged
--   • SMALLINT replaces TINYINT
--   • JSONB replaces JSON (binary, indexable, faster operators)
--   • TIMESTAMPTZ replaces DATETIME(3) — timezone-aware
--   • No ENGINE=InnoDB clause
--   • CHECK constraints are enforced (PostgreSQL always validates them)

-- ─────────────────────────────────────────────────────────
-- 1. SERVICE_CATEGORIES
-- ─────────────────────────────────────────────────────────
CREATE TABLE service_categories (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL,
    category_type SMALLINT     NOT NULL,
    status        SMALLINT     NOT NULL DEFAULT 1,
    created_by    UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_service_categories PRIMARY KEY (id),
    CONSTRAINT uq_service_categories_name UNIQUE (name)
);

COMMENT ON COLUMN service_categories.category_type
    IS 'ServiceCategory enum: 0=DIAGNOSTICS, 1=ROOM_CHARGE, 2=PHARMACY, 3=CONSULTATION, 4=PROCEDURE, …';

-- ─────────────────────────────────────────────────────────
-- 2. SERVICE_CATALOG_ITEMS (replaces charges table)
-- ─────────────────────────────────────────────────────────
CREATE TABLE service_catalog_items (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    name           VARCHAR(150) NOT NULL,
    category_id    UUID         NOT NULL,
    service_type   SMALLINT     NOT NULL DEFAULT 0,
    requires_order BOOLEAN      NOT NULL DEFAULT FALSE,
    status         SMALLINT     NOT NULL DEFAULT 1,
    created_by     UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by    UUID,
    modified_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_service_catalog_items PRIMARY KEY (id),
    CONSTRAINT fk_sci_category FOREIGN KEY (category_id) REFERENCES service_categories(id)
);

COMMENT ON COLUMN service_catalog_items.service_type IS '0=INDIVIDUAL, 1=PACKAGE, 2=INPATIENT';

CREATE INDEX idx_sci_category ON service_catalog_items(category_id);
CREATE INDEX idx_sci_status    ON service_catalog_items(status);

-- ─────────────────────────────────────────────────────────
-- 3. PRICING_TIERS (replaces tariffs table)
-- ─────────────────────────────────────────────────────────
CREATE TABLE pricing_tiers (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    service_catalog_item_id UUID        NOT NULL,
    bill_type               SMALLINT    NOT NULL,
    unit_rate               BIGINT      NOT NULL DEFAULT 0,
    status                  SMALLINT    NOT NULL DEFAULT 1,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by             UUID,
    modified_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_pricing_tiers     PRIMARY KEY (id),
    CONSTRAINT fk_pt_item           FOREIGN KEY (service_catalog_item_id) REFERENCES service_catalog_items(id),
    CONSTRAINT uq_pt_item_bill_type UNIQUE (service_catalog_item_id, bill_type)
);

COMMENT ON COLUMN pricing_tiers.bill_type  IS '0=CASH, 1=CREDIT, 2=INSURANCE';
COMMENT ON COLUMN pricing_tiers.unit_rate  IS 'Stored in smallest currency unit (paise / cents)';

CREATE INDEX idx_pt_item ON pricing_tiers(service_catalog_item_id);

-- ─────────────────────────────────────────────────────────
-- 4. PAYORS
-- ─────────────────────────────────────────────────────────
CREATE TABLE payors (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    code        VARCHAR(30),
    contact     VARCHAR(20),
    email       VARCHAR(120),
    status      SMALLINT     NOT NULL DEFAULT 1,
    type        VARCHAR(40),
    address     TEXT,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_payors PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────
-- 5. REFERRALS
-- ─────────────────────────────────────────────────────────
CREATE TABLE referrals (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(50),
    contact    VARCHAR(20),
    status     SMALLINT     NOT NULL DEFAULT 1,
    created_by UUID,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_referrals PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────
-- 6. BILLS
--    due_amount is NEVER stored:
--    bill_amount - discount_total - payment_total - service_refund_total + refund_total
--    (computed in BillingEngine.computeDueAmount())
-- ─────────────────────────────────────────────────────────
CREATE TABLE bills (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id           UUID        NOT NULL,
    encounter_id         UUID,
    primary_provider_id  UUID,
    payor_id             UUID,
    referral_id          UUID,
    bill_amount          BIGINT      NOT NULL DEFAULT 0,
    discount_total       BIGINT      NOT NULL DEFAULT 0,
    payment_total        BIGINT      NOT NULL DEFAULT 0,
    service_refund_total BIGINT      NOT NULL DEFAULT 0,
    discount_refund_total BIGINT     NOT NULL DEFAULT 0,
    refund_total         BIGINT      NOT NULL DEFAULT 0,
    bill_status          SMALLINT    NOT NULL DEFAULT 0,
    status               SMALLINT    NOT NULL DEFAULT 1,
    bill_type            SMALLINT    NOT NULL,
    encounter_type       SMALLINT    NOT NULL,
    bill_date            DATE,
    admission_at         TIMESTAMPTZ,
    discharge_at         TIMESTAMPTZ,
    bed_number           VARCHAR(20),
    cancelled_at         TIMESTAMPTZ,
    created_by           UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by          UUID,
    modified_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_bills PRIMARY KEY (id),
    -- patients FK added in V003 after patients table exists
    CONSTRAINT chk_bills_status        CHECK (bill_status   IN (0, 1, 2, 3, 4)),
    CONSTRAINT chk_bills_bill_type     CHECK (bill_type     IN (0, 1, 2)),
    CONSTRAINT chk_bills_encounter_type CHECK (encounter_type IN (0, 1))
);

COMMENT ON COLUMN bills.bill_status    IS '0=DRAFT, 1=SETTLED, 2=PARTIALLY_SETTLED, 3=REFUNDED, 4=CANCELLED';
COMMENT ON COLUMN bills.bill_type      IS '0=CASH, 1=CREDIT, 2=INSURANCE';
COMMENT ON COLUMN bills.encounter_type IS '0=OUTPATIENT, 1=INPATIENT';

CREATE INDEX idx_bills_patient    ON bills(patient_id);
CREATE INDEX idx_bills_encounter  ON bills(encounter_id);
CREATE INDEX idx_bills_status     ON bills(bill_status);
CREATE INDEX idx_bills_bill_date  ON bills(bill_date);

-- ─────────────────────────────────────────────────────────
-- 7. CHARGE_LINE_ITEMS (replaces bill_details table)
-- ─────────────────────────────────────────────────────────
CREATE TABLE charge_line_items (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    bill_id                 UUID        NOT NULL,
    service_catalog_item_id UUID        NOT NULL,
    pricing_tier_id         UUID,
    diagnostic_order_id     UUID,
    pharmacy_sale_id        UUID,
    pharmacy_return_id      UUID,
    package_group_id        UUID,
    amount                  BIGINT      NOT NULL DEFAULT 0,
    unit_rate               BIGINT      NOT NULL DEFAULT 0,
    quantity                INTEGER     NOT NULL DEFAULT 1,
    discount_amount         BIGINT      NOT NULL DEFAULT 0,
    disallowed_amount       BIGINT      NOT NULL DEFAULT 0,
    -- NULL = active; mirrors legacy ServiceStatus null=active behaviour
    line_status             INTEGER,
    status                  SMALLINT    NOT NULL DEFAULT 1,
    bed_charge_from         DATE,
    bed_charge_to           DATE,
    cancelled_at            TIMESTAMPTZ,
    cancel_reason           VARCHAR(500),
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by             UUID,
    modified_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_charge_line_items PRIMARY KEY (id),
    CONSTRAINT fk_cli_bill FOREIGN KEY (bill_id)                 REFERENCES bills(id) ON DELETE CASCADE,
    CONSTRAINT fk_cli_item FOREIGN KEY (service_catalog_item_id) REFERENCES service_catalog_items(id),
    CONSTRAINT chk_cli_status CHECK (line_status IS NULL OR line_status IN (1, 2, 3))
);

COMMENT ON COLUMN charge_line_items.line_status      IS 'NULL=active, 1=CANCELLED, 2=MODIFIED, 3=REFUNDED';
COMMENT ON COLUMN charge_line_items.disallowed_amount IS 'Insurance disallowance; only via updateBillDetails endpoint';
COMMENT ON COLUMN charge_line_items.package_group_id  IS 'Groups lines belonging to the same IP package charge';

CREATE INDEX idx_cli_bill   ON charge_line_items(bill_id);
CREATE INDEX idx_cli_status ON charge_line_items(line_status);
CREATE INDEX idx_cli_pkg    ON charge_line_items(package_group_id);

-- ─────────────────────────────────────────────────────────
-- 8. CHARGE_LINE_MODIFICATIONS (audit trail for updateCharge)
-- ─────────────────────────────────────────────────────────
CREATE TABLE charge_line_modifications (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    charge_line_item_id UUID        NOT NULL,
    previous_amount     BIGINT      NOT NULL,
    previous_rate       BIGINT      NOT NULL,
    previous_quantity   INTEGER     NOT NULL,
    reason              VARCHAR(500),
    modified_by         UUID,
    modified_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_charge_line_modifications PRIMARY KEY (id),
    CONSTRAINT fk_clm_line FOREIGN KEY (charge_line_item_id) REFERENCES charge_line_items(id)
);

CREATE INDEX idx_clm_line ON charge_line_modifications(charge_line_item_id);

-- ─────────────────────────────────────────────────────────
-- 9. DISCOUNT_ADJUSTMENTS
-- ─────────────────────────────────────────────────────────
CREATE TABLE discount_adjustments (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    bill_id        UUID        NOT NULL,
    total_discount BIGINT      NOT NULL DEFAULT 0,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    reason         VARCHAR(255),
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_discount_adjustments PRIMARY KEY (id),
    CONSTRAINT fk_da_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
);

CREATE INDEX idx_da_bill ON discount_adjustments(bill_id);

-- ─────────────────────────────────────────────────────────
-- 10. LINE_ITEM_DISCOUNTS
-- ─────────────────────────────────────────────────────────
CREATE TABLE line_item_discounts (
    id                     UUID   NOT NULL DEFAULT gen_random_uuid(),
    discount_adjustment_id UUID   NOT NULL,
    charge_line_item_id    UUID   NOT NULL,
    amount                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_line_item_discounts PRIMARY KEY (id),
    CONSTRAINT fk_lid_adjustment FOREIGN KEY (discount_adjustment_id) REFERENCES discount_adjustments(id) ON DELETE CASCADE,
    CONSTRAINT fk_lid_line       FOREIGN KEY (charge_line_item_id)    REFERENCES charge_line_items(id)
);

CREATE INDEX idx_lid_adjustment ON line_item_discounts(discount_adjustment_id);

-- ─────────────────────────────────────────────────────────
-- 11. PAYMENTS (replaces collections table)
-- ─────────────────────────────────────────────────────────
CREATE TABLE payments (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    bill_id         UUID        NOT NULL,
    patient_id      UUID,
    amount          BIGINT      NOT NULL,
    payment_mode    VARCHAR(30) NOT NULL,
    payment_type    VARCHAR(30) NOT NULL,
    payment_date    DATE        NOT NULL,
    status          VARCHAR(20) DEFAULT 'Active',
    sequence_number VARCHAR(40),
    notes           TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_payments_bill FOREIGN KEY (bill_id) REFERENCES bills(id)
);


CREATE INDEX idx_payments_bill ON payments(bill_id);
CREATE INDEX idx_payments_type ON payments(payment_type);

-- ─────────────────────────────────────────────────────────
-- 12. BILL_AUDIT (immutable snapshot after every billing mutation)
-- ─────────────────────────────────────────────────────────
CREATE TABLE bill_audit (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    bill_id              UUID        NOT NULL,
    bill_amount          BIGINT      NOT NULL,
    discount_total       BIGINT      NOT NULL,
    payment_total        BIGINT      NOT NULL,
    service_refund_total BIGINT      NOT NULL,
    refund_total         BIGINT      NOT NULL,
    status               SMALLINT    NOT NULL,
    operation_type       VARCHAR(60) NOT NULL,
    performed_by         UUID,
    performed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_bill_audit PRIMARY KEY (id)
);

COMMENT ON COLUMN bill_audit.operation_type IS 'ADD_CHARGE, RECORD_PAYMENT, GENERATE_BILL, APPLY_DISCOUNT, REFUND, …';

CREATE INDEX idx_ba_bill         ON bill_audit(bill_id);
CREATE INDEX idx_ba_performed_at ON bill_audit(performed_at);
