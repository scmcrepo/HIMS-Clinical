-- V059__seed_remaining_permissions.sql
-- Seeds the remaining features from vitalsoft legacy data and links them to the ADMIN role.

-- 1. Insert Features
INSERT INTO features (id, feature_key, module, description) VALUES
    (gen_random_uuid(), 'REGISTRATION', 'RECEPTION', 'Access Patient Registration'),
    (gen_random_uuid(), 'OP_BILLING', 'BILLING', 'Access Outpatient Billing'),
    (gen_random_uuid(), 'IP_BILLING', 'BILLING', 'Access Inpatient Billing'),
    (gen_random_uuid(), 'LAB_REPORT', 'DIAGNOSTICS', 'Access Lab Reports'),
    (gen_random_uuid(), 'IP_AUTOMATED_ORDERS', 'CLINICAL', 'Automated IP Orders'),
    (gen_random_uuid(), 'SETTINGS_CHARGES', 'SETTINGS', 'Charges Settings'),
    (gen_random_uuid(), 'SETTINGS_PREFIX', 'SETTINGS', 'Prefix Configuration'),
    (gen_random_uuid(), 'SETTINGS_PAYERTYPE', 'SETTINGS', 'Payer Type Configuration'),
    (gen_random_uuid(), 'SETTINGS_HOSPITALPROFILE', 'SETTINGS', 'Hospital Profile Settings'),
    (gen_random_uuid(), 'MARKETING', 'MARKETING', 'Access Marketing Module'),
    (gen_random_uuid(), 'RADIOLOGY', 'DIAGNOSTICS', 'Access Radiology Module'),
    (gen_random_uuid(), 'SETTINGS_FREQUENCY', 'SETTINGS', 'Frequency Settings'),
    (gen_random_uuid(), 'SETTINGS_BED', 'SETTINGS', 'Bed Configuration'),
    (gen_random_uuid(), 'SETTINGS_BEDTYPE', 'SETTINGS', 'Bed Type Configuration'),
    (gen_random_uuid(), 'BEDMANAGEMENT', 'CLINICAL', 'Access Bed Management'),
    (gen_random_uuid(), 'SETTINGS_SPECIMEN', 'SETTINGS', 'Specimen Settings'),
    (gen_random_uuid(), 'MEDICAL_RECORD', 'MRD', 'Access Medical Records'),
    (gen_random_uuid(), 'SETTINGS_CONFIGURATION', 'SETTINGS', 'General Configuration'),
    (gen_random_uuid(), 'SETTINGS_CATEGORY', 'SETTINGS', 'Category Configuration'),
    (gen_random_uuid(), 'PURCHASE_ORDER', 'INVENTORY', 'Access Purchase Orders'),
    (gen_random_uuid(), 'SETTINGS_SUPPLIER', 'SETTINGS', 'Supplier Settings'),
    (gen_random_uuid(), 'SETTINGS_TAX', 'SETTINGS', 'Tax Settings'),
    (gen_random_uuid(), 'OT_SCHEDULE', 'OTSCHEDULE', 'Access OT Schedule'),
    (gen_random_uuid(), 'SETTINGS_STAFF', 'SETTINGS', 'Staff Settings'),
    (gen_random_uuid(), 'STOCK_ADJUSTMENT', 'INVENTORY', 'Access Stock Adjustments'),
    (gen_random_uuid(), 'MRD_MANAGEMENT', 'MRD', 'Access MRD Management'),
    (gen_random_uuid(), 'INSURANCE', 'INSURANCE', 'Access Insurance Module'),
    (gen_random_uuid(), 'MRD_REQUEST', 'MRD', 'Access MRD Requests'),
    (gen_random_uuid(), 'SETTINGS_FAVORITES', 'SETTINGS', 'Favorites Configuration'),
    (gen_random_uuid(), 'ADMISSION_REQUEST', 'CLINICAL', 'Access Admission Requests')
ON CONFLICT (feature_key) DO NOTHING;

-- 2. Link all features to ADMIN role
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id 
FROM roles r, features f
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
