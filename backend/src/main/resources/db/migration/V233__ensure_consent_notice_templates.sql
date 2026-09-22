-- ---------------------------------------------------------------------------
--  V233 — Ensure consent_notice_templates table exists and is populated
--
--  WHY THIS EXISTS
--
--  If V222 was skipped or had a script collision in flyway_schema_history on
--  certain environments, consent_notice_templates does not exist.
--  This migration ensures the table exists and seeds templates from
--  consent_notices if not already present.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS consent_notice_templates (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    purpose      VARCHAR(40)  NOT NULL,
    version      VARCHAR(20)  NOT NULL,
    language     VARCHAR(10)  NOT NULL,
    body_text    TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_consent_notice_templates PRIMARY KEY (id),
    CONSTRAINT uq_notice_template UNIQUE (purpose, version, language)
);

-- Backfill templates if table was empty or newly created
INSERT INTO consent_notice_templates (purpose, version, language, body_text)
SELECT DISTINCT ON (n.purpose, n.language)
       n.purpose, n.version, n.language, n.body_text
FROM consent_notices n
WHERE n.version = 'v2.0-draft'
ORDER BY n.purpose, n.language, n.created_at
ON CONFLICT (purpose, version, language) DO NOTHING;

COMMENT ON TABLE consent_notice_templates IS
    'Notice text copied into a new tenant at onboarding, always as DRAFT. '
    'Belongs to no tenant on purpose: a notice is a statement by a specific '
    'Data Fiduciary, so one hospital must never inherit another''s as ACTIVE.';
