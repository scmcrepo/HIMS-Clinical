-- =============================================================================
--  V193 — ABHA card access and PII disclosure audit  (WO-012 / AB-004)
--
--  Every other ABHA endpoint returns a masked number. The card endpoint is the
--  one place the unmasked national health ID leaves the database, so it gets its
--  own permission rather than riding on ABHA_MANAGE: the clerk who links an ABHA
--  during registration does not need to pull the card, and under DPDP the
--  hospital has to be able to show that access was scoped, not blanket.
--
--  pii_disclosure_audit is deliberately general rather than ABHA-specific.
--  Module 3 will release external health records under an ABDM consent artifact,
--  and that disclosure needs the same trail. Building a second table then would
--  leave two half-answers to "who saw this patient's data".
--
--  The table records THAT a disclosure happened, never the disclosed value.
--  An audit log containing the identifiers it exists to protect is a second
--  copy of the problem.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS pii_disclosure_audit;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key = 'ABHA_CARD_VIEW');
--    DELETE FROM features WHERE feature_key = 'ABHA_CARD_VIEW';
--  Purely additive.
-- =============================================================================

CREATE TABLE IF NOT EXISTS pii_disclosure_audit (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL,
    branch_id         UUID,

    -- ABHA_CARD | EXTERNAL_HEALTH_RECORD | POLICY_DOCUMENT
    disclosure_type   VARCHAR(40)  NOT NULL,
    -- Surrogate key of the subject. Never a name, never an identifier value.
    subject_id        UUID         NOT NULL,
    -- The row that was disclosed, where one exists (e.g. the abha_linkages id).
    resource_id       UUID,

    actor_user_id     UUID,
    -- Why the actor said they needed it. Free text on purpose: a fixed list
    -- trains people to pick the first option.
    purpose           TEXT,

    outcome           VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    failure_code      VARCHAR(80),

    correlation_id    VARCHAR(64),
    disclosed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    status            SMALLINT     NOT NULL DEFAULT 1,
    created_by        UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by       UUID,
    modified_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_pii_disclosure_audit PRIMARY KEY (id),
    CONSTRAINT fk_disclosure_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_disclosure_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE')),
    CONSTRAINT ck_disclosure_type CHECK (disclosure_type IN
        ('ABHA_CARD', 'EXTERNAL_HEALTH_RECORD', 'POLICY_DOCUMENT'))
);

-- "Everything ever disclosed about this patient" is the question a DPDP subject
-- access request asks, so it is the index that exists.
CREATE INDEX IF NOT EXISTS ix_disclosure_subject
    ON pii_disclosure_audit (tenant_id, subject_id, disclosed_at DESC);
CREATE INDEX IF NOT EXISTS ix_disclosure_actor
    ON pii_disclosure_audit (tenant_id, actor_user_id, disclosed_at DESC);

INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.feature_key, v.module, v.description, t.id
FROM (VALUES
    ('ABHA_CARD_VIEW', 'ABHA', 'Download the ABHA card, which reveals the unmasked ABHA number')
) AS v(feature_key, module, description)
CROSS JOIN tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- Granted narrowly and on purpose. ABHA_MANAGE is held by registration staff so
-- they can link an identity; revealing the number is a separate decision, and
-- defaulting it to the same roles would make the new permission cosmetic.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN', 'DOCTOR')
  AND f.feature_key = 'ABHA_CARD_VIEW'
ON CONFLICT DO NOTHING;
