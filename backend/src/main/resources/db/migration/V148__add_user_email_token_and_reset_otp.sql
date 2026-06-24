-- V148: Add user email token, reset otp table and seed default SMTP config
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_token VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_users_email_token ON users (email_token);

CREATE TABLE IF NOT EXISTS password_reset_otp (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255)    NOT NULL,
    otp         VARCHAR(6)      NOT NULL,
    expires_at  TIMESTAMPTZ     NOT NULL,
    verified    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_password_reset_otp_email ON password_reset_otp(email);

-- Seed default SMTP config structure (password is updated securely on startup runner)
INSERT INTO smtp_config (id, smtp_host, smtp_port, username, password, protocol, tls_enabled, ssl_enabled, from_email, from_name, active, tenant_id, branch_id, status)
SELECT gen_random_uuid(), 'smtp.gmail.com', 587, 'scmcrepo@gmail.com', NULL, 'SMTP', TRUE, FALSE, 'scmcrepo@gmail.com', 'HIMS Clinical', TRUE, '00000000-0000-0000-0000-000000000001', NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM smtp_config WHERE tenant_id = '00000000-0000-0000-0000-000000000001' AND active = TRUE
);
