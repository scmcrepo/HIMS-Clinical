ALTER TABLE inventory_items DROP CONSTRAINT IF EXISTS fk_ii_molecule;
ALTER TABLE inventory_items DROP COLUMN IF EXISTS molecule_id;
