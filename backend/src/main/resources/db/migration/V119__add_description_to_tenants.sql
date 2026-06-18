-- V102__add_description_to_tenants.sql
-- Add description column to the tenants table.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS description VARCHAR(255);
