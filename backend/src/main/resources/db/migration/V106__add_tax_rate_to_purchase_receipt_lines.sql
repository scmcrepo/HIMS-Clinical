-- V106: Add tax_rate column to purchase_receipt_lines
-- Stores the GST/tax percentage applied at time of receipt
-- purchase_rate now stores the original (pre-tax) price; tax-inclusive total = purchase_rate * (1 + tax_rate/100)
ALTER TABLE purchase_receipt_lines
    ADD COLUMN tax_rate NUMERIC(6, 2) NOT NULL DEFAULT 0;
