-- V108: Add discount and round_off columns to purchase_receipts table
ALTER TABLE purchase_receipts ADD COLUMN IF NOT EXISTS discount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE purchase_receipts ADD COLUMN IF NOT EXISTS round_off NUMERIC(12,2) NOT NULL DEFAULT 0;
