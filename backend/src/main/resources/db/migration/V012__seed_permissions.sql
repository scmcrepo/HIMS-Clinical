-- V012__seed_permissions.sql
-- Seeds feature keys and links them to the ADMIN role.

-- 1. Insert Features
INSERT INTO features (id, feature_key, module, description)
VALUES 
    (gen_random_uuid(), 'SETTINGS_CONSULTANT', 'SETTINGS', 'Manage consultants and their slots'),
    (gen_random_uuid(), 'SETTINGS_USERS',      'SETTINGS', 'Manage system users'),
    (gen_random_uuid(), 'SETTINGS_ROLE',       'SETTINGS', 'Manage roles and permissions'),
    (gen_random_uuid(), 'SETTINGS_ITEM',       'SETTINGS', 'Manage service catalog items'),
    (gen_random_uuid(), 'SETTINGS_DEPARTMENT', 'SETTINGS', 'Manage departments'),
    (gen_random_uuid(), 'PATIENT_BILLS',       'BILLING',  'Access patient billing'),
    (gen_random_uuid(), 'APPOINTMENT',         'CLINICAL', 'Manage appointments'),
    (gen_random_uuid(), 'OUT_PATIENT',         'CLINICAL', 'Access outpatient module'),
    (gen_random_uuid(), 'IN_PATIENT',          'CLINICAL', 'Access inpatient module')
ON CONFLICT (feature_key) DO NOTHING;

-- 2. Link all features to ADMIN role
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id 
FROM roles r, features f
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
