-- V008__create_core_missing_tables.sql
-- Creates all tables required by modules identified in gap analysis:
-- charges/tariffs, consultants, visits, payors, customers, departments,
-- categories, specimens, temp_stock, payments, account_units, stock movements

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. CATEGORIES (consumed by ChargeService, ItemService, DiagnosticService)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE categories (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL,
    category_type VARCHAR(50),
    status        SMALLINT    NOT NULL DEFAULT 1,
    created_by    UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by   UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_categories PRIMARY KEY (id)
);
CREATE INDEX idx_cat_type ON categories(category_type);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. CHARGES + TARIFFS (price master with versioning)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE charges (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(200) NOT NULL,
    category_id   UUID        REFERENCES categories(id),
    charge_type   SMALLINT    NOT NULL DEFAULT 0,
    start_date    DATE,
    end_date      DATE,
    status        SMALLINT    NOT NULL DEFAULT 1,
    created_by    UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by   UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_charges PRIMARY KEY (id)
);
CREATE INDEX idx_charge_name ON charges(name);
CREATE INDEX idx_charge_cat  ON charges(category_id);

CREATE TABLE tariffs (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    charge_id   UUID        NOT NULL REFERENCES charges(id) ON DELETE CASCADE,
    payor_id    UUID,
    bill_type   VARCHAR(20) NOT NULL DEFAULT 'CASH',
    rate        BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_tariffs PRIMARY KEY (id)
);
CREATE INDEX idx_tariff_charge ON tariffs(charge_id);

CREATE TABLE charge_package_includes (
    charge_id   UUID NOT NULL REFERENCES charges(id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    PRIMARY KEY (charge_id, category_id)
);

CREATE TABLE charge_package_excludes (
    charge_id   UUID NOT NULL REFERENCES charges(id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    PRIMARY KEY (charge_id, category_id)
);

-- 3. CONSULTANTS (skipped — already in V003)

-- 4. DEPARTMENTS (skipped — already in V001)

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. VISITS (clinical encounter sessions)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE visits (
    id                     UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id             UUID        NOT NULL,
    consultant_id          UUID,
    appointment_id         UUID,
    bill_id                UUID,
    visit_type             SMALLINT    NOT NULL DEFAULT 0,
    visit_date             DATE        NOT NULL DEFAULT CURRENT_DATE,
    checked_time           TIME,
    discharge_date         DATE,
    last_bed_id            UUID,
    bed_status             BOOLEAN     NOT NULL DEFAULT FALSE,
    bill_status            BOOLEAN     NOT NULL DEFAULT FALSE,
    diagnosis              TEXT,
    diagnosis_code_id      UUID,
    visit_status           SMALLINT    NOT NULL DEFAULT 0,
    visit_mode             SMALLINT    NOT NULL DEFAULT 0,
    casesheet_created_date TIMESTAMPTZ,
    bed_no                 VARCHAR(40),
    is_cancelled           BOOLEAN     NOT NULL DEFAULT FALSE,
    status                 SMALLINT    NOT NULL DEFAULT 1,
    created_by             UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by            UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_visits PRIMARY KEY (id)
);
CREATE INDEX idx_visit_patient    ON visits(patient_id);
CREATE INDEX idx_visit_consultant ON visits(consultant_id);
CREATE INDEX idx_visit_date       ON visits(visit_date);
CREATE INDEX idx_visit_active     ON visits(patient_id, bed_status, bill_status);

-- 6. PAYORS (skipped — already in V002)

-- 7. SPECIMENS (skipped — already in V005)

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. TEMP_STOCK (staging for goods receipt processing)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE temp_stock (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    item_id           UUID           NOT NULL,
    department_id     UUID           NOT NULL,
    batch_number      VARCHAR(50),
    quantity          INTEGER        NOT NULL,
    purchase_rate     NUMERIC(12, 4) NOT NULL,
    mrp               NUMERIC(12, 4) NOT NULL,
    selling_rate      NUMERIC(12, 4) NOT NULL,
    expiry_date       DATE,
    source_receipt_id UUID,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_temp_stock PRIMARY KEY (id)
);

-- 9. PAYMENTS (skipped — already in V002)

-- 10. ACCOUNT_UNITS (skipped — already in V001)

-- ─────────────────────────────────────────────────────────────────────────────
-- 11. STOCK MOVEMENT TABLES
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE stock_indents (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    indent_from_dept_id UUID        NOT NULL,
    indent_to_dept_id   UUID        NOT NULL,
    sequence_number     VARCHAR(40),
    indent_date         DATE        NOT NULL DEFAULT CURRENT_DATE,
    indent_status       VARCHAR(30) NOT NULL DEFAULT 'INDENT',
    notes               TEXT,
    status              SMALLINT    NOT NULL DEFAULT 1,
    created_by          UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by         UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_stock_indents PRIMARY KEY (id)
);
CREATE INDEX idx_si_date ON stock_indents(indent_date);

CREATE TABLE stock_issues (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    from_department_id UUID        NOT NULL,
    to_department_id   UUID        NOT NULL,
    sequence_number    VARCHAR(40),
    issue_date         DATE        NOT NULL DEFAULT CURRENT_DATE,
    notes              TEXT,
    status             SMALLINT    NOT NULL DEFAULT 1,
    created_by         UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by        UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_stock_issues PRIMARY KEY (id)
);
CREATE INDEX idx_sis_date ON stock_issues(issue_date);

CREATE TABLE stock_consumptions (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    department_id   UUID        NOT NULL,
    sequence_number VARCHAR(40),
    consumption_date DATE       NOT NULL DEFAULT CURRENT_DATE,
    notes           TEXT,
    status          SMALLINT    NOT NULL DEFAULT 1,
    created_by      UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by     UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_stock_consumptions PRIMARY KEY (id)
);

CREATE TABLE stock_returns (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    from_department_id UUID        NOT NULL,
    to_department_id   UUID        NOT NULL,
    sequence_number    VARCHAR(40),
    return_date        DATE        NOT NULL DEFAULT CURRENT_DATE,
    stock_issue_id     UUID,
    status             SMALLINT    NOT NULL DEFAULT 1,
    created_by         UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by        UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_stock_returns PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 12. PURCHASE_REQUESTS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE purchase_requests (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    department_id   UUID        NOT NULL,
    request_date    DATE        NOT NULL DEFAULT CURRENT_DATE,
    sequence_number VARCHAR(40),
    request_status  VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    notes           TEXT,
    status          SMALLINT    NOT NULL DEFAULT 1,
    created_by      UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by     UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_purchase_requests PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 13. SALES_RETURNS + SALES_RETURN_LINES
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE sales_returns (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    sale_id              UUID           NOT NULL,
    patient_id           UUID,
    department_id        UUID           NOT NULL,
    return_date          DATE           NOT NULL DEFAULT CURRENT_DATE,
    sequence_number      VARCHAR(40),
    total_return_amount  NUMERIC(14, 4) NOT NULL DEFAULT 0,
    status               SMALLINT       NOT NULL DEFAULT 1,
    created_by           UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by          UUID, modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_sales_returns PRIMARY KEY (id)
);

CREATE TABLE sales_return_lines (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    sales_return_id   UUID           NOT NULL REFERENCES sales_returns(id) ON DELETE CASCADE,
    inventory_batch_id UUID          NOT NULL,
    sale_line_id      UUID,
    quantity          INTEGER        NOT NULL,
    return_amount     NUMERIC(14, 4) NOT NULL,
    CONSTRAINT pk_srl PRIMARY KEY (id)
);

-- 14. APPOINTMENT_SLOTS (skipped — already in V003)

-- ─────────────────────────────────────────────────────────────────────────────
-- 15. NUMBER_SEQUENCES (patient numbers, bill numbers, etc.)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE number_sequences (
    id      UUID        NOT NULL,
    value   VARCHAR(40) NOT NULL,
    type_id UUID,
    CONSTRAINT pk_number_sequences PRIMARY KEY (id)
);
