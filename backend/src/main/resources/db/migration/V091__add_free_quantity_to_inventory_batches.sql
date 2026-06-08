-- V091__add_free_quantity_to_inventory_batches.sql
-- Track free quantity separately from purchased quantity in inventory batches.
-- current_quantity remains the TOTAL (purchased + free), while free_quantity
-- records how many of those were received free from the supplier.

ALTER TABLE inventory_batches ADD COLUMN free_quantity INTEGER NOT NULL DEFAULT 0;
