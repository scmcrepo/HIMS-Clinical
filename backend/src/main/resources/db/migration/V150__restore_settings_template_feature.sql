-- V147__restore_settings_template_feature.sql
-- Re-create the SETTINGS_TEMPLATE feature (deleted in V124) and assign it to relevant roles.

-- 1. Seed SETTINGS_TEMPLATE feature for every tenant
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'SETTINGS_TEMPLATE', 'SETTINGS', 'Manage clinical templates', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- 2. Grant SETTINGS_TEMPLATE to ADMIN and HOSPITAL_ADMIN roles for all tenants
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name IN ('ADMIN', 'HOSPITAL_ADMIN')
  AND f.feature_key = 'SETTINGS_TEMPLATE'
ON CONFLICT (role_id, feature_id) DO NOTHING;

-- 3. Grant SETTINGS_TEMPLATE to DOCTOR role for all tenants (clinical users need template access)
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name = 'DOCTOR'
  AND f.feature_key = 'SETTINGS_TEMPLATE'
ON CONFLICT (role_id, feature_id) DO NOTHING;
