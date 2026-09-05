-- ---------------------------------------------------------------------------
--  V218 — MFA for privileged users: TOTP credentials, recovery codes,
--         login challenges  (WO-029, card U-002)
--
--  WHY THIS EXISTS
--
--  DPDP Rule 6 requires reasonable security safeguards, and names access
--  control among them. A single reusable password on an account that can read
--  every patient record in a hospital is not one. This is the last of the three
--  Rule 6 items; JSONB encryption landed in V214, key rotation (U-003) is still
--  open.
--
--  ── Why TOTP and not an SMS or email OTP ───────────────────────────────────
--
--  The system already has an email OTP for password reset, so reusing that
--  machinery would have been less work. It would also have been worse:
--
--    * SMS is phishable in real time and defeated outright by a SIM swap. The
--      accounts being protected here are the ones worth that effort.
--    * Email OTP inherits the mailbox's security. If the mailbox is the second
--      factor, the mailbox password is the second factor.
--    * Both send a privileged user's phone number or address to a third-party
--      provider on every login. Adding a processor and a data flow in order to
--      satisfy a security rule is a poor trade under an Act that also cares
--      about minimisation.
--
--  TOTP is offline, involves no provider, costs nothing per login, and adds no
--  personal data to the system. The shared secret is the only new sensitive
--  value and it is encrypted at rest.
--
--  ── These tables deliberately do NOT extend AuditableEntity ────────────────
--
--  Every one of them is read or written BEFORE authentication completes, during
--  login. At that point there is no TenantContext and no BranchContext, so an
--  AuditableEntity subclass would stamp a null tenant_id on insert and its
--  tenant filter would be meaningless on read. This follows the precedent set
--  by password_reset_otps, which is pre-auth for the same reason.
--
--  tenant_id is still stored on the credential — not for filtering, but so an
--  investigation can answer "which hospital did this account belong to" without
--  a join to a table that may itself have changed.
--
--  ── The secret is a credential, not a record ───────────────────────────────
--
--  user_mfa_credentials.secret holds the shared TOTP seed, encrypted with
--  EncryptedStringConverter (AES-256-GCM), so it is TEXT rather than a sized
--  column. Anyone who reads it can generate valid codes forever; it is closer
--  to a password than to patient data, and it is the reason this table is worth
--  more attention than its size suggests.
--
--  Recovery codes are stored as BCrypt hashes, never in the clear, and are
--  single-use. They are shown to the user exactly once, at enrolment.
--
--  ── Replay ─────────────────────────────────────────────────────────────────
--
--  last_time_step records the RFC 6238 time step of the last accepted code. A
--  code is refused if its step is not strictly greater. Without this, a code
--  observed over someone's shoulder stays valid for the rest of its window and
--  the whole ±1-step tolerance becomes an attack surface.
--
--  ── Rollback ───────────────────────────────────────────────────────────────
--
--      DROP TABLE IF EXISTS mfa_challenges CASCADE;
--      DROP TABLE IF EXISTS mfa_recovery_codes CASCADE;
--      DROP TABLE IF EXISTS user_mfa_credentials CASCADE;
--      DELETE FROM features WHERE feature_key = 'MFA_ADMIN';
--
--  Safe while hms.mfa.mode is OFF, which is the default. Dropping these tables
--  while the mode is REQUIRED locks every privileged user out.
--
--  Additive. No existing table is altered and no data is destroyed.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS user_mfa_credentials (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL,

    -- Recorded for investigation, NOT used for filtering. See the header.
    tenant_id         UUID,

    -- AES-256-GCM ciphertext via EncryptedStringConverter. Never a sized column:
    -- ciphertext length is not the plaintext length.
    secret            TEXT         NOT NULL,

    -- NULL until the user has proved they can generate a code from the secret.
    -- An unconfirmed credential must never satisfy a login challenge: enrolling
    -- without confirming would otherwise lock the user out of their own account
    -- the moment the mode became REQUIRED.
    confirmed_at      TIMESTAMPTZ,

    -- RFC 6238 time step of the last ACCEPTED code. Replay guard.
    last_time_step    BIGINT,

    failed_attempts   INT          NOT NULL DEFAULT 0,
    locked_until      TIMESTAMPTZ,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_mfa_credentials PRIMARY KEY (id),
    CONSTRAINT fk_mfa_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_mfa_user UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS mfa_recovery_codes (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    credential_id UUID         NOT NULL,

    -- BCrypt hash. The plaintext is displayed once at enrolment and never stored.
    code_hash     VARCHAR(100) NOT NULL,

    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_mfa_recovery_codes PRIMARY KEY (id),
    CONSTRAINT fk_recovery_credential FOREIGN KEY (credential_id)
        REFERENCES user_mfa_credentials(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_recovery_unused
    ON mfa_recovery_codes (credential_id) WHERE used_at IS NULL;

-- The short-lived handle between "password accepted" and "second factor
-- accepted". Server-side rather than a signed token in the client, so that
-- revoking it is a DELETE and the attempt counter cannot be discarded by the
-- caller simply by dropping the token and starting again.
CREATE TABLE IF NOT EXISTS mfa_challenges (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    consumed_at  TIMESTAMPTZ,

    -- Carried through from the first step: the user already chose a branch
    -- before the challenge was raised, and asking again after the code would be
    -- a second decision point for no reason.
    branch_id    UUID,
    force_logout BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_mfa_challenges PRIMARY KEY (id),
    CONSTRAINT fk_challenge_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_mfa_challenge_expiry ON mfa_challenges (expires_at);

COMMENT ON COLUMN user_mfa_credentials.secret IS
    'AES-256-GCM ciphertext of the TOTP shared secret. Treat as a credential: '
    'anyone who can read it can generate valid codes indefinitely.';

COMMENT ON COLUMN user_mfa_credentials.last_time_step IS
    'RFC 6238 time step of the last accepted code. A code whose step is not '
    'strictly greater is a replay and is refused.';

-- Admin reset of another user's MFA. Self-service enrolment needs no feature
-- key — a user may always enrol themselves — but clearing someone else's second
-- factor is the break-glass path and has to be permissioned and audited.
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'MFA_ADMIN', 'SECURITY',
       'Reset another user''s multi-factor authentication', t.id
FROM tenants t
ON CONFLICT DO NOTHING;
