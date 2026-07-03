-- V165: Add failed_login_attempts column to track consecutive wrong-password attempts.
-- After 5 failed attempts the application locks the account (status=0, account_locked=true).
-- An admin reactivation resets this counter to 0.
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0;
