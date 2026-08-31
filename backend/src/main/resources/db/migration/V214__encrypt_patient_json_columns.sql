-- ---------------------------------------------------------------------------
--  V214 — Encrypt the unstructured patient JSON columns  (WO-029, partial)
--
--  WHY THIS EXISTS
--
--  patients.pediatric_data and patients.template_data are unstructured JSONB
--  maps whose contents depend on how each tenant configures its forms. The DPIA
--  flags this under §2 (data minimisation): minimisation cannot be assessed for
--  a field that can hold anything, so the honest position is that both may hold
--  anything and must be protected accordingly.
--
--  Paediatric data is also the category Rule 12 treats most carefully, and this
--  column was the only place in the patient record still storing it in the clear.
--
--  ── The columns stop being JSONB ───────────────────────────────────────────
--
--  Ciphertext is opaque. Postgres can no longer index into these columns, query
--  inside them with the JSON operators, or validate that the content is JSON.
--  They become TEXT holding a blob only the application can read.
--
--  That is a genuine loss of capability, not just a type change. Any future
--  feature that wants to filter patients by a key inside template_data will not
--  be able to. Check for existing JSONB queries before deploying:
--
--      SELECT * FROM pg_proc WHERE prosrc LIKE '%template_data%';
--      -- and grep the codebase for ->> and @> against these columns
--
--  A blind index is the escape hatch if one specific key ever needs to be
--  searchable, the same pattern patients.contact_number_token uses. That has to
--  be designed per key rather than granted wholesale.
--
--  ── Existing rows ──────────────────────────────────────────────────────────
--
--  Not encrypted here. PiiMigrationRunner does that on next startup, and the
--  converter reads plaintext JSON as well as ciphertext so nothing breaks in
--  between. This is the V208 mistake corrected in advance rather than repaired
--  afterwards: widen first, tolerate both forms, then backfill.
--
--  ROLLBACK
--    Not safely reversible once the runner has encrypted rows — the ciphertext
--    is not valid JSON and casting back to JSONB will fail. Restore from backup
--    if this must be undone after the backfill has run.
-- ---------------------------------------------------------------------------

ALTER TABLE patients ALTER COLUMN pediatric_data TYPE TEXT;
ALTER TABLE patients ALTER COLUMN template_data  TYPE TEXT;

COMMENT ON COLUMN patients.pediatric_data IS
    'Encrypted via EncryptedJsonMapConverter (WO-029). Was JSONB and is no longer '
    'queryable inside. Children''s data under Rule 12.';
COMMENT ON COLUMN patients.template_data IS
    'Encrypted via EncryptedJsonMapConverter (WO-029). Was JSONB and is no longer '
    'queryable inside. Free-form tenant-configured content.';

-- Progress flag for the backfill, matching the pattern V212 established for the
-- other four late-encrypted columns.
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS json_pii_encrypted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN patients.json_pii_encrypted IS
    'PiiMigrationRunner progress flag for pediatric_data and template_data. '
    'Separate from pii_encrypted, which tracks the string columns — the two '
    'backfills ran at different times and conflating them would re-walk rows '
    'that are already done.';

-- Rows with nothing in either column need no visit from the runner.
UPDATE patients SET json_pii_encrypted = TRUE
 WHERE json_pii_encrypted = FALSE
   AND pediatric_data IS NULL
   AND template_data IS NULL;

CREATE INDEX IF NOT EXISTS ix_patients_json_pii_pending
    ON patients (id) WHERE json_pii_encrypted = FALSE;
