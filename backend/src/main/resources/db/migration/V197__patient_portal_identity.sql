-- =============================================================================
--  V197 — Patient Self-Service Portal: identity  (WO-017 / PT-001)
--
--  Adds the schema the patient-facing mobile app needs to authenticate:
--
--    portal_otp_challenges   short-lived SMS one-time codes, hashed at rest
--    portal_sessions         rotating refresh-token chains, one row per rotation
--    patients.self_registered  flag for portal-created patients
--
--  ...plus the PORTAL_IDENTITY and PORTAL_PATIENT feature keys and the
--  PORTAL_PATIENT role, seeded for every existing tenant. Future tenants are
--  covered by TenantService.seedRbac() — seeding here alone would 403 for the
--  next hospital onboarded, which is the most-repeated defect in this campaign.
--
--  ─────────────────────────────────────────────────────────────────────────
--  WHY OTP EXISTS AT ALL
--
--  The requirement document specifies mobile-number-only login with no OTP.
--  That is not built. The portal returns diagnosis text, approved lab results,
--  casesheets and downloadable attachments, and the lookup that finds them runs
--  across every tenant on the platform. A 10-digit mobile number is an
--  identifier, not a secret — it is printed on hospital forms and sold in bulk
--  — so number-only login means anyone who types a number reads that person's
--  medical history at every hospital here at once. Under the DPDP Act that is
--  unauthorised disclosure of health data by the Data Fiduciary, and it cannot
--  be undone after the fact.
--
--  See WO-017 §4.0. Configurable via hms.portal.otp.required, default true.
--  ─────────────────────────────────────────────────────────────────────────
--
--  NOTE ON TENANCY: neither new table extends AuditableEntity, and neither
--  carries the tenantFilter. This is deliberate and is the one place in the
--  portal where that is true. Both are read BEFORE a tenant is known:
--  a challenge is looked up by phone token at login, when the caller has not
--  yet chosen a hospital, and a refresh row is looked up by token hash when the
--  request carries no session. An entity extending AuditableEntity would throw
--  CrossTenantAccessException from its @PostLoad on exactly those reads.
--  portal_sessions still stores tenant_id — as data used to build the
--  principal, not as a filter column.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS portal_sessions, portal_otp_challenges;
--    ALTER TABLE patients DROP COLUMN IF EXISTS self_registered;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key LIKE 'PORTAL\_%');
--    DELETE FROM features WHERE feature_key LIKE 'PORTAL\_%';
--    DELETE FROM roles WHERE UPPER(name) = 'PORTAL_PATIENT';
--  Purely additive apart from one nullable-with-default column on patients,
--  so rollback loses no pre-existing data.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. OTP CHALLENGES
--
--    There is deliberately NO plaintext mobile column. The challenge is keyed
--    by the same HMAC token the patients table uses (PiiSearchTokenService),
--    so this table can be joined to a patient without ever holding a phone
--    number — and a dump of it discloses nothing about who was trying to log in.
--
--    code_hash is BCrypt, not SHA-256. A 6-digit code has a 20-bit search
--    space; a fast hash over a leaked table is brute-forced instantly, and the
--    per-verification cost of BCrypt is irrelevant at login volumes.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portal_otp_challenges (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    contact_number_token VARCHAR(64) NOT NULL,
    code_hash            VARCHAR(72) NOT NULL,
    attempts             SMALLINT    NOT NULL DEFAULT 0,
    max_attempts         SMALLINT    NOT NULL DEFAULT 5,
    issued_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at           TIMESTAMPTZ NOT NULL,
    consumed_at          TIMESTAMPTZ,
    -- Salted hash of the caller IP, never the IP itself: enough to rate-limit
    -- an abusive source, not enough to build a location history of patients.
    source_hash          CHAR(64),
    CONSTRAINT pk_portal_otp_challenges PRIMARY KEY (id),
    CONSTRAINT ck_portal_otp_expiry CHECK (expires_at > issued_at)
);

CREATE INDEX IF NOT EXISTS ix_portal_otp_token_issued
    ON portal_otp_challenges (contact_number_token, issued_at DESC);
CREATE INDEX IF NOT EXISTS ix_portal_otp_expires
    ON portal_otp_challenges (expires_at);
CREATE INDEX IF NOT EXISTS ix_portal_otp_source
    ON portal_otp_challenges (source_hash, issued_at DESC);

COMMENT ON TABLE portal_otp_challenges IS
    'Patient portal SMS one-time codes. Authentication artefacts, not records: purged at 24h. '
    'Holds NO plaintext mobile number — keyed by the same HMAC token as patients.contact_number_token.';
COMMENT ON COLUMN portal_otp_challenges.code_hash IS
    'BCrypt. A 6-digit code has only ~20 bits of entropy; a fast hash would be trivially reversed from a dump.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. PORTAL SESSIONS  (rotating refresh chains)
--
--    One row per issued refresh token. Rotation inserts a child row and marks
--    the parent consumed, so the chain is the audit trail.
--
--    Presenting a refresh token whose row is already consumed means the token
--    leaked: the legitimate device would be holding its successor. The service
--    revokes the whole chain on that signal rather than merely rejecting the
--    request, because by then an attacker may already hold a live access token.
--    That is why parent_id exists — without it, a chain cannot be walked and
--    revocation would have to be all-or-nothing per patient.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS portal_sessions (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    chain_id           UUID         NOT NULL,
    parent_id          UUID,
    patient_id         UUID         NOT NULL,
    tenant_id          UUID         NOT NULL,
    branch_id          UUID         NOT NULL,
    refresh_token_hash CHAR(64)     NOT NULL,
    device_label       VARCHAR(120),
    issued_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ  NOT NULL,
    consumed_at        TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    revoked_reason     VARCHAR(40),
    CONSTRAINT pk_portal_sessions PRIMARY KEY (id),
    CONSTRAINT fk_portal_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_session_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    CONSTRAINT fk_portal_session_parent FOREIGN KEY (parent_id) REFERENCES portal_sessions(id) ON DELETE SET NULL,
    CONSTRAINT ck_portal_session_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_portal_session_revoked_reason CHECK (
        revoked_reason IS NULL OR revoked_reason IN
        ('LOGOUT', 'REUSE_DETECTED', 'DEVICE_LIMIT', 'CONSENT_WITHDRAWN', 'ERASURE')
    )
);

-- SHA-256 of the refresh token, looked up on every refresh. Unique because a
-- collision here would hand one patient another patient's session.
CREATE UNIQUE INDEX IF NOT EXISTS uq_portal_sessions_refresh_hash
    ON portal_sessions (refresh_token_hash);
CREATE INDEX IF NOT EXISTS ix_portal_sessions_chain    ON portal_sessions (chain_id);
CREATE INDEX IF NOT EXISTS ix_portal_sessions_patient  ON portal_sessions (patient_id, issued_at DESC);
CREATE INDEX IF NOT EXISTS ix_portal_sessions_expires  ON portal_sessions (expires_at);
-- Supports the two-active-devices cap without scanning a patient's history.
CREATE INDEX IF NOT EXISTS ix_portal_sessions_live
    ON portal_sessions (patient_id, chain_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

COMMENT ON TABLE portal_sessions IS
    'Patient portal refresh-token chains, one row per rotation. Retention 30 days past expiry: '
    'long enough to investigate a credential-theft report, short enough not to be a device-history archive.';
COMMENT ON COLUMN portal_sessions.parent_id IS
    'Previous token in the rotation chain. Lets reuse detection revoke the whole chain rather than one row.';
COMMENT ON COLUMN portal_sessions.tenant_id IS
    'Scope carried into the principal at refresh time. NOT a Hibernate filter column — this table is read '
    'before any tenant context exists.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. SELF-REGISTERED FLAG
--
--    Informational only; it restricts nothing. Its purpose is that front-desk
--    staff can see a patient arrived via the app and should have their ID
--    checked at the first visit, which is the whole identity-assurance story
--    for self-registration.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS self_registered BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN patients.self_registered IS
    'TRUE when the patient record was created through the self-service portal rather than at a desk. '
    'Prompts identity verification at first visit. Grants and restricts nothing.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. FEATURE KEYS — seeded for EVERY existing tenant
--
--    Two, not one, and the split is the security boundary:
--
--      PORTAL_IDENTITY  "you proved you hold this mobile number".
--                       Can list hospitals and self-register. Reads NO clinical data.
--      PORTAL_PATIENT   "you are this patient, at this hospital, at this branch".
--                       Everything else.
--
--    A single key would let a client that captured any candidate list swap the
--    patientId and read a sibling's records, because the server would have no
--    way to distinguish "verified the number" from "authorised as the patient".
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.feature_key, 'PORTAL', v.description, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('PORTAL_IDENTITY', 'Patient portal: mobile number verified, pre-selection scope'),
    ('PORTAL_PATIENT',  'Patient portal: authenticated patient, own records only')
) AS v(feature_key, description)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. PORTAL_PATIENT ROLE — one per tenant, tenant-wide (branch_id NULL)
--
--    Authorization resolves through the scopes carried on the portal principal,
--    exactly as it does for AGENT. The role row exists so portal principals
--    remain visible in role-based reporting and in Settings → Roles rather than
--    being an invisible parallel auth system.
--
--    Tenant-wide rather than per-branch because the role is identical in every
--    branch; the branch restriction comes from the token's branch_id, which
--    TenantResolutionFilter turns into the Hibernate branchFilter.
--
--    The roles unique index is an expression index
--    (COALESCE(tenant_id), COALESCE(branch_id), LOWER(name)) from V153, so
--    ON CONFLICT cannot target it cleanly — WHERE NOT EXISTS is the guard.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO roles (id, name, description, status, tenant_id, branch_id)
SELECT gen_random_uuid(), 'PORTAL_PATIENT', 'Patient self-service portal principal', 1, t.id, NULL
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM roles r
    WHERE r.tenant_id = t.id
      AND LOWER(r.name) = 'portal_patient'
      AND r.branch_id IS NULL
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. GRANTS
--
--    PORTAL_PATIENT gets exactly its two portal keys and nothing else. It must
--    never receive REGISTRATION, APPOINTMENT or MEDICAL_RECORD: those are the
--    staff-wide features, and a patient principal holding one could read every
--    patient in the tenant rather than only themselves.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE LOWER(r.name) = 'portal_patient'
  AND f.feature_key IN ('PORTAL_IDENTITY', 'PORTAL_PATIENT')
ON CONFLICT DO NOTHING;

-- ADMIN carries the full catalogue by convention (TenantService FULL_ACCESS_ROLES).
-- Keep that invariant true for the new module.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) = 'ADMIN'
  AND f.module = 'PORTAL'
ON CONFLICT DO NOTHING;
