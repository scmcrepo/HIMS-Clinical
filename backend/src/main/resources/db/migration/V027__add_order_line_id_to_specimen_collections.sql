ALTER TABLE specimen_collections ADD COLUMN order_line_id UUID;
CREATE INDEX idx_sc_order_line ON specimen_collections(order_line_id);
