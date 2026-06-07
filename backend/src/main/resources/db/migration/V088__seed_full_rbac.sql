-- V088__seed_full_rbac.sql  (PostgreSQL 16)
-- Auto-generated: seeds EVERY feature key gated in the codebase, the standard
-- clinical roles, and links all features to ADMIN. SUPERADMIN bypasses all checks.
-- Idempotent: safe to re-run (ON CONFLICT DO NOTHING).

-- 1. Features ------------------------------------------------------------
INSERT INTO features (id, feature_key, module, description) VALUES
    (gen_random_uuid(), 'APPOINTMENT', 'CLINICAL', 'Appointment'),
    (gen_random_uuid(), 'ATTACHMENT', 'CLINICAL', 'Attachment'),
    (gen_random_uuid(), 'BEDMANAGEMENT', 'CLINICAL', 'Bedmanagement'),
    (gen_random_uuid(), 'DATA_IMPORT', 'SETTINGS', 'Data Import'),
    (gen_random_uuid(), 'INSURANCE', 'INSURANCE', 'Insurance'),
    (gen_random_uuid(), 'INVENTORY', 'INVENTORY', 'Inventory'),
    (gen_random_uuid(), 'INVENTORY_GOODS_RETURN', 'INVENTORY', 'Inventory Goods Return'),
    (gen_random_uuid(), 'INVENTORY_GRN', 'INVENTORY', 'Inventory Grn'),
    (gen_random_uuid(), 'IN_PATIENT', 'CLINICAL', 'In Patient'),
    (gen_random_uuid(), 'IP_AUTOMATED_ORDERS', 'CLINICAL', 'Ip Automated Orders'),
    (gen_random_uuid(), 'IP_AUTOMATED_OTHER_CHARGE', 'CLINICAL', 'Ip Automated Other Charge'),
    (gen_random_uuid(), 'LAB_REPORT', 'DIAGNOSTICS', 'Lab Report'),
    (gen_random_uuid(), 'MARKETING', 'CLINICAL', 'Marketing'),
    (gen_random_uuid(), 'MEDICAL_RECORD', 'MRD', 'Medical Record'),
    (gen_random_uuid(), 'OT_SCHEDULE', 'CLINICAL', 'Ot Schedule'),
    (gen_random_uuid(), 'OUT_PATIENT', 'CLINICAL', 'Out Patient'),
    (gen_random_uuid(), 'PATIENT_BILLS', 'BILLING', 'Patient Bills'),
    (gen_random_uuid(), 'PAYMENT', 'BILLING', 'Payment'),
    (gen_random_uuid(), 'PURCHASE_ORDER', 'INVENTORY', 'Purchase Order'),
    (gen_random_uuid(), 'PURCHASE_REQUEST', 'INVENTORY', 'Purchase Request'),
    (gen_random_uuid(), 'RADIOLOGY', 'DIAGNOSTICS', 'Radiology'),
    (gen_random_uuid(), 'REFERRAL', 'CLINICAL', 'Referral'),
    (gen_random_uuid(), 'REGISTRATION', 'CLINICAL', 'Registration'),
    (gen_random_uuid(), 'REPORT_APPOINTMENT', 'REPORTS', 'Report Appointment'),
    (gen_random_uuid(), 'REPORT_BILLING', 'REPORTS', 'Report Billing'),
    (gen_random_uuid(), 'REPORT_COLLECTION', 'REPORTS', 'Report Collection'),
    (gen_random_uuid(), 'REPORT_DIAGNOSTICS', 'REPORTS', 'Report Diagnostics'),
    (gen_random_uuid(), 'REPORT_ENCOUNTER', 'REPORTS', 'Report Encounter'),
    (gen_random_uuid(), 'REPORT_INPATIENT', 'REPORTS', 'Report Inpatient'),
    (gen_random_uuid(), 'REPORT_INVENTORY', 'REPORTS', 'Report Inventory'),
    (gen_random_uuid(), 'REPORT_PATIENT', 'REPORTS', 'Report Patient'),
    (gen_random_uuid(), 'REPORT_PHARMACY', 'REPORTS', 'Report Pharmacy'),
    (gen_random_uuid(), 'REPORT_PROCUREMENT', 'REPORTS', 'Report Procurement'),
    (gen_random_uuid(), 'REPORT_REVENUE', 'REPORTS', 'Report Revenue'),
    (gen_random_uuid(), 'SALES', 'PHARMACY', 'Sales'),
    (gen_random_uuid(), 'SALES_RETURN', 'PHARMACY', 'Sales Return'),
    (gen_random_uuid(), 'SETTINGS_ACCOUNTUNIT', 'SETTINGS', 'Settings Accountunit'),
    (gen_random_uuid(), 'SETTINGS_AREA', 'SETTINGS', 'Settings Area'),
    (gen_random_uuid(), 'SETTINGS_BED', 'SETTINGS', 'Settings Bed'),
    (gen_random_uuid(), 'SETTINGS_BEDTYPE', 'SETTINGS', 'Settings Bedtype'),
    (gen_random_uuid(), 'SETTINGS_CASESHEET_TEMPLATE', 'SETTINGS', 'Settings Casesheet Template'),
    (gen_random_uuid(), 'SETTINGS_CATEGORY', 'SETTINGS', 'Settings Category'),
    (gen_random_uuid(), 'SETTINGS_CHARGES', 'SETTINGS', 'Settings Charges'),
    (gen_random_uuid(), 'SETTINGS_CONFIGURATION', 'SETTINGS', 'Settings Configuration'),
    (gen_random_uuid(), 'SETTINGS_CONSULTANT', 'SETTINGS', 'Settings Consultant'),
    (gen_random_uuid(), 'SETTINGS_DATAQUERY', 'SETTINGS', 'Settings Dataquery'),
    (gen_random_uuid(), 'SETTINGS_DEPARTMENT', 'SETTINGS', 'Settings Department'),
    (gen_random_uuid(), 'SETTINGS_FREQUENCY', 'SETTINGS', 'Settings Frequency'),
    (gen_random_uuid(), 'SETTINGS_HOSPITALPROFILE', 'SETTINGS', 'Settings Hospitalprofile'),
    (gen_random_uuid(), 'SETTINGS_ITEM', 'SETTINGS', 'Settings Item'),
    (gen_random_uuid(), 'SETTINGS_MOLECULE', 'SETTINGS', 'Settings Molecule'),
    (gen_random_uuid(), 'SETTINGS_ORDERSET', 'SETTINGS', 'Settings Orderset'),
    (gen_random_uuid(), 'SETTINGS_PAYERTYPE', 'SETTINGS', 'Settings Payertype'),
    (gen_random_uuid(), 'SETTINGS_PREFIX', 'SETTINGS', 'Settings Prefix'),
    (gen_random_uuid(), 'SETTINGS_PRINT_TEMPLATE', 'SETTINGS', 'Settings Print Template'),
    (gen_random_uuid(), 'SETTINGS_RESULT_TEMPLATE', 'SETTINGS', 'Settings Result Template'),
    (gen_random_uuid(), 'SETTINGS_ROLE', 'SETTINGS', 'Settings Role'),
    (gen_random_uuid(), 'SETTINGS_SMS_TEMPLATE', 'SETTINGS', 'Settings Sms Template'),
    (gen_random_uuid(), 'SETTINGS_SPECIMEN', 'SETTINGS', 'Settings Specimen'),
    (gen_random_uuid(), 'SETTINGS_STAFF', 'SETTINGS', 'Settings Staff'),
    (gen_random_uuid(), 'SETTINGS_SUPPLIER', 'SETTINGS', 'Settings Supplier'),
    (gen_random_uuid(), 'SETTINGS_TAX', 'SETTINGS', 'Settings Tax'),
    (gen_random_uuid(), 'SETTINGS_TEMPLATE', 'SETTINGS', 'Settings Template'),
    (gen_random_uuid(), 'SETTINGS_UOM', 'SETTINGS', 'Settings Uom'),
    (gen_random_uuid(), 'SETTINGS_USERS', 'SETTINGS', 'Settings Users'),
    (gen_random_uuid(), 'STOCK', 'INVENTORY', 'Stock'),
    (gen_random_uuid(), 'STOCK_ADJUSTMENT', 'INVENTORY', 'Stock Adjustment'),
    (gen_random_uuid(), 'STOCK_CONSUMPTION', 'INVENTORY', 'Stock Consumption'),
    (gen_random_uuid(), 'STOCK_INDENT', 'INVENTORY', 'Stock Indent'),
    (gen_random_uuid(), 'STOCK_ISSUE', 'INVENTORY', 'Stock Issue'),
    (gen_random_uuid(), 'STOCK_RETURN', 'INVENTORY', 'Stock Return')
ON CONFLICT (feature_key) DO NOTHING;

-- 2. Standard clinical roles --------------------------------------------
INSERT INTO roles (id, name, description, status, created_at, modified_at) VALUES
    (gen_random_uuid(), 'DOCTOR', 'Doctor role', 1, NOW(), NOW()),
    (gen_random_uuid(), 'NURSE', 'Nurse role', 1, NOW(), NOW()),
    (gen_random_uuid(), 'LAB', 'Lab role', 1, NOW(), NOW()),
    (gen_random_uuid(), 'RADIOLOGY', 'Radiology role', 1, NOW(), NOW()),
    (gen_random_uuid(), 'RECEPTION', 'Reception role', 1, NOW(), NOW()),
    (gen_random_uuid(), 'BILLING', 'Billing role', 1, NOW(), NOW()),
    (gen_random_uuid(), 'PHARMACY', 'Pharmacy role', 1, NOW(), NOW()),
    (gen_random_uuid(), 'STOCK', 'Stock role', 1, NOW(), NOW()),
    (gen_random_uuid(), 'USER', 'User role', 1, NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- 3. Grant ALL features to ADMIN (admin keeps full access; SUPERADMIN bypasses)
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

