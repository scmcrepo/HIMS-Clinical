-- V162__add_prescribed_at_to_pharmacy_sales.sql
-- Add prescribed_at column to pharmacy_sales to link to a specific prescription order
ALTER TABLE pharmacy_sales ADD COLUMN IF NOT EXISTS prescribed_at TIMESTAMPTZ;
