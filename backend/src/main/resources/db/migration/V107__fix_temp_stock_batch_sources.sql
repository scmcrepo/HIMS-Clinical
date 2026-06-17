-- V107__fix_temp_stock_batch_sources.sql
-- Fixes source_transaction_id for inventory batches originally created as temp stock but later received under a purchase receipt/GRN

UPDATE inventory_batches ib
SET source_transaction_id = (
    SELECT prl.receipt_id
    FROM purchase_receipt_lines prl
    JOIN purchase_receipts pr ON prl.receipt_id = pr.id
    WHERE prl.item_id = ib.item_id
      AND prl.batch_number = ib.batch_number
    ORDER BY pr.receipt_date DESC, pr.created_at DESC
    LIMIT 1
)
WHERE NOT EXISTS (
    SELECT 1 FROM purchase_receipts pr
    WHERE pr.id = ib.source_transaction_id
)
AND EXISTS (
    SELECT 1 FROM purchase_receipt_lines prl
    WHERE prl.item_id = ib.item_id
      AND prl.batch_number = ib.batch_number
);
