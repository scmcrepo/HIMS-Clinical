-- V232__seed_reports_and_roles_sync.sql
-- 1. Ensure missing report and settings features exist for all tenants.
-- 2. Create missing standard branch-scoped roles (LABORATORY, RADIOLOGY, INSURANCE, INSURANCE_DESK).
-- 3. Remove inappropriate permissions from RECEPTION (remove OP_QUEUE, IN_PATIENT, billing, MFA_ADMIN).
-- 4. Remove Front Desk and Diagnostics permissions from DOCTOR (remove OUT_PATIENT, APPOINTMENT, ADMISSION_REQUEST, LAB_REPORT, RADIOLOGY, etc.).
-- 5. Remove MFA_ADMIN from all roles except administrative roles.
-- 6. Seed report permissions into standard roles for all existing hospitals.
-- 7. Grant all features to ADMIN role.

-- ── 1. Ensure missing features exist for all tenants ─────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), f_data.f_key, f_data.module, f_data.descr, t.id
FROM tenants t
CROSS JOIN (VALUES
    ('REPORT_GST', 'REPORTS', 'GST reports: pharmacy sales GST detailed, purchase GST details'),
    ('REPORT_PATIENT', 'REPORTS', 'Patient registration, summary, and demographic reports'),
    ('REPORT_APPOINTMENT', 'REPORTS', 'Appointment scheduling and doctor calendar reports'),
    ('SETTINGS_GST', 'SETTINGS', 'Configure GST rates, HSN codes, and tax categories')
) AS f_data(f_key, module, descr)
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- ── 2. Create missing standard branch-scoped roles for all existing branches ──
INSERT INTO roles (id, name, description, status, tenant_id, branch_id)
SELECT gen_random_uuid(), r_data.role_name, r_data.role_name || ' (seeded)', 1, b.tenant_id, b.id
FROM branches b
CROSS JOIN (VALUES
    ('LABORATORY'),
    ('RADIOLOGY'),
    ('INSURANCE'),
    ('INSURANCE_DESK')
) AS r_data(role_name)
WHERE NOT EXISTS (
    SELECT 1 FROM roles r
    WHERE r.tenant_id = b.tenant_id
      AND r.branch_id = b.id
      AND LOWER(r.name) = LOWER(r_data.role_name)
);

-- ── 3. Remove inappropriate permissions from RECEPTION ─────────────────────────
-- Front desk / reception staff should not have consultant queues, inpatient lists, or billing access.
DELETE FROM role_features rf
USING roles r, features f
WHERE rf.role_id = r.id
  AND rf.feature_id = f.id
  AND UPPER(r.name) = 'RECEPTION'
  AND f.feature_key IN ('OP_QUEUE', 'IN_PATIENT', 'OP_BILLING', 'IP_BILLING', 'PETTY_CASH', 'MFA_ADMIN');

-- ── 4. Remove Front Desk & Diagnostics permissions from DOCTOR ────────────────
-- Doctors manage clinical consultations (OP_QUEUE, IN_PATIENT, MEDICAL_RECORD, SETTINGS_FAVORITES)
-- and view reports, not front desk operations or diagnostic tech queues.
DELETE FROM role_features rf
USING roles r, features f
WHERE rf.role_id = r.id
  AND rf.feature_id = f.id
  AND UPPER(r.name) = 'DOCTOR'
  AND f.feature_key IN ('OUT_PATIENT', 'APPOINTMENT', 'ADMISSION_REQUEST', 'REGISTRATION', 'BEDMANAGEMENT', 'LAB_REPORT', 'RADIOLOGY', 'OP_BILLING', 'IP_BILLING', 'PETTY_CASH', 'MFA_ADMIN');

-- ── 5. Remove MFA_ADMIN from all roles except administrative ones ────────────
DELETE FROM role_features rf
USING roles r, features f
WHERE rf.role_id = r.id
  AND rf.feature_id = f.id
  AND f.feature_key = 'MFA_ADMIN'
  AND UPPER(r.name) NOT IN ('ADMIN', 'HOSPITAL_ADMIN', 'BRANCH_ADMIN', 'SUPERADMIN');

-- ── 6. Seed default grants into standard roles for all existing tenants ────────
-- Insert mapping of role names to their feature keys
INSERT INTO role_features (role_id, feature_id)
SELECT DISTINCT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
JOIN (VALUES
    -- Reception
    ('RECEPTION', 'REGISTRATION'),
    ('RECEPTION', 'APPOINTMENT'),
    ('RECEPTION', 'OUT_PATIENT'),
    ('RECEPTION', 'ADMISSION_REQUEST'),
    ('RECEPTION', 'REPORT_ENCOUNTER'),
    ('RECEPTION', 'REPORT_PATIENT'),

    -- Doctor
    ('DOCTOR', 'OP_QUEUE'),
    ('DOCTOR', 'IN_PATIENT'),
    ('DOCTOR', 'MEDICAL_RECORD'),
    ('DOCTOR', 'SETTINGS_FAVORITES'),
    ('DOCTOR', 'REPORT_ENCOUNTER'),
    ('DOCTOR', 'REPORT_INPATIENT'),

    -- Nurse
    ('NURSE', 'REPORT_ENCOUNTER'),
    ('NURSE', 'REPORT_INPATIENT'),

    -- Billing
    ('BILLING', 'REPORT_BILLING'),
    ('BILLING', 'REPORT_COLLECTION'),
    ('BILLING', 'REPORT_REVENUE'),

    -- Pharmacist
    ('PHARMACIST', 'REPORT_PHARMACY'),
    ('PHARMACIST', 'REPORT_INVENTORY'),
    ('PHARMACIST', 'REPORT_PROCUREMENT'),
    ('PHARMACIST', 'REPORT_GST'),
    ('PHARMACY', 'REPORT_PHARMACY'),
    ('PHARMACY', 'REPORT_INVENTORY'),
    ('PHARMACY', 'REPORT_PROCUREMENT'),
    ('PHARMACY', 'REPORT_GST'),

    -- Diagnostics
    ('LABORATORY', 'LAB_REPORT'),
    ('LABORATORY', 'REPORT_DIAGNOSTICS'),
    ('LAB', 'LAB_REPORT'),
    ('LAB', 'REPORT_DIAGNOSTICS'),
    ('RADIOLOGY', 'RADIOLOGY'),
    ('RADIOLOGY', 'REPORT_DIAGNOSTICS'),

    -- Insurance
    ('INSURANCE', 'INSURANCE'),
    ('INSURANCE', 'INSURANCE_REPORTS'),
    ('INSURANCE', 'REPORT_INSURANCE'),
    ('INSURANCE_DESK', 'INSURANCE'),
    ('INSURANCE_DESK', 'INSURANCE_REPORTS'),
    ('INSURANCE_DESK', 'REPORT_INSURANCE'),

    -- Hospital Admin
    ('HOSPITAL_ADMIN', 'SETTINGS_ROLE'),
    ('HOSPITAL_ADMIN', 'SETTINGS_GST'),
    ('HOSPITAL_ADMIN', 'REPORT_GST'),
    ('HOSPITAL_ADMIN', 'REPORT_PATIENT'),
    ('HOSPITAL_ADMIN', 'REPORT_APPOINTMENT')
) AS grants(role_name, feature_key)
  ON UPPER(r.name) = grants.role_name AND f.feature_key = grants.feature_key
ON CONFLICT DO NOTHING;

-- ── 7. Ensure ADMIN role has all features for its tenant ─────────────────────
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) = 'ADMIN'
ON CONFLICT DO NOTHING;
