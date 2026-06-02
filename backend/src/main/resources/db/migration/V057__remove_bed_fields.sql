ALTER TABLE beds DROP COLUMN floor;
ALTER TABLE beds DROP COLUMN ward;
DROP VIEW IF EXISTS bed_types;
ALTER TABLE room_categories DROP COLUMN billing_cycle;
