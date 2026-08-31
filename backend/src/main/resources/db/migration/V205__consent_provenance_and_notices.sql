-- ---------------------------------------------------------------------------
--  V205 — Consent provenance and the notice registry  (WO-022 / C-001)
--
--  WHY THIS EXISTS
--
--  Until this migration the consent gate could not fail. Four call sites
--  (AbhaService, PolicyDiscoveryService x2, PreAuthService) granted the consent
--  they were about to check:
--
--      if (!hasConsent(p, PURPOSE)) { grant(p, PURPOSE, ... , null, ...); }
--      requireConsent(p, PURPOSE);
--
--  Every row those sites wrote claims capture_channel='VERBAL_IN_PERSON' with
--  captured_by NULL — verbal consent captured by nobody. Under DPDP that is not
--  consent; worse, it is documentary evidence asserting consent that was never
--  obtained, sitting in the exact table an inquiry would ask for.
--
--  Two changes here:
--
--  1. provenance. Distinguishes consent a human attested to from consent the
--     system inferred. hasConsent() ignores SYSTEM_INFERRED, so the affected
--     patients are re-asked at next contact.
--
--  2. consent_notices. notice_text_hash previously hashed the ConsentPurpose
--     enum's own one-line summary — a developer-authored UI label. A hash whose
--     preimage cannot be produced proves nothing. Notices now live in a table,
--     per tenant, per language, versioned.
--
--  THE EXISTING ROWS ARE NOT DELETED. They are marked SYSTEM_INFERRED and kept.
--  Deleting them would destroy the record that the system once asserted consent,
--  which is the first thing an investigation would want to see. This is a
--  deliberate decision recorded in WO-022 §4.2 and is flagged for legal review.
--
--  ROLLBACK
--    ALTER TABLE consent_records DROP COLUMN provenance;
--    DROP TABLE consent_notices;
--  The backfill is not reversible: the original rows carried no provenance at
--  all, so SYSTEM_INFERRED is strictly more information than existed before.
-- ---------------------------------------------------------------------------

-- ── 1. Provenance ──────────────────────────────────────────────────────────

ALTER TABLE consent_records
    ADD COLUMN IF NOT EXISTS provenance VARCHAR(20) NOT NULL DEFAULT 'STAFF_ATTESTED';

COMMENT ON COLUMN consent_records.provenance IS
    'STAFF_ATTESTED: a named user attested the patient was shown the notice and agreed. '
    'PATIENT_DIGITAL: the patient agreed themselves via portal or app. '
    'SYSTEM_INFERRED: written by the pre-V205 self-granting defect; NOT valid consent. '
    'IMPORTED: migrated from a prior system with its own evidence trail.';

-- Backfill. Predicated on captured_by IS NULL rather than applied blanket: every
-- row the defect wrote had a null capturer, and a genuine staff-captured row
-- (had any existed) would not. This is deliberately conservative — it will not
-- mislabel a real capture as inferred.
UPDATE consent_records
   SET provenance = 'SYSTEM_INFERRED'
 WHERE captured_by IS NULL
   AND provenance = 'STAFF_ATTESTED';

ALTER TABLE consent_records
    DROP CONSTRAINT IF EXISTS ck_consent_provenance;
ALTER TABLE consent_records
    ADD CONSTRAINT ck_consent_provenance
    CHECK (provenance IN ('STAFF_ATTESTED', 'PATIENT_DIGITAL', 'SYSTEM_INFERRED', 'IMPORTED'));

-- Supports the hms_consent_inferred_remaining gauge and the re-consent burndown.
CREATE INDEX IF NOT EXISTS ix_consent_inferred
    ON consent_records (tenant_id, provenance)
 WHERE state = 'GRANTED' AND provenance = 'SYSTEM_INFERRED';

-- ── 2. Notice registry ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS consent_notices (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    branch_id       UUID,

    purpose         VARCHAR(40)  NOT NULL,
    version         VARCHAR(20)  NOT NULL,
    language        VARCHAR(10)  NOT NULL DEFAULT 'en',

    -- The text actually shown to the patient. Hospital copy, not patient data,
    -- so it is not encrypted and not in scope for erasure.
    body_text       TEXT         NOT NULL,

    -- DRAFT text is a placeholder carried over from the enum summaries so the
    -- desk is not hard-blocked on day one. It is NOT an adequate DPDP notice:
    -- it states no retention period, no recipients and no withdrawal method.
    -- ACTIVE means counsel-approved.
    notice_state    VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',

    effective_from  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    effective_to    TIMESTAMPTZ,

    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_consent_notices PRIMARY KEY (id),
    CONSTRAINT fk_notice_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_notice_state CHECK (notice_state IN ('DRAFT', 'ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_notice_dates CHECK (effective_to IS NULL OR effective_to > effective_from)
);

COMMENT ON TABLE consent_notices IS
    'The exact text shown to a patient when consent was captured, per tenant, '
    'purpose, version and language. consent_records.notice_text_hash is the '
    'SHA-256 of body_text, so what the patient saw can be reproduced on demand.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_consent_notice
    ON consent_notices (tenant_id, purpose, version, language);

-- One live notice per tenant/purpose/language. A second ACTIVE row would make
-- "which text did we show?" ambiguous at exactly the moment it matters.
CREATE UNIQUE INDEX IF NOT EXISTS uq_consent_notice_active
    ON consent_notices (tenant_id, purpose, language)
 WHERE notice_state = 'ACTIVE' AND effective_to IS NULL;

CREATE INDEX IF NOT EXISTS ix_consent_notice_lookup
    ON consent_notices (tenant_id, purpose, language, notice_state);

-- ── 3. Seed v1.0 placeholders for every existing tenant ────────────────────
--
-- Text mirrors ConsentPurpose.getNoticeSummary() so behaviour is unchanged on
-- day one. Seeded as DRAFT deliberately: these are UI labels, not notices, and
-- WO-022 does not claim to close the S. 5 notice obligation. Replacing them
-- with counsel-approved wording is a data change, not a deployment.

INSERT INTO consent_notices (id, tenant_id, purpose, version, language, body_text, notice_state)
SELECT gen_random_uuid(), t.id, v.purpose, 'v1.0', 'en', v.body, 'DRAFT'
FROM tenants t
CROSS JOIN (VALUES
    ('TREATMENT',       'Treatment and clinical care'),
    ('AGENT_MESSAGING', 'Automated messaging about your care'),
    ('AGENT_VOICE',     'Automated voice calls, which may be recorded'),
    ('INSURANCE_CLAIM', 'Sharing your details with your insurer for claims'),
    ('ABHA_LINKAGE',    'Creating or linking your ABHA health account'),
    ('MARKETING',       'Updates and offers from the hospital')
) AS v(purpose, body)
ON CONFLICT (tenant_id, purpose, version, language) DO NOTHING;
