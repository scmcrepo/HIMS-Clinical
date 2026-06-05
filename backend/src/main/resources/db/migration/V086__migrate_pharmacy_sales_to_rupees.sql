-- Convert May's pharmacy_sales and pharmacy_sale_lines from paise to rupees
UPDATE pharmacy_sale_lines 
SET unit_rate = ROUND(unit_rate / 100, 4),
    amount = ROUND(amount / 100, 4),
    discount_amount = ROUND(discount_amount / 100, 4)
WHERE created_at < '2026-06-01 00:00:00+00';

UPDATE pharmacy_sales
SET total_amount = ROUND(total_amount / 100, 4),
    discount_amount = ROUND(discount_amount / 100, 4),
    paid_amount = ROUND(paid_amount / 100, 4),
    due_amount = ROUND(due_amount / 100, 4)
WHERE created_at < '2026-06-01 00:00:00+00';

UPDATE pharmacy_sale_payments
SET amount = ROUND(amount / 100, 4)
WHERE created_at < '2026-06-01 00:00:00+00';
