-- ---------------------------------------------------------------------------
--  V212 — Encryption backfill flags and the OTP search token  (F-002, F-001)
--
--  Two related pieces of unfinished business from WO-028.
--
--  ── F-002: the four columns V208 widened but never encrypted ───────────────
--
--  V208 widened visits.diagnosis, nhcx_transactions.diagnosis_code and
--  .diagnosis_text, and pharmacy_sales.customer_phone to TEXT so ciphertext
--  would fit, and added the @Convert annotations. It did not encrypt the rows
--  already there, because SQL cannot reach the application's encryption service
--  and doing it in SQL would mean putting the key in a migration file.
--
--  The consequence was a silent one: new writes encrypted, old rows plaintext,
--  and reads of old rows throwing on decryption. A patient's recent diagnoses
--  readable and their older ones not.
--
--  PiiMigrationRunner does the actual encryption, and it tracks progress with a
--  per-table pii_encrypted flag. These three tables never had one. That column
--  is what this migration adds; the runner does the rest on next startup.
--
--  ── F-001: password_reset_otp.email ────────────────────────────────────────
--
--  WO-028 flagged this as plaintext PII. I encrypted it during that work order
--  and then reverted, because the reset flow queries
--  findFirstByEmailAndOtp... and EncryptedStringConverter is non-deterministic —
--  the same address encrypts differently every time, so every lookup would have
--  missed and password reset would have broken for every user with no error
--  anywhere to explain why.
--
--  The fix is the pattern patients.contact_number_token already uses: keep the
--  ciphertext in one column and a deterministic HMAC in another, and query the
--  HMAC.
--
--  ── Why the OTP rows are deleted rather than migrated ──────────────────────
--
--  Password reset OTPs expire five minutes after issue. Migrating them would
--  mean encrypting and tokenising rows that are, without exception, either
--  already useless or about to be — and doing it in the one table where a
--  half-migrated state locks users out of their own accounts.
--
--  Deleting them is the safer trade. The cost is that anyone holding an
--  unused OTP at the moment of deployment must request a new one, which is a
--  button they already know how to press. The alternative risks a partially
--  migrated authentication table, which is a much worse Monday morning.
--
--  ROLLBACK
--    ALTER TABLE visits            DROP COLUMN pii_encrypted;
--    ALTER TABLE nhcx_transactions DROP COLUMN pii_encrypted;
--    ALTER TABLE pharmacy_sales    DROP COLUMN pii_encrypted;
--    ALTER TABLE password_reset_otp DROP COLUMN email_token;
--  The deleted OTP rows are not recoverable and do not need to be. Rows the
--  runner has already encrypted are NOT reverted by dropping the flag — see the
--  V208 rollback caveat in the deployment runbook.
-- ---------------------------------------------------------------------------

-- ── 1. Progress flags for the four F-002 columns ──────────────────────────
--
-- Defaults FALSE so every existing row is picked up by the runner. Rows written
-- after this migration are already encrypted by the converter, but the runner's
-- encryptIfPlaintext() checks looksEncrypted() before acting, so re-processing
-- them is a no-op rather than double encryption.

ALTER TABLE visits
    ADD COLUMN IF NOT EXISTS pii_encrypted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE nhcx_transactions
    ADD COLUMN IF NOT EXISTS pii_encrypted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pharmacy_sales
    ADD COLUMN IF NOT EXISTS pii_encrypted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN visits.pii_encrypted IS
    'PiiMigrationRunner progress flag for the diagnosis column (F-002). '
    'FALSE means the row may still hold plaintext.';

-- Rows with nothing to encrypt are marked done here rather than making the
-- runner walk them. Same shortcut migrateClinicalEncounters already takes.
UPDATE visits SET pii_encrypted = TRUE
 WHERE pii_encrypted = FALSE AND diagnosis IS NULL;

UPDATE nhcx_transactions SET pii_encrypted = TRUE
 WHERE pii_encrypted = FALSE AND diagnosis_code IS NULL AND diagnosis_text IS NULL;

UPDATE pharmacy_sales SET pii_encrypted = TRUE
 WHERE pii_encrypted = FALSE AND customer_phone IS NULL;

-- Partial indexes so the runner's batch SELECT does not scan the whole table on
-- every pass once most rows are done.
CREATE INDEX IF NOT EXISTS ix_visits_pii_pending
    ON visits (id) WHERE pii_encrypted = FALSE;
CREATE INDEX IF NOT EXISTS ix_nhcx_pii_pending
    ON nhcx_transactions (id) WHERE pii_encrypted = FALSE;
CREATE INDEX IF NOT EXISTS ix_pharmacy_sales_pii_pending
    ON pharmacy_sales (id) WHERE pii_encrypted = FALSE;

-- ── 2. OTP search token ───────────────────────────────────────────────────

ALTER TABLE password_reset_otp
    ADD COLUMN IF NOT EXISTS email_token VARCHAR(64);

COMMENT ON COLUMN password_reset_otp.email_token IS
    'Deterministic HMAC of the lowercased email, used for lookup because the '
    'email column itself is now encrypted with a non-deterministic converter. '
    'Same pattern as patients.contact_number_token (F-001).';

-- The email column now holds ciphertext, which is longer than any address.
ALTER TABLE password_reset_otp ALTER COLUMN email TYPE TEXT;

CREATE INDEX IF NOT EXISTS ix_password_reset_otp_token
    ON password_reset_otp (email_token, created_at DESC);

-- Clear the table. See the reasoning above: every row here has a five-minute
-- TTL, and a half-migrated authentication table is a worse outcome than a
-- handful of people pressing "resend".
DELETE FROM password_reset_otp;
