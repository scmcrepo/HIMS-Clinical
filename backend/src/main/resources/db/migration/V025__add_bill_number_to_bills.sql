-- Add bill_number column to bills table
ALTER TABLE bills ADD COLUMN IF NOT EXISTS bill_number VARCHAR(40);
