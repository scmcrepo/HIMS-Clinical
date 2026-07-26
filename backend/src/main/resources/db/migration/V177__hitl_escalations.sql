-- =============================================================================
--  V177 — Human-in-the-Loop escalations  (WO-010 / H-001)
--
--  When the agent graph interrupts, the run lands here and a front-desk operator
--  picks it up from the Administrative Copilot.
--
--  The design point that matters: an escalation has a DEADLINE. A graph paused
--  for a human is a patient waiting, and silent indefinite waiting is the worst
--  failure mode of a human-in-the-loop system — worse than the agent simply
--  refusing, because the patient believes they are being helped. expires_at is
--  therefore NOT NULL, and a scheduled job acts on it.
--
--  Carries the AuditableEntity column set so the Hibernate tenantFilter applies
--  automatically; an operator must never see another hospital's queue.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS hitl_escalations;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key = 'HITL_MANAGE');
--    DELETE FROM features WHERE feature_key = 'HITL_MANAGE';
--  Purely additive; no existing table is altered.
-- =============================================================================

CREATE TABLE IF NOT EXISTS hitl_escalations (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    branch_id          UUID,

    run_id             VARCHAR(64)  NOT NULL,
    correlation_id     VARCHAR(64),
    channel            VARCHAR(20)  NOT NULL,
    reason             VARCHAR(40)  NOT NULL,
    detail             VARCHAR(500),
    intent             VARCHAR(40),
    confidence         NUMERIC(4,3),

    -- The operator needs the conversation to judge, so it is stored — which
    -- makes this column PHI-grade, not log data. Encrypted at rest via
    -- EncryptedStringConverter, and never written to a log line.
    transcript         TEXT,
    proposed_actions   JSONB        NOT NULL DEFAULT '[]'::jsonb,

    state              VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    expires_at         TIMESTAMPTZ  NOT NULL,

    resolved_at        TIMESTAMPTZ,
    resolved_by        UUID,
    operator_action    VARCHAR(20),
    -- Mandatory for correct/override at the API layer. It is both the audit
    -- record of why a human disagreed and the training signal for the agent.
    operator_reason    VARCHAR(500),
    operator_reply     TEXT,

    status             SMALLINT     NOT NULL DEFAULT 1,
    created_by         UUID,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by        UUID,
    modified_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_hitl_escalations PRIMARY KEY (id),
    CONSTRAINT fk_hitl_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_hitl_state CHECK (state IN ('WAITING', 'RESOLVED', 'TIMED_OUT', 'ABANDONED')),
    CONSTRAINT ck_hitl_action CHECK (operator_action IS NULL OR operator_action IN
        ('APPROVE', 'CORRECT', 'OVERRIDE', 'TAKE_OVER')),
    CONSTRAINT ck_hitl_expiry CHECK (expires_at > created_at)
);

-- One live escalation per run. A graph that interrupts twice for the same run
-- should update, not queue a duplicate in front of the operator.
CREATE UNIQUE INDEX IF NOT EXISTS uq_hitl_run_waiting
    ON hitl_escalations (tenant_id, run_id)
    WHERE state = 'WAITING';

CREATE INDEX IF NOT EXISTS ix_hitl_queue    ON hitl_escalations (tenant_id, state, created_at);
CREATE INDEX IF NOT EXISTS ix_hitl_expiring ON hitl_escalations (expires_at) WHERE state = 'WAITING';
CREATE INDEX IF NOT EXISTS ix_hitl_corr     ON hitl_escalations (correlation_id);

COMMENT ON TABLE hitl_escalations IS
    'Agent runs paused awaiting a human. transcript is PHI — encrypted at rest, never logged.';
COMMENT ON COLUMN hitl_escalations.expires_at IS
    'Deadline. A patient waiting on nobody is the worst HITL failure mode; the timeout job acts on this.';

-- ── Feature key: operating the Copilot queue ────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.feature_key, 'AGENT', v.description, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('HITL_MANAGE',      'Review and resolve escalated agent conversations'),
    -- The agent needs its own scope to file an escalation. Separate from
    -- HITL_MANAGE: an agent must be able to ask for help, never to resolve
    -- its own request for help.
    ('AGENT_HITL_RAISE', 'Agent: escalate a conversation to a human')
) AS v(feature_key, description)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- The AGENT role gets the raise scope only.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE LOWER(r.name) = 'agent' AND f.feature_key = 'AGENT_HITL_RAISE'
ON CONFLICT DO NOTHING;

-- Front-desk staff operate this queue, not just admins: the whole point is that
-- a receptionist can take over a conversation without waiting for a manager.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN', 'BRANCH_ADMIN', 'RECEPTION')
  AND f.feature_key = 'HITL_MANAGE'
ON CONFLICT DO NOTHING;
