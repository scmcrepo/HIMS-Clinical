-- ── Module A: GST filing provision ──────────────────────────────────────────
--  Configuration, filing records, and the two invoice-tagging gaps the A8 audit
--  found: nothing in the schema held the hospital's own GSTIN, and nothing held
--  a place of supply. A GST return cannot be built without either.

-- 1. PER-HOSPITAL GST CONFIGURATION ─────────────────────────────────────────
--  One row per tenant (D2: filing is at hospital level, branches roll up).
CREATE TABLE IF NOT EXISTS gst_configurations (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                UUID         NOT NULL,
    gstin                    VARCHAR(512),
    -- First two digits of the GSTIN. Denormalised because it is read on every
    -- payload row as the default place of supply, and the GSTIN is encrypted.
    state_code               VARCHAR(2),
    legal_name               VARCHAR(200),
    api_key                  VARCHAR(512),
    api_secret               VARCHAR(512),
    sandbox_base_url         VARCHAR(300),
    production_base_url      VARCHAR(300),
    active_environment       VARCHAR(20)  NOT NULL DEFAULT 'SANDBOX',
    -- D3: only a platform super-admin may turn this on, per hospital.
    integration_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_sync_frequency      VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    status                   SMALLINT     NOT NULL DEFAULT 1,
    branch_id                UUID,
    created_by               UUID,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by              UUID,
    modified_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_gst_configurations PRIMARY KEY (id),
    CONSTRAINT fk_gstcfg_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gstcfg_tenant ON gst_configurations (tenant_id);

COMMENT ON COLUMN gst_configurations.api_key    IS 'Encrypted at rest via EncryptedStringConverter';
COMMENT ON COLUMN gst_configurations.api_secret IS 'Encrypted at rest via EncryptedStringConverter';
COMMENT ON COLUMN gst_configurations.gstin      IS 'Encrypted at rest; state_code holds the plaintext prefix';

-- 2. FILING RECORDS ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS gst_filing_records (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    return_type         VARCHAR(20)  NOT NULL,           -- GSTR1 | GSTR3B
    return_period       VARCHAR(6)   NOT NULL,           -- MMYYYY, as GST expects
    period_start        DATE         NOT NULL,
    period_end          DATE         NOT NULL,
    filing_status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    environment         VARCHAR(20)  NOT NULL DEFAULT 'SANDBOX',
    gstin               VARCHAR(512),
    payload             TEXT,
    response_body       TEXT,
    reference_id        VARCHAR(100),                    -- ARN or GSP reference
    total_taxable_value NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_cgst          NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_sgst          NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_igst          NUMERIC(16, 2) NOT NULL DEFAULT 0,
    total_cess          NUMERIC(16, 2) NOT NULL DEFAULT 0,
    line_item_count     INTEGER      NOT NULL DEFAULT 0,
    submitted_at        TIMESTAMPTZ,
    status              SMALLINT     NOT NULL DEFAULT 1,
    branch_id           UUID,
    created_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by         UUID,
    modified_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_gst_filing_records PRIMARY KEY (id),
    CONSTRAINT fk_gstfr_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX IF NOT EXISTS idx_gstfr_tenant_period
    ON gst_filing_records (tenant_id, return_type, return_period);
CREATE INDEX IF NOT EXISTS idx_gstfr_status ON gst_filing_records (tenant_id, filing_status);

-- 3. FILING LINE ITEMS ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS gst_filing_line_items (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    filing_record_id  UUID         NOT NULL,
    tenant_id         UUID         NOT NULL,
    source            VARCHAR(20)  NOT NULL,             -- PHARMACY | SERVICE
    source_id         UUID,
    invoice_number    VARCHAR(60),
    invoice_date      DATE,
    recipient_gstin   VARCHAR(512),
    place_of_supply   VARCHAR(2),
    hsn_sac_code      VARCHAR(20),
    tax_rate          NUMERIC(6, 2)  NOT NULL DEFAULT 0,
    taxable_value     NUMERIC(16, 2) NOT NULL DEFAULT 0,
    cgst              NUMERIC(16, 2) NOT NULL DEFAULT 0,
    sgst              NUMERIC(16, 2) NOT NULL DEFAULT 0,
    igst              NUMERIC(16, 2) NOT NULL DEFAULT 0,
    cess              NUMERIC(16, 2) NOT NULL DEFAULT 0,
    reverse_charge    BOOLEAN      NOT NULL DEFAULT FALSE,
    gst_treatment     VARCHAR(20),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_gst_filing_line_items PRIMARY KEY (id),
    CONSTRAINT fk_gstfli_record FOREIGN KEY (filing_record_id)
        REFERENCES gst_filing_records(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_gstfli_record ON gst_filing_line_items (filing_record_id);

-- 4. VALIDATION ERRORS ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS gst_filing_errors (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    filing_record_id UUID         NOT NULL,
    tenant_id        UUID         NOT NULL,
    error_code       VARCHAR(50),
    error_message    TEXT,
    invoice_number   VARCHAR(60),
    field_name       VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_gst_filing_errors PRIMARY KEY (id),
    CONSTRAINT fk_gstfe_record FOREIGN KEY (filing_record_id)
        REFERENCES gst_filing_records(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_gstfe_record ON gst_filing_errors (filing_record_id);

-- 5. CLOSING THE A8 TAGGING GAPS ────────────────────────────────────────────
--  Neither bills nor pharmacy_sales held a place of supply or a recipient GSTIN,
--  so no supply could be attributed to a state and no B2B invoice could be
--  distinguished from B2C. Both are required fields of a GSTR-1.
--
--  All three are nullable with a safe default. Every existing row is a domestic
--  intra-state B2C supply — a patient walking into the hospital — and the payload
--  builder falls back to the hospital's own state code when place_of_supply is
--  NULL, which is the correct treatment for a supply made at the establishment.
--  Backfilling a literal value here would freeze that assumption into the data;
--  leaving it NULL keeps it a resolution rule that can be corrected in one place.
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS place_of_supply VARCHAR(2),
    ADD COLUMN IF NOT EXISTS recipient_gstin VARCHAR(512),
    ADD COLUMN IF NOT EXISTS reverse_charge  BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE pharmacy_sales
    ADD COLUMN IF NOT EXISTS place_of_supply VARCHAR(2),
    ADD COLUMN IF NOT EXISTS recipient_gstin VARCHAR(512),
    ADD COLUMN IF NOT EXISTS reverse_charge  BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN bills.place_of_supply IS
    'Two-digit GST state code. NULL resolves to the hospital''s own state.';
COMMENT ON COLUMN pharmacy_sales.place_of_supply IS
    'Two-digit GST state code. NULL resolves to the hospital''s own state.';

-- 6. FEATURE ────────────────────────────────────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'SETTINGS_GST', 'SETTINGS',
       'GST filing configuration: GSTIN, credentials, environment, filing history', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

--  Hospital admins maintain their own GSTIN and credentials. Turning the
--  integration ON is a platform super-admin action and is enforced in the
--  service layer, not by this grant — SUPERADMIN bypasses feature checks
--  entirely, so a feature key alone cannot express "super-admin only".
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('ADMIN', 'HOSPITAL_ADMIN')
  AND f.feature_key = 'SETTINGS_GST'
ON CONFLICT DO NOTHING;
