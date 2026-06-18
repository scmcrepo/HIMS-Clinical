-- V103__fix_tenant_unique_constraints.sql
-- Drop global unique constraints on tenant-isolated master data tables
-- and replace them with tenant-scoped unique indexes.

-- 1. Departments
ALTER TABLE departments DROP CONSTRAINT IF EXISTS uq_departments_name;
DROP INDEX IF EXISTS uq_departments_tenant_name;
CREATE UNIQUE INDEX uq_departments_tenant_name ON departments (tenant_id, name);

-- 2. Account Units
ALTER TABLE account_units DROP CONSTRAINT IF EXISTS uq_account_units_name;
DROP INDEX IF EXISTS uq_account_units_tenant_name;
CREATE UNIQUE INDEX uq_account_units_tenant_name ON account_units (tenant_id, name);

-- 3. Room Categories
ALTER TABLE room_categories DROP CONSTRAINT IF EXISTS uq_room_categories_name;
DROP INDEX IF EXISTS uq_room_categories_tenant_name;
CREATE UNIQUE INDEX uq_room_categories_tenant_name ON room_categories (tenant_id, name);

-- 4. Molecules
ALTER TABLE molecules DROP CONSTRAINT IF EXISTS uq_molecules_name;
DROP INDEX IF EXISTS uq_molecules_tenant_name;
CREATE UNIQUE INDEX uq_molecules_tenant_name ON molecules (tenant_id, name);

-- 5. Units of Measure
ALTER TABLE units_of_measure DROP CONSTRAINT IF EXISTS uq_uom_name;
DROP INDEX IF EXISTS uq_uom_tenant_name;
CREATE UNIQUE INDEX uq_uom_tenant_name ON units_of_measure (tenant_id, name);

-- 6. Service Categories
ALTER TABLE service_categories DROP CONSTRAINT IF EXISTS uq_service_categories_name;
DROP INDEX IF EXISTS uq_service_categories_tenant_name;
CREATE UNIQUE INDEX uq_service_categories_tenant_name ON service_categories (tenant_id, name);

-- 7. Case Sheet Templates
ALTER TABLE case_sheet_templates DROP CONSTRAINT IF EXISTS uq_cst_name_spec;
DROP INDEX IF EXISTS uq_cst_tenant_name_spec;
CREATE UNIQUE INDEX uq_cst_tenant_name_spec ON case_sheet_templates (tenant_id, name, specialization, visit_type);
