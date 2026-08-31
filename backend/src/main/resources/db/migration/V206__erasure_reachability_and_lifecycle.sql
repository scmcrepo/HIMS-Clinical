-- ---------------------------------------------------------------------------
--  V206 — Erasure reachability and request lifecycle  (WO-024)
--
--  WHY THIS EXISTS
--
--  ErasureService was written but never called and never run. Reading its SQL
--  against the actual schema turned up three defects, all of which would only
--  have surfaced the first time a patient exercised their right to erasure:
--
--  1. agent_idempotency_keys has NO patient_id, but the sweep ran
--       DELETE FROM agent_idempotency_keys WHERE patient_id = :pid ...
--     which throws "column patient_id does not exist". The target would be
--     recorded FAILED and the cached tool responses — response_body is free
--     text and routinely contains patient detail — would survive the erasure.
--
--  2. hitl_escalations has NO patient_id either, and the anonymisation read
--       WHERE tenant_id = :tid AND run_id IN
--         (SELECT run_id FROM hitl_escalations WHERE tenant_id = :tid)
--     The subquery selects every run in the tenant. One patient exercising
--     erasure would have wiped every other patient's HITL transcript in that
--     hospital. This is data destruction, not under-deletion, and it is the
--     more dangerous of the two failure modes.
--
--  3. The registry covered 6 stores. The schema has 21 tables carrying
--     patient_id, plus the patients row itself. Erasure that reaches a quarter
--     of the copies is not erasure.
--
--  This migration makes the two unreachable stores reachable and gives the
--  request itself a real lifecycle: who verified the requester, how, when it is
--  due, and what a correction actually asked for.
--
--  ROLLBACK
--    ALTER TABLE hitl_escalations        DROP COLUMN patient_id;
--    ALTER TABLE agent_idempotency_keys  DROP COLUMN patient_id;
--    ALTER TABLE erasure_requests
--      DROP COLUMN requester_verified_at, DROP COLUMN verification_method,
--      DROP COLUMN verified_by,           DROP COLUMN due_at,
--      DROP COLUMN correction_payload,    DROP COLUMN requested_by_patient;
--  No data loss on rollback: every column added here is nullable and new.
-- ---------------------------------------------------------------------------

-- ── 1. Make the two PHI-bearing agent tables reachable by erasure ──────────

ALTER TABLE hitl_escalations
    ADD COLUMN IF NOT EXISTS patient_id UUID;

COMMENT ON COLUMN hitl_escalations.patient_id IS
    'Which patient this escalation concerns. Nullable because an escalation can '
    'be raised before the patient is identified, and because rows written before '
    'V206 have no way to be attributed retrospectively — see the caveat below.';

CREATE INDEX IF NOT EXISTS ix_hitl_patient
    ON hitl_escalations (tenant_id, patient_id)
 WHERE patient_id IS NOT NULL;

ALTER TABLE agent_idempotency_keys
    ADD COLUMN IF NOT EXISTS patient_id UUID;

COMMENT ON COLUMN agent_idempotency_keys.patient_id IS
    'Which patient the cached response_body concerns, so erasure can reach it. '
    'Nullable: tool calls that touch no patient (bed occupancy) leave it null.';

CREATE INDEX IF NOT EXISTS ix_agent_idem_patient
    ON agent_idempotency_keys (tenant_id, patient_id)
 WHERE patient_id IS NOT NULL;

-- CAVEAT, recorded here because it cannot be fixed by SQL:
--
-- Rows written before V206 carry a null patient_id and there is no join that
-- recovers it — neither table ever stored the link. Those rows cannot be reached
-- by a per-patient sweep. Two mitigations, both in the service layer:
--
--   * ErasureService sweeps pre-V206 rows by age: any escalation or idempotency
--     row older than the column's introduction is anonymised wholesale during
--     the first erasure run for that tenant, because unattributable PHI that
--     cannot be erased on request should not be retained at all.
--   * Writers now populate patient_id, so the problem does not recur.
--
-- The alternative — leaving unattributable transcripts in place — means the
-- hospital cannot honour an erasure request over data it is still holding.

-- ── 2. Request lifecycle: verification, due date, correction payload ───────

ALTER TABLE erasure_requests
    ADD COLUMN IF NOT EXISTS requester_verified_at TIMESTAMPTZ;
ALTER TABLE erasure_requests
    ADD COLUMN IF NOT EXISTS verification_method   VARCHAR(30);
ALTER TABLE erasure_requests
    ADD COLUMN IF NOT EXISTS verified_by           UUID;
ALTER TABLE erasure_requests
    ADD COLUMN IF NOT EXISTS due_at                TIMESTAMPTZ;
ALTER TABLE erasure_requests
    ADD COLUMN IF NOT EXISTS correction_payload    JSONB;
ALTER TABLE erasure_requests
    ADD COLUMN IF NOT EXISTS requested_by_patient  BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN erasure_requests.requester_verified_at IS
    'When the requester was proved to be the patient. A sweep must not run '
    'before this: acting on an unverified erasure request is itself a breach, '
    'because deleting a patient record on a stranger''s say-so destroys data '
    'and denies the real patient their history.';

COMMENT ON COLUMN erasure_requests.due_at IS
    'Statutory response deadline. DPDP Rules 2025 set a 90-day ceiling for '
    'grievance resolution; this project applies the same clock to rights '
    'requests as the conservative reading. Confirm the operative period with '
    'counsel — see WO-024 open questions.';

ALTER TABLE erasure_requests
    DROP CONSTRAINT IF EXISTS ck_erasure_verified_before_complete;
ALTER TABLE erasure_requests
    ADD CONSTRAINT ck_erasure_verified_before_complete
    CHECK (state NOT IN ('COMPLETED', 'PARTIALLY_COMPLETED')
           OR requester_verified_at IS NOT NULL);

ALTER TABLE erasure_requests
    DROP CONSTRAINT IF EXISTS ck_erasure_verification_method;
ALTER TABLE erasure_requests
    ADD CONSTRAINT ck_erasure_verification_method
    CHECK (verification_method IS NULL OR verification_method IN
        ('PORTAL_OTP', 'IN_PERSON_ID', 'ABHA_VERIFIED', 'REGISTERED_POST', 'STAFF_OVERRIDE'));

CREATE INDEX IF NOT EXISTS ix_erasure_due ON erasure_requests (due_at)
 WHERE state IN ('RECEIVED', 'IN_PROGRESS');

-- ── 3. Backfill due_at for any request already on file ────────────────────

UPDATE erasure_requests
   SET due_at = requested_at + INTERVAL '90 days'
 WHERE due_at IS NULL;

-- ── 4. Feature key for the patient-facing rights surface ──────────────────
--
-- ERASURE_MANAGE already exists from V179 and covers staff processing requests.
-- This one covers a patient raising their own, which is a different actor and a
-- different permission.

INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'ERASURE_REQUEST', 'COMPLIANCE',
       'Raise a data-principal erasure or correction request', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE f.feature_key = 'ERASURE_REQUEST'
  AND UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN', 'RECEPTION')
ON CONFLICT DO NOTHING;
