-- ============================================================
-- V093 — Clinical Masters: Scheduled Drugs
-- ============================================================

CREATE TABLE IF NOT EXISTS scheduled_drugs (
    id           UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL UNIQUE,
    status       SMALLINT     NOT NULL DEFAULT 1,    -- EntityStatus ordinal: 0=INACTIVE, 1=ACTIVE, 2=DELETED
    created_by   UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by  UUID,
    modified_at  TIMESTAMPTZ
);

-- Seed common scheduled drug categories
INSERT INTO scheduled_drugs (name, status) VALUES
  ('H', 1),
  ('H1', 1)
ON CONFLICT (name) DO NOTHING;

-- Seed permission feature key
INSERT INTO features (id, feature_key, module, description) VALUES
    (gen_random_uuid(), 'SETTINGS_SCHEDULEDDRUG', 'SETTINGS', 'Settings Scheduled Drug')
ON CONFLICT (feature_key) DO NOTHING;

-- Grant permission to ADMIN role
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id FROM roles r CROSS JOIN features f
WHERE r.name = 'ADMIN' AND f.feature_key = 'SETTINGS_SCHEDULEDDRUG'
ON CONFLICT DO NOTHING;
