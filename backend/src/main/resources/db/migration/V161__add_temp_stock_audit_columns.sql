-- V161__add_temp_stock_audit_columns.sql
-- Add missing audit columns to temp_stock table to align with AuditableEntity

ALTER TABLE temp_stock ADD COLUMN IF NOT EXISTS status SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE temp_stock ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE temp_stock ADD COLUMN IF NOT EXISTS modified_by UUID;
ALTER TABLE temp_stock ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
