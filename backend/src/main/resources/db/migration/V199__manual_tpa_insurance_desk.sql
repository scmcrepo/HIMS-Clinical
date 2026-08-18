-- =============================================================================
--  V199 — Manual TPA insurance desk: 7-stage progressive workflow  (WO-020)
--
--  HIMS already speaks NHCX (V191/V192/V196): digital eligibility, pre-auth,
--  claims and payment advice. Most Indian TPAs still do not. The insurance desk
--  works a fax-and-courier process — pre-auth faxed, approval faxed back,
--  enhancement raised mid-stay, a physical document checklist assembled, a
--  docket couriered with a POD number, and finally a cheque with a page of
--  disallowances. None of that had anywhere to live in this schema.
--
--  This migration adds those stages to `insurances`. It is deliberately the
--  SAME ROW rather than a workflow table: every stage is a fact about one
--  claim, the desk edits them out of order, and a single row keeps the whole
--  stage transition transactional.
--
--  ALL AMOUNTS IN PAISE as BIGINT, matching billing, V191, V192 and V196.
--  The source specification used rupee INT(11); that is a legacy artefact and
--  mixing units is how money bugs happen.
--
--  Column names follow VITALSOFT_VS_HIMS_INSURANCE_GAPS.md verbatim so the
--  requirement stays greppable against the schema. Note the requirement titled
--  this migration V197 — V197 was already taken by the patient portal and V198
--  is reserved by WO-018, so this is V199.
--
--  ENCRYPTED COLUMNS (WO-020 decisions D-7, D-8), stored as TEXT because
--  AES-GCM ciphertext plus IV and tag, Base64-encoded, is roughly 2.4x the
--  plaintext — the same reason V146 widened policy_number to 512:
--    claim_no                      — identifies the patient to their insurer
--    preauth_rejection_reason      — free text; "denied, pre-existing diabetic
--    enhancement_rejection_reason    nephropathy" is a diagnosis
--    reason_for_enhancement        — free text; "extended ICU stay post-op
--                                    sepsis" is clinical information
--  NOT encrypted, deliberately: TPA fax numbers and mail ids are the INSURER's
--  business contact endpoints, not patient data; dispatched_by is a staff name,
--  which the reports layer already displays plainly for users.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS insurance_cheque_receipts;
--    ALTER TABLE insurances
--      DROP COLUMN IF EXISTS card_validity,
--      DROP COLUMN IF EXISTS preauth_communication_to_tpa,
--      DROP COLUMN IF EXISTS preauth_fax_no,
--      DROP COLUMN IF EXISTS preauth_mail_id,
--      DROP COLUMN IF EXISTS preauth_applied_date,
--      DROP COLUMN IF EXISTS preauth_requested_amount,
--      DROP COLUMN IF EXISTS preauth_created_by,   DROP COLUMN IF EXISTS preauth_created_date,
--      DROP COLUMN IF EXISTS preauth_updated_by,   DROP COLUMN IF EXISTS preauth_updated_date,
--      DROP COLUMN IF EXISTS claim_no,             DROP COLUMN IF EXISTS claim_no_token,
--      DROP COLUMN IF EXISTS preauth_approval_status,
--      DROP COLUMN IF EXISTS preauth_date_of_approval,
--      DROP COLUMN IF EXISTS preauth_communication_by_tpa,
--      DROP COLUMN IF EXISTS preauth_approve_fax_no,
--      DROP COLUMN IF EXISTS preauth_approve_mail_id,
--      DROP COLUMN IF EXISTS preauth_approved_limit,
--      DROP COLUMN IF EXISTS preauth_rejection_reason,
--      DROP COLUMN IF EXISTS preauth_approval_created_by,
--      DROP COLUMN IF EXISTS preauth_approval_created_date,
--      DROP COLUMN IF EXISTS preauth_approval_updated_by,
--      DROP COLUMN IF EXISTS preauth_approval_updated_date,
--      DROP COLUMN IF EXISTS enhancement_type,
--      DROP COLUMN IF EXISTS enhancement_applied_date,
--      DROP COLUMN IF EXISTS enhancement_requested_amount,
--      DROP COLUMN IF EXISTS enhancement_communication_to_tpa,
--      DROP COLUMN IF EXISTS enhancement_fax_no,
--      DROP COLUMN IF EXISTS enhancement_mail_id,
--      DROP COLUMN IF EXISTS reason_for_enhancement,
--      DROP COLUMN IF EXISTS enhancement_created_by,  DROP COLUMN IF EXISTS enhancement_created_date,
--      DROP COLUMN IF EXISTS enhancement_updated_by,  DROP COLUMN IF EXISTS enhancement_updated_date,
--      DROP COLUMN IF EXISTS enhancement_approval_status,
--      DROP COLUMN IF EXISTS enhancement_date_of_approval,
--      DROP COLUMN IF EXISTS enhancement_communication_by_tpa,
--      DROP COLUMN IF EXISTS enhancement_approved_limit,
--      DROP COLUMN IF EXISTS enhancement_rejection_reason,
--      DROP COLUMN IF EXISTS enhancement_approval_created_by,
--      DROP COLUMN IF EXISTS enhancement_approval_created_date,
--      DROP COLUMN IF EXISTS enhancement_approval_updated_by,
--      DROP COLUMN IF EXISTS enhancement_approval_updated_date,
--      DROP COLUMN IF EXISTS checklist,
--      DROP COLUMN IF EXISTS check_list_created_by,  DROP COLUMN IF EXISTS check_list_created_date,
--      DROP COLUMN IF EXISTS check_list_updated_by,  DROP COLUMN IF EXISTS check_list_updated_date,
--      DROP COLUMN IF EXISTS mode_of_dispatch,       DROP COLUMN IF EXISTS courier,
--      DROP COLUMN IF EXISTS dispatch_date,          DROP COLUMN IF EXISTS dispatched_by,
--      DROP COLUMN IF EXISTS dispatch_mail_id,       DROP COLUMN IF EXISTS pod_no,
--      DROP COLUMN IF EXISTS reason_for_delay,
--      DROP COLUMN IF EXISTS dispatch_created_by,    DROP COLUMN IF EXISTS dispatch_created_date,
--      DROP COLUMN IF EXISTS disallowance_created_by,
--      DROP COLUMN IF EXISTS disallowance_created_date,
--      DROP COLUMN IF EXISTS insurance_current_status;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key = 'REPORT_INSURANCE');
--    DELETE FROM features WHERE feature_key = 'REPORT_INSURANCE';
--
--  Purely additive: no existing column is altered or dropped, no existing row
--  is rewritten. Legacy insurance rows keep insurance_current_status NULL,
--  which the UI reads as "pre-desk record, show the flat view". A backfill is
--  deliberately NOT attempted here — guessing which stage a historical row
--  reached would invent an audit trail.
-- =============================================================================

-- ── Stage 1: Preauthorise request ───────────────────────────────────────────
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS card_validity                DATE;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_communication_to_tpa VARCHAR(40);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_fax_no               VARCHAR(80);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_mail_id              VARCHAR(150);
--  Separate from the existing DATE-only pre_auth_date: the desk's SLA is
--  measured in hours, and a date alone cannot answer "was this faxed before the
--  TPA's 4pm cutoff".
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_applied_date         TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_requested_amount     BIGINT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_created_by           UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_created_date         TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_updated_by           UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_updated_date         TIMESTAMPTZ;

-- ── Stage 2: Preauthorise approval / rejection ──────────────────────────────
--  claim_no is the TPA's own docket number and is NOT the same as
--  pre_auth_number (ours) or policy_number (the insurer's master policy).
--  Encrypted + blind-index token, because the desk searches by it constantly
--  and an encrypted column cannot be searched directly.
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS claim_no                     TEXT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS claim_no_token               VARCHAR(64);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_approval_status      VARCHAR(40);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_date_of_approval     TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_communication_by_tpa VARCHAR(40);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_approve_fax_no       VARCHAR(80);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_approve_mail_id      VARCHAR(150);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_approved_limit       BIGINT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_rejection_reason     TEXT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_approval_created_by  UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_approval_created_date  TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_approval_updated_by  UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS preauth_approval_updated_date  TIMESTAMPTZ;

-- ── Stage 3: Enhancement request ────────────────────────────────────────────
--  This is the MANUAL enhancement, not preauth_enhancements (V196). That table
--  is keyed by nhcx_transaction_id and its states are gateway states driven by
--  async callbacks; this one changes state when a human reads a fax. See
--  WO-020 decision D-2 for why they are deliberately not merged.
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_type             VARCHAR(40);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_applied_date     TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_requested_amount BIGINT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_communication_to_tpa VARCHAR(40);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_fax_no           VARCHAR(80);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_mail_id          VARCHAR(150);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS reason_for_enhancement       TEXT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_created_by       UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_created_date     TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_updated_by       UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_updated_date     TIMESTAMPTZ;

-- ── Stage 4: Enhancement approval / rejection ───────────────────────────────
--  A SEPARATE approved limit rather than overwriting preauth_approved_limit.
--  Overwriting would destroy the answer to "what did they sanction originally",
--  which is exactly the question asked when a claim is short-paid.
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_approval_status  VARCHAR(40);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_date_of_approval TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_communication_by_tpa VARCHAR(40);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_approved_limit   BIGINT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_rejection_reason TEXT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_approval_created_by   UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_approval_created_date TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_approval_updated_by   UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS enhancement_approval_updated_date TIMESTAMPTZ;

-- ── Stage 5: Pre-dispatch document checklist ────────────────────────────────
--  JSONB rather than a child table: the checklist is a document manifest with
--  no money, no aggregation beyond "how many are short", and its row shape is
--  whatever the TPA asked for this week. Contrast the cheque receipts below.
--  Shape: {"checklists":[{"name":..,"toBeSubmit":..,"submitted":..,"nonSubmission":..}]}
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS checklist                    JSONB DEFAULT '{}'::jsonb;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS check_list_created_by        UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS check_list_created_date      TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS check_list_updated_by        UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS check_list_updated_date      TIMESTAMPTZ;

-- ── Stage 6: Dispatch ───────────────────────────────────────────────────────
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS mode_of_dispatch             VARCHAR(40);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS courier                      VARCHAR(80);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS dispatch_date                TIMESTAMPTZ;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS dispatched_by                VARCHAR(150);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS dispatch_mail_id             VARCHAR(150);
--  The consignment number is the only proof the hospital has that the docket
--  reached the TPA. Lost claims are argued with this number.
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS pod_no                       VARCHAR(100);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS reason_for_delay             TEXT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS dispatch_created_by          UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS dispatch_created_date        TIMESTAMPTZ;

-- ── Stage 7: Disallowance ───────────────────────────────────────────────────
--  Itemised deductions are NOT stored here. They are written to
--  charge_line_items.disallowed_amount through BillingOperationsService, the
--  method that already owns that column (WO-020 decision D-3). A second write
--  path to bill money is how two totals start disagreeing.
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS disallowance_created_by      UUID;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS disallowance_created_date    TIMESTAMPTZ;

-- ── The workflow stage itself ───────────────────────────────────────────────
--  VARCHAR, not the source system's INT ordinal. Ordinals in the database are
--  precisely why the source specification has to carry a "DO NOT reorder"
--  warning next to its enum table.
--  NO DEFAULT: a NULL here means "legacy row, created before the desk flow
--  existed". Defaulting every historical row to PREAUTHORISATION would assert
--  that ten thousand settled claims are sitting unsubmitted.
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS insurance_current_status     VARCHAR(50);

ALTER TABLE insurances DROP CONSTRAINT IF EXISTS ck_insurance_current_status;
ALTER TABLE insurances ADD CONSTRAINT ck_insurance_current_status
    CHECK (insurance_current_status IS NULL OR insurance_current_status IN (
        'PREAUTHORISATION',
        'PREAUTHORISATION_APPROVAL',
        'PREAUTHORISATION_REJECTED',
        'ENHANCEMENT_REQUEST',
        'ENHANCEMENT_APPROVAL',
        'ENHANCEMENT_REJECTED',
        'CHECK_LIST_ENTRY',
        'DISPATCH_ENTRY',
        'DISALLOWANCE_ENTRY'
    ));

ALTER TABLE insurances DROP CONSTRAINT IF EXISTS ck_insurance_preauth_approval_status;
ALTER TABLE insurances ADD CONSTRAINT ck_insurance_preauth_approval_status
    CHECK (preauth_approval_status IS NULL
           OR preauth_approval_status IN ('APPROVED', 'REJECTED'));

ALTER TABLE insurances DROP CONSTRAINT IF EXISTS ck_insurance_enhancement_approval_status;
ALTER TABLE insurances ADD CONSTRAINT ck_insurance_enhancement_approval_status
    CHECK (enhancement_approval_status IS NULL
           OR enhancement_approval_status IN ('APPROVED', 'REJECTED'));

--  The desk's primary screen is "everything at stage X for this branch, newest
--  first". Tenant first because every query is tenant-scoped.
CREATE INDEX IF NOT EXISTS ix_insurance_current_status
    ON insurances (tenant_id, insurance_current_status, created_at DESC);

--  Encrypted claim_no is unsearchable; this token is what makes it findable.
CREATE INDEX IF NOT EXISTS ix_insurance_claim_no_token
    ON insurances (tenant_id, claim_no_token);

--  GET /insurance?searchFromDate=&searchToDate= — the desk's default landing
--  query, currently a stub that ignores both parameters (fixed in WO-020).
CREATE INDEX IF NOT EXISTS ix_insurance_tenant_created
    ON insurances (tenant_id, created_at DESC);

-- ── Cheque / remittance receipts ────────────────────────────────────────────
--  A real table, not the source system's cheque_list JSON column (WO-020
--  decision D-1). These rows carry money that Reports 6, 9 and 10 must SUM,
--  need their own created_by so a mis-keyed cheque is attributable, and cannot
--  be constrained inside JSONB. Every other money path in this repo is a BIGINT
--  paise column in a real table.
CREATE TABLE IF NOT EXISTS insurance_cheque_receipts (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID,
    branch_id       UUID,
    insurance_id    UUID         NOT NULL,

    --  Cheque number or NEFT/RTGS UTR. Not encrypted: it is the insurer's
    --  banking reference, and finance reconciles against the bank statement by
    --  eye. It is still kept out of logs (see the work order's log-event table).
    cheque_no       VARCHAR(100) NOT NULL,
    cheque_date     DATE,
    drawn_on        VARCHAR(150),
    payable_at      VARCHAR(150),
    --  PAISE. Positive only — a negative receipt is a refund, which is a
    --  different transaction and does not belong in this table.
    amount          BIGINT       NOT NULL,
    authorised_by   VARCHAR(150),

    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_insurance_cheque_receipts PRIMARY KEY (id),
    CONSTRAINT fk_icr_insurance FOREIGN KEY (insurance_id)
        REFERENCES insurances (id),
    CONSTRAINT ck_icr_amount_positive CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS ix_icr_insurance
    ON insurance_cheque_receipts (insurance_id);
CREATE INDEX IF NOT EXISTS ix_icr_tenant_date
    ON insurance_cheque_receipts (tenant_id, cheque_date DESC);

-- ── REPORT_INSURANCE feature (WO-021) ───────────────────────────────────────
--  Seeded here rather than in WO-021 because one migration is cheaper than two
--  and the reports are meaningless without the desk data this migration adds.
--  Without this row every insurance report endpoint is 403 for everyone,
--  including the hospital admin who asked for them.
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'REPORT_INSURANCE', 'REPORTS',
       'Insurance MIS reports: pre-auth, enhancement, dispatch, disallowance, ageing', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

--  ADMIN receives the full catalogue by convention; HOSPITAL_ADMIN holds the
--  other nine REPORT_* keys, so it holds this one too. Deliberately NOT granted
--  to BILLING or RECEPTION: these reports expose every patient's claim value
--  and disallowance history across the branch.
--  NOTE: the roles table keys on `name`, not `role_key` — V001 defines
--  roles(id, name, description, ...). Matching V192's UPPER(r.name) form.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('ADMIN', 'HOSPITAL_ADMIN')
  AND f.feature_key = 'REPORT_INSURANCE'
ON CONFLICT DO NOTHING;
