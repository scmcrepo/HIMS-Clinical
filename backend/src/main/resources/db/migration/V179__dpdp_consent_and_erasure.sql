-- =============================================================================
--  V179 — DPDP consent records and erasure requests  (WO-011 / P-003)
--
--  Under the Digital Personal Data Protection Act the hospital is a Data
--  Fiduciary and the patient a Data Principal. Two obligations drive this
--  migration.
--
--  CONSENT IS PER PURPOSE, NOT PER PATIENT. A patient consenting to treatment
--  has not consented to an AI agent calling them on WhatsApp, nor to their claim
--  being posted to an insurer. Each is a separate purpose with its own record.
--  This is why consent is a table and not a boolean column: a boolean cannot
--  answer "what exactly did this patient agree to, on what date, in which
--  language, shown what text" — which is the question an audit asks.
--
--  ERASURE MUST REACH EVERY COPY. Patient data does not only live in the patient
--  table. It is copied into agent idempotency caches, HITL transcripts, NHCX
--  response payloads and ABHA linkages. An erasure request that clears the
--  primary record and leaves those behind has not complied; it has just made the
--  remaining copies harder to find. erasure_requests tracks the sweep per target.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS erasure_targets, erasure_requests, consent_records;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key IN ('CONSENT_MANAGE','ERASURE_MANAGE'));
--    DELETE FROM features WHERE feature_key IN ('CONSENT_MANAGE','ERASURE_MANAGE');
--  Purely additive.
-- =============================================================================

CREATE TABLE IF NOT EXISTS consent_records (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    branch_id          UUID,
    patient_id         UUID         NOT NULL,

    -- One row per purpose. See ConsentPurpose in Java for the vocabulary.
    purpose            VARCHAR(40)  NOT NULL,
    -- GRANTED | WITHDRAWN | EXPIRED
    state              VARCHAR(20)  NOT NULL DEFAULT 'GRANTED',

    -- What the patient was actually shown, and in which language. Without these
    -- the record proves only that a checkbox was ticked, which is not informed
    -- consent.
    notice_version     VARCHAR(20)  NOT NULL,
    notice_language    VARCHAR(10)  NOT NULL DEFAULT 'en',
    notice_text_hash   CHAR(64),

    -- How it was captured: IN_PERSON | WHATSAPP | VOICE | WEB | PAPER
    capture_channel    VARCHAR(20)  NOT NULL,
    captured_by        UUID,
    granted_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ,

    withdrawn_at       TIMESTAMPTZ,
    withdrawn_by       UUID,
    withdrawal_channel VARCHAR(20),

    -- Children's data needs verifiable parental consent. Relevant the moment a
    -- paediatric scheduling agent goes live.
    is_minor           BOOLEAN      NOT NULL DEFAULT FALSE,
    guardian_verified  BOOLEAN      NOT NULL DEFAULT FALSE,

    status             SMALLINT     NOT NULL DEFAULT 1,
    created_by         UUID,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by        UUID,
    modified_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_consent_records PRIMARY KEY (id),
    CONSTRAINT fk_consent_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_consent_state CHECK (state IN ('GRANTED', 'WITHDRAWN', 'EXPIRED')),
    CONSTRAINT ck_consent_minor CHECK (NOT is_minor OR guardian_verified OR state <> 'GRANTED')
);

-- One live grant per patient per purpose. Withdrawn rows are kept forever —
-- they are the evidence that consent was withdrawn and when.
CREATE UNIQUE INDEX IF NOT EXISTS uq_consent_active
    ON consent_records (tenant_id, patient_id, purpose) WHERE state = 'GRANTED';
CREATE INDEX IF NOT EXISTS ix_consent_patient ON consent_records (tenant_id, patient_id);
CREATE INDEX IF NOT EXISTS ix_consent_expiry  ON consent_records (expires_at)
    WHERE state = 'GRANTED';

COMMENT ON TABLE consent_records IS
    'DPDP consent, one row per purpose. Withdrawn rows are never deleted - they are the audit trail.';

-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS erasure_requests (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID         NOT NULL,
    patient_id       UUID         NOT NULL,

    -- ERASURE | CORRECTION
    request_type     VARCHAR(20)  NOT NULL DEFAULT 'ERASURE',
    -- RECEIVED | IN_PROGRESS | COMPLETED | REJECTED | PARTIALLY_COMPLETED
    state            VARCHAR(24)  NOT NULL DEFAULT 'RECEIVED',

    requested_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    requested_via    VARCHAR(20),
    completed_at     TIMESTAMPTZ,

    -- Erasure is not absolute: clinical records carry statutory retention, and a
    -- claim under adjudication cannot be deleted mid-flight. When a request is
    -- refused or partially fulfilled the patient must be told why, so the reason
    -- is recorded rather than inferred.
    rejection_reason VARCHAR(500),
    retained_reason  VARCHAR(500),

    status           SMALLINT     NOT NULL DEFAULT 1,
    created_by       UUID,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by      UUID,
    modified_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_erasure_requests PRIMARY KEY (id),
    CONSTRAINT fk_erasure_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_erasure_type CHECK (request_type IN ('ERASURE', 'CORRECTION')),
    CONSTRAINT ck_erasure_state CHECK (state IN
        ('RECEIVED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'PARTIALLY_COMPLETED'))
);

CREATE INDEX IF NOT EXISTS ix_erasure_patient ON erasure_requests (tenant_id, patient_id);
CREATE INDEX IF NOT EXISTS ix_erasure_open    ON erasure_requests (state)
    WHERE state IN ('RECEIVED', 'IN_PROGRESS');

-- One row per data store the sweep must visit. Making each target explicit is
-- what turns "we deleted the patient" into something auditable: an incomplete
-- sweep is visible as a target still PENDING rather than silently forgotten.
CREATE TABLE IF NOT EXISTS erasure_targets (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    request_id     UUID         NOT NULL,
    tenant_id      UUID         NOT NULL,
    target_store   VARCHAR(60)  NOT NULL,
    -- PENDING | ERASED | ANONYMISED | RETAINED | FAILED
    outcome        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    rows_affected  INTEGER,
    detail         VARCHAR(300),
    processed_at   TIMESTAMPTZ,

    CONSTRAINT pk_erasure_targets PRIMARY KEY (id),
    CONSTRAINT fk_erasure_target_request FOREIGN KEY (request_id)
        REFERENCES erasure_requests(id) ON DELETE CASCADE,
    CONSTRAINT ck_erasure_outcome CHECK (outcome IN
        ('PENDING', 'ERASED', 'ANONYMISED', 'RETAINED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS ix_erasure_targets_request ON erasure_targets (request_id);

COMMENT ON TABLE erasure_targets IS
    'Per-store sweep result for one erasure request. A PENDING row is an incomplete erasure.';

-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.feature_key, 'COMPLIANCE', v.description, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('CONSENT_MANAGE', 'Capture and withdraw patient consent'),
    ('ERASURE_MANAGE', 'Process erasure and correction requests')
) AS v(feature_key, description)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- Front desk captures consent; erasure is an administrative act with legal
-- consequences and stays with admins.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('HOSPITAL_ADMIN','ADMIN','BRANCH_ADMIN','RECEPTION')
  AND f.feature_key = 'CONSENT_MANAGE'
ON CONFLICT DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('HOSPITAL_ADMIN','ADMIN')
  AND f.feature_key = 'ERASURE_MANAGE'
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
--  Rollout control (P-004).
--
--  One row per tenant. kill_switch is separate from stage and deliberately
--  blunt: at 2am whoever is awake needs a single flag that stops everything, not
--  a decision about which stage to unwind.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_rollout (
    tenant_id        UUID         NOT NULL,
    stage            VARCHAR(30)  NOT NULL DEFAULT 'shadow',
    kill_switch      BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled_channels JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- Promotion and rollback both require a written reason. If you cannot say
    -- what changed, the change is not ready.
    last_change_reason VARCHAR(500),
    last_changed_by  UUID,
    last_changed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_agent_rollout PRIMARY KEY (tenant_id),
    CONSTRAINT fk_rollout_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_rollout_stage CHECK (stage IN
        ('off', 'shadow', 'whatsapp_scheduling', 'voice_reception', 'claims_automation'))
);

-- Every tenant starts in shadow: reads and proposes, executes nothing.
INSERT INTO agent_rollout (tenant_id, stage)
SELECT t.id, 'shadow' FROM tenants t
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'ROLLOUT_MANAGE', 'COMPLIANCE',
       'Control agent rollout stage and kill switch', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('HOSPITAL_ADMIN','ADMIN') AND f.feature_key = 'ROLLOUT_MANAGE'
ON CONFLICT DO NOTHING;
