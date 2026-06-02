-- V015__add_item_name_to_diagnostic_order_lines.sql
-- Add item_name column to diagnostic_order_lines to avoid schema validation errors

ALTER TABLE diagnostic_order_lines ADD COLUMN IF NOT EXISTS item_name VARCHAR(200);

-- Back-populate item_name from service_catalog_items
UPDATE diagnostic_order_lines dol
SET item_name = sci.name
FROM service_catalog_items sci
WHERE dol.service_catalog_item_id = sci.id
  AND dol.item_name IS NULL;
