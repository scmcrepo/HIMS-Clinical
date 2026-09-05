-- ---------------------------------------------------------------------------
--  V220 — Encrypt sms_logs and template_data, and make both reachable by an
--         erasure request  (WO-029, card U-005)
--
--  WHY THIS EXISTS
--
--  Two more tables holding personal data in the clear, found by the unmapped-
--  table ratchet added for U-004. Both were invisible for the same reason
--  patient_pediatric was: no JPA entity maps them, so every encryption review
--  done on this codebase — all of which worked by enumerating entities — looked
--  straight past them.
--
--    sms_logs        to_number      a patient's mobile number
--                    message_body   the content of a message about their care
--                    error_message  provider errors, which quote the number
--
--    template_data   content        clinical template content, per encounter
--
--  ── The erasure gap is worse than the encryption gap ───────────────────────
--
--  Neither table was in ErasureService.TARGETS. A patient exercising the s. 12
--  right to erasure had their record anonymised everywhere the registry knew
--  about, while their phone number and the text of messages about their
--  treatment stayed behind in sms_logs indefinitely. The receipt said the
--  request completed.
--
--  Note how this was missed, because the same blind spot will hide the next one:
--  the erasure-coverage check enumerated tables carrying a patient_id column and
--  reported 24 of 25 covered. NEITHER OF THESE HAS A patient_id. sms_logs
--  reaches a patient through a phone number, template_data through an encounter.
--  A coverage check keyed on a column name cannot see either, and reported a
--  clean bill of health twice.
--
--  ── Linking rows to a patient ──────────────────────────────────────────────
--
--  sms_logs gains to_number_token: the same deterministic search token
--  PiiSearchTokenService already computes for patients.contact_number_token.
--  Once the backfill has populated it, an erasure can find a patient's rows by
--  joining on the token — which is the only way to find them at all once
--  to_number is encrypted, because the encryption is non-deterministic and two
--  ciphertexts of the same number do not match.
--
--  This is why the token column has to exist BEFORE the backfill encrypts the
--  column. Encrypting first and adding the token later would leave rows that can
--  never be linked to anyone: unreadable, unerasable, and undeletable except by
--  truncating the table.
--
--  template_data links through clinical_encounters.encounter_id, which already
--  carries patient_id and tenant_id.
--
--  ── Column types ───────────────────────────────────────────────────────────
--
--  to_number is VARCHAR(20) — it holds a phone number and nothing longer was
--  ever needed. Ciphertext is Base64 and far longer, so an encrypted value would
--  be truncated or rejected. Widened to TEXT, the same treatment V208 gave the
--  other encrypted string columns.
--
--  content is JSONB and becomes TEXT, as V214 and V219 did, because ciphertext
--  is not a JSON document.
--
--  ── Neither table has a live writer ────────────────────────────────────────
--
--  Nothing in the Java writes to either. SMS goes out through TwilioSmsAdapter,
--  which persists nothing (and, until this work order, logged the recipient's
--  number in the clear — fixed separately). So these are legacy rows.
--
--  That raises a question this migration does not answer: personal data with no
--  reader, no writer and no stated purpose is a storage-limitation problem under
--  s. 8(7) in its own right, and the honest remedy may be to delete the rows
--  rather than encrypt them. Encrypting is the reversible step and is taken
--  here; deleting destroys data and needs sign-off.
--
--  ── The data is NOT encrypted by this migration ────────────────────────────
--
--  Column types and flags only. The rows stay readable until
--  PiiMigrationRunner.migrateSmsLogs() and migrateTemplateData() run, for the
--  same reason as every other PII migration here: encrypting inside Flyway puts
--  the key in the migration path and makes a mid-way failure unrecoverable.
--
--  ── Rollback ───────────────────────────────────────────────────────────────
--
--      ALTER TABLE sms_logs ALTER COLUMN to_number TYPE VARCHAR(20);
--      ALTER TABLE sms_logs DROP COLUMN IF EXISTS to_number_token;
--      ALTER TABLE sms_logs DROP COLUMN IF EXISTS pii_encrypted;
--      ALTER TABLE template_data ALTER COLUMN content TYPE JSONB USING content::jsonb;
--      CREATE INDEX idx_td_content_gin ON template_data USING gin(content);
--      ALTER TABLE template_data DROP COLUMN IF EXISTS pii_encrypted;
--
--  Both casts only succeed while the values are still plaintext. After the
--  backfill, rolling back requires decrypting first.
--
--  Additive except for the two type widenings. No data is destroyed.
-- ---------------------------------------------------------------------------

-- ── sms_logs ───────────────────────────────────────────────────────────────

ALTER TABLE sms_logs
    ALTER COLUMN to_number TYPE TEXT;

ALTER TABLE sms_logs
    ADD COLUMN IF NOT EXISTS to_number_token VARCHAR(64);

ALTER TABLE sms_logs
    ADD COLUMN IF NOT EXISTS pii_encrypted BOOLEAN NOT NULL DEFAULT FALSE;

-- The token is the only route from a patient to their rows once to_number is
-- ciphertext, so erasure reads this index on every request.
CREATE INDEX IF NOT EXISTS ix_sms_logs_to_number_token
    ON sms_logs (to_number_token);

-- ── template_data ──────────────────────────────────────────────────────────

-- V003 created idx_td_content_gin as a GIN index over the JSONB content. The
-- type change cannot rebuild it — GIN has no default operator class for text —
-- so it has to go first.
--
-- Dropping it costs nothing that encryption had not already cost. A GIN index
-- exists to search inside the document; after the backfill the column holds
-- AES-GCM ciphertext, which is not searchable by any index. Keeping it would
-- mean maintaining an index over unsearchable bytes on every write.
--
-- If content-search over clinical templates is needed later, it needs the
-- deterministic-token approach used for phone numbers and email, not an index
-- over the ciphertext.
DROP INDEX IF EXISTS idx_td_content_gin;

ALTER TABLE template_data
    ALTER COLUMN content TYPE TEXT;

ALTER TABLE template_data
    ADD COLUMN IF NOT EXISTS pii_encrypted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS ix_template_data_encounter
    ON template_data (encounter_id);

COMMENT ON COLUMN sms_logs.to_number IS
    'Patient mobile number. AES-256-GCM ciphertext once the backfill has run. '
    'Non-deterministic: join on to_number_token, never on this column.';

COMMENT ON COLUMN sms_logs.to_number_token IS
    'Deterministic search token, same scheme as patients.contact_number_token. '
    'The only way to link an sms_logs row to a patient after encryption.';

COMMENT ON COLUMN sms_logs.message_body IS
    'Content of a message about a patient''s care. Encrypted by the backfill.';

COMMENT ON TABLE sms_logs IS
    'LEGACY: no live writer — TwilioSmsAdapter persists nothing. Personal data '
    'with no reader and no stated purpose is an s. 8(7) question; retained '
    'pending a decision on deleting it outright.';

COMMENT ON COLUMN template_data.content IS
    'Clinical template content. Encrypted by the backfill. Linked to a patient '
    'through clinical_encounters.encounter_id.';
