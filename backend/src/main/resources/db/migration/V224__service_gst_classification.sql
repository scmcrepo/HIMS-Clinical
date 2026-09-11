-- ── GST classification for services (P2.1) ──────────────────────────────────
--  OP and IP billing carried no GST information of any kind: charge_line_items
--  has no tax columns, and service_catalog_items had neither a SAC code nor a
--  rate. Pharmacy items at least had hsn_code and tax_rate. These columns close
--  that gap so services can be reported.
--
--  Fields land on BOTH tables because `charges` is the master people edit and
--  `service_catalog_items` is the billing-time mirror ChargeService keeps in
--  sync (sharing the same id). The report joins through the mirror.

ALTER TABLE charges
    ADD COLUMN IF NOT EXISTS sac_code      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS tax_rate      NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gst_treatment VARCHAR(20)   NOT NULL DEFAULT 'UNCLASSIFIED';

ALTER TABLE service_catalog_items
    ADD COLUMN IF NOT EXISTS sac_code      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS tax_rate      NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gst_treatment VARCHAR(20)   NOT NULL DEFAULT 'UNCLASSIFIED';

COMMENT ON COLUMN charges.gst_treatment IS
    'UNCLASSIFIED | TAXABLE | EXEMPT | NIL_RATED | NON_GST';

-- Existing services default to UNCLASSIFIED rather than EXEMPT.
--
--  Most healthcare services are GST-exempt in India, so EXEMPT would be right
--  more often than not — but "more often than not" is not a tax position, and
--  defaulting to it would silently assert one on the hospital's behalf across
--  its entire service master. UNCLASSIFIED asserts nothing; the OP/IP report
--  surfaces the count the same way the HSN summary surfaces unclassified codes,
--  so the gap stays visible until the hospital's tax advisor rules on it.
--
--  No CHECK constraint on gst_treatment: the value set may grow (SEZ, export)
--  and a constraint here would need a migration each time. The enum is enforced
--  in the application layer.

CREATE INDEX IF NOT EXISTS idx_sci_gst_treatment
    ON service_catalog_items (tenant_id, gst_treatment);
