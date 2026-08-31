-- ---------------------------------------------------------------------------
--  V209 — Security incident register and breach notification  (WO-026)
--
--  WHY THIS EXISTS
--
--  DPDP s. 8(6) and Rule 7 require a Data Fiduciary to notify both the Data
--  Protection Board and every affected Data Principal of a personal data breach.
--  Nothing in this codebase could do any part of that. There was no detection,
--  no register, no notification path, and no way to answer the first question an
--  inquiry asks: whose data, how much, and when did you know.
--
--  The trigger for building it now: WO-028 found that AbdmConsentCallbackController
--  accepted unauthenticated cross-tenant writes. Had that been exploited, the
--  hospital would have had a reportable breach and no mechanism to report it.
--
--  ── The two clocks ──────────────────────────────────────────────────────────
--
--  Rule 7 runs two deadlines from the moment of becoming aware, and they are
--  separate columns here because they are separate obligations that are missed
--  independently:
--
--    board_notified_at        — initial intimation to the Board, without delay
--    board_detail_report_at   — the fuller report, within 72 hours
--    principals_notified_at   — affected individuals, without delay
--
--  Confirm the exact periods with counsel; the 72-hour figure is the widely
--  reported reading of Rule 7 and is encoded as a default, not a certainty.
--
--  ── Multi-tenant note ───────────────────────────────────────────────────────
--
--  tenant_id is NULLABLE. Under the Processor/Fiduciary split confirmed
--  2026-08-30, a breach in the platform layer can span every tenant — the ABDM
--  callback defect did exactly that. Forcing a tenant here would mean either
--  inventing one or filing the same incident N times, and neither describes what
--  happened. A null tenant means platform-wide, and the affected-principal rows
--  carry the per-tenant detail.
--
--  ROLLBACK
--    DROP TABLE incident_affected_principals;
--    DROP TABLE security_incidents;
--  Safe: both tables are new. But do not drop them once an incident has been
--  filed — the register is the evidence of what was known and when.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS security_incidents (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- Null means platform-wide. See the note above.
    tenant_id               UUID,
    branch_id               UUID,

    -- Human-quotable reference for correspondence with the Board and with
    -- affected people. Generated, not the UUID, because nobody reads a UUID
    -- over the phone.
    incident_ref            VARCHAR(30)  NOT NULL,

    category                VARCHAR(40)  NOT NULL,
    severity                VARCHAR(10)  NOT NULL,

    -- When we became aware. This is the moment both statutory clocks start, so
    -- it is deliberately distinct from created_at: an incident discovered on
    -- Monday and filed on Tuesday is still a Monday incident.
    detected_at             TIMESTAMPTZ  NOT NULL,
    occurred_at             TIMESTAMPTZ,
    detection_source        VARCHAR(40)  NOT NULL,

    summary                 VARCHAR(500) NOT NULL,
    -- Free text, deliberately not encrypted: it must never contain personal
    -- data. The affected-principals table carries who; this carries what.
    detail                  TEXT,

    data_categories         VARCHAR(300),
    affected_principal_count INTEGER     NOT NULL DEFAULT 0,
    -- Set when the true scope cannot be established. An unknown blast radius is
    -- itself a finding and must not be silently recorded as zero.
    scope_uncertain         BOOLEAN      NOT NULL DEFAULT FALSE,

    state                   VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    contained_at            TIMESTAMPTZ,

    board_notified_at       TIMESTAMPTZ,
    board_detail_report_at  TIMESTAMPTZ,
    board_reference         VARCHAR(80),
    principals_notified_at  TIMESTAMPTZ,

    remediation             TEXT,
    root_cause              TEXT,

    status                  SMALLINT     NOT NULL DEFAULT 1,
    created_by              UUID,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by             UUID,
    modified_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_security_incidents PRIMARY KEY (id),
    CONSTRAINT uq_incident_ref UNIQUE (incident_ref),
    CONSTRAINT ck_incident_state CHECK (state IN
        ('OPEN', 'CONTAINED', 'NOTIFIED', 'CLOSED', 'DISMISSED')),
    CONSTRAINT ck_incident_severity CHECK (severity IN
        ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incident_category CHECK (category IN
        ('CROSS_TENANT_ACCESS', 'UNAUTHORISED_ACCESS', 'DATA_LOSS', 'DATA_EXPOSURE',
         'CREDENTIAL_COMPROMISE', 'INTEGRITY_COMPROMISE', 'AVAILABILITY', 'OTHER')),

    -- An incident cannot be closed while anyone is still owed a notification.
    -- Closing with people un-notified is the failure this constraint exists to
    -- make impossible, rather than merely discouraged by process.
    CONSTRAINT ck_incident_closed_notified CHECK (
        state <> 'CLOSED'
        OR (board_notified_at IS NOT NULL AND principals_notified_at IS NOT NULL)
        OR affected_principal_count = 0
    )
);

COMMENT ON TABLE security_incidents IS
    'Register of personal data breaches and near-misses. This is the record an '
    'inquiry asks for first: what happened, when we knew, who was affected, and '
    'when each party was told.';

COMMENT ON COLUMN security_incidents.detected_at IS
    'When we became aware. Both Rule 7 clocks run from here, not from created_at.';

CREATE INDEX IF NOT EXISTS ix_incident_state ON security_incidents (state, detected_at DESC);
CREATE INDEX IF NOT EXISTS ix_incident_tenant ON security_incidents (tenant_id, detected_at DESC);
-- Backs the overdue-notification alert.
CREATE INDEX IF NOT EXISTS ix_incident_unnotified ON security_incidents (detected_at)
 WHERE board_notified_at IS NULL AND state NOT IN ('DISMISSED', 'CLOSED');

-- ── Who was affected, and were they told ──────────────────────────────────

CREATE TABLE IF NOT EXISTS incident_affected_principals (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    incident_id         UUID         NOT NULL,
    tenant_id           UUID         NOT NULL,

    -- Surrogate ids only. This table must never hold a name or a contact
    -- detail: a breach register that itself accumulates personal data enlarges
    -- the problem it exists to manage. Contact details are read from the
    -- patient record at send time and not copied here.
    patient_id          UUID,
    user_id             UUID,

    notified_at         TIMESTAMPTZ,
    notification_channel VARCHAR(20),
    notification_state  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- Undeliverable is a real outcome and must stay visible; silently dropping
    -- it would let an incident close with people who were never reached.
    failure_reason      VARCHAR(200),

    status              SMALLINT     NOT NULL DEFAULT 1,
    created_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by         UUID,
    modified_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_incident_affected PRIMARY KEY (id),
    CONSTRAINT fk_affected_incident FOREIGN KEY (incident_id)
        REFERENCES security_incidents(id) ON DELETE CASCADE,
    CONSTRAINT ck_affected_state CHECK (notification_state IN
        ('PENDING', 'SENT', 'FAILED', 'NOT_REQUIRED')),
    CONSTRAINT ck_affected_subject CHECK (patient_id IS NOT NULL OR user_id IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_affected_patient
    ON incident_affected_principals (incident_id, patient_id)
 WHERE patient_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_affected_pending
    ON incident_affected_principals (incident_id, notification_state);

-- ── Permissions ───────────────────────────────────────────────────────────
--
-- Split three ways because the acts differ in consequence. Raising an incident
-- is deliberately wide: anyone who notices something wrong must be able to say
-- so without hunting for an administrator, and a near-miss nobody could file is
-- a near-miss nobody learns from.

INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.key, 'COMPLIANCE', v.descr, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('INCIDENT_RAISE',  'Report a suspected security or data incident'),
    ('INCIDENT_MANAGE', 'Triage, contain and close security incidents'),
    ('INCIDENT_NOTIFY', 'Notify the Data Protection Board and affected individuals')
) AS v(key, descr)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE f.feature_key = 'INCIDENT_RAISE'
  AND UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN', 'RECEPTION', 'DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;

-- Notifying the Board is an irreversible external act with legal weight. Narrow.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE f.feature_key IN ('INCIDENT_MANAGE', 'INCIDENT_NOTIFY')
  AND UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
