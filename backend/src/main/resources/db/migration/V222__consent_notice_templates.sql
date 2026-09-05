-- ---------------------------------------------------------------------------
--  V222 — Consent notice templates for onboarding  (WO-033, card E-006)
--
--  WHY THIS EXISTS
--
--  A hospital onboarded after V211/V217 got no consent notices at all. Those
--  migrations seed by CROSS JOIN over the tenants that existed when they ran, so
--  tenant five starts with an empty notice registry and every ConsentGate serves
--  nothing.
--
--  The first fix for that copied notices out of a hardcoded tenant
--  (00000000-...-0001) at onboarding time. That is the wrong source, for two
--  reasons that matter under the Act:
--
--   1. A consent notice is a statement by a SPECIFIC Data Fiduciary about what
--      IT does with personal data. Copying one hospital's notices into another
--      means the second hospital publishes text it never wrote, describing
--      practices that may not be its own.
--
--   2. The copy carried notice_state across. The moment tenant 1 has its notices
--      approved to ACTIVE — which is the whole point of L-005 — every hospital
--      onboarded afterwards inherits ACTIVE notices nobody at that hospital has
--      read. That defeats the reason V211 and V217 both ship DRAFT.
--
--  So onboarding copies from a template set that belongs to no tenant, and
--  always writes DRAFT.
--
--  ── Where the template text comes from ─────────────────────────────────────
--
--  From the notices already seeded by V211 and V217, taken from the
--  earliest-created tenant. That is faithful rather than lossy: at the moment
--  this migration runs, every tenant holds byte-identical text, because V211 and
--  V217 inserted the same bodies for all of them and nothing edits notices
--  automatically.
--
--  It is not risk-free. If a hospital has hand-edited its notices between V211
--  and this migration, that edit becomes the template. Ordering by created_at
--  and taking the earliest tenant makes the source predictable, and the
--  templates are inspectable afterwards:
--
--      SELECT purpose, language, left(body_text, 60) FROM consent_notice_templates;
--
--  Check that before onboarding a hospital, once, and the risk is closed.
--
--  ── Only the current version ───────────────────────────────────────────────
--
--  v2.0-draft only. V205 also seeded a superseded v1.0 English set, and copying
--  that into a new hospital would hand it a version it never used and an
--  immediate question about which one is in force.
--
--  ── Rollback ───────────────────────────────────────────────────────────────
--
--      DROP TABLE IF EXISTS consent_notice_templates;
--
--  Safe. Onboarding then logs an error and creates a hospital with no notices,
--  which is the behaviour before this migration and is visible rather than
--  silent.
--
--  Additive. No existing table is touched.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS consent_notice_templates (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- No tenant_id, deliberately. These belong to the product, not to a
    -- hospital, and that is the entire point of the table.
    purpose      VARCHAR(40)  NOT NULL,
    version      VARCHAR(20)  NOT NULL,
    language     VARCHAR(10)  NOT NULL,
    body_text    TEXT         NOT NULL,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_consent_notice_templates PRIMARY KEY (id),
    CONSTRAINT uq_notice_template UNIQUE (purpose, version, language)
);

-- Capture the canonical text as seeded, from the earliest tenant.
INSERT INTO consent_notice_templates (purpose, version, language, body_text)
SELECT DISTINCT ON (n.purpose, n.language)
       n.purpose, n.version, n.language, n.body_text
FROM consent_notices n
WHERE n.tenant_id = (SELECT t.id FROM tenants t ORDER BY t.created_at, t.id LIMIT 1)
  AND n.version = 'v2.0-draft'
ORDER BY n.purpose, n.language, n.created_at
ON CONFLICT (purpose, version, language) DO NOTHING;

COMMENT ON TABLE consent_notice_templates IS
    'Notice text copied into a new tenant at onboarding, always as DRAFT. '
    'Belongs to no tenant on purpose: a notice is a statement by a specific '
    'Data Fiduciary, so one hospital must never inherit another''s as ACTIVE.';

COMMENT ON COLUMN consent_notice_templates.body_text IS
    'Still carries the [HOSPITAL], [RETENTION] and [CONTACT] placeholders, and '
    'the Tamil and Hindi rows are unreviewed translations. Copying these into a '
    'tenant does not make them approved — see L-005 and E-005.';
