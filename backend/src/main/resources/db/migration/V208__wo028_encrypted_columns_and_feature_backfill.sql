-- ---------------------------------------------------------------------------
--  V208 — WO-028 remediation: encrypted column widths, tenant feature backfill
--
--  Two unrelated fixes that share a migration because both came out of the same
--  triage pass.
--
--  ── 1. Column widths for newly encrypted fields ─────────────────────────────
--
--  Four columns gained @Convert(EncryptedStringConverter) in WO-028:
--
--    visits.diagnosis                  — clinical diagnosis, health data
--    nhcx_transactions.diagnosis_code  — ICD-10 sent to the payer
--    nhcx_transactions.diagnosis_text  — free-text diagnosis
--    pharmacy_sales.customer_phone     — walk-in customer's only identifier
--
--  visits.diagnosis was plaintext while the equivalent column on
--  clinical_encounters was already encrypted — the kind of gap that only
--  surfaces when someone enumerates the schema rather than reading the code.
--
--  Ciphertext is base64 and runs roughly 40% longer than plaintext, plus the IV
--  and tag. A VARCHAR(20) phone becomes ~90 characters. Without widening these
--  first, the application starts, appears healthy, and then throws on the first
--  write to each column — at the pharmacy counter, mid-sale.
--
--  ── 2. Feature seeding across tenants ───────────────────────────────────────
--
--  Thirteen historical migrations (V012 through V195) inserted feature rows
--  without iterating tenants. Migrations are immutable, so those cannot be
--  corrected in place; this backfills any feature key that exists for some
--  tenant but not for others.
--
--  Consequence of the gap: a tenant created before a feature was added, or one
--  that a hand-written seed missed, silently has no row for that key. Every
--  hasPermission check against it returns false, so the feature is invisible —
--  no error, just an absent button, in one hospital and not another.
--
--  ROLLBACK
--    Column widening is not reversible without truncating ciphertext. The
--    feature backfill can be undone by deleting rows created by this migration,
--    but doing so would re-open the gap.
--
--  ORDERING
--    This must run BEFORE the application boots with the new converters. Flyway
--    runs at startup ahead of the entity manager, so that ordering is automatic.
--
--  NOT DONE HERE
--    Existing plaintext in these four columns is not encrypted by this
--    migration. PiiMigrationRunner is the mechanism for that — SQL cannot call
--    the application's encryption service, and doing it in SQL would mean
--    putting the key in a migration file. Registration of these columns with
--    that runner is a follow-on card.
-- ---------------------------------------------------------------------------

-- ── 1. Widen ──────────────────────────────────────────────────────────────

ALTER TABLE visits            ALTER COLUMN diagnosis      TYPE TEXT;
ALTER TABLE nhcx_transactions ALTER COLUMN diagnosis_code TYPE TEXT;
ALTER TABLE nhcx_transactions ALTER COLUMN diagnosis_text TYPE TEXT;
ALTER TABLE pharmacy_sales    ALTER COLUMN customer_phone TYPE TEXT;

COMMENT ON COLUMN visits.diagnosis IS
    'Encrypted via EncryptedStringConverter (WO-028). Health data — was plaintext '
    'while clinical_encounters.diagnosis was already encrypted.';
COMMENT ON COLUMN nhcx_transactions.diagnosis_code IS
    'Encrypted via EncryptedStringConverter (WO-028). ICD-10, health data.';
COMMENT ON COLUMN nhcx_transactions.diagnosis_text IS
    'Encrypted via EncryptedStringConverter (WO-028).';
COMMENT ON COLUMN pharmacy_sales.customer_phone IS
    'Encrypted via EncryptedStringConverter (WO-028). Not queried by value, so no '
    'search token is needed — unlike patients.contact_number.';

-- ── 2. Backfill every feature for every tenant ────────────────────────────
--
-- Takes the union of all feature keys known anywhere and ensures each tenant
-- has a row for each. Descriptions and modules are copied from an existing row
-- for that key so nothing is invented here.

INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), fk.feature_key, fk.module, fk.description, t.id
FROM tenants t
CROSS JOIN (
    -- One canonical row per key. features has no created_at (V001 predates the
    -- auditing columns and V113 only added tenant_id), so the tiebreak is on id
    -- — arbitrary but stable, and module/description are identical across
    -- tenants for a given key in practice.
    SELECT DISTINCT ON (feature_key) feature_key, module, description
    FROM features
    ORDER BY feature_key, id
) AS fk
WHERE NOT EXISTS (
    SELECT 1 FROM features f
    WHERE f.tenant_id = t.id AND f.feature_key = fk.feature_key
);

-- Role grants are deliberately NOT backfilled. Which roles hold which feature
-- is a per-hospital decision, and guessing it here would hand permissions to
-- roles an administrator never chose. The rows now exist to be granted; granting
-- them is an administrative act.
