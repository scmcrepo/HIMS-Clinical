-- Add discount type and discount value to pharmacy_sales
ALTER TABLE pharmacy_sales
ADD COLUMN discount_type VARCHAR(20) DEFAULT 'AMOUNT',
ADD COLUMN discount_value DECIMAL(14,4) DEFAULT 0.0000;

-- Add discount type to pharmacy_sale_lines (discount_amount already exists)
ALTER TABLE pharmacy_sale_lines
ADD COLUMN discount_type VARCHAR(20) DEFAULT 'AMOUNT',
ADD COLUMN discount_value DECIMAL(14,4) DEFAULT 0.0000;
