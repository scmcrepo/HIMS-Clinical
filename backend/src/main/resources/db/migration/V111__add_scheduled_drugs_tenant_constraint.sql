-- V104__add_scheduled_drugs_tenant_constraint.sql
-- Drop global unique constraint on scheduled_drugs
-- and replace it with a tenant-scoped unique index.

ALTER TABLE scheduled_drugs DROP CONSTRAINT IF EXISTS scheduled_drugs_name_key;
DROP INDEX IF EXISTS uq_scheduled_drugs_tenant_name;
CREATE UNIQUE INDEX uq_scheduled_drugs_tenant_name ON scheduled_drugs (tenant_id, name);
