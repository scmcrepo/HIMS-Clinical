-- V166__add_settings_config_to_hospital_admin.sql
-- Grant SETTINGS_CONFIGURATION feature to HOSPITAL_ADMIN role for all tenants.

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name = 'HOSPITAL_ADMIN'
  AND f.feature_key = 'SETTINGS_CONFIGURATION'
ON CONFLICT (role_id, feature_id) DO NOTHING;
