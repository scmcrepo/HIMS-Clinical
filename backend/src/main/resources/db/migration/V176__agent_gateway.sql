-- =============================================================================
--  V176 — Agent Gateway  (WO-001 / T-003)
--
--  Adds the schema the AI agent layer needs to authenticate, be audited, and
--  retry safely:
--
--    agent_api_tokens        scoped, expiring, revocable machine credentials
--    agent_tool_invocations  append-only audit of every agent action
--    agent_idempotency_keys  de-duplication for retried write tools
--
--  ...plus the RBAC feature keys and the AGENT role, seeded for every existing
--  tenant. Future tenants are covered by TenantService.seedRbac() — seeding here
--  alone would silently 403 for the next hospital onboarded.
--
--  The three new tables carry the AuditableEntity column set (status, created_by,
--  created_at, modified_by, modified_at, branch_id) because their JPA entities
--  extend AuditableEntity — which is what gives them the Hibernate tenantFilter
--  and branchFilter for free, rather than each repository remembering to scope
--  its own queries.
--
--  This migration is purely additive. No existing table is altered.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS agent_idempotency_keys, agent_tool_invocations, agent_api_tokens;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key LIKE 'AGENT\_%');
--    DELETE FROM features WHERE feature_key LIKE 'AGENT\_%';
--    DELETE FROM roles WHERE name = 'AGENT';
--  No pre-existing data is touched, so rollback is data-loss-free.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. AGENT API TOKENS
--
--    Only the SHA-256 hash of the token is stored. The plaintext is shown once
--    at issue and is unrecoverable thereafter — there is no reset path, only
--    reissue. branch_id is nullable: a token may be tenant-wide or pinned to a
--    single branch.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_api_tokens (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    branch_id    UUID,
    name         VARCHAR(120) NOT NULL,
    token_hash   CHAR(64)     NOT NULL,
    scopes       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    status       SMALLINT     NOT NULL DEFAULT 1,
    created_by   UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by  UUID,
    modified_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    revoked_by   UUID,
    last_used_at TIMESTAMPTZ,
    CONSTRAINT pk_agent_api_tokens PRIMARY KEY (id),
    CONSTRAINT fk_agent_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_token_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    CONSTRAINT ck_agent_token_expiry CHECK (expires_at > created_at)
);

-- Hash lookup is on the hot path of every agent request.
CREATE UNIQUE INDEX IF NOT EXISTS uq_agent_api_tokens_hash ON agent_api_tokens (token_hash);
CREATE INDEX IF NOT EXISTS ix_agent_api_tokens_tenant     ON agent_api_tokens (tenant_id);
CREATE INDEX IF NOT EXISTS ix_agent_api_tokens_expires    ON agent_api_tokens (expires_at)
    WHERE revoked_at IS NULL;

COMMENT ON TABLE  agent_api_tokens IS
    'Scoped machine credentials for the AI agent layer. Plaintext is never stored.';
COMMENT ON COLUMN agent_api_tokens.scopes IS
    'JSON array of feature keys. Scopes ARE feature keys — single source of truth with the RBAC catalogue.';
COMMENT ON COLUMN agent_api_tokens.branch_id IS
    'NULL = tenant-wide token. Non-null = pinned to one branch.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. AGENT TOOL INVOCATIONS  (append-only audit)
--
--    Answers, months later: which agent run, on whose behalf, from which
--    channel, with what outcome. Deliberately carries NO patient identifiers
--    beyond a surrogate entity id and NO free text — the transcript lives
--    elsewhere under PHI controls.
--
--    Retention: 7 years, matching clinical record retention (confirmed decision,
--    WO-001 §9.5). Enforced by policy/job, not by this migration.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_tool_invocations (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    branch_id          UUID,
    token_id           UUID,
    correlation_id     VARCHAR(64),
    run_id             VARCHAR(64),
    tool_name          VARCHAR(80)  NOT NULL,
    outcome            VARCHAR(20)  NOT NULL,
    error_code         VARCHAR(60),
    duration_ms        INTEGER,
    idempotency_key    VARCHAR(128),
    target_entity_type VARCHAR(60),
    target_entity_id   UUID,
    status             SMALLINT     NOT NULL DEFAULT 1,
    created_by         UUID,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by        UUID,
    modified_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_agent_tool_invocations PRIMARY KEY (id),
    CONSTRAINT fk_agent_inv_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_agent_inv_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'REPLAYED', 'DENIED'))
);

CREATE INDEX IF NOT EXISTS ix_agent_inv_tenant_time  ON agent_tool_invocations (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_agent_inv_correlation  ON agent_tool_invocations (correlation_id);
CREATE INDEX IF NOT EXISTS ix_agent_inv_run          ON agent_tool_invocations (run_id);
CREATE INDEX IF NOT EXISTS ix_agent_inv_token        ON agent_tool_invocations (token_id);

COMMENT ON TABLE agent_tool_invocations IS
    'APPEND-ONLY audit of agent actions. Retention 7 years. No UPDATE or DELETE in application code. '
    'If your deployment uses a restricted DB role, revoke UPDATE/DELETE on this table.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. AGENT IDEMPOTENCY KEYS
--
--    LLM agents retry. Without this, a retried book_slot double-books a patient
--    and the hospital finds out in the waiting room.
--
--    response_body is stored encrypted (EncryptedStringConverter) because a
--    cached tool response may contain patient data. TTL 24h keeps that copy
--    short-lived so erasure requests do not have to chase it.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_idempotency_keys (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL,
    key_hash       CHAR(64)     NOT NULL,
    tool_name      VARCHAR(80)  NOT NULL,
    request_hash   CHAR(64),
    response_status INTEGER,
    response_body  TEXT,
    branch_id      UUID,
    status       SMALLINT     NOT NULL DEFAULT 1,
    created_by   UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by  UUID,
    modified_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_agent_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT fk_agent_idem_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- Tenant-scoped: the same key string in two tenants must not collide.
-- The unique constraint is also the concurrency control — a duplicate insert
-- raising a violation IS the replay path, which is race-free in a way that a
-- read-then-write check is not.
CREATE UNIQUE INDEX IF NOT EXISTS uq_agent_idem_tenant_key
    ON agent_idempotency_keys (tenant_id, key_hash);
CREATE INDEX IF NOT EXISTS ix_agent_idem_expires ON agent_idempotency_keys (expires_at);

COMMENT ON COLUMN agent_idempotency_keys.response_body IS
    'Encrypted at rest via EncryptedStringConverter — cached tool responses may contain PII.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. FEATURE KEYS — seeded for EVERY existing tenant
--
--    features.feature_key is unique per tenant (uq_features_tenant_key from
--    V113), so ON CONFLICT (tenant_id, feature_key) is the correct idempotency
--    guard here.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.feature_key, 'AGENT', v.description, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('AGENT_SCHEDULING_READ',  'Agent: read appointment slot availability'),
    ('AGENT_SCHEDULING_WRITE', 'Agent: book and modify appointments'),
    ('AGENT_BILLING_READ',     'Agent: read patient billing ledger'),
    ('AGENT_BED_READ',         'Agent: read bed occupancy'),
    ('AGENT_TOOLS_READ',       'Agent: read tool schema catalogue'),
    ('AGENT_TOKEN_MANAGE',     'Manage agent API tokens')
) AS v(feature_key, description)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. AGENT ROLE — one per tenant
--
--    Authorization for agents actually resolves through token scopes carried as
--    authorities, but a real role keeps agent principals visible in role-based
--    reporting and in Settings → Roles.
--
--    The roles unique index is an expression index
--    (COALESCE(tenant_id), COALESCE(branch_id), LOWER(name)) from V153, so
--    ON CONFLICT cannot target it cleanly. WHERE NOT EXISTS is the reliable
--    idempotency guard.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO roles (id, name, description, status, tenant_id, branch_id)
SELECT gen_random_uuid(), 'AGENT', 'AI agent service principal', 1, t.id, NULL
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM roles r
    WHERE r.tenant_id = t.id
      AND LOWER(r.name) = 'agent'
      AND r.branch_id IS NULL
);

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. GRANTS
--
--    AGENT role gets the four operational tool features. It deliberately does
--    NOT get AGENT_TOKEN_MANAGE — an agent must not be able to mint itself a
--    wider credential.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE LOWER(r.name) = 'agent'
  AND f.feature_key IN (
      'AGENT_SCHEDULING_READ', 'AGENT_SCHEDULING_WRITE',
      'AGENT_BILLING_READ', 'AGENT_BED_READ', 'AGENT_TOOLS_READ'
  )
ON CONFLICT DO NOTHING;

-- Token management is an administrative act: HOSPITAL_ADMIN and ADMIN only.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN')
  AND f.feature_key = 'AGENT_TOKEN_MANAGE'
ON CONFLICT DO NOTHING;

-- ADMIN carries the full catalogue by convention (see TenantService
-- FULL_ACCESS_ROLES); keep that invariant true for the new agent features.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) = 'ADMIN'
  AND f.module = 'AGENT'
ON CONFLICT DO NOTHING;
