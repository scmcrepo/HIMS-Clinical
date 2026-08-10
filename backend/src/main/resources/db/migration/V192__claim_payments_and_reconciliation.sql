-- =============================================================================
--  V192 — Claim financial lifecycle and bank reconciliation  (WO-016 / Module 5)
--
--  The flow document defines five financial statuses. Three of them —
--  PAYMENT_INITIATED, AMOUNT_RECEIVED_IN_BANK and CLAIM_DISPUTED — existed
--  nowhere in the schema, so a claim could be approved and then vanish from the
--  system's view until someone noticed the bank statement by hand.
--
--  claim_payment_advices holds what the insurer asserts it paid (UTR, date,
--  gross, TDS, deductions). It is kept separate from the reconciliation columns
--  because the two are different facts: the payer saying "we sent 95,000" and
--  the hospital's accounts confirming 95,000 actually landed are exactly the
--  pair that has to be compared, and merging them would destroy the comparison.
--
--  ALL AMOUNTS IN PAISE as BIGINT, matching billing and V191.
--
--  Deduction lines are itemised rather than stored as a single total so the
--  billing team can dispute a specific disallowed item, which is what
--  Screen 5.2's CLAIM_DISPUTED path requires.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS claim_deduction_lines, claim_payment_advices;
--    ALTER TABLE nhcx_transactions
--      DROP COLUMN IF EXISTS financial_state,
--      DROP COLUMN IF EXISTS claimed_amount,
--      DROP COLUMN IF EXISTS disallowed_amount,
--      DROP COLUMN IF EXISTS patient_copay_amount;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key = 'CLAIM_PAYMENTS');
--    DELETE FROM features WHERE feature_key = 'CLAIM_PAYMENTS';
--  Purely additive.
-- =============================================================================

-- ── The five-status financial lifecycle ──────────────────────────────────────
--  Deliberately a NEW column rather than widening `state`. `state` tracks the
--  NHCX exchange (submitted / acknowledged / responded); this tracks the money.
--  A claim can be exchange-complete and financially unpaid for weeks, and
--  collapsing the two would make that invisible.
ALTER TABLE nhcx_transactions
    ADD COLUMN IF NOT EXISTS financial_state       VARCHAR(28);
ALTER TABLE nhcx_transactions
    ADD COLUMN IF NOT EXISTS claimed_amount        BIGINT;
ALTER TABLE nhcx_transactions
    ADD COLUMN IF NOT EXISTS disallowed_amount     BIGINT;
ALTER TABLE nhcx_transactions
    ADD COLUMN IF NOT EXISTS patient_copay_amount  BIGINT;

ALTER TABLE nhcx_transactions DROP CONSTRAINT IF EXISTS ck_nhcx_financial_state;
ALTER TABLE nhcx_transactions ADD CONSTRAINT ck_nhcx_financial_state
    CHECK (financial_state IS NULL OR financial_state IN (
        'CLAIM_SUBMITTED',
        'CLAIM_APPROVED',
        'PAYMENT_INITIATED',
        'AMOUNT_RECEIVED_IN_BANK',
        'CLAIM_DISPUTED'
    ));

CREATE INDEX IF NOT EXISTS ix_nhcx_financial_state
    ON nhcx_transactions (tenant_id, financial_state, created_at DESC);

-- ── Insurer payment advice (NHCX PaymentNotice) ──────────────────────────────
CREATE TABLE IF NOT EXISTS claim_payment_advices (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id              UUID         NOT NULL,
    branch_id              UUID,

    -- The link the flow document calls for: payment advice tied to the claim id.
    nhcx_transaction_id    UUID         NOT NULL,
    correlation_id         VARCHAR(64)  NOT NULL,
    payer_code             VARCHAR(60)  NOT NULL,

    -- Bank UTR / NEFT reference. Unique per tenant: the same UTR arriving twice
    -- is a duplicate advice, not a second payment, and crediting the ledger
    -- twice is the exact failure this constraint exists to prevent.
    utr_number             VARCHAR(64)  NOT NULL,
    payment_date           TIMESTAMPTZ,

    gross_amount           BIGINT       NOT NULL,
    tds_amount             BIGINT       NOT NULL DEFAULT 0,
    deduction_amount       BIGINT       NOT NULL DEFAULT 0,
    -- What the insurer says actually left its account.
    net_disbursed_amount   BIGINT       NOT NULL,

    -- Reconciliation: filled by the hospital's accounts team, not the payer.
    reconciled             BOOLEAN      NOT NULL DEFAULT FALSE,
    reconciled_at          TIMESTAMPTZ,
    reconciled_by          UUID,
    bank_credited_amount   BIGINT,
    reconciliation_note    TEXT,

    raw_payload            TEXT,

    status                 SMALLINT     NOT NULL DEFAULT 1,
    created_by             UUID,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by            UUID,
    modified_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_claim_payment_advices PRIMARY KEY (id),
    CONSTRAINT fk_advice_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_advice_txn FOREIGN KEY (nhcx_transaction_id)
        REFERENCES nhcx_transactions(id) ON DELETE CASCADE,
    -- Negative money is a parse failure, never a payment.
    CONSTRAINT ck_advice_amounts CHECK (
        gross_amount >= 0 AND tds_amount >= 0 AND deduction_amount >= 0
        AND net_disbursed_amount >= 0
        AND (bank_credited_amount IS NULL OR bank_credited_amount >= 0)
    ),
    -- Reconciled means someone recorded what the bank actually credited.
    CONSTRAINT ck_advice_reconciled CHECK (
        reconciled = FALSE OR (reconciled_at IS NOT NULL AND bank_credited_amount IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_advice_utr
    ON claim_payment_advices (tenant_id, utr_number);
CREATE INDEX IF NOT EXISTS ix_advice_txn
    ON claim_payment_advices (nhcx_transaction_id);
CREATE INDEX IF NOT EXISTS ix_advice_unreconciled
    ON claim_payment_advices (tenant_id, reconciled, payment_date)
    WHERE reconciled = FALSE;

-- ── Itemised disallowances ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS claim_deduction_lines (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    branch_id            UUID,
    nhcx_transaction_id  UUID         NOT NULL,

    -- NON_MEDICAL | NOT_COVERED | EXCEEDS_LIMIT | DOCUMENT_MISSING | TDS | OTHER
    reason_category      VARCHAR(24)  NOT NULL DEFAULT 'OTHER',
    reason_code          VARCHAR(60),
    description          TEXT         NOT NULL,
    amount               BIGINT       NOT NULL,

    -- Set when billing challenges this specific line.
    disputed             BOOLEAN      NOT NULL DEFAULT FALSE,
    disputed_at          TIMESTAMPTZ,
    dispute_note         TEXT,

    status               SMALLINT     NOT NULL DEFAULT 1,
    created_by           UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by          UUID,
    modified_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_claim_deduction_lines PRIMARY KEY (id),
    CONSTRAINT fk_deduction_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_deduction_txn FOREIGN KEY (nhcx_transaction_id)
        REFERENCES nhcx_transactions(id) ON DELETE CASCADE,
    CONSTRAINT ck_deduction_amount CHECK (amount >= 0),
    CONSTRAINT ck_deduction_category CHECK (reason_category IN
        ('NON_MEDICAL', 'NOT_COVERED', 'EXCEEDS_LIMIT', 'DOCUMENT_MISSING', 'TDS', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS ix_deduction_txn ON claim_deduction_lines (nhcx_transaction_id);

-- ── Feature key ──────────────────────────────────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.feature_key, v.module, v.description, t.id
FROM (VALUES
    ('CLAIM_PAYMENTS', 'CLAIM', 'Track insurer disbursals and reconcile hospital bank credits')
) AS v(feature_key, module, description)
CROSS JOIN tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- Accounts reconciliation is a narrower job than filing claims, so this is
-- granted to admin roles only rather than to everyone holding NHCX_CLAIMS.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN', 'BRANCH_ADMIN', 'ACCOUNTS')
  AND f.feature_key = 'CLAIM_PAYMENTS'
ON CONFLICT DO NOTHING;

-- Backfill: existing claims get a financial state consistent with their
-- exchange state, so the control tower is not empty on day one.
UPDATE nhcx_transactions
SET financial_state = CASE
        WHEN state = 'APPROVED' THEN 'CLAIM_APPROVED'
        ELSE 'CLAIM_SUBMITTED'
    END
WHERE financial_state IS NULL
  AND exchange_type IN ('CLAIM', 'PREAUTH');
