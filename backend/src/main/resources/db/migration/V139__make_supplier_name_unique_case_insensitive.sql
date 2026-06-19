-- V139__make_supplier_name_unique_case_insensitive.sql

-- 1. Clean up duplicate supplier names case-insensitively per tenant
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT tenant_id, LOWER(name) as lower_name, MIN(id::text)::uuid as keep_id
        FROM suppliers
        GROUP BY tenant_id, LOWER(name)
        HAVING COUNT(*) > 1
    ) LOOP
        -- Update any purchase receipts referencing duplicate suppliers to point to the keep_id
        UPDATE purchase_receipts
        SET supplier_id = r.keep_id
        WHERE supplier_id IN (
            SELECT id FROM suppliers
            WHERE tenant_id = r.tenant_id AND LOWER(name) = r.lower_name AND id <> r.keep_id
        );

        -- Delete the duplicate supplier records
        DELETE FROM suppliers
        WHERE tenant_id = r.tenant_id AND LOWER(name) = r.lower_name AND id <> r.keep_id;
    END LOOP;
END $$;

-- 2. Drop the old index
DROP INDEX IF EXISTS uq_suppliers_tenant_name;

-- 3. Create a case-insensitive unique index
CREATE UNIQUE INDEX uq_suppliers_tenant_name ON suppliers (tenant_id, LOWER(name));
