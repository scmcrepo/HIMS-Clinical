-- V019__add_item_name_to_charge_line_items.sql
-- Add item_name column to charge_line_items to avoid schema validation errors

ALTER TABLE charge_line_items ADD COLUMN IF NOT EXISTS item_name VARCHAR(200);

-- Back-populate item_name from service_catalog_items
UPDATE charge_line_items cli
SET item_name = sci.name
FROM service_catalog_items sci
WHERE cli.service_catalog_item_id = sci.id
  AND cli.item_name IS NULL;
