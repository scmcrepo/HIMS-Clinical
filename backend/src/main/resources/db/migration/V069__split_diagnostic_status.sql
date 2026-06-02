-- V069__split_diagnostic_status.sql
-- Splitting diagnostic status into payment_status and test_status
-- diagnostic_orders has 'order_status' column (NOT 'status' — that belongs to AuditableEntity)
-- diagnostic_order_lines has 'status' column

ALTER TABLE diagnostic_orders ADD COLUMN payment_status SMALLINT;
ALTER TABLE diagnostic_orders ADD COLUMN test_status SMALLINT;

ALTER TABLE diagnostic_order_lines ADD COLUMN payment_status SMALLINT;
ALTER TABLE diagnostic_order_lines ADD COLUMN test_status SMALLINT;

-- Map old order_status to new statuses for diagnostic_orders
-- OLD: 0=ORDERED, 1=BILLED, 2=RESULTED, 3=CANCELLED, 4=PART_PAID
-- NEW Payment: 0=ORDERED, 1=BILLED, 2=PART_PAID
-- NEW Test: 0=PENDING, 1=RECORDED, 2=RESULTED, 3=CANCELLED

UPDATE diagnostic_orders SET payment_status = 0, test_status = 0 WHERE order_status = 0; -- ORDERED
UPDATE diagnostic_orders SET payment_status = 1, test_status = 0 WHERE order_status = 1; -- BILLED
UPDATE diagnostic_orders SET payment_status = 1, test_status = 2 WHERE order_status = 2; -- RESULTED
UPDATE diagnostic_orders SET payment_status = 0, test_status = 3 WHERE order_status = 3; -- CANCELLED
UPDATE diagnostic_orders SET payment_status = 2, test_status = 0 WHERE order_status = 4; -- PART_PAID

-- Map old status to new statuses for diagnostic_order_lines
UPDATE diagnostic_order_lines SET payment_status = 0, test_status = 0 WHERE status = 0; -- ORDERED
UPDATE diagnostic_order_lines SET payment_status = 1, test_status = 0 WHERE status = 1; -- BILLED
UPDATE diagnostic_order_lines SET payment_status = 1, test_status = 2 WHERE status = 2; -- RESULTED
UPDATE diagnostic_order_lines SET payment_status = 0, test_status = 3 WHERE status = 3; -- CANCELLED
UPDATE diagnostic_order_lines SET payment_status = 2, test_status = 0 WHERE status = 4; -- PART_PAID

-- Set constraints on diagnostic_orders (keep 'status' — it's AuditableEntity, drop 'order_status')
ALTER TABLE diagnostic_orders ALTER COLUMN payment_status SET NOT NULL;
ALTER TABLE diagnostic_orders ALTER COLUMN test_status SET NOT NULL;
ALTER TABLE diagnostic_orders DROP COLUMN order_status;

-- Set constraints on diagnostic_order_lines (drop 'status' — it's the old line status)
ALTER TABLE diagnostic_order_lines ALTER COLUMN payment_status SET NOT NULL;
ALTER TABLE diagnostic_order_lines ALTER COLUMN test_status SET NOT NULL;
ALTER TABLE diagnostic_order_lines DROP COLUMN status;
