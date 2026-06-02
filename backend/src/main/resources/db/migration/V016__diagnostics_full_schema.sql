-- V016__diagnostics_full_schema.sql  (PostgreSQL 16)
-- Adds: diagnostic_departments, lab_template_details, diagnostic_reports
-- Alters: diagnostic_templates (add format, header, method, department_id)

-- ─────────────────────────────────────────────────────────
-- 1. DIAGNOSTIC_DEPARTMENTS
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS diagnostic_departments (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL,
    type          VARCHAR(50)  NOT NULL DEFAULT 'DIAGNOSTICS',
    display_order INT          NOT NULL DEFAULT 0,
    status        SMALLINT     NOT NULL DEFAULT 1,
    created_by    UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_diagnostic_departments PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────
-- 2. LAB_TEMPLATE_DETAILS
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS lab_template_details (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    result_name      VARCHAR(200) NOT NULL,
    normal_range     VARCHAR(500),
    normal_range_exp TEXT,
    unit             VARCHAR(50),
    lab_type         VARCHAR(30)  NOT NULL DEFAULT 'NUMERIC',
    order_number     INT          NOT NULL DEFAULT 0,
    row_count        SMALLINT     DEFAULT 1,
    status           SMALLINT     NOT NULL DEFAULT 1,
    created_by       UUID,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by      UUID,
    modified_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_lab_template_details PRIMARY KEY (id)
);

COMMENT ON COLUMN lab_template_details.lab_type IS 'NUMERIC, TEXT, HEADER, PANEL';

-- ─────────────────────────────────────────────────────────
-- 3. DIAGNOSTIC_TEMPLATE ↔ LAB_TEMPLATE_DETAIL join table
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS diagnostic_template_lab_template (
    diagnostic_template_id UUID NOT NULL,
    lab_template_detail_id UUID NOT NULL,
    CONSTRAINT pk_dt_ltd PRIMARY KEY (diagnostic_template_id, lab_template_detail_id),
    CONSTRAINT fk_dtltd_template FOREIGN KEY (diagnostic_template_id) REFERENCES diagnostic_templates(id) ON DELETE CASCADE,
    CONSTRAINT fk_dtltd_detail   FOREIGN KEY (lab_template_detail_id) REFERENCES lab_template_details(id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────────────────
-- 4. ALTER diagnostic_templates — add new columns
-- ─────────────────────────────────────────────────────────
ALTER TABLE diagnostic_templates ADD COLUMN IF NOT EXISTS format        VARCHAR(30) DEFAULT 'LAB_TEMPLATE';
ALTER TABLE diagnostic_templates ADD COLUMN IF NOT EXISTS header        VARCHAR(200);
ALTER TABLE diagnostic_templates ADD COLUMN IF NOT EXISTS method        VARCHAR(200);
ALTER TABLE diagnostic_templates ADD COLUMN IF NOT EXISTS department_id UUID;
ALTER TABLE diagnostic_templates ADD COLUMN IF NOT EXISTS order_number  INT DEFAULT 0;

-- FK to departments
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_dt_department') THEN
    ALTER TABLE diagnostic_templates ADD CONSTRAINT fk_dt_department FOREIGN KEY (department_id) REFERENCES diagnostic_departments(id);
  END IF;
END $$;

-- ─────────────────────────────────────────────────────────
-- 5. DIAGNOSTIC_REPORTS
-- ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS diagnostic_reports (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    diagnostic_order_line_id UUID       NOT NULL,
    diagnostic_template_id  UUID,
    lab_template_detail_id  UUID,
    value                   TEXT,
    result                  VARCHAR(30),
    is_approved             BOOLEAN     NOT NULL DEFAULT FALSE,
    template_data           TEXT,
    status                  SMALLINT    NOT NULL DEFAULT 1,
    created_by              UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by             UUID,
    modified_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_diagnostic_reports PRIMARY KEY (id),
    CONSTRAINT fk_dr_order_line FOREIGN KEY (diagnostic_order_line_id) REFERENCES diagnostic_order_lines(id) ON DELETE CASCADE,
    CONSTRAINT fk_dr_template   FOREIGN KEY (diagnostic_template_id)  REFERENCES diagnostic_templates(id),
    CONSTRAINT fk_dr_lab_detail FOREIGN KEY (lab_template_detail_id)  REFERENCES lab_template_details(id)
);

CREATE INDEX IF NOT EXISTS idx_dr_order_line ON diagnostic_reports(diagnostic_order_line_id);
CREATE INDEX IF NOT EXISTS idx_dr_template   ON diagnostic_reports(diagnostic_template_id);

-- Allow RESULTED status (3) in diagnostic_order_lines
ALTER TABLE diagnostic_order_lines DROP CONSTRAINT IF EXISTS diagnostic_order_lines_status_check;
ALTER TABLE diagnostic_order_lines ADD CONSTRAINT diagnostic_order_lines_status_check CHECK (status IN (0, 1, 2, 3));
