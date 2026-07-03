-- V136__make_service_categories_tenant_wide.sql
-- Consolidation script for service categories: deduplicates by name per tenant and transitions to tenant-wide (branch_id = NULL)

DO $$
DECLARE
    rec RECORD;
    kept_id UUID;
BEGIN
    -- 1. Find duplicates and consolidate referencing catalog items to a single kept ID
    FOR rec IN 
        SELECT tenant_id, LOWER(TRIM(name)) as clean_name
        FROM service_categories
        GROUP BY tenant_id, LOWER(TRIM(name))
        HAVING COUNT(*) > 1
    LOOP
        -- Keep the oldest/first created category
        SELECT id INTO kept_id
        FROM service_categories
        WHERE tenant_id = rec.tenant_id AND LOWER(TRIM(name)) = rec.clean_name
        ORDER BY created_at ASC, id ASC
        LIMIT 1;

        -- Update referencing service_catalog_items to point to the kept category ID
        UPDATE service_catalog_items 
        SET category_id = kept_id 
        WHERE category_id IN (
            SELECT id 
            FROM service_categories 
            WHERE tenant_id = rec.tenant_id 
              AND LOWER(TRIM(name)) = rec.clean_name 
              AND id <> kept_id
        );

        -- Delete duplicate service categories
        DELETE FROM service_categories 
        WHERE tenant_id = rec.tenant_id 
          AND LOWER(TRIM(name)) = rec.clean_name 
          AND id <> kept_id;
    END LOOP;
END $$;

-- 2. Drop any legacy unique branch-scoped constraints or indexes
ALTER TABLE service_categories DROP CONSTRAINT IF EXISTS uq_service_categories_tenant_branch_name;
DROP INDEX IF EXISTS uq_service_categories_tenant_branch_name;
ALTER TABLE service_categories DROP CONSTRAINT IF EXISTS uq_service_categories_tenant_name;
DROP INDEX IF EXISTS uq_service_categories_tenant_name;

-- 3. Set branch_id to NULL to make categories tenant-wide
UPDATE service_categories SET branch_id = NULL;

-- 4. Re-create the clean tenant-wide unique index
CREATE UNIQUE INDEX IF NOT EXISTS uq_service_categories_tenant_name ON service_categories (tenant_id, name);
