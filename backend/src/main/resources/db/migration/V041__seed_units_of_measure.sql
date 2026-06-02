-- V041__seed_units_of_measure.sql
INSERT INTO units_of_measure (id, name, symbol, status, created_at, modified_at)
VALUES 
    ('a0e28efa-f067-45ec-94c9-4002da454501', 'Tablet', 'TAB', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454502', 'Capsule', 'CAP', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454503', 'Milliliter', 'ML', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454504', 'Milligram', 'MG', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454505', 'Gram', 'GM', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454506', 'Strip', 'STRIP', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454507', 'Bottle', 'BOT', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454508', 'Vial', 'VIAL', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454509', 'Ampoule', 'AMP', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454510', 'Sachet', 'SACH', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454511', 'Unit', 'UNIT', 1, NOW(), NOW()),
    ('a0e28efa-f067-45ec-94c9-4002da454512', 'Piece', 'PCS', 1, NOW(), NOW())
ON CONFLICT (name) DO NOTHING;
