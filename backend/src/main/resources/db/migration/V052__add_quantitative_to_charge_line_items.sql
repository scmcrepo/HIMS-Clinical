-- V052__add_quantitative_to_charge_line_items.sql
ALTER TABLE charge_line_items ADD COLUMN quantitative BOOLEAN NOT NULL DEFAULT FALSE;
