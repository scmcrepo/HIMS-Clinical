-- =============================================================================
--  V191 — Policy discovery and coverage benefits  (WO-013 / Screens 1.2, 1.3, 2.1)
--
--  Screen 2.1 displays thirteen benefit values — sum insured, utilisation,
--  room-rent and ICU caps, co-pay, deductible, PED waiting period, exclusions.
--  None of them had anywhere to live: nhcx_transactions stored only
--  approved_amount. They go in patient_policy_coverages rather than onto the
--  insurance row because a policy is re-checked per encounter and each check is
--  a point-in-time snapshot the hospital may later have to justify to the payer.
--  Overwriting the previous answer would destroy that evidence.
--
--  MONEY IS STORED IN PAISE as BIGINT, matching the existing billing columns.
--  Rupees in a DOUBLE is how co-pay splits end up off by a few paise per claim
--  and irreconcilable at month end.
--
--  The policy holder's member id is a personal identifier, so it is encrypted
--  with a blind-index token beside it — the same pattern as abha_linkages.
--
--  ROLLBACK:
--    DROP TABLE IF EXISTS policy_benefit_exclusions, patient_policy_coverages,
--                         discovered_policies;
--    ALTER TABLE insurances DROP COLUMN IF EXISTS member_id,
--                           DROP COLUMN IF EXISTS member_id_token,
--                           DROP COLUMN IF EXISTS tpa_name,
--                           DROP COLUMN IF EXISTS policy_type;
--    DELETE FROM role_features WHERE feature_id IN
--      (SELECT id FROM features WHERE feature_key = 'POLICY_DISCOVERY');
--    DELETE FROM features WHERE feature_key = 'POLICY_DISCOVERY';
--  Purely additive; no existing column is altered or dropped.
-- =============================================================================

-- ── Screen 1.3: fields the manual-registration form needs ────────────────────
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS member_id       TEXT;
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS member_id_token VARCHAR(64);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS tpa_name        VARCHAR(160);
ALTER TABLE insurances ADD COLUMN IF NOT EXISTS policy_type     VARCHAR(24);

CREATE INDEX IF NOT EXISTS ix_insurance_member_token
    ON insurances (tenant_id, member_id_token);

-- ── Screen 1.2: policies returned by an NHCX discovery call ──────────────────
--  These are the payer's assertion, not ours. They are kept separate from
--  `insurances` until a human links one, so an unverified discovery result can
--  never be mistaken for a policy the hospital has accepted.
CREATE TABLE IF NOT EXISTS discovered_policies (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    branch_id            UUID,
    patient_id           UUID         NOT NULL,

    correlation_id       VARCHAR(64)  NOT NULL,
    payer_code           VARCHAR(60)  NOT NULL,
    payer_name           VARCHAR(160),
    tpa_name             VARCHAR(160),

    policy_number        TEXT,
    policy_number_token  VARCHAR(64),
    member_id            TEXT,
    member_id_token      VARCHAR(64),

    -- INDIVIDUAL | FAMILY_FLOATER | PM_JAY | GROUP
    policy_type          VARCHAR(24),
    policy_start_date    DATE,
    policy_end_date      DATE,

    primary_insured_name TEXT,
    -- SELF | SPOUSE | CHILD | PARENT | OTHER
    relationship         VARCHAR(20),

    -- Set once a human links this discovery result to a real insurance row.
    linked_insurance_id  UUID,
    linked_at            TIMESTAMPTZ,

    status               SMALLINT     NOT NULL DEFAULT 1,
    created_by           UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by          UUID,
    modified_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_discovered_policies PRIMARY KEY (id),
    CONSTRAINT fk_discovered_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_discovered_policy_type CHECK (policy_type IS NULL OR policy_type IN
        ('INDIVIDUAL', 'FAMILY_FLOATER', 'PM_JAY', 'GROUP')),
    CONSTRAINT ck_discovered_relationship CHECK (relationship IS NULL OR relationship IN
        ('SELF', 'SPOUSE', 'CHILD', 'PARENT', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS ix_discovered_patient
    ON discovered_policies (tenant_id, patient_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_discovered_correlation
    ON discovered_policies (correlation_id);
CREATE INDEX IF NOT EXISTS ix_discovered_policy_token
    ON discovered_policies (tenant_id, policy_number_token);

-- ── Screen 2.1: the point-in-time coverage snapshot ──────────────────────────
CREATE TABLE IF NOT EXISTS patient_policy_coverages (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id              UUID         NOT NULL,
    branch_id              UUID,
    patient_id             UUID         NOT NULL,
    insurance_id           UUID,
    encounter_id           UUID,

    correlation_id         VARCHAR(64)  NOT NULL,
    payer_code             VARCHAR(60)  NOT NULL,

    -- ACTIVE | EXPIRED | LAPSED | SUSPENDED | UNKNOWN
    policy_status          VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',

    -- All amounts in PAISE.
    sum_insured_paise      BIGINT,
    utilised_paise         BIGINT,
    balance_paise          BIGINT,
    room_rent_cap_paise    BIGINT,
    icu_cap_paise          BIGINT,
    deductible_paise       BIGINT,

    -- Room eligibility is often a category ("Single Private AC") rather than an
    -- amount, and payers send either or both.
    room_category          VARCHAR(120),

    -- Basis points, not percent: 10% is 1000. Percent as an integer cannot
    -- express the 7.5% co-pay that several retail policies actually carry.
    co_pay_basis_points    INTEGER,

    ped_waiting_months     INTEGER,
    ped_waiting_satisfied  BOOLEAN,

    checked_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    status                 SMALLINT     NOT NULL DEFAULT 1,
    created_by             UUID,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by            UUID,
    modified_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_patient_policy_coverages PRIMARY KEY (id),
    CONSTRAINT fk_coverage_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_coverage_status CHECK (policy_status IN
        ('ACTIVE', 'EXPIRED', 'LAPSED', 'SUSPENDED', 'UNKNOWN')),
    -- A negative balance or a co-pay above 100% is a parse error, not a policy.
    CONSTRAINT ck_coverage_amounts CHECK (
        (sum_insured_paise   IS NULL OR sum_insured_paise   >= 0) AND
        (utilised_paise      IS NULL OR utilised_paise      >= 0) AND
        (balance_paise       IS NULL OR balance_paise       >= 0) AND
        (co_pay_basis_points IS NULL OR (co_pay_basis_points BETWEEN 0 AND 10000))
    )
);

CREATE INDEX IF NOT EXISTS ix_coverage_patient
    ON patient_policy_coverages (tenant_id, patient_id, checked_at DESC);
CREATE INDEX IF NOT EXISTS ix_coverage_encounter
    ON patient_policy_coverages (tenant_id, encounter_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_coverage_correlation
    ON patient_policy_coverages (tenant_id, correlation_id);

-- ── Screen 2.1: exclusions and restrictions, one row each ────────────────────
CREATE TABLE IF NOT EXISTS policy_benefit_exclusions (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    branch_id     UUID,
    coverage_id   UUID         NOT NULL,

    -- EXCLUSION | RESTRICTION | SUB_LIMIT
    kind          VARCHAR(20)  NOT NULL DEFAULT 'EXCLUSION',
    code          VARCHAR(60),
    description   TEXT         NOT NULL,
    limit_paise   BIGINT,

    status        SMALLINT     NOT NULL DEFAULT 1,
    created_by    UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_policy_benefit_exclusions PRIMARY KEY (id),
    CONSTRAINT fk_exclusion_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_exclusion_coverage FOREIGN KEY (coverage_id)
        REFERENCES patient_policy_coverages(id) ON DELETE CASCADE,
    CONSTRAINT ck_exclusion_kind CHECK (kind IN ('EXCLUSION', 'RESTRICTION', 'SUB_LIMIT'))
);

CREATE INDEX IF NOT EXISTS ix_exclusion_coverage
    ON policy_benefit_exclusions (coverage_id);

-- ── Feature key, seeded per tenant ───────────────────────────────────────────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), v.feature_key, v.module, v.description, t.id
FROM (VALUES
    ('POLICY_DISCOVERY', 'CLAIM', 'Discover and verify patient insurance policies via NHCX')
) AS v(feature_key, module, description)
CROSS JOIN tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- Grant to the same roles that already hold NHCX_CLAIMS, so a hospital that can
-- file a claim can also check the coverage that claim depends on.
INSERT INTO role_features (role_id, feature_id)
SELECT DISTINCT rf.role_id, f_new.id
FROM role_features rf
JOIN features f_old ON f_old.id = rf.feature_id AND f_old.feature_key = 'NHCX_CLAIMS'
JOIN features f_new ON f_new.tenant_id = f_old.tenant_id
                   AND f_new.feature_key = 'POLICY_DISCOVERY'
ON CONFLICT DO NOTHING;
