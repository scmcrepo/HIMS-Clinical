ALTER TABLE purchase_receipts ADD COLUMN purchase_order_id UUID;
ALTER TABLE purchase_receipts ADD CONSTRAINT fk_pr_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id);
