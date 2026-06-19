-- V130__fix_diagnostic_templates_charge_ids.sql
-- Update diagnostic_templates.charge_id to match the correct service_catalog_items.id
-- by joining on name (case-insensitive, trimmed), tenant_id, and branch_id.

UPDATE diagnostic_templates dt
SET charge_id = sci.id,
    modified_at = NOW()
FROM service_catalog_items sci
WHERE UPPER(TRIM(dt.name)) = UPPER(TRIM(sci.name))
  AND dt.tenant_id = sci.tenant_id
  AND (dt.branch_id = sci.branch_id OR (dt.branch_id IS NULL AND sci.branch_id IS NULL));
