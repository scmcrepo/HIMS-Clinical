ALTER TABLE pharmacy_sales
ADD COLUMN IF NOT EXISTS paid_amount numeric(14,4) DEFAULT 0,
ADD COLUMN IF NOT EXISTS due_amount numeric(14,4) DEFAULT 0;

