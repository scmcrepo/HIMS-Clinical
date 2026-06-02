CREATE TABLE IF NOT EXISTS pharmacy_sale_payments (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    sale_id     UUID          NOT NULL,
    amount      NUMERIC(14,4) NOT NULL,
    payment_mode VARCHAR(20)  NOT NULL,
    card_type    VARCHAR(50),
    card_number  VARCHAR(25),
    bank_name    VARCHAR(100),
    status      SMALLINT      NOT NULL DEFAULT 0,
    created_by  UUID,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_pharmacy_sale_payments PRIMARY KEY (id),
    CONSTRAINT fk_psp_sale FOREIGN KEY (sale_id) REFERENCES pharmacy_sales(id) ON DELETE CASCADE
);
