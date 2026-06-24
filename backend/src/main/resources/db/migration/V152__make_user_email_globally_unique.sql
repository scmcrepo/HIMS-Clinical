-- V152__make_user_email_globally_unique.sql
-- Ensure email addresses (via their deterministic email token) are globally unique across all users.

-- 1. Drop the non-unique index on email_token created in V148
DROP INDEX IF EXISTS idx_users_email_token;

-- 2. Create a unique index on email_token (ignoring NULLs)
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_token ON users (email_token);
