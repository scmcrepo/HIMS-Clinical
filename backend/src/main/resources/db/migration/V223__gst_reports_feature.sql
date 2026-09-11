-- ── REPORT_GST feature (GST Stage 1) ────────────────────────────────────────
--  Without this row every /report/gst endpoint is 403 for everyone, including
--  the hospital admin who asked for the reports.
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'REPORT_GST', 'REPORTS',
       'GST reports: pharmacy sales GST detailed, purchase GST details', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

--  ADMIN receives the full catalogue by convention; HOSPITAL_ADMIN holds the
--  other REPORT_* keys, so it holds this one too. The GST reports expose
--  supplier pricing and patient-level purchase history, so they are deliberately
--  NOT granted to BILLING or RECEPTION.
--  NOTE: the roles table keys on `name`, not `role_key` — matching V199's form.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('ADMIN', 'HOSPITAL_ADMIN')
  AND f.feature_key = 'REPORT_GST'
ON CONFLICT DO NOTHING;

-- ── Indexes for the GST report date scans ───────────────────────────────────
--  Both reports scan a date range within one tenant. The existing single-column
--  date indexes (idx_ps_date, idx_pr_date) force a tenant filter afterwards;
--  these composites let the range scan start already scoped.
CREATE INDEX IF NOT EXISTS idx_ps_tenant_sale_date
    ON pharmacy_sales (tenant_id, sale_date);

CREATE INDEX IF NOT EXISTS idx_pr_tenant_receipt_date
    ON purchase_receipts (tenant_id, receipt_date);

-- NOTE ON HSN VALIDATION (P1.1)
--  Format validation for inventory_items.hsn_code is enforced at the application
--  layer (@Pattern on InventoryItem plus an explicit check in BulkImportService),
--  NOT as a CHECK constraint here. Existing rows hold malformed codes — 5, 6, 7
--  and 9-digit values where valid HSN is 4, 6 or 8 — and a constraint would fail
--  this migration on every live database. The backlog is cleaned through the
--  HSN data-quality tooling; the application guard stops it growing meanwhile.
