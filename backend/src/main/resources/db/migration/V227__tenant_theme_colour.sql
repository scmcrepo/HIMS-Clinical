-- ── Per-hospital theme colour ───────────────────────────────────────────────
--  Lives on `tenants` rather than `system_settings` because that table is
--  global: it is keyed UNIQUE (setting_type, setting_key) with no tenant
--  column, so one hospital's choice would overwrite every other hospital's.
--  Hospital-level values already resolve from the tenant row — see
--  SettingsRegistryImpl's HOSPITAL_PARAM handling — and this follows that.
--
--  NULL means "not chosen": the frontend keeps its default slate palette, so
--  every existing hospital looks exactly as it does today until someone picks.
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS theme_color VARCHAR(7);

COMMENT ON COLUMN tenants.theme_color IS
    'Hospital theme colour as #rrggbb. NULL = application default.';
