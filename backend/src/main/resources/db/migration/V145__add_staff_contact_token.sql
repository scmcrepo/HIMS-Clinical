-- V144: Add HMAC contact_token column to staff table for encryption-safe duplicate checking.
-- The contact column is now AES-encrypted and can't be used for SQL equality checks.
ALTER TABLE staff ADD COLUMN IF NOT EXISTS contact_token VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_staff_contact_token ON staff (contact_token);
