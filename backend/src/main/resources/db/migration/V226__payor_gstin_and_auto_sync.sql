-- ── B2B capture and auto-generated filings ──────────────────────────────────

-- 1. PAYOR GSTIN ────────────────────────────────────────────────────────────
--  V225 gave bills and pharmacy_sales a recipient_gstin, but nothing ever wrote
--  one, so GSTR-1's b2b section could only ever come out empty. The recipient of
--  a credit bill is its payor — a corporate, TPA or insurer — and that is where
--  a GSTIN naturally lives: entered once on the master rather than retyped onto
--  every bill by the front desk.
--
--  Resolution is left to payload-build time rather than stamped onto each bill,
--  so a payor's GSTIN arriving today also fixes the bills already raised against
--  it. bills.recipient_gstin stays as a per-bill override for the odd registered
--  recipient who is not a payor.
ALTER TABLE payors
    ADD COLUMN IF NOT EXISTS gstin      VARCHAR(512),
    ADD COLUMN IF NOT EXISTS state_code VARCHAR(2);

COMMENT ON COLUMN payors.gstin IS
    'Encrypted at rest. Recipient GSTIN for B2B invoices raised against this payor.';
COMMENT ON COLUMN payors.state_code IS
    'Plaintext first two digits of the GSTIN — the place of supply for this payor.';

-- 2. AUTO-GENERATED FILINGS ─────────────────────────────────────────────────
--  Distinguishes a return the scheduler produced from one a person asked for.
--  The job supersedes its own previous output for a period; it must never touch
--  a return somebody generated deliberately.
ALTER TABLE gst_filing_records
    ADD COLUMN IF NOT EXISTS auto_generated BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_gstfr_auto
    ON gst_filing_records (tenant_id, auto_generated, return_type, return_period);
