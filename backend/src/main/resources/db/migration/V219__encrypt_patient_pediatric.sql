-- ---------------------------------------------------------------------------
--  V219 — Encrypt patient_pediatric.pediatric_data at rest  (WO-029, card U-004)
--
--  WHY THIS EXISTS
--
--  V214 encrypted patients.pediatric_data and patients.template_data, and its
--  own header said pediatric_data "was the only place in the patient record
--  still storing [paediatric data] in the clear". That was not true.
--
--  patient_pediatric (V010) holds its own pediatric_data JSONB column, one row
--  per patient, still plaintext. It is children's growth-chart data: health data
--  about a child, which is the Rule 12 category and the most sensitive
--  combination the Act contemplates. It has been sitting unencrypted through
--  every work order that claimed the patient record was covered.
--
--  It survived because it is invisible from Java. No JPA entity maps it, so it
--  does not appear in any entity scan, any converter registry, or any of the
--  encryption audits done so far. It appears in exactly two places in the
--  codebase — ErasureService's target registry and RetentionService's
--  NEVER_SWEEP list — and in both it is a string in a list rather than a type.
--  An encryption review that works by reading entities cannot see this table.
--
--  ── This table has no live writer ──────────────────────────────────────────
--
--  PatientController.updatePediatric is a stub: it accepts a body, writes
--  nothing, and returns "Pediatric data updated". Whatever wrote these rows was
--  an earlier version of the application. So the rows here are legacy data, and
--  the current home for paediatric data is patients.pediatric_data, which V214
--  encrypted.
--
--  That means this migration protects data at rest and nothing else. It does not
--  need a converter, because no code path reads or writes the column.
--
--  Two follow-ups are raised rather than taken here, because both need a
--  decision this migration is not entitled to make:
--    * the stub endpoint silently discards what callers send it;
--    * two tables now hold paediatric data, one live and encrypted and one
--      orphaned, which invites a future reader to pick the wrong one. Merging
--      them and dropping this table destroys data and needs sign-off.
--
--  ── What changes ───────────────────────────────────────────────────────────
--
--  JSONB -> TEXT, matching what V214 did to patients.pediatric_data. Ciphertext
--  is Base64 and is not a JSON document; storing it in a JSONB column would
--  either fail or coerce it into a JSON string literal, and neither is honest.
--
--  Verified against real rows before shipping: values survive intact, including
--  non-ASCII, and every row remains parseable JSON afterwards. One real change —
--  JSONB normalises key order and whitespace on storage, so '{"b":1,"a":2}'
--  emerges as '{"a": 2, "b": 1}'. Semantically identical, byte-different. That
--  matters only if something ever hashed or signed these documents, which
--  nothing does.
--
--  The DEFAULT '{}' is dropped. An encrypted column should not have a plaintext
--  default quietly writing readable values on any future insert.
--
--  pii_encrypted tracks the backfill, following the convention established for
--  the other encrypted tables. It starts FALSE so PiiMigrationRunner picks every
--  existing row up.
--
--  ── The data is NOT encrypted by this migration ────────────────────────────
--
--  This only changes the column type and adds the flag. The rows stay readable
--  until PiiMigrationRunner.migratePatientPediatric() runs. That is deliberate
--  and matches every other PII migration here: encrypting inside a Flyway
--  migration would put the encryption key in the migration path and make a
--  failure mid-way unrecoverable.
--
--  Until that backfill is run, this table is still plaintext children's health
--  data.
--
--  ── Rollback ───────────────────────────────────────────────────────────────
--
--      ALTER TABLE patient_pediatric ALTER COLUMN pediatric_data TYPE JSONB
--          USING pediatric_data::jsonb;
--      ALTER TABLE patient_pediatric ALTER COLUMN pediatric_data SET DEFAULT '{}';
--      ALTER TABLE patient_pediatric DROP COLUMN IF EXISTS pii_encrypted;
--
--  The USING cast only succeeds while the values are still plaintext JSON. Once
--  the backfill has run, rolling back requires decrypting first — the same
--  one-way step as every other encrypted column here.
--
--  No data is read, written or destroyed by this migration.
-- ---------------------------------------------------------------------------

ALTER TABLE patient_pediatric
    ALTER COLUMN pediatric_data DROP DEFAULT;

ALTER TABLE patient_pediatric
    ALTER COLUMN pediatric_data TYPE TEXT;

ALTER TABLE patient_pediatric
    ADD COLUMN IF NOT EXISTS pii_encrypted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN patient_pediatric.pediatric_data IS
    'Children''s growth-chart data. AES-256-GCM ciphertext once '
    'PiiMigrationRunner.migratePatientPediatric() has run; plaintext JSON until '
    'then. Do not read this column with raw SQL and expect JSON.';

COMMENT ON COLUMN patient_pediatric.pii_encrypted IS
    'FALSE until the row has been through the encryption backfill. Not a '
    'guarantee the value is ciphertext — see encryptIfPlaintext, which is '
    'idempotent and tolerates rows that were already encrypted.';

COMMENT ON TABLE patient_pediatric IS
    'Pediatric growth chart JSON, one row per patient. LEGACY: no live writer — '
    'PatientController.updatePediatric is a stub, and the current home for this '
    'data is patients.pediatric_data. Retained pending a decision on merging.';
