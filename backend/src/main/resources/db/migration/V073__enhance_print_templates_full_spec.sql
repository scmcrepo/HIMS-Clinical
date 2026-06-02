-- V073__enhance_print_templates_full_spec.sql
-- Enhance print_templates to support full print engine spec

ALTER TABLE print_templates
    ADD COLUMN IF NOT EXISTS print_mode       VARCHAR(20)   NOT NULL DEFAULT 'HTML'
        CHECK (print_mode IN ('HTML', 'DOT_MATRIX')),
    ADD COLUMN IF NOT EXISTS height           VARCHAR(20)   NOT NULL DEFAULT '297mm',
    ADD COLUMN IF NOT EXISTS width            VARCHAR(20)   NOT NULL DEFAULT '210mm',
    ADD COLUMN IF NOT EXISTS margin_top       VARCHAR(20)   NOT NULL DEFAULT '10mm',
    ADD COLUMN IF NOT EXISTS margin_bottom    VARCHAR(20)   NOT NULL DEFAULT '10mm',
    ADD COLUMN IF NOT EXISTS margin_left      VARCHAR(20)   NOT NULL DEFAULT '10mm',
    ADD COLUMN IF NOT EXISTS margin_right     VARCHAR(20)   NOT NULL DEFAULT '10mm',
    ADD COLUMN IF NOT EXISTS margin           VARCHAR(20),
    ADD COLUMN IF NOT EXISTS page_size        VARCHAR(20)   NOT NULL DEFAULT 'A4',
    ADD COLUMN IF NOT EXISTS pug_template     TEXT,
    ADD COLUMN IF NOT EXISTS default_printer  VARCHAR(100);

CREATE TABLE IF NOT EXISTS print_data_queries (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    api_key          VARCHAR(100)  NOT NULL,
    query_string     TEXT          NOT NULL,
    print_template   UUID          REFERENCES print_templates(id) ON DELETE SET NULL,
    status           SMALLINT      NOT NULL DEFAULT 1,
    created_by       UUID,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    modified_by      UUID,
    modified_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_print_data_queries        PRIMARY KEY (id),
    CONSTRAINT uq_print_data_queries_key    UNIQUE (api_key)
);

COMMENT ON TABLE  print_data_queries IS 'SQL queries keyed by TemplateType values, used to hydrate print templates.';
COMMENT ON COLUMN print_data_queries.api_key IS 'Maps to TemplateType enum (BILL, LAB, PRESCRIPTION, etc).';

INSERT INTO print_data_queries (api_key, query_string) VALUES
  ('BILL',                     'SELECT b.id, b.bill_amount, b.status FROM bills b WHERE b.id = :id'),
  ('OP_RECEIPT',               'SELECT b.id, b.bill_amount FROM bills b WHERE b.id = :id'),
  ('IP_RECEIPT',               'SELECT b.id, b.bill_amount FROM bills b WHERE b.id = :id'),
  ('IP_BILL_CONSOLIDATED',     'SELECT b.id, b.bill_amount FROM bills b WHERE b.id = :id'),
  ('IP_BILL_DETAIL',           'SELECT b.id, b.bill_amount FROM bills b WHERE b.id = :id'),
  ('PATIENT_ID',               'SELECT p.id, p.first_name, p.last_name, p.patient_number FROM patients p WHERE p.id = :id'),
  ('PRESCRIPTION',             'SELECT e.id FROM clinical_encounters e WHERE e.id = :id'),
  ('DIAGNOSTIC_ORDER',         'SELECT d.id FROM diagnostic_orders d WHERE d.id = :id'),
  ('PAYMENT',                  'SELECT p.id, p.amount FROM payments p WHERE p.id = :id'),
  ('LAB',                      'SELECT d.id FROM diagnostic_orders d WHERE d.id = :id'),
  ('RADIOLOGY',                'SELECT d.id FROM diagnostic_orders d WHERE d.id = :id'),
  ('DISCHARGE_SUMMARY',        'SELECT e.id FROM clinical_encounters e WHERE e.id = :id'),
  ('SALES',                    'SELECT s.id, s.total_amount FROM pharmacy_sales s WHERE s.id = :id'),
  ('ADVANCE_REFUND_RECEIPT',   'SELECT p.id, p.amount FROM payments p WHERE p.id = :id'),
  ('REFUND_RECEIPT',           'SELECT p.id, p.amount FROM payments p WHERE p.id = :id'),
  ('PURCHASE_ORDER',           'SELECT po.id FROM purchase_orders po WHERE po.id = :id'),
  ('CLINICAL',                 'SELECT e.id FROM clinical_encounters e WHERE e.id = :id'),
  ('SAMPLE',                   'SELECT d.id FROM diagnostic_orders d WHERE d.id = :id'),
  ('SPECIMEN',                 'SELECT d.id FROM diagnostic_orders d WHERE d.id = :id'),
  ('VEHICLE_MOVEMENT',         'SELECT 1 AS placeholder'),
  ('AMBULANCE_SERVICE_ENTRY',  'SELECT 1 AS placeholder'),
  ('CSSD_BATCH',               'SELECT 1 AS placeholder')
ON CONFLICT (api_key) DO NOTHING;
