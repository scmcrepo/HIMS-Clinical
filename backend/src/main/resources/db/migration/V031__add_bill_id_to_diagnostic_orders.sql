ALTER TABLE diagnostic_orders ADD COLUMN bill_id UUID;
CREATE INDEX idx_do_bill ON diagnostic_orders(bill_id);
