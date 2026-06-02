-- V058__update_units_of_measure.sql
DELETE FROM units_of_measure WHERE name NOT IN ('Tablet', 'Bottle', 'Strip', 'Box', 'NOS') AND id NOT IN (SELECT DISTINCT unit_of_measure_id FROM inventory_items WHERE unit_of_measure_id IS NOT NULL);

INSERT INTO units_of_measure (id, name, symbol, status, created_at, modified_at)
VALUES 
    ('a0e28efa-f067-45ec-94c9-4002da454513', 'Box', 'BOX', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454514', 'NOS', 'NOS', 1, NOW(), NOW())
ON CONFLICT (name) DO NOTHING;
