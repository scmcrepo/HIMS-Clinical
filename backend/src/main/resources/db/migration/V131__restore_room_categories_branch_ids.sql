-- V130__restore_room_categories_branch_ids.sql (PostgreSQL 16)
-- Assign any NULL branch_id in room_categories to the default/first branch of their tenant.

UPDATE room_categories rc
SET branch_id = (
    SELECT id FROM branches b
    WHERE b.tenant_id = rc.tenant_id
    ORDER BY b.is_default DESC, b.created_at ASC
    LIMIT 1
)
WHERE rc.branch_id IS NULL;
