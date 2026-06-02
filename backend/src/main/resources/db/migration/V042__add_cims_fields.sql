-- V042__add_cims_fields.sql
ALTER TABLE inventory_items ADD COLUMN cims_id VARCHAR(100);
ALTER TABLE inventory_items ADD COLUMN cims_name VARCHAR(200);
ALTER TABLE inventory_items ADD COLUMN cims_type VARCHAR(50);

ALTER TABLE molecules ADD COLUMN cims_id VARCHAR(100);
