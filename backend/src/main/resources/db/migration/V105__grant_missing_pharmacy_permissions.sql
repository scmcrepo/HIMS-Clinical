-- V105__grant_missing_pharmacy_permissions.sql
-- Grant PHARMACY_SALES and PHARMACY_SALES_HISTORY to ADMIN and PHARMACY roles.

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
CROSS JOIN features f
WHERE r.name IN ('ADMIN', 'PHARMACY')
  AND f.feature_key IN ('PHARMACY_SALES', 'PHARMACY_SALES_HISTORY')
ON CONFLICT DO NOTHING;
