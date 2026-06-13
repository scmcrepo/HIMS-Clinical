-- V101__add_favorites_permission.sql
-- Seed permission feature key for Favorites Settings
INSERT INTO features (id, feature_key, module, description) VALUES
    (gen_random_uuid(), 'SETTINGS_FAVORITES', 'SETTINGS', 'Settings Favorites')
ON CONFLICT (feature_key) DO NOTHING;

-- Grant permission to ADMIN and DOCTOR roles by default
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name IN ('ADMIN', 'DOCTOR') AND f.feature_key = 'SETTINGS_FAVORITES'
ON CONFLICT DO NOTHING;
