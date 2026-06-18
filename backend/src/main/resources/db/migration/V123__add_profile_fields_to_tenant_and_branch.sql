-- V113__add_profile_fields_to_tenant_and_branch.sql
-- Add address and contact_number fields to tenants and branches tables.

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS address TEXT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS contact_number VARCHAR(50);

ALTER TABLE branches ADD COLUMN IF NOT EXISTS address TEXT;
ALTER TABLE branches ADD COLUMN IF NOT EXISTS contact_number VARCHAR(50);
