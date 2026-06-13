-- V102__separate_feature_keys.sql
-- 1. Insert new feature keys
INSERT INTO features (id, feature_key, module, description) VALUES
    (gen_random_uuid(), 'OP_QUEUE', 'CLINICAL', 'Access outpatient queue'),
    (gen_random_uuid(), 'ADMISSION_REQUEST', 'CLINICAL', 'Access admission requests'),
    (gen_random_uuid(), 'OP_BILLING', 'BILLING', 'Access outpatient billing'),
    (gen_random_uuid(), 'IP_BILLING', 'BILLING', 'Access inpatient billing'),
    (gen_random_uuid(), 'PHARMACY_SALES', 'INVENTORY', 'Access pharmacy sales'),
    (gen_random_uuid(), 'PHARMACY_SALES_HISTORY', 'INVENTORY', 'Access pharmacy sales history'),
    (gen_random_uuid(), 'SETTINGS_DISCHARGE_TEMPLATE', 'SETTINGS', 'Settings Discharge Template')
ON CONFLICT (feature_key) DO NOTHING;

-- 2. Copy assignments for role features
-- Grant OP_QUEUE to roles that have OUT_PATIENT
INSERT INTO role_features (role_id, feature_id)
SELECT rf.role_id, f_new.id
FROM role_features rf
JOIN features f_old ON rf.feature_id = f_old.id
JOIN features f_new ON f_new.feature_key = 'OP_QUEUE'
WHERE f_old.feature_key = 'OUT_PATIENT'
ON CONFLICT DO NOTHING;

-- Grant ADMISSION_REQUEST to roles that have IN_PATIENT
INSERT INTO role_features (role_id, feature_id)
SELECT rf.role_id, f_new.id
FROM role_features rf
JOIN features f_old ON rf.feature_id = f_old.id
JOIN features f_new ON f_new.feature_key = 'ADMISSION_REQUEST'
WHERE f_old.feature_key = 'IN_PATIENT'
ON CONFLICT DO NOTHING;

-- Grant OP_BILLING to roles that have PATIENT_BILLS
INSERT INTO role_features (role_id, feature_id)
SELECT rf.role_id, f_new.id
FROM role_features rf
JOIN features f_old ON rf.feature_id = f_old.id
JOIN features f_new ON f_new.feature_key = 'OP_BILLING'
WHERE f_old.feature_key = 'PATIENT_BILLS'
ON CONFLICT DO NOTHING;

-- Grant IP_BILLING to roles that have PATIENT_BILLS
INSERT INTO role_features (role_id, feature_id)
SELECT rf.role_id, f_new.id
FROM role_features rf
JOIN features f_old ON rf.feature_id = f_old.id
JOIN features f_new ON f_new.feature_key = 'IP_BILLING'
WHERE f_old.feature_key = 'PATIENT_BILLS'
ON CONFLICT DO NOTHING;

-- Grant PHARMACY_SALES to roles that have SALES
INSERT INTO role_features (role_id, feature_id)
SELECT rf.role_id, f_new.id
FROM role_features rf
JOIN features f_old ON rf.feature_id = f_old.id
JOIN features f_new ON f_new.feature_key = 'PHARMACY_SALES'
WHERE f_old.feature_key = 'SALES'
ON CONFLICT DO NOTHING;

-- Grant PHARMACY_SALES_HISTORY to roles that have SALES
INSERT INTO role_features (role_id, feature_id)
SELECT rf.role_id, f_new.id
FROM role_features rf
JOIN features f_old ON rf.feature_id = f_old.id
JOIN features f_new ON f_new.feature_key = 'PHARMACY_SALES_HISTORY'
WHERE f_old.feature_key = 'SALES'
ON CONFLICT DO NOTHING;

-- Grant SETTINGS_DISCHARGE_TEMPLATE to roles that have SETTINGS_CASESHEET_TEMPLATE
INSERT INTO role_features (role_id, feature_id)
SELECT rf.role_id, f_new.id
FROM role_features rf
JOIN features f_old ON rf.feature_id = f_old.id
JOIN features f_new ON f_new.feature_key = 'SETTINGS_DISCHARGE_TEMPLATE'
WHERE f_old.feature_key = 'SETTINGS_CASESHEET_TEMPLATE'
ON CONFLICT DO NOTHING;
