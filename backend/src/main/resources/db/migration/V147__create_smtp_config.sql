-- =============================================================================
-- V147: Create smtp_config table for SMTP mail server configuration
-- =============================================================================

CREATE TABLE IF NOT EXISTS smtp_config (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    smtp_host       VARCHAR(255)    NOT NULL,
    smtp_port       INT             NOT NULL,
    username        VARCHAR(255)    NOT NULL,
    password        VARCHAR(500),
    protocol        VARCHAR(50)     NOT NULL DEFAULT 'SMTP',
    tls_enabled     BOOLEAN         NOT NULL DEFAULT FALSE,
    ssl_enabled     BOOLEAN         NOT NULL DEFAULT FALSE,
    from_email      VARCHAR(255)    NOT NULL,
    from_name       VARCHAR(255),
    active          BOOLEAN         NOT NULL DEFAULT TRUE,

    -- AuditableEntity standard columns
    tenant_id       UUID,
    branch_id       UUID,
    status          SMALLINT        NOT NULL DEFAULT 0,
    created_by      UUID,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT fk_smtp_config_tenant  FOREIGN KEY (tenant_id)  REFERENCES tenants(id),
    CONSTRAINT fk_smtp_config_branch  FOREIGN KEY (branch_id)  REFERENCES branches(id)
);

CREATE INDEX IF NOT EXISTS idx_smtp_config_tenant  ON smtp_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_smtp_config_active  ON smtp_config(tenant_id, active) WHERE active = TRUE;

-- Seed SETTINGS_SMTP permission
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'SETTINGS_SMTP', 'SETTINGS', 'SMTP Configuration', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- Grant SETTINGS_SMTP to ADMIN and HOSPITAL_ADMIN roles for each tenant
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name IN ('ADMIN', 'HOSPITAL_ADMIN')
  AND f.feature_key = 'SETTINGS_SMTP'
ON CONFLICT (role_id, feature_id) DO NOTHING;
