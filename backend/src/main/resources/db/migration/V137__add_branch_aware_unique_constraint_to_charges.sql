-- V137__add_branch_aware_unique_constraint_to_charges.sql
-- Drop any potential existing index and create a branch-scoped unique index
-- on charges (tenant_id, branch_id, name) for active charges.

DROP INDEX IF EXISTS uq_charges_branch_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_charges_branch_name ON charges (
    tenant_id, 
    COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid), 
    LOWER(name)
) WHERE status = 1;
