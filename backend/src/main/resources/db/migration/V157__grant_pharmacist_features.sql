-- V157__grant_pharmacist_features.sql
-- Grant PRESCRIBED_ORDERS, SALES_RETURN, INVENTORY_GOODS_RETURN, and STOCK_ADJUSTMENT features to the PHARMACIST and PHARMACY roles.

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON (r.tenant_id = f.tenant_id OR (r.tenant_id IS NULL AND f.tenant_id = '00000000-0000-0000-0000-000000000001'))
WHERE r.name IN ('PHARMACIST', 'PHARMACY')
  AND f.feature_key IN ('PRESCRIBED_ORDERS', 'SALES_RETURN', 'INVENTORY_GOODS_RETURN', 'STOCK_ADJUSTMENT')
ON CONFLICT DO NOTHING;
