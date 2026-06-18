-- V125__fix_department_branch_unique_constraint.sql
-- Drop the tenant-scoped unique index on departments name and replace it with
-- a branch-scoped unique index (using COALESCE to safely handle nullable branch_id).

DROP INDEX IF EXISTS uq_departments_tenant_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_departments_branch_name ON departments (
    tenant_id, 
    COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid), 
    name
);
