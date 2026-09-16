-- V228: Add tax_rate column to goods_return_lines
-- Stores the GST/tax percentage at the time of return (derived from the purchase receipt line / GRN)
-- so ITC reversal in GST reports does not depend on current inventory_items master rate.

ALTER TABLE goods_return_lines
    ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(6, 2) DEFAULT 0;

-- Backfill from purchase_receipt_lines where batch links to purchase receipt,
-- falling back to inventory_items.tax_rate if not found.
UPDATE goods_return_lines grl
SET tax_rate = COALESCE(sub.tax_rate, 0)
FROM (
    SELECT grl_inner.id AS line_id,
           COALESCE(MAX(prl.tax_rate), MAX(ii.tax_rate), 0) AS tax_rate
    FROM goods_return_lines grl_inner
    JOIN inventory_batches ib ON grl_inner.batch_id = ib.id
    LEFT JOIN purchase_receipt_lines prl
      ON prl.receipt_id = ib.source_transaction_id
     AND prl.item_id = ib.item_id
     AND (prl.batch_number = ib.batch_number OR (prl.batch_number IS NULL AND ib.batch_number IS NULL))
    LEFT JOIN inventory_items ii ON ib.item_id = ii.id
    GROUP BY grl_inner.id
) sub
WHERE grl.id = sub.line_id;

UPDATE goods_return_lines SET tax_rate = 0 WHERE tax_rate IS NULL;
ALTER TABLE goods_return_lines ALTER COLUMN tax_rate SET NOT NULL;
