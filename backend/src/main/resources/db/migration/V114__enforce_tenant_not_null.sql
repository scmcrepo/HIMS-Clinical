-- V097__enforce_tenant_not_null.sql  (PostgreSQL 16)
-- ============================================================================
-- Run this ONLY after the tenant-aware application build is deployed and you
-- have verified every write path stamps tenant_id (TenantContext / @PrePersist).
-- Tightens tenant_id to NOT NULL on all tenant-scoped tables.
--
-- users.tenant_id is deliberately NOT included — it must remain NULLABLE so
-- platform-level SUPERADMIN accounts (tenant_id IS NULL) continue to work.
-- ============================================================================
DO $$
DECLARE
    t text;
    tenant_scoped_tables text[] := ARRAY[
        'patients','visits','clinical_encounters','appointments','appointment_slots',
        'consultants','departments','staff','areas',
        'bills','charge_line_items','payments',
        'inventory_items','inventory_batches','stock_adjustments','purchase_orders',
        'purchase_receipts','pharmacy_sales','sales_returns','stock_indents','stock_issues',
        'stock_returns','stock_consumptions','goods_returns',
        'diagnostic_orders','diagnostic_reports','specimen_collections',
        'roles','features','beds','bed_types','room_categories','bed_occupancies',
        'service_catalog_items','service_categories','case_sheet_templates','case_sheet_records',
        'print_templates','diagnostic_templates','lab_template_details','order_sets','charges',
        'tariffs','molecules','items','categories','units_of_measure','taxes','tax_categories',
        'frequencies','specimens','suppliers','payors','account_units','attachments','referrals',
        'scheduled_drugs'
    ];
BEGIN
    FOREACH t IN ARRAY tenant_scoped_tables LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema()
                     AND table_name = t AND column_name = 'tenant_id') THEN
            -- Fail loudly if any row is still unscoped rather than silently skip.
            EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', t);
        END IF;
    END LOOP;
END $$;
