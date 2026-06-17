-- V106__create_petty_cash.sql
-- Create petty_cash table and seed permission

CREATE TABLE petty_cash (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    petty_cash_no VARCHAR(40),
    reason        VARCHAR(500),
    paid_to       VARCHAR(100)  NOT NULL,
    amount        BIGINT        NOT NULL,
    payment_date  DATE          NOT NULL,
    payment_mode  VARCHAR(30)   NOT NULL DEFAULT 'CASH',
    status        VARCHAR(20)   NOT NULL DEFAULT 'Active',
    created_by    UUID,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_petty_cash PRIMARY KEY (id)
);

-- Seed feature permission key
INSERT INTO features (id, feature_key, module, description) VALUES
    (gen_random_uuid(), 'PETTY_CASH', 'BILLING', 'Petty Cash Billing')
ON CONFLICT (feature_key) DO NOTHING;

-- Grant permissions to ADMIN and BILLING roles by default
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name IN ('ADMIN', 'BILLING') AND f.feature_key = 'PETTY_CASH'
ON CONFLICT DO NOTHING;
