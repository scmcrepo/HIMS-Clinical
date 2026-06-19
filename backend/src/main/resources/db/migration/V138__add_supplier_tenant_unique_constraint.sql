-- V138__add_supplier_tenant_unique_constraint.sql

-- 1. Trim all names in the suppliers table
UPDATE suppliers SET name = TRIM(name);

-- 2. Clean up duplicate supplier names case-insensitively per tenant
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

-- 3. Create a unique index on (tenant_id, name)
DROP INDEX IF EXISTS uq_suppliers_tenant_name;
CREATE UNIQUE INDEX uq_suppliers_tenant_name ON suppliers (tenant_id, name);
