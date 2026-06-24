-- V155__clone_roles_for_all_branches.sql
-- Clone roles from the default branch to all other branches in the same tenant.

-- 1. Create a temporary table or insert directly with gen_random_uuid()
WITH cloned_roles AS (
    SELECT
        gen_random_uuid() AS new_role_id,
        r.id AS old_role_id,
        r.name,
        r.description,
        r.status,
        b.tenant_id,
        b.id AS new_branch_id
    FROM roles r
    JOIN branches db ON r.branch_id = db.id AND db.is_default = true
    JOIN branches b ON b.tenant_id = db.tenant_id AND b.is_default = false
    WHERE r.name NOT IN ('SUPERADMIN', 'HOSPITAL_ADMIN', 'ADMIN')
      AND r.tenant_id IS NOT NULL
      -- Ensure we don't duplicate if a role with the same name already exists in the target branch
      AND NOT EXISTS (
          SELECT 1 FROM roles er WHERE er.tenant_id = b.tenant_id AND er.branch_id = b.id AND lower(er.name) = lower(r.name)
      )
),
inserted_roles AS (
    INSERT INTO roles (id, name, description, status, tenant_id, branch_id)
    SELECT new_role_id, name, description, status, tenant_id, new_branch_id
    FROM cloned_roles
    RETURNING id, name
)
INSERT INTO role_features (role_id, feature_id)
SELECT cr.new_role_id, rf.feature_id
FROM cloned_roles cr
JOIN role_features rf ON rf.role_id = cr.old_role_id
ON CONFLICT DO NOTHING;

-- 3. Assign the new roles to existing users who had the old role and are authorized for the new branch
-- This prevents users from losing access if they had the default branch's role but switch to a new branch
WITH cloned_roles AS (
    SELECT
        r.id AS new_role_id,
        old_r.id AS old_role_id,
        r.branch_id AS new_branch_id
    FROM roles r
    JOIN roles old_r ON lower(r.name) = lower(old_r.name) AND r.tenant_id = old_r.tenant_id
    JOIN branches db ON old_r.branch_id = db.id AND db.is_default = true
    JOIN branches b ON r.branch_id = b.id AND b.is_default = false
    WHERE r.name NOT IN ('SUPERADMIN', 'HOSPITAL_ADMIN', 'ADMIN')
)
INSERT INTO user_roles (user_id, role_id)
SELECT ur.user_id, cr.new_role_id
FROM user_roles ur
JOIN cloned_roles cr ON ur.role_id = cr.old_role_id
JOIN user_branches ub ON ub.user_id = ur.user_id AND ub.branch_id = cr.new_branch_id
ON CONFLICT DO NOTHING;

-- Also assign to users whose primary branch_id is the new branch, just in case
WITH cloned_roles AS (
    SELECT
        r.id AS new_role_id,
        old_r.id AS old_role_id,
        r.branch_id AS new_branch_id
    FROM roles r
    JOIN roles old_r ON lower(r.name) = lower(old_r.name) AND r.tenant_id = old_r.tenant_id
    JOIN branches db ON old_r.branch_id = db.id AND db.is_default = true
    JOIN branches b ON r.branch_id = b.id AND b.is_default = false
    WHERE r.name NOT IN ('SUPERADMIN', 'HOSPITAL_ADMIN', 'ADMIN')
)
INSERT INTO user_roles (user_id, role_id)
SELECT ur.user_id, cr.new_role_id
FROM user_roles ur
JOIN cloned_roles cr ON ur.role_id = cr.old_role_id
JOIN users u ON u.id = ur.user_id AND u.branch_id = cr.new_branch_id
ON CONFLICT DO NOTHING;
