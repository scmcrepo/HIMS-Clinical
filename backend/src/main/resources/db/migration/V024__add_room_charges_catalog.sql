-- V024__add_room_charges_catalog.sql
-- 1. Create Room Charges category if not exists
INSERT INTO service_categories (id, name, category_type, status, created_at)
VALUES ('b47c61f0-0a2a-4f51-8735-8622765893f1', 'Room Charges', 1, 1, NOW())
ON CONFLICT (name) DO NOTHING;

-- 2. Create a default Bed Charge item
INSERT INTO service_catalog_items (id, name, category_id, service_type, status, created_at)
VALUES ('748c116d-33d3-4fc6-879e-4c22762b0001', 'Hospital Bed', 
        (SELECT id FROM service_categories WHERE name = 'Room Charges' LIMIT 1), 
        2, 1, NOW())
ON CONFLICT DO NOTHING;

-- 3. Link all room categories to this default item if they are currently null
UPDATE room_categories SET service_catalog_item_id = '748c116d-33d3-4fc6-879e-4c22762b0001'
WHERE service_catalog_item_id IS NULL;

-- 4. Allow NULL service_catalog_item_id in charge_line_items for manual/automated injections
-- actually better to keep it NOT NULL but ensure we always have a fallback.
-- However, for robustness, let's allow NULL.
ALTER TABLE charge_line_items ALTER COLUMN service_catalog_item_id DROP NOT NULL;
