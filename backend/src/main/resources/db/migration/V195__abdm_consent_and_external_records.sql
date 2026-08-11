-- =============================================================================
--  V195 — ABDM consent and external health records  (WO-014 / Module 3)
--
--  This is the hospital acting as a Health Information USER (HIU): asking the
--  patient, through ABDM's Consent Manager, for permission to read records held
--  by other providers.
--
--  ── These are NOT the consents in `consent_records` ────────────────────────
--  `consent_records` is the hospital's own DPDP register: our lawful basis for
--  processing data we hold. An ABDM consent artifact is issued by the Consent
--  Manager, signed by it, scoped to specific health-information types and a date
--  range, and it expires. Conflating them would mean either treating a DPDP
--  consent as authority to pull a stranger's records, or letting an expired
--  ABDM artifact look like standing permission. Both tables exist on purpose.
--
--  ── Fetched records are another provider's PHI ─────────────────────────────
--  external_health_records holds clinical data the hospital did not author and
--  does not own. The payload is encrypted, access is written to
--  pii_disclosure_audit (V193), and rows carry the artifact id that authorised
--  them so an expired or revoked consent can be traced to everything it let in.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS external_health_records, abdm_consent_artifacts,
--                         abdm_consent_requests;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key IN ('ABDM_CONSENT_REQUEST','ABDM_RECORDS_VIEW'));
--    DELETE FROM features WHERE feature_key IN ('ABDM_CONSENT_REQUEST','ABDM_RECORDS_VIEW');
--  Purely additive.
-- =============================================================================

-- ── The request we sent to the Consent Manager ───────────────────────────────
CREATE TABLE IF NOT EXISTS abdm_consent_requests (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID         NOT NULL,
    branch_id             UUID,
    patient_id            UUID         NOT NULL,
    encounter_id          UUID,

    -- Consent Manager's id for the request. Ours until it answers.
    consent_request_id    VARCHAR(80),
    correlation_id        VARCHAR(64)  NOT NULL,

    -- ABDM purpose code: CAREMGT | BTG | PUBHLTH | HPAYMT | DSRCH | PATRQT
    purpose_code          VARCHAR(20)  NOT NULL,
    purpose_text          TEXT,

    -- Which record types were asked for, and over what clinical date range.
    hi_types              TEXT         NOT NULL,
    date_range_from       DATE,
    date_range_to         DATE,
    -- When our permission to hold what we fetch runs out.
    expires_at            TIMESTAMPTZ,

    requested_by          UUID,
    -- REQUESTED | PENDING_APPROVAL | GRANTED | DENIED | EXPIRED | REVOKED
    request_state         VARCHAR(24)  NOT NULL DEFAULT 'REQUESTED',
    failure_code          VARCHAR(80),

    status                SMALLINT     NOT NULL DEFAULT 1,
    created_by            UUID,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by           UUID,
    modified_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_abdm_consent_requests PRIMARY KEY (id),
    CONSTRAINT fk_consent_req_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_consent_req_state CHECK (request_state IN
        ('REQUESTED','PENDING_APPROVAL','GRANTED','DENIED','EXPIRED','REVOKED')),
    CONSTRAINT ck_consent_req_purpose CHECK (purpose_code IN
        ('CAREMGT','BTG','PUBHLTH','HPAYMT','DSRCH','PATRQT')),
    -- A range that ends before it starts would silently fetch nothing.
    CONSTRAINT ck_consent_req_range CHECK (
        date_range_from IS NULL OR date_range_to IS NULL OR date_range_from <= date_range_to)
);

CREATE INDEX IF NOT EXISTS ix_consent_req_patient
    ON abdm_consent_requests (tenant_id, patient_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_consent_req_correlation
    ON abdm_consent_requests (tenant_id, correlation_id);

-- ── The signed artifact the Consent Manager issued ───────────────────────────
CREATE TABLE IF NOT EXISTS abdm_consent_artifacts (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID         NOT NULL,
    branch_id             UUID,
    consent_request_id    UUID         NOT NULL,
    patient_id            UUID         NOT NULL,

    -- Consent Manager's artifact id. One artifact per granting provider.
    artifact_id           VARCHAR(80)  NOT NULL,
    -- The CM's signature. Kept because it is the proof the grant was genuine;
    -- without it the hospital cannot later show why it held another
    -- provider's records.
    signature             TEXT,

    hip_id                VARCHAR(80),
    hip_name              VARCHAR(200),

    granted_at            TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ,
    revoked_at            TIMESTAMPTZ,

    -- GRANTED | EXPIRED | REVOKED
    artifact_state        VARCHAR(20)  NOT NULL DEFAULT 'GRANTED',

    status                SMALLINT     NOT NULL DEFAULT 1,
    created_by            UUID,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by           UUID,
    modified_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_abdm_consent_artifacts PRIMARY KEY (id),
    CONSTRAINT fk_artifact_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_artifact_request FOREIGN KEY (consent_request_id)
        REFERENCES abdm_consent_requests(id) ON DELETE CASCADE,
    CONSTRAINT ck_artifact_state CHECK (artifact_state IN ('GRANTED','EXPIRED','REVOKED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_artifact_id
    ON abdm_consent_artifacts (tenant_id, artifact_id);
CREATE INDEX IF NOT EXISTS ix_artifact_patient
    ON abdm_consent_artifacts (tenant_id, patient_id, expires_at DESC);

-- ── Records fetched under an artifact ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS external_health_records (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID         NOT NULL,
    branch_id             UUID,
    patient_id            UUID         NOT NULL,
    artifact_id           UUID         NOT NULL,

    -- DiagnosticReport | Prescription | DischargeSummary | OPConsultation |
    -- ImmunizationRecord | HealthDocumentRecord | WellnessRecord
    hi_type               VARCHAR(40)  NOT NULL,
    -- When the care happened, not when we fetched it. The viewer sorts on this.
    record_date           TIMESTAMPTZ,

    source_hip_id         VARCHAR(80),
    source_hip_name       VARCHAR(200),

    -- The FHIR bundle, encrypted. Another provider's clinical data.
    payload               TEXT,
    -- Short human-readable line for the list, so the viewer need not decrypt
    -- and parse every bundle just to render an index.
    display_title         VARCHAR(300),

    fetched_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Set when a clinician copies this into the local case sheet.
    imported_at           TIMESTAMPTZ,
    imported_by           UUID,
    imported_case_sheet_id UUID,

    status                SMALLINT     NOT NULL DEFAULT 1,
    created_by            UUID,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by           UUID,
    modified_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_external_health_records PRIMARY KEY (id),
    CONSTRAINT fk_ehr_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_ehr_artifact FOREIGN KEY (artifact_id)
        REFERENCES abdm_consent_artifacts(id) ON DELETE CASCADE,
    CONSTRAINT ck_ehr_hi_type CHECK (hi_type IN
        ('DiagnosticReport','Prescription','DischargeSummary','OPConsultation',
         'ImmunizationRecord','HealthDocumentRecord','WellnessRecord'))
);

CREATE INDEX IF NOT EXISTS ix_ehr_patient
    ON external_health_records (tenant_id, patient_id, record_date DESC);
-- "What did this consent let in?" — the question asked when one is revoked.
CREATE INDEX IF NOT EXISTS ix_ehr_artifact
    ON external_health_records (artifact_id);

-- ── Feature keys ─────────────────────────────────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.feature_key, v.module, v.description, t.id
FROM (VALUES
    ('ABDM_CONSENT_REQUEST', 'ABDM', 'Request patient consent to view records held by other providers'),
    ('ABDM_RECORDS_VIEW',    'ABDM', 'View external health records fetched under an ABDM consent')
) AS v(feature_key, module, description)
CROSS JOIN tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- Clinicians only. Requesting a patient's history from other hospitals is a
-- clinical decision, and reading it is clinical work; neither belongs to
-- registration or billing staff.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('DOCTOR', 'HOSPITAL_ADMIN', 'ADMIN')
  AND f.feature_key IN ('ABDM_CONSENT_REQUEST', 'ABDM_RECORDS_VIEW')
ON CONFLICT DO NOTHING;
