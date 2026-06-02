-- V039__fix_specimen_status_active.sql
-- All seed specimens were inserted without a status column, so they defaulted
-- to 0 (INACTIVE in EntityStatus ordinal). Set them all to 1 (ACTIVE).
UPDATE specimens SET status = 1 WHERE status IS NULL OR status = 0;

-- Also set the modified_at column if it is null (required NOT NULL on some deployments)
UPDATE specimens SET modified_at = created_at WHERE modified_at IS NULL;
