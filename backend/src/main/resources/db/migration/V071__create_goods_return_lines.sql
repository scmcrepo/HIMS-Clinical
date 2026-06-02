CREATE TABLE goods_return_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id UUID NOT NULL REFERENCES goods_returns(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL REFERENCES inventory_batches(id),
    quantity INTEGER NOT NULL,
    purchase_rate NUMERIC(12,4) NOT NULL
);
