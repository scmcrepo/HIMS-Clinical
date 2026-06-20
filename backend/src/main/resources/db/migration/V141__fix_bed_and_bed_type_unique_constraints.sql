-- V141__fix_bed_and_bed_type_unique_constraints.sql
-- Drop the tenant-scoped uniqueness on room categories and replace it with a branch-scoped unique index.
-- Also add a branch-scoped unique index on beds.

-- 1. Room Categories
ALTER TABLE room_categories DROP CONSTRAINT IF EXISTS uq_room_categories_name;
ALTER TABLE room_categories DROP CONSTRAINT IF EXISTS uq_room_categories_tenant_name;
DROP INDEX IF EXISTS uq_room_categories_tenant_name;
DROP INDEX IF EXISTS uq_room_categories_branch_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_room_categories_branch_name ON room_categories (
    tenant_id,
    COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid),
    name
) WHERE status = 1;

-- 2. Beds
DROP INDEX IF EXISTS uq_beds_branch_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_beds_branch_name ON beds (
    tenant_id,
    COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid),
    name
) WHERE status = 1;
