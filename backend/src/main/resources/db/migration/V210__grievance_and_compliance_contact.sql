-- ---------------------------------------------------------------------------
--  V210 — Grievance redressal and the published contact point  (WO-027)
--
--  WHY THIS EXISTS
--
--  DPDP s. 8(9) requires a Data Fiduciary to publish an effective grievance
--  redressal mechanism, and s. 13 gives every Data Principal the right to use it.
--  Rule 13 additionally requires a Significant Data Fiduciary to publish the
--  contact details of a Data Protection Officer based in India.
--
--  None of this existed. There was no intake, no SLA tracking, and nothing
--  published anywhere — so a patient with a complaint had no route short of
--  going straight to the Data Protection Board, which is precisely the outcome
--  a grievance mechanism is supposed to prevent.
--
--  ── The clock ───────────────────────────────────────────────────────────────
--
--  The Rules set a 90-day ceiling for grievance resolution. That is a maximum,
--  not a target, so this schema tracks both a target date and the statutory
--  deadline. A complaint answered on day 89 is compliant and is also a bad
--  outcome; separating the two makes the difference visible instead of letting
--  90 days become the working norm.
--
--  ── Contact point vs DPO ────────────────────────────────────────────────────
--
--  One table, a flag, and a reason. Every Fiduciary needs a contact point; only
--  an SDF must name a DPO. Whether this platform or its tenants are SDFs is
--  unresolved and sits with counsel, so the schema supports both rather than
--  forcing the question prematurely. is_dpo makes the claim explicit rather than
--  implied by a job title someone typed.
--
--  ── Multi-tenant note ───────────────────────────────────────────────────────
--
--  tenant_id is NOT NULL here, unlike security_incidents. Under the split
--  confirmed 2026-08-30, the hospital is the Fiduciary for patient data and owes
--  its own patients the grievance mechanism. A platform-level contact does not
--  discharge a hospital's duty, and publishing one shared address would tell a
--  patient to complain to the wrong party.
--
--  ROLLBACK
--    DROP TABLE grievance_events;
--    DROP TABLE grievances;
--    DROP TABLE compliance_contacts;
--  Safe while unused. Once a grievance is filed the register is evidence of what
--  was raised and when, and must not be dropped.
-- ---------------------------------------------------------------------------

-- ── 1. The published contact point ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS compliance_contacts (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    branch_id       UUID,

    -- Displayed publicly, so deliberately a role and not a person's name where
    -- the hospital prefers that. Both are lawful; naming an individual who then
    -- leaves is the more common failure.
    display_name    VARCHAR(120) NOT NULL,
    designation     VARCHAR(120),

    email           VARCHAR(160) NOT NULL,
    phone           VARCHAR(30),
    postal_address  TEXT,

    -- True only where the hospital has determined it is a Significant Data
    -- Fiduciary and this person is its DPO. Explicit because "we have a DPO" is
    -- a legal claim with obligations attached, not a job title.
    is_dpo          BOOLEAN      NOT NULL DEFAULT FALSE,
    -- India-based is a Rule 13 requirement for an SDF's DPO.
    based_in_india  BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Only one live contact per tenant may be published at a time.
    active_from     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    active_to       TIMESTAMPTZ,

    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_compliance_contacts PRIMARY KEY (id),
    CONSTRAINT fk_contact_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    -- A DPO who is not in India does not satisfy Rule 13, so the combination is
    -- rejected at the schema rather than discovered during an audit.
    CONSTRAINT ck_dpo_in_india CHECK (NOT is_dpo OR based_in_india)
);

COMMENT ON TABLE compliance_contacts IS
    'The contact point published to data principals under s. 8(9), and the DPO '
    'under Rule 13 where the tenant is a Significant Data Fiduciary.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_contact_active
    ON compliance_contacts (tenant_id) WHERE active_to IS NULL;

-- ── 2. The grievance register ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS grievances (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    branch_id           UUID,

    -- Quotable over the phone. A patient chasing a complaint should not have to
    -- read out a UUID.
    grievance_ref       VARCHAR(30)  NOT NULL,

    -- Nullable: a complaint may arrive from someone we cannot yet match to a
    -- patient record, and refusing to record it until we can would be a neat way
    -- of never recording the inconvenient ones.
    patient_id          UUID,
    -- Free-text contact for an unmatched complainant. Encrypted at the entity
    -- layer: this is personal data given to us by someone exercising a right.
    complainant_contact TEXT,

    category            VARCHAR(40)  NOT NULL,
    channel             VARCHAR(20)  NOT NULL,

    subject             VARCHAR(200) NOT NULL,
    -- The complaint in the person's own words. Encrypted — it routinely contains
    -- clinical detail and always contains a person's account of their own care.
    body                TEXT,

    received_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Internal target. Deliberately earlier than due_at so that 90 days does not
    -- silently become the working norm.
    target_at           TIMESTAMPTZ  NOT NULL,
    -- Statutory ceiling.
    due_at              TIMESTAMPTZ  NOT NULL,

    state               VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',
    assigned_to         UUID,

    acknowledged_at     TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    -- What the complainant was told. Encrypted for the same reason as body.
    resolution          TEXT,

    -- Set when the complainant took it to the Board. Not a failure state on its
    -- own — they are entitled to — but it is the number that matters most when
    -- assessing whether the mechanism is actually effective.
    escalated_to_board  BOOLEAN      NOT NULL DEFAULT FALSE,
    board_reference     VARCHAR(80),

    -- Links a grievance to the incident it turned out to be about. Complaints
    -- are often the first sign of a breach.
    incident_id         UUID,

    status              SMALLINT     NOT NULL DEFAULT 1,
    created_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by         UUID,
    modified_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_grievances PRIMARY KEY (id),
    CONSTRAINT uq_grievance_ref UNIQUE (grievance_ref),
    CONSTRAINT fk_grievance_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_grievance_incident FOREIGN KEY (incident_id)
        REFERENCES security_incidents(id) ON DELETE SET NULL,
    CONSTRAINT ck_grievance_state CHECK (state IN
        ('RECEIVED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'WITHDRAWN')),
    CONSTRAINT ck_grievance_category CHECK (category IN
        ('CONSENT', 'ACCESS_REQUEST', 'CORRECTION', 'ERASURE', 'DATA_ACCURACY',
         'UNAUTHORISED_USE', 'SERVICE', 'OTHER')),
    -- Resolving means telling the complainant something. A resolution with no
    -- text is a status change dressed up as an answer.
    CONSTRAINT ck_grievance_resolved CHECK (
        state NOT IN ('RESOLVED', 'CLOSED')
        OR (resolved_at IS NOT NULL AND resolution IS NOT NULL)
    ),
    -- We must be able to reach whoever complained, or the resolution goes nowhere.
    CONSTRAINT ck_grievance_reachable CHECK (
        patient_id IS NOT NULL OR complainant_contact IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS ix_grievance_open
    ON grievances (tenant_id, state, due_at)
 WHERE state NOT IN ('RESOLVED', 'CLOSED', 'WITHDRAWN');

CREATE INDEX IF NOT EXISTS ix_grievance_due ON grievances (due_at)
 WHERE state NOT IN ('RESOLVED', 'CLOSED', 'WITHDRAWN');

CREATE INDEX IF NOT EXISTS ix_grievance_patient ON grievances (tenant_id, patient_id);

-- ── 3. Audit trail of what was done about it ──────────────────────────────
--
-- Separate table rather than columns on the grievance, because "who did what and
-- when" is the evidence that the mechanism is effective rather than merely
-- present. A state field alone cannot show that anyone actually worked on it.

CREATE TABLE IF NOT EXISTS grievance_events (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    grievance_id    UUID         NOT NULL,

    event_type      VARCHAR(30)  NOT NULL,
    -- Internal working note. Encrypted: staff write freely here and it will
    -- contain patient detail.
    note            TEXT,
    -- True when the complainant was told about this step. The distinction
    -- between working on something and telling someone you are working on it is
    -- exactly what complainants notice.
    communicated    BOOLEAN      NOT NULL DEFAULT FALSE,

    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_grievance_events PRIMARY KEY (id),
    CONSTRAINT fk_gevent_grievance FOREIGN KEY (grievance_id)
        REFERENCES grievances(id) ON DELETE CASCADE,
    CONSTRAINT ck_gevent_type CHECK (event_type IN
        ('RECEIVED', 'ACKNOWLEDGED', 'ASSIGNED', 'NOTE', 'INFO_REQUESTED',
         'INFO_RECEIVED', 'RESOLVED', 'REOPENED', 'ESCALATED', 'WITHDRAWN'))
);

CREATE INDEX IF NOT EXISTS ix_gevent_grievance
    ON grievance_events (grievance_id, occurred_at DESC);

-- ── 4. Permissions ────────────────────────────────────────────────────────
--
-- GRIEVANCE_RAISE is wide for the same reason INCIDENT_RAISE is: a complaint
-- that can only be logged by an administrator is a complaint that gets talked
-- out of existence at the desk.

INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.key, 'COMPLIANCE', v.descr, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('GRIEVANCE_RAISE',   'Record a data protection grievance from a patient'),
    ('GRIEVANCE_MANAGE',  'Work, resolve and close grievances'),
    ('COMPLIANCE_CONTACT_MANAGE', 'Maintain the published data protection contact and DPO')
) AS v(key, descr)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE f.feature_key = 'GRIEVANCE_RAISE'
  AND UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN', 'RECEPTION', 'DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE f.feature_key IN ('GRIEVANCE_MANAGE', 'COMPLIANCE_CONTACT_MANAGE')
  AND UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
