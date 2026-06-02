-- V054__add_item_scmc_fields.sql
-- Add SCMC fields to inventory_items table

ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS second_level_unit VARCHAR(50);
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS purchase_unit VARCHAR(50);
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS selling_unit VARCHAR(50);
ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS scheduled_drug VARCHAR(50);
