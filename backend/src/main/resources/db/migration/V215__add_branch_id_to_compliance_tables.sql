-- ---------------------------------------------------------------------------
--  V215 — Add the missing branch_id column to six compliance tables  (WO-032, F1)
--
--  WHY THIS EXISTS
--
--  AuditableEntity maps BOTH tenant_id and branch_id as columns. Every entity
--  extending it therefore has both emitted in every SELECT and INSERT Hibernate
--  builds, whether or not the branch means anything for that table.
--
--  V113-V118 bulk-added those two columns to the tables that existed at the
--  time, working from hardcoded table lists. Any table created after V118 has
--  to declare branch_id itself. Six did not:
--
--      V179  erasure_requests               ErasureRequestEntity
--      V209  incident_affected_principals   IncidentAffectedPrincipalEntity
--      V210  grievance_events               GrievanceEventEntity
--      V213  retention_policies             RetentionPolicyEntity
--      V213  retention_runs                 RetentionRunEntity
--      V213  retention_run_items            RetentionRunItemEntity
--
--  All six sit behind Spring Data JPA repositories, so every read and every
--  write against them throws:
--
--      ERROR: column rpe1_0.branch_id does not exist
--
--  Observed on every boot in the application error log, alongside
--  event=retention.startup.validation_failed. Those six tables are the storage
--  engine for s. 8(7) retention, s. 12 erasure, the s. 8(9) grievance audit
--  trail and the Rule 7 affected-principals list. The code for all four exists
--  and none of it has ever executed.
--
--  Note which tables were NOT missed: consent_records, security_incidents,
--  grievances and compliance_contacts all declared branch_id correctly. The
--  parents were done and the detail tables were not, which is why the defect
--  survived four work orders — the subsystem looked wired up from the top.
--
--  ── Why the column is nullable and stays NULL ──────────────────────────────
--
--  These six are declared tenant-wide in the entity layer
--  (@Filter(name="branchFilter", condition="1=1")), which makes
--  AuditableEntity.stampScope() skip branch stamping on insert. The column
--  exists so the O/R mapping is valid, not to carry data.
--
--  That is deliberate rather than incidental. A retention period is a
--  hospital-level legal determination. An erasure request is made to the
--  hospital, and branch-scoping it would let the statutory clock run against a
--  record the compliance officer at another location cannot see. The Rule 7
--  affected-principals list decides who gets notified, so a partially visible
--  list means under-notification. And grievance_events is an audit trail hanging
--  off a grievance that is itself branch-scoped — the parent row is the access
--  control, and a half-visible audit trail on a visible complaint is worse than
--  either extreme.
--
--  ── Rollback ───────────────────────────────────────────────────────────────
--
--      ALTER TABLE erasure_requests             DROP COLUMN IF EXISTS branch_id;
--      ALTER TABLE incident_affected_principals DROP COLUMN IF EXISTS branch_id;
--      ALTER TABLE grievance_events             DROP COLUMN IF EXISTS branch_id;
--      ALTER TABLE retention_policies           DROP COLUMN IF EXISTS branch_id;
--      ALTER TABLE retention_runs               DROP COLUMN IF EXISTS branch_id;
--      ALTER TABLE retention_run_items          DROP COLUMN IF EXISTS branch_id;
--
--  Safe: the column is nullable, carries no data, and is referenced by no
--  application query directly. Reverting the entity changes without reverting
--  this migration is also safe; the reverse is not, and would restore the fault.
--
--  Additive and idempotent. No data is read, written or destroyed.
-- ---------------------------------------------------------------------------

ALTER TABLE erasure_requests
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

ALTER TABLE incident_affected_principals
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

ALTER TABLE grievance_events
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

ALTER TABLE retention_policies
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

ALTER TABLE retention_runs
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

ALTER TABLE retention_run_items
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES branches(id);

COMMENT ON COLUMN retention_policies.branch_id IS
    'Present for AuditableEntity mapping only. Always NULL: retention policy is '
    'a tenant-level determination and the entity declares branchFilter = 1=1.';

COMMENT ON COLUMN erasure_requests.branch_id IS
    'Present for AuditableEntity mapping only. Always NULL: a data-principal '
    'request belongs to the hospital, not to one of its locations.';
