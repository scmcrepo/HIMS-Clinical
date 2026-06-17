-- V108__delete_equipment_permission.sql
-- Delete unused/obsolete EQUIPMENT permission key from role grants and features

DELETE FROM role_features WHERE feature_id IN (
    SELECT id FROM features WHERE feature_key = 'EQUIPMENT'
);

DELETE FROM features WHERE feature_key = 'EQUIPMENT';
