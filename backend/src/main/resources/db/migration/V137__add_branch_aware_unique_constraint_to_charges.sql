-- V137__add_branch_aware_unique_constraint_to_charges.sql
-- Consolidation and constraint script for charges: deduplicates active charges by name (case-insensitive) per tenant and branch, then creates a branch-scoped unique index.

DO $$
DECLARE
    rec RECORD;
    kept_id UUID;
BEGIN
    -- 1. Find duplicate active charges and consolidate referencing tables
    FOR rec IN 
        SELECT tenant_id, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) as clean_branch_id, LOWER(TRIM(name)) as clean_name
        FROM charges
        WHERE status = 1
        GROUP BY tenant_id, COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(TRIM(name))
        HAVING COUNT(*) > 1
    LOOP
        -- Keep the oldest/first created charge row
        SELECT id INTO kept_id
        FROM charges
        WHERE tenant_id = rec.tenant_id 
          AND COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = rec.clean_branch_id 
          AND LOWER(TRIM(name)) = rec.clean_name
          AND status = 1
        ORDER BY created_at ASC, id ASC
        LIMIT 1;

        -- Update tariffs
        UPDATE tariffs 
        SET charge_id = kept_id 
        WHERE charge_id IN (
            SELECT id 
            FROM charges 
            WHERE tenant_id = rec.tenant_id 
              AND COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = rec.clean_branch_id 
              AND LOWER(TRIM(name)) = rec.clean_name
              AND id <> kept_id
        );

        -- Update charge_package_includes (remove unique duplicates first)
        DELETE FROM charge_package_includes t1
        WHERE charge_id IN (
            SELECT id 
            FROM charges 
            WHERE tenant_id = rec.tenant_id 
              AND COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = rec.clean_branch_id 
              AND LOWER(TRIM(name)) = rec.clean_name
              AND id <> kept_id
        ) AND EXISTS (
            SELECT 1 FROM charge_package_includes t2
            WHERE t2.charge_id = kept_id
              AND t2.category_id = t1.category_id
        );

        UPDATE charge_package_includes 
        SET charge_id = kept_id 
        WHERE charge_id IN (
            SELECT id 
            FROM charges 
            WHERE tenant_id = rec.tenant_id 
              AND COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = rec.clean_branch_id 
              AND LOWER(TRIM(name)) = rec.clean_name
              AND id <> kept_id
        );

        -- Update charge_package_excludes (remove unique duplicates first)
        DELETE FROM charge_package_excludes t1
        WHERE charge_id IN (
            SELECT id 
            FROM charges 
            WHERE tenant_id = rec.tenant_id 
              AND COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = rec.clean_branch_id 
              AND LOWER(TRIM(name)) = rec.clean_name
              AND id <> kept_id
        ) AND EXISTS (
            SELECT 1 FROM charge_package_excludes t2
            WHERE t2.charge_id = kept_id
              AND t2.category_id = t1.category_id
        );

        UPDATE charge_package_excludes 
        SET charge_id = kept_id 
        WHERE charge_id IN (
            SELECT id 
            FROM charges 
            WHERE tenant_id = rec.tenant_id 
              AND COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = rec.clean_branch_id 
              AND LOWER(TRIM(name)) = rec.clean_name
              AND id <> kept_id
        );

        -- Update packages
        UPDATE packages 
        SET package_id = kept_id 
        WHERE package_id IN (
            SELECT id 
            FROM charges 
            WHERE tenant_id = rec.tenant_id 
              AND COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = rec.clean_branch_id 
              AND LOWER(TRIM(name)) = rec.clean_name
              AND id <> kept_id
        );

        UPDATE packages 
        SET charge_id = kept_id 
        WHERE charge_id IN (
            SELECT id 
            FROM charges 
            WHERE tenant_id = rec.tenant_id 
              AND COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = rec.clean_branch_id 
              AND LOWER(TRIM(name)) = rec.clean_name
              AND id <> kept_id
        );

        -- Finally, delete duplicate charge row
        DELETE FROM charges 
        WHERE tenant_id = rec.tenant_id 
          AND COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid) = rec.clean_branch_id 
          AND LOWER(TRIM(name)) = rec.clean_name
          AND id <> kept_id
          AND status = 1;
    END LOOP;
END $$;

-- 2. Drop any potential existing index and create the branch-scoped unique index
DROP INDEX IF EXISTS uq_charges_branch_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_charges_branch_name ON charges (
    tenant_id, 
    COALESCE(branch_id, '00000000-0000-0000-0000-000000000000'::uuid), 
    LOWER(name)
) WHERE status = 1;
