-- V136__make_service_categories_tenant_wide.sql
-- Update all existing service categories to be tenant-wide (branch_id = NULL)
UPDATE service_categories SET branch_id = NULL;
