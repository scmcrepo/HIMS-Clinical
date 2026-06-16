-- V089__seed_default_role_grants.sql  (PostgreSQL 16)
-- Gives the standard operational roles (seeded in V088) a sensible default set
-- of feature grants so each role can do its job out of the box.
--
-- These are DEFAULTS ONLY. Everything here is fully editable afterwards from
-- the frontend (Settings -> Roles & Permissions / RolesTab), which writes to
-- the same role_features table and triggers an immediate permission-cache
-- rebuild on the server.
--
-- Idempotent: ON CONFLICT DO NOTHING. Rows whose role or feature_key does not
-- exist are simply skipped by the inner joins, so this never fails.
-- ADMIN already has every feature (V088); SUPERADMIN bypasses all checks.

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM (VALUES
    -- Reception / front desk
    ('RECEPTION', 'REGISTRATION'),
    ('RECEPTION', 'APPOINTMENT'),
    ('RECEPTION', 'OUT_PATIENT'),
    ('RECEPTION', 'IN_PATIENT'),
    ('RECEPTION', 'OP_BILLING'),
    ('RECEPTION', 'IP_BILLING'),

    -- Doctor / consultant
    ('DOCTOR', 'OUT_PATIENT'),
    ('DOCTOR', 'IN_PATIENT'),
    ('DOCTOR', 'APPOINTMENT'),
    ('DOCTOR', 'LAB_REPORT'),
    ('DOCTOR', 'RADIOLOGY'),
    ('DOCTOR', 'MEDICAL_RECORD'),
    ('DOCTOR', 'OT_SCHEDULE'),
    ('DOCTOR', 'BEDMANAGEMENT'),

    -- Nurse
    ('NURSE', 'IN_PATIENT'),
    ('NURSE', 'OUT_PATIENT'),
    ('NURSE', 'BEDMANAGEMENT'),
    ('NURSE', 'IP_AUTOMATED_ORDERS'),

    -- Laboratory
    ('LAB', 'LAB_REPORT'),

    -- Radiology
    ('RADIOLOGY', 'RADIOLOGY'),

    -- Billing
    ('BILLING', 'OP_BILLING'),
    ('BILLING', 'IP_BILLING'),

    -- Pharmacy
    ('PHARMACY', 'PHARMACY_SALES'),
    ('PHARMACY', 'PRESCRIBED_ORDERS'),
    ('PHARMACY', 'PHARMACY_SALES_HISTORY'),
    ('PHARMACY', 'SALES_RETURN'),
    ('PHARMACY', 'STOCK_ADJUSTMENT'),

    -- Stock / inventory
    ('STOCK', 'STOCK_ADJUSTMENT'),
    ('STOCK', 'INVENTORY'),
    ('STOCK', 'INVENTORY_GRN'),
    ('STOCK', 'INVENTORY_GOODS_RETURN'),
    ('STOCK', 'PURCHASE_ORDER')
) AS grant_map(role_name, feature_key)
JOIN roles    r ON r.name        = grant_map.role_name
JOIN features f ON f.feature_key = grant_map.feature_key
ON CONFLICT DO NOTHING;
