-- V153__scope_roles_by_branch.sql
-- Change Roles Master from Tenant-wise to Branch-wise

-- 1. Add branch_id to roles table
ALTER TABLE roles ADD COLUMN branch_id UUID REFERENCES branches(id);

-- 2. Backfill branch_id for existing roles.
-- For regular roles, assign to the tenant's default branch.
-- Leave branch_id NULL for system roles: 'SUPERADMIN', 'HOSPITAL_ADMIN', 'ADMIN'
UPDATE roles
SET branch_id = (
    SELECT id FROM branches 
    WHERE tenant_id = roles.tenant_id AND is_default = true 
    LIMIT 1
)
WHERE name NOT IN ('SUPERADMIN', 'HOSPITAL_ADMIN', 'ADMIN') 
  AND tenant_id IS NOT NULL;

-- 3. Drop existing tenant-level unique constraint on role name
DROP INDEX IF EXISTS uq_roles_tenant_name;

-- 4. Add a new branch-level unique constraint
-- PostgreSQL ignores NULLs in UNIQUE constraints by default.
-- Using COALESCE ensures we only allow one 'HOSPITAL_ADMIN' per tenant, etc.
CREATE UNIQUE INDEX uq_roles_tenant_branch_name ON roles (
    COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'), 
    COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'), 
    LOWER(name)
);
