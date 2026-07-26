-- =============================================================================
--  V178 — ABHA linkage and NHCX transactions  (WO-003 / WO-008)
--
--  Note what is NOT here: any Aadhaar column. Aadhaar is passed to ABDM for OTP
--  verification and discarded. Storing it would create a retention and breach
--  obligation that nothing in this system actually needs.
--
--  Encrypted columns cannot be searched, so every identifier that must be looked
--  up carries a deterministic blind-index token alongside it — the same pattern
--  as the existing staff contact tokens.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS nhcx_transactions, abha_linkages;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key IN ('ABHA_MANAGE','NHCX_CLAIMS'));
--    DELETE FROM features WHERE feature_key IN ('ABHA_MANAGE','NHCX_CLAIMS');
--  Purely additive.
-- =============================================================================

CREATE TABLE IF NOT EXISTS abha_linkages (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    branch_id           UUID,
    patient_id          UUID         NOT NULL,

    abha_number         TEXT,
    abha_number_token   VARCHAR(64),
    abha_address        TEXT,
    abha_address_token  VARCHAR(64),

    linkage_state       VARCHAR(24)  NOT NULL DEFAULT 'PENDING_OTP',
    transaction_id      VARCHAR(64),
    linked_at           TIMESTAMPTZ,
    failure_code        VARCHAR(60),

    -- DPDP consent is separate from ABDM's own consent-manager artefact. A
    -- patient consenting to treatment has not consented to a national health-id
    -- linkage; the two are recorded independently.
    consent_recorded_at TIMESTAMPTZ,
    consent_version     VARCHAR(20),

    status              SMALLINT     NOT NULL DEFAULT 1,
    created_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by         UUID,
    modified_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_abha_linkages PRIMARY KEY (id),
    CONSTRAINT fk_abha_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_abha_state CHECK (linkage_state IN
        ('PENDING_OTP', 'LINKED', 'FAILED', 'NOT_INTEGRATED'))
);

-- One live linkage per patient per tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_abha_patient_linked
    ON abha_linkages (tenant_id, patient_id) WHERE linkage_state = 'LINKED';
CREATE INDEX IF NOT EXISTS ix_abha_number_token  ON abha_linkages (tenant_id, abha_number_token);
CREATE INDEX IF NOT EXISTS ix_abha_address_token ON abha_linkages (tenant_id, abha_address_token);
CREATE INDEX IF NOT EXISTS ix_abha_txn           ON abha_linkages (transaction_id);

COMMENT ON TABLE abha_linkages IS
    'Patient ABHA identities. No Aadhaar is stored. Identifiers encrypted; tokens are blind indexes.';

CREATE TABLE IF NOT EXISTS nhcx_transactions (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    branch_id        UUID,

    correlation_id   VARCHAR(64)  NOT NULL,
    api_call_id      VARCHAR(64),
    exchange_type    VARCHAR(20)  NOT NULL,
    payer_code       VARCHAR(60)  NOT NULL,

    patient_id       UUID,
    encounter_id     UUID,
    bill_id          UUID,
    claim_amount     BIGINT,

    state            VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    outcome_code     VARCHAR(60),
    approved_amount  BIGINT,
    response_payload TEXT,

    submitted_at     TIMESTAMPTZ,
    responded_at     TIMESTAMPTZ,
    -- The payer may simply never respond. Without a deadline the claim sits in
    -- limbo and the hospital is unpaid and unaware; a sweep escalates on this.
    expires_at       TIMESTAMPTZ  NOT NULL,

    status           SMALLINT     NOT NULL DEFAULT 1,
    created_by       UUID,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by      UUID,
    modified_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_nhcx_transactions PRIMARY KEY (id),
    CONSTRAINT fk_nhcx_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_nhcx_type CHECK (exchange_type IN ('ELIGIBILITY', 'PREAUTH', 'CLAIM')),
    CONSTRAINT ck_nhcx_state CHECK (state IN
        ('SUBMITTED', 'ACKNOWLEDGED', 'APPROVED', 'REJECTED', 'TIMED_OUT', 'FAILED'))
);

-- Callbacks are at-least-once. The unique correlation id is what makes
-- duplicate delivery a no-op rather than a second claim.
CREATE UNIQUE INDEX IF NOT EXISTS uq_nhcx_correlation
    ON nhcx_transactions (correlation_id);
CREATE INDEX IF NOT EXISTS ix_nhcx_pending ON nhcx_transactions (expires_at)
    WHERE state IN ('SUBMITTED', 'ACKNOWLEDGED');
CREATE INDEX IF NOT EXISTS ix_nhcx_tenant_state ON nhcx_transactions (tenant_id, state, created_at);

COMMENT ON COLUMN nhcx_transactions.response_payload IS
    'Payer response bundle — contains clinical detail. Encrypted via EncryptedStringConverter.';

-- ── Feature keys ────────────────────────────────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.feature_key, v.module, v.description, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('ABHA_MANAGE',      'ABDM',  'Create and link patient ABHA identities'),
    ('NHCX_CLAIMS',      'CLAIM', 'Submit and track NHCX claims'),
    ('AGENT_ABHA_WRITE', 'AGENT', 'Agent: initiate ABHA linkage'),
    ('AGENT_CLAIMS_READ','AGENT', 'Agent: read claim and eligibility status')
) AS v(feature_key, module, description)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN', 'BRANCH_ADMIN', 'RECEPTION')
  AND f.feature_key IN ('ABHA_MANAGE', 'NHCX_CLAIMS')
ON CONFLICT DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE LOWER(r.name) = 'agent'
  AND f.feature_key IN ('AGENT_ABHA_WRITE', 'AGENT_CLAIMS_READ')
ON CONFLICT DO NOTHING;
