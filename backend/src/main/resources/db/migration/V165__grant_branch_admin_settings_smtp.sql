-- V165__grant_branch_admin_settings_smtp.sql
-- Grant SETTINGS_SMTP and SETTINGS_TEMPLATE features to all BRANCH_ADMIN roles.

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name = 'BRANCH_ADMIN'
  AND f.feature_key IN ('SETTINGS_SMTP', 'SETTINGS_TEMPLATE')
ON CONFLICT (role_id, feature_id) DO NOTHING;
