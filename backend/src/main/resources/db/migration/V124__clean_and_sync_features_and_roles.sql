-- V124__clean_and_sync_features_and_roles.sql
-- Clean up obsolete features and sync roles/features across all tenants

-- 1. Delete obsolete/unused features globally (cascades to role_features)
DELETE FROM features 
WHERE feature_key IN (
    'SETTINGS_SMS_TEMPLATE', 'SETTINGS_UOM', 'SETTINGS_AREA', 'SETTINGS_ACCOUNTUNIT',
    'SETTINGS_MOLECULE', 'STOCK_INDENT', 'STOCK_CONSUMPTION', 'STOCK_ISSUE', 'STOCK_RETURN',
    'ATTACHMENT', 'PURCHASE_REQUEST', 'REFERRAL', 'REPORT_APPOINTMENT', 'REPORT_PATIENT',
    'STOCK', 'SALES', 'PATIENT_BILLS', 'PAYMENT', 'IP_AUTOMATED_OTHER_CHARGE',
    'SETTINGS_DATAQUERY', 'SETTINGS_TEMPLATE', 'IP_AUTOMATED_ORDERS'
);

-- 2. Update modules for MARKETING and OT_SCHEDULE to their correct values
UPDATE features SET module = 'MARKETING' WHERE feature_key = 'MARKETING';
UPDATE features SET module = 'OTSCHEDULE' WHERE feature_key = 'OT_SCHEDULE';

-- 3. Seed new features (PETTY_CASH, NURSE_OP_QUEUE, NURSE_IN_PATIENT) for all tenants
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), f.feature_key, f.module, f.description, t.id
FROM tenants t
CROSS JOIN (
    VALUES 
        ('PETTY_CASH', 'BILLING', 'Petty Cash Billing'),
        ('NURSE_OP_QUEUE', 'CLINICAL', 'Access nurse outpatient queue'),
        ('NURSE_IN_PATIENT', 'CLINICAL', 'Access nurse inpatient list')
) AS f(feature_key, module, description)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- 4. Seed NURSE role for all tenants
INSERT INTO roles (id, name, description, status, tenant_id)
SELECT gen_random_uuid(), 'NURSE', 'NURSE (seeded)', 1, t.id
FROM tenants t
ON CONFLICT (tenant_id, name) DO NOTHING;

-- 5. Grant PETTY_CASH to ADMIN and BILLING roles for all tenants
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name IN ('ADMIN', 'BILLING')
  AND f.feature_key = 'PETTY_CASH'
ON CONFLICT (role_id, feature_id) DO NOTHING;

-- 6. Grant NURSE_OP_QUEUE and NURSE_IN_PATIENT to NURSE role for all tenants
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name = 'NURSE'
  AND f.feature_key IN ('NURSE_OP_QUEUE', 'NURSE_IN_PATIENT')
ON CONFLICT (role_id, feature_id) DO NOTHING;

-- 7. Grant all features to ADMIN and HOSPITAL_ADMIN roles for all tenants
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name IN ('ADMIN', 'HOSPITAL_ADMIN')
ON CONFLICT (role_id, feature_id) DO NOTHING;
