-- ---------------------------------------------------------------------------
--  V221 — Key rotation progress ledger  (WO-029, card U-003)
--
--  WHY THIS EXISTS
--
--  PiiKeyRotationUtil has existed, undocumented as unsafe, since the encryption
--  work landed. It has never been run. Reading it before running it turned up
--  three defects, any one of which would have destroyed the database:
--
--   1. INFINITE RE-SELECT. rotateTable issued the same
--      "SELECT ... LIMIT 100" on every pass, with no cursor and no marker of
--      what had been done. After rotating the first hundred rows it selected the
--      SAME hundred rows — now encrypted under the NEW key — and tried to
--      decrypt them with the OLD one. Any table with 100 or more rows therefore
--      failed partway through, leaving it half-rotated with NO record of which
--      rows used which key. Unrecoverable: you cannot resume, and you cannot
--      tell a rotated row from an unrotated one by looking at it.
--
--   2. NO TRANSACTION. @Transactional was imported and never applied, so every
--      row committed on its own and a failure left mixed-key state behind.
--
--   3. THREE TABLES OF TWENTY-THREE. It rotated patients, users and
--      consultants. Reflection over the entity model finds 23 tables and 69
--      encrypted columns. Rotating with it and then swapping the key in config
--      would have made twenty tables permanently undecryptable — including
--      every diagnosis, every insurance claim and every grievance.
--
--  The rewrite fixes all three. This table is what makes the first one fixable:
--  rotation now records where it got to, so an interrupted run resumes instead
--  of restarting into rows it has already converted.
--
--  ── Why a cursor and not a per-row key_version column ──────────────────────
--
--  A key_version on every encrypted table would be more precise — you could
--  read any row and know which key it needs. It also means altering 23 tables
--  and teaching the converter to choose a key per row, which is a larger change
--  to the read path than the rotation itself.
--
--  A keyset cursor per table gives resumability without touching the read path:
--  rotation walks id order, records the last id committed, and resumes from
--  there. The cost is that the cursor is the only record of progress, so it must
--  be written in the same transaction as the batch it describes. It is.
--
--  ── This table holds no personal data ──────────────────────────────────────
--
--  Table names, row counts, a UUID cursor and timestamps. No key material: the
--  keys live in configuration and are passed in at call time, never stored here.
--  Recording a key, or even a hash of one, would turn an operational ledger into
--  a target.
--
--  ── Rollback ───────────────────────────────────────────────────────────────
--
--      DROP TABLE IF EXISTS pii_key_rotation_progress;
--
--  Safe when no rotation is in flight. Dropping it mid-rotation loses the
--  cursor, which means the next run restarts from the beginning and hits
--  already-rotated rows — the original defect, reintroduced by hand.
--
--  Additive. No existing table is touched.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pii_key_rotation_progress (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),

    -- The run these rows belong to. One id per rotation attempt, so a second
    -- attempt after a failure is distinguishable from the first in the audit
    -- trail rather than overwriting it.
    run_id         UUID         NOT NULL,

    table_name     TEXT         NOT NULL,

    -- Keyset cursor: the highest primary key committed so far. NULL means the
    -- table has been claimed but no batch has committed yet.
    last_id        UUID,

    rows_done      INTEGER      NOT NULL DEFAULT 0,
    rows_failed    INTEGER      NOT NULL DEFAULT 0,

    -- PENDING | IN_PROGRESS | COMPLETED | FAILED
    state          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',

    -- Exception TYPE only, never the message: a decryption failure message can
    -- quote ciphertext or partial plaintext. Same rule as PiiMigrationRunner.
    last_error     VARCHAR(120),

    -- TRUE when the run was a dry run: decrypt, re-encrypt and verify in memory,
    -- writing nothing. A dry run leaves a row here so the plan is auditable, and
    -- so nobody has to guess afterwards whether a run was real.
    dry_run        BOOLEAN      NOT NULL DEFAULT FALSE,

    started_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_pii_key_rotation_progress PRIMARY KEY (id),
    CONSTRAINT uq_rotation_run_table UNIQUE (run_id, table_name)
);

CREATE INDEX IF NOT EXISTS ix_rotation_progress_run
    ON pii_key_rotation_progress (run_id);

COMMENT ON TABLE pii_key_rotation_progress IS
    'Resumability and audit trail for PII key rotation (U-003). Holds no key '
    'material and no personal data.';

COMMENT ON COLUMN pii_key_rotation_progress.last_id IS
    'Keyset cursor. Written in the same transaction as the batch it describes — '
    'if it were not, a crash between the two would leave the cursor claiming '
    'rows that were never rotated, or rotating rows twice.';
