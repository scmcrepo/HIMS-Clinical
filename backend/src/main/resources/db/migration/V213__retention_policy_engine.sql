-- ---------------------------------------------------------------------------
--  V213 — Retention policy engine  (WO-025)
--
--  WHY THIS EXISTS
--
--  DPDP s. 8(7) requires a Data Fiduciary to erase personal data once the
--  purpose is served and retention is no longer required by law. Nothing in this
--  system does that. Seven scheduled jobs exist and not one deletes patient data
--  by policy. ConsentService.expireLapsedConsents expires consent rows; the data
--  those grants authorised lives forever.
--
--  The DPIA rates this R6, and it is the only risk in that document rated as
--  actively WORSENING — every day of operation adds data nothing will ever
--  remove, and every other risk on the list grows with it.
--
--  ── Policy in a table, not in code ─────────────────────────────────────────
--
--  Retention periods are a legal determination that differs per hospital and
--  changes when the law does. Encoding them as constants would mean a lawyer's
--  decision becomes a deployment. They live in a table so counsel can set them
--  and an administrator can change them.
--
--  ErasureService.TARGETS stays in code because it answers a different question
--  — the ORDER and MECHANISM of clearing a store, which is a structural property
--  of the schema. This table answers WHEN.
--
--  ── Nothing deletes until someone says so ──────────────────────────────────
--
--  Every policy seeded here is enabled = FALSE and dry_run = TRUE. The job will
--  report what it WOULD delete and delete nothing. That is deliberate:
--
--    * The periods below are engineering defaults, not legal advice. No lawyer
--      has reviewed them.
--    * A scheduled job that deletes patient records is the single
--      highest-consequence thing in this codebase, and it has never been run.
--    * A hospital discovering its retention policy by watching records vanish
--      is a worse outcome than one that never turns the job on.
--
--  Turning it on is two column updates and should follow a dry run whose output
--  someone actually read.
--
--  ROLLBACK
--    DROP TABLE retention_run_items;
--    DROP TABLE retention_runs;
--    DROP TABLE retention_policies;
--  Safe. Nothing else references these, and with dry_run = TRUE no data has been
--  removed by them.
-- ---------------------------------------------------------------------------

-- ── 1. The policies ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS retention_policies (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,

    -- Matches ErasureService.TARGETS keys where the two overlap, so an operator
    -- reading one can find the other.
    target_store        VARCHAR(60)  NOT NULL,
    -- Column the age is measured from. Must be a real timestamp on that table;
    -- the service validates this at startup rather than at 2am.
    date_column         VARCHAR(60)  NOT NULL,

    retention_days      INTEGER      NOT NULL,
    -- DELETE removes the row. ANONYMISE keeps it and clears the identifier,
    -- for rows something non-personal still depends on (counts, reconciliation).
    action              VARCHAR(20)  NOT NULL,
    -- Which column ANONYMISE nulls. Defaults to patient_id, but not every store
    -- links to a patient by that name — agent_tool_invocations uses
    -- target_entity_id because the same table logs calls against appointments and
    -- encounters too. Ignored when action = 'DELETE'.
    anonymise_column    VARCHAR(60)  NOT NULL DEFAULT 'patient_id',

    -- Why this period. Free text, and mandatory: a retention period nobody can
    -- justify is one nobody can defend to a regulator or to a patient.
    justification       TEXT         NOT NULL,
    -- Set where a statute rather than a business decision fixes the period.
    statutory_basis     VARCHAR(200),

    -- Both default to the safe setting. See the header.
    enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    dry_run             BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Guard against a policy that would clear a large fraction of a table in one
    -- pass, which is the shape of a misconfigured date_column or a wrong unit.
    max_rows_per_run    INTEGER      NOT NULL DEFAULT 500,

    last_run_at         TIMESTAMPTZ,
    last_run_affected   INTEGER,

    status              SMALLINT     NOT NULL DEFAULT 1,
    created_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by         UUID,
    modified_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_retention_policies PRIMARY KEY (id),
    CONSTRAINT fk_retention_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT uq_retention_policy UNIQUE (tenant_id, target_store),
    CONSTRAINT ck_retention_action CHECK (action IN ('DELETE', 'ANONYMISE')),
    -- A zero or negative period would delete rows the moment they are written.
    CONSTRAINT ck_retention_days CHECK (retention_days > 0),
    CONSTRAINT ck_retention_batch CHECK (max_rows_per_run > 0 AND max_rows_per_run <= 10000)
);

COMMENT ON TABLE retention_policies IS
    'Per-tenant storage limitation policy under s. 8(7). Every row seeded by V213 '
    'is disabled and in dry-run: the periods are engineering defaults and no '
    'lawyer has reviewed them.';

COMMENT ON COLUMN retention_policies.dry_run IS
    'TRUE means report what would be affected and change nothing. Turn off only '
    'after reading a dry-run report for that store.';

CREATE INDEX IF NOT EXISTS ix_retention_enabled
    ON retention_policies (tenant_id) WHERE enabled = TRUE;

-- ── 2. What each run did ──────────────────────────────────────────────────
--
-- A retention job that deletes without a record is indistinguishable from data
-- loss. This is the evidence that a deletion was policy rather than an incident,
-- and it is the first thing anyone will ask for when a record is missing.

CREATE TABLE IF NOT EXISTS retention_runs (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID,

    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    -- RUNNING | COMPLETED | FAILED | ABORTED
    state           VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    dry_run         BOOLEAN      NOT NULL,

    policies_evaluated INTEGER   NOT NULL DEFAULT 0,
    rows_affected      INTEGER   NOT NULL DEFAULT 0,
    error_detail       TEXT,

    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_retention_runs PRIMARY KEY (id),
    CONSTRAINT ck_retention_run_state CHECK (state IN
        ('RUNNING', 'COMPLETED', 'FAILED', 'ABORTED'))
);

CREATE INDEX IF NOT EXISTS ix_retention_run_recent
    ON retention_runs (started_at DESC);

CREATE TABLE IF NOT EXISTS retention_run_items (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    run_id          UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,

    target_store    VARCHAR(60)  NOT NULL,
    action          VARCHAR(20)  NOT NULL,
    cutoff_at       TIMESTAMPTZ  NOT NULL,
    rows_matched    INTEGER      NOT NULL DEFAULT 0,
    -- Differs from rows_matched in a dry run, and when the batch cap bites.
    rows_affected   INTEGER      NOT NULL DEFAULT 0,
    capped          BOOLEAN      NOT NULL DEFAULT FALSE,
    outcome         VARCHAR(20)  NOT NULL,
    detail          VARCHAR(300),

    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_retention_run_items PRIMARY KEY (id),
    CONSTRAINT fk_run_item FOREIGN KEY (run_id)
        REFERENCES retention_runs(id) ON DELETE CASCADE,
    CONSTRAINT ck_run_item_outcome CHECK (outcome IN
        ('DRY_RUN', 'APPLIED', 'SKIPPED', 'FAILED', 'CAPPED'))
);

CREATE INDEX IF NOT EXISTS ix_run_item_run ON retention_run_items (run_id);

-- ── 3. Seeded defaults — disabled, dry-run ────────────────────────────────
--
-- Chosen from what the data is for, not from a legal source. Clinical records
-- are deliberately ABSENT: their retention is governed by medico-legal rules
-- this project has no basis to encode, and a wrong period there destroys
-- evidence a patient may need. ErasureService already treats them as RETAIN.

INSERT INTO retention_policies
    (id, tenant_id, target_store, date_column, retention_days, action,
     anonymise_column, justification, statutory_basis, enabled, dry_run, max_rows_per_run)
SELECT gen_random_uuid(), t.id, v.store, v.datecol, v.days, v.action,
       v.anoncol, v.reason, v.basis, FALSE, TRUE, 500
FROM tenants t
CROSS JOIN (VALUES
    ('portal_sessions', 'expires_at', 30, 'DELETE', 'patient_id',
     'A consumed or expired portal session has no further purpose. Kept 30 days past expiry only so a support query about a login can be answered.',
     NULL),

    ('agent_idempotency_keys', 'expires_at', 30, 'DELETE', 'patient_id',
     'Cached tool responses. response_body routinely contains patient detail and the cache stops being useful the moment the key expires.',
     NULL),

    -- Anonymises target_entity_id, not patient_id: this table has no patient_id
    -- column, because it logs tool calls against appointments and encounters as
    -- well as patients.
    ('agent_tool_invocations', 'created_at', 400, 'ANONYMISE', 'target_entity_id',
     'The record that a tool ran is operationally useful; which patient it ran against is not, after the fact. 400 days aligns with the Rule 6(e) log retention floor.',
     'DPDP Rules 2025, Rule 6(e) — one year minimum for security logs'),

    ('hitl_escalations', 'resolved_at', 400, 'ANONYMISE', 'patient_id',
     'Transcripts contain health data in free text. The escalation and its timings stay for operational analysis; the words spoken do not.',
     NULL),

    ('discovered_policies', 'created_at', 365, 'DELETE', 'patient_id',
     'Insurance policy details discovered from a registry lookup. Derived data that can be rediscovered on demand; holding it is convenience, not necessity.',
     NULL),

    ('pii_disclosure_audit', 'created_at', 400, 'DELETE', 'patient_id',
     'Audit of PHI access. Retained one year past the Rule 6(e) floor, then removed — an access log is itself a record of who looked at whom.',
     'DPDP Rules 2025, Rule 6(e)')

    -- NOT SEEDED: appointments.
    --
    -- The table has no tenant_id column. The retention job runs from a scheduled
    -- thread with no tenant context, so the Hibernate filter is off and the
    -- tenant predicate in the statement is the only thing keeping one hospital's
    -- policy from reaching another's rows. Without that column there is no safe
    -- predicate to write.
    --
    -- Appointments are also reachable through ErasureService (ANONYMISE) on an
    -- individual erasure request, so they are not unreachable — only unscheduled.
    -- Adding tenant_id to appointments is the prerequisite, and it is a schema
    -- change with its own blast radius rather than something to slip into a
    -- retention migration.

) AS v(store, datecol, days, action, anoncol, reason, basis)
ON CONFLICT (tenant_id, target_store) DO NOTHING;

-- ── 4. Permission ─────────────────────────────────────────────────────────
--
-- Narrow. Changing a retention period changes when patient records are
-- destroyed, which is closer to a legal act than an administrative one.

INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'RETENTION_MANAGE', 'COMPLIANCE',
       'View and configure data retention policies', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE f.feature_key = 'RETENTION_MANAGE'
  AND UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
