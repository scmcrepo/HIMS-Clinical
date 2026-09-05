-- ---------------------------------------------------------------------------
--  V216 — Complete the audit columns on customers  (WO-032, F5 / card X-006)
--
--  WHY THIS EXISTS
--
--  Second instance of the defect V215 fixed, found by the check V215 shipped
--  with (AuditableEntitySchemaTest / check_entity_schema.py). Worth noting that
--  the check earned its keep on the first run: this was not in the original
--  review and nobody had spotted it.
--
--  Customer extends AuditableEntity, which maps status, modified_by and
--  modified_at, but the customers table (V006) predates those columns and was
--  never included in the V113-V118 bulk additions. Hibernate emits all three in
--  every SELECT and INSERT, so POST /customer — the pharmacy walk-in customer
--  save — throws:
--
--      ERROR: column "status" of relation "customers" does not exist
--
--  This is not dead code. CustomerController is live behind PHARMACY_SALES, and
--  the table holds name, contact_no, email and address for walk-in pharmacy
--  customers — four encrypted PII columns that PiiMigrationRunner.migrateCustomers
--  backfills. So unlike areas (removed in this same work order), the fix here is
--  to complete the table rather than delete the feature.
--
--  ── Defaults ───────────────────────────────────────────────────────────────
--
--  Matched to the convention on every other AuditableEntity table (checked
--  against taxes in a replayed schema): status smallint NOT NULL DEFAULT 1
--  (EntityStatus.ACTIVE), modified_by nullable uuid, modified_at NOT NULL
--  DEFAULT now().
--
--  Existing rows therefore become ACTIVE with modified_at set to the moment this
--  migration runs. That timestamp is a backfill artefact and not a real
--  modification time — the true one was never recorded and cannot be recovered.
--  Stated here so nobody later reads a wall of identical modified_at values as
--  evidence of a bulk edit.
--
--  ── Rollback ───────────────────────────────────────────────────────────────
--
--      ALTER TABLE customers DROP COLUMN IF EXISTS status;
--      ALTER TABLE customers DROP COLUMN IF EXISTS modified_by;
--      ALTER TABLE customers DROP COLUMN IF EXISTS modified_at;
--
--  Reverting restores the fault. Additive and idempotent; no data is destroyed.
-- ---------------------------------------------------------------------------

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS status SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS modified_by UUID;

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS modified_at TIMESTAMPTZ NOT NULL DEFAULT now();

COMMENT ON COLUMN customers.modified_at IS
    'Backfilled by V216 for rows created before the column existed; identical '
    'values across old rows are a migration artefact, not a bulk edit.';
