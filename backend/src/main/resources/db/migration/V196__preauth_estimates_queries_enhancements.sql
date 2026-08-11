-- =============================================================================
--  V196 — Cashless pre-authorisation  (WO-015 / Module 4)
--
--  Screen 4.1 builds an itemised estimate, 4.2 tracks the insurer's answer, 4.3
--  answers a query, 4.4 asks for more once the actual cost overruns.
--
--  ── Why estimates are lines, not a total ───────────────────────────────────
--  An insurer that approves 80,000 against a 1,00,000 estimate has disallowed
--  something specific. Without lines, nobody can tell what, and the enhancement
--  request in 4.4 becomes "please send more money" rather than "the implant was
--  costed at 40,000 and you allowed 20,000". The lines are the argument.
--
--  ── Queries are a thread ───────────────────────────────────────────────────
--  Insurers raise multiple rounds. One query column would overwrite the first
--  question with the second, losing what was already answered.
--
--  ALL AMOUNTS IN PAISE as BIGINT, matching billing, V191 and V192.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS preauth_enhancements, preauth_queries,
--                         preauth_estimate_lines, icd10_codes;
--    ALTER TABLE nhcx_transactions
--      DROP COLUMN IF EXISTS diagnosis_code, DROP COLUMN IF EXISTS diagnosis_text,
--      DROP COLUMN IF EXISTS planned_procedure, DROP COLUMN IF EXISTS expected_los_days,
--      DROP COLUMN IF EXISTS room_type, DROP COLUMN IF EXISTS estimated_amount,
--      DROP COLUMN IF EXISTS insurance_id;
--  Purely additive.
-- =============================================================================

-- ── Clinical context on the pre-auth exchange itself ─────────────────────────
ALTER TABLE nhcx_transactions ADD COLUMN IF NOT EXISTS diagnosis_code     VARCHAR(20);
ALTER TABLE nhcx_transactions ADD COLUMN IF NOT EXISTS diagnosis_text     TEXT;
ALTER TABLE nhcx_transactions ADD COLUMN IF NOT EXISTS planned_procedure  TEXT;
ALTER TABLE nhcx_transactions ADD COLUMN IF NOT EXISTS expected_los_days  INTEGER;
ALTER TABLE nhcx_transactions ADD COLUMN IF NOT EXISTS room_type          VARCHAR(120);
ALTER TABLE nhcx_transactions ADD COLUMN IF NOT EXISTS estimated_amount   BIGINT;
-- Which policy this pre-auth is against. Absent before Module 4: a claim could
-- be filed without any recorded link to the coverage it relied on.
ALTER TABLE nhcx_transactions ADD COLUMN IF NOT EXISTS insurance_id       UUID;
CREATE INDEX IF NOT EXISTS ix_nhcx_insurance ON nhcx_transactions (tenant_id, insurance_id);

ALTER TABLE nhcx_transactions DROP CONSTRAINT IF EXISTS ck_nhcx_los;
ALTER TABLE nhcx_transactions ADD CONSTRAINT ck_nhcx_los
    CHECK (expected_los_days IS NULL OR expected_los_days BETWEEN 0 AND 365);

-- ── ICD-10 lookup ────────────────────────────────────────────────────────────
--  Deliberately EMPTY. ICD-10 is published by WHO and localised by MoHFW; the
--  codes are not something to invent, and a partial hand-written list would be
--  worse than none because it looks authoritative while silently missing the
--  diagnosis a clinician needs. Load the official release before go-live —
--  the search endpoint degrades to "no matches" until you do, which is honest.
CREATE TABLE IF NOT EXISTS icd10_codes (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID,
    code          VARCHAR(20)  NOT NULL,
    title         TEXT         NOT NULL,
    chapter       VARCHAR(120),
    -- Kept out of search results but retained: some codes are valid for
    -- historical records yet must not be used on new claims.
    billable      BOOLEAN      NOT NULL DEFAULT TRUE,

    status        SMALLINT     NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_icd10_codes PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_icd10_code ON icd10_codes (code);
-- Clinicians search by words in the title far more often than by code.
CREATE INDEX IF NOT EXISTS ix_icd10_title ON icd10_codes USING gin (to_tsvector('english', title));
CREATE INDEX IF NOT EXISTS ix_icd10_code_prefix ON icd10_codes (code varchar_pattern_ops);

-- ── Itemised estimate ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS preauth_estimate_lines (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    branch_id            UUID,
    nhcx_transaction_id  UUID         NOT NULL,

    -- ROOM | OT | IMPLANT | CONSUMABLE | INVESTIGATION | PROFESSIONAL | OTHER
    category             VARCHAR(24)  NOT NULL DEFAULT 'OTHER',
    description          TEXT         NOT NULL,
    quantity             NUMERIC(10,2) NOT NULL DEFAULT 1,
    unit_amount          BIGINT       NOT NULL,
    -- Stored, not derived, so an insurer's later dispute is against the exact
    -- number that was sent rather than one recomputed under today's rules.
    line_amount          BIGINT       NOT NULL,

    -- Filled from the insurer's response, per line, when it itemises.
    approved_amount      BIGINT,

    status               SMALLINT     NOT NULL DEFAULT 1,
    created_by           UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by          UUID,
    modified_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_preauth_estimate_lines PRIMARY KEY (id),
    CONSTRAINT fk_estimate_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_estimate_txn FOREIGN KEY (nhcx_transaction_id)
        REFERENCES nhcx_transactions(id) ON DELETE CASCADE,
    CONSTRAINT ck_estimate_category CHECK (category IN
        ('ROOM','OT','IMPLANT','CONSUMABLE','INVESTIGATION','PROFESSIONAL','OTHER')),
    CONSTRAINT ck_estimate_amounts CHECK (
        unit_amount >= 0 AND line_amount >= 0 AND quantity > 0
        AND (approved_amount IS NULL OR approved_amount >= 0))
);

CREATE INDEX IF NOT EXISTS ix_estimate_txn ON preauth_estimate_lines (nhcx_transaction_id);

-- ── Insurer query thread — Screen 4.3 ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS preauth_queries (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    branch_id            UUID,
    nhcx_transaction_id  UUID         NOT NULL,

    round_number         INTEGER      NOT NULL DEFAULT 1,
    raised_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    query_code           VARCHAR(60),
    query_text           TEXT         NOT NULL,

    responded_at         TIMESTAMPTZ,
    responded_by         UUID,
    response_text        TEXT,
    -- Comma-separated attachment ids; documents live in the existing
    -- attachments table rather than being duplicated here.
    response_attachments TEXT,

    status               SMALLINT     NOT NULL DEFAULT 1,
    created_by           UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by          UUID,
    modified_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_preauth_queries PRIMARY KEY (id),
    CONSTRAINT fk_query_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_query_txn FOREIGN KEY (nhcx_transaction_id)
        REFERENCES nhcx_transactions(id) ON DELETE CASCADE,
    -- A response without a timestamp, or a timestamp without a response, means
    -- nobody can tell whether the insurer is still waiting on us.
    CONSTRAINT ck_query_response CHECK (
        (responded_at IS NULL AND response_text IS NULL)
        OR (responded_at IS NOT NULL AND response_text IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_query_round
    ON preauth_queries (nhcx_transaction_id, round_number);
CREATE INDEX IF NOT EXISTS ix_query_unanswered
    ON preauth_queries (tenant_id, raised_at) WHERE responded_at IS NULL;

-- ── Enhancement request — Screen 4.4 ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS preauth_enhancements (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    branch_id            UUID,
    nhcx_transaction_id  UUID         NOT NULL,

    sequence_number      INTEGER      NOT NULL DEFAULT 1,
    -- What was approved before this request, so the delta is reconstructable
    -- even after the headline approved amount moves.
    previous_approved    BIGINT       NOT NULL,
    revised_estimate     BIGINT       NOT NULL,
    justification        TEXT         NOT NULL,

    correlation_id       VARCHAR(64),
    -- SUBMITTED | APPROVED | REJECTED | QUERY_RAISED
    enhancement_state    VARCHAR(24)  NOT NULL DEFAULT 'SUBMITTED',
    approved_amount      BIGINT,
    responded_at         TIMESTAMPTZ,

    status               SMALLINT     NOT NULL DEFAULT 1,
    created_by           UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by          UUID,
    modified_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_preauth_enhancements PRIMARY KEY (id),
    CONSTRAINT fk_enh_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_enh_txn FOREIGN KEY (nhcx_transaction_id)
        REFERENCES nhcx_transactions(id) ON DELETE CASCADE,
    CONSTRAINT ck_enh_state CHECK (enhancement_state IN
        ('SUBMITTED','APPROVED','REJECTED','QUERY_RAISED')),
    CONSTRAINT ck_enh_amounts CHECK (
        previous_approved >= 0 AND revised_estimate >= 0
        AND (approved_amount IS NULL OR approved_amount >= 0)),
    -- An enhancement asking for less than is already approved is a data entry
    -- error; the correct action there is a claim, not an enhancement.
    CONSTRAINT ck_enh_increase CHECK (revised_estimate > previous_approved)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_enh_sequence
    ON preauth_enhancements (nhcx_transaction_id, sequence_number);

-- ── Feature key ──────────────────────────────────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'PREAUTH_MANAGE', 'CLAIM',
       'Raise and manage cashless pre-authorisation requests', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT DISTINCT rf.role_id, f_new.id
FROM role_features rf
JOIN features f_old ON f_old.id = rf.feature_id AND f_old.feature_key = 'NHCX_CLAIMS'
JOIN features f_new ON f_new.tenant_id = f_old.tenant_id
                   AND f_new.feature_key = 'PREAUTH_MANAGE'
ON CONFLICT DO NOTHING;
