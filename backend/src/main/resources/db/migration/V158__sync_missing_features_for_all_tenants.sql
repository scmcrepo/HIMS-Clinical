-- V158__sync_missing_features_for_all_tenants.sql
-- Backfill missing features (SETTINGS_SMTP, SETTINGS_TEMPLATE) for all tenants.

-- 1. Seed missing features for all tenants
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'SETTINGS_SMTP', 'SETTINGS', 'SMTP Configuration', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'SETTINGS_TEMPLATE', 'SETTINGS', 'Manage clinical templates', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- 2. Grant these features to ADMIN and HOSPITAL_ADMIN roles for each tenant
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name IN ('ADMIN', 'HOSPITAL_ADMIN')
  AND f.feature_key IN ('SETTINGS_SMTP', 'SETTINGS_TEMPLATE')
ON CONFLICT (role_id, feature_id) DO NOTHING;
