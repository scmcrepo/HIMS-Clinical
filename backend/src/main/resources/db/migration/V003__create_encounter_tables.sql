-- V003__create_encounter_tables.sql  (PostgreSQL 16)
-- Tables: clinical_codes, clinical_providers, patients, appointment_slots,
--         appointments, clinical_encounters, room_categories, beds,
--         bed_occupancies, attachments, template_data
--
-- Also back-fills FKs on bills that require these tables.

-- ─────────────────────────────────────────────────────────
-- 1. CLINICAL_CODES (ICD / diagnosis codes)
-- ─────────────────────────────────────────────────────────
CREATE TABLE clinical_codes (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(20)  NOT NULL,
    description VARCHAR(300) NOT NULL,
    code_type   VARCHAR(10)  NOT NULL DEFAULT 'ICD10',
    status      SMALLINT     NOT NULL DEFAULT 1,
    CONSTRAINT pk_clinical_codes PRIMARY KEY (id),
    CONSTRAINT uq_clinical_codes_code UNIQUE (code)
);

-- ─────────────────────────────────────────────────────────
-- 2. CLINICAL_PROVIDERS (replaces consultants table)
-- ─────────────────────────────────────────────────────────
CREATE TABLE consultants (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    first_name          VARCHAR(60)  NOT NULL,
    last_name           VARCHAR(60)  NOT NULL,
    salutation          VARCHAR(15),
    consultant_type     SMALLINT,
    specialisation      VARCHAR(100),
    registration_no     VARCHAR(60),
    contact             VARCHAR(20),
    email               VARCHAR(120),
    department_id       UUID,
    user_id             UUID,
    photo_attachment_id UUID,
    is_available        BOOLEAN      NOT NULL DEFAULT TRUE,
    status              SMALLINT     NOT NULL DEFAULT 1,
    created_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by         UUID,
    modified_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_consultants PRIMARY KEY (id),
    CONSTRAINT fk_cp_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_cp_user FOREIGN KEY (user_id)       REFERENCES users(id)
);

COMMENT ON COLUMN consultants.user_id IS 'Linked system user; nullable — not all providers have a login';

CREATE INDEX idx_cp_status ON consultants(status);
CREATE INDEX idx_cp_dept   ON consultants(department_id);

-- ─────────────────────────────────────────────────────────
-- 3. PATIENTS
-- ─────────────────────────────────────────────────────────
CREATE TABLE patients (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    salutation              VARCHAR(10),
    first_name              VARCHAR(60)  NOT NULL,
    last_name               VARCHAR(40)  NOT NULL,
    gender                  SMALLINT     NOT NULL CHECK (gender IN (0, 1)),
    date_of_birth           DATE,
    estimated_date_of_birth DATE         NOT NULL,
    contact_number          VARCHAR(15),
    address                 TEXT,
    primary_provider_id     UUID,
    area_id                 UUID,
    category_id             UUID,
    number_sequence_suffix  VARCHAR(20),
    is_clinical_trial       BOOLEAN      NOT NULL DEFAULT FALSE,
    -- JSONB: binary JSON, GIN-indexable, supports @>, ?, etc.
    pediatric_data          JSONB,
    template_data           JSONB,
    status                  SMALLINT     NOT NULL DEFAULT 1,
    created_by              UUID,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by             UUID,
    modified_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_patients PRIMARY KEY (id),
    CONSTRAINT fk_patients_provider FOREIGN KEY (primary_provider_id) REFERENCES consultants(id),
    CONSTRAINT fk_patients_area     FOREIGN KEY (area_id)             REFERENCES areas(id),
    CONSTRAINT fk_patients_category FOREIGN KEY (category_id)         REFERENCES patient_categories(id)
);

COMMENT ON COLUMN patients.gender                  IS '0=MALE, 1=FEMALE';
COMMENT ON COLUMN patients.estimated_date_of_birth IS 'Always required; used for age calculation when exact DOB is unknown';

CREATE INDEX idx_patients_contact  ON patients(contact_number);
CREATE INDEX idx_patients_status   ON patients(status);
-- PostgreSQL supports expression indexes for case-insensitive name search
CREATE INDEX idx_patients_name ON patients(lower(first_name), lower(last_name));

-- Now add the patients FK to bills (bills table was created in V002)
ALTER TABLE bills
    ADD CONSTRAINT fk_bills_patient FOREIGN KEY (patient_id) REFERENCES patients(id);

-- ─────────────────────────────────────────────────────────
-- 4. APPOINTMENT_SLOTS
-- ─────────────────────────────────────────────────────────
CREATE TABLE appointment_slots (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    consultant_id    UUID        NOT NULL,
    day_of_week      SMALLINT    NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    from_time        VARCHAR(30) NOT NULL,
    to_time          VARCHAR(30) NOT NULL,
    number_of_patients INTEGER     NOT NULL DEFAULT 10,
    concat_time        VARCHAR(60) NOT NULL,
    status           SMALLINT    NOT NULL DEFAULT 1,
    created_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by      UUID,
    modified_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_appointment_slots PRIMARY KEY (id),
    CONSTRAINT fk_as_consultant FOREIGN KEY (consultant_id) REFERENCES consultants(id),
    CONSTRAINT uq_as_provider_day_time UNIQUE (consultant_id, day_of_week, concat_time)
);

COMMENT ON COLUMN appointment_slots.day_of_week    IS '0=MON … 6=SUN';
COMMENT ON COLUMN appointment_slots.concat_time IS 'from_time||to_time string; used for duplicate detection';

CREATE INDEX idx_as_provider_day ON appointment_slots(consultant_id, day_of_week);

-- ─────────────────────────────────────────────────────────
-- 5. APPOINTMENTS
-- ─────────────────────────────────────────────────────────
CREATE TABLE appointments (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id       UUID,
    provider_id      UUID        NOT NULL,
    slot_id          UUID,
    appointment_status SMALLINT    NOT NULL DEFAULT 0 CHECK (appointment_status IN (0, 1, 2, 3)),
    status             SMALLINT    NOT NULL DEFAULT 1,
    appointment_date DATE        NOT NULL,
    appointment_time TIME        NOT NULL,
    visit_mode       SMALLINT    NOT NULL DEFAULT 0,
    notes            TEXT,
    created_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by      UUID,
    modified_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_appointments PRIMARY KEY (id),
    CONSTRAINT fk_apt_patient  FOREIGN KEY (patient_id)  REFERENCES patients(id),
    CONSTRAINT fk_apt_provider FOREIGN KEY (provider_id) REFERENCES consultants(id),
    CONSTRAINT fk_apt_slot     FOREIGN KEY (slot_id)     REFERENCES appointment_slots(id)
);

COMMENT ON COLUMN appointments.appointment_status     IS '0=BOOKED, 1=RESCHEDULED, 2=CHECKED_IN, 3=CANCELLED';
COMMENT ON COLUMN appointments.visit_mode IS '0=WALK_IN, 1=APPOINTMENT, 2=TELE_CONSULT';

CREATE INDEX idx_apt_date_provider ON appointments(appointment_date, provider_id);
CREATE INDEX idx_apt_patient       ON appointments(patient_id);
CREATE INDEX idx_apt_status        ON appointments(appointment_status);
CREATE INDEX idx_apt_row_status    ON appointments(status);

-- ─────────────────────────────────────────────────────────
-- 6. CLINICAL_ENCOUNTERS (replaces visit table)
-- ─────────────────────────────────────────────────────────
CREATE TABLE clinical_encounters (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    patient_id            UUID        NOT NULL,
    primary_provider_id   UUID        NOT NULL,
    appointment_id        UUID,
    encounter_type        SMALLINT    NOT NULL CHECK (encounter_type IN (0, 1)),
    encounter_status      SMALLINT    NOT NULL DEFAULT 0 CHECK (encounter_status BETWEEN 0 AND 3),
    status                SMALLINT    NOT NULL DEFAULT 1,
    visit_mode            SMALLINT    NOT NULL DEFAULT 0,
    started_at            TIMESTAMPTZ NOT NULL,
    checked_in_at         TIME,
    discharged_at         TIMESTAMPTZ,
    diagnosis             TEXT,
    diagnosis_code_id     UUID,
    last_bed_id           UUID,
    has_bed               BOOLEAN     NOT NULL DEFAULT FALSE,
    has_draft_bill        BOOLEAN     NOT NULL DEFAULT FALSE,
    casesheet_recorded_at TIMESTAMPTZ,
    -- JSONB for vital signs and multi-consultant share map — supports GIN index queries
    vital_data            JSONB,
    consultant_share_map  JSONB,
    is_cancelled          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by           UUID,
    modified_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_clinical_encounters PRIMARY KEY (id),
    CONSTRAINT fk_ce_patient     FOREIGN KEY (patient_id)          REFERENCES patients(id),
    CONSTRAINT fk_ce_provider    FOREIGN KEY (primary_provider_id) REFERENCES consultants(id),
    CONSTRAINT fk_ce_appointment FOREIGN KEY (appointment_id)      REFERENCES appointments(id),
    CONSTRAINT fk_ce_code        FOREIGN KEY (diagnosis_code_id)   REFERENCES clinical_codes(id)
);

COMMENT ON COLUMN clinical_encounters.encounter_type IS '0=OUTPATIENT, 1=INPATIENT';
COMMENT ON COLUMN clinical_encounters.encounter_status         IS '0=CHECKED_IN, 1=CONSULTATION_STARTED, 2=CASESHEET_RECORDED, 3=BILLING_DONE';

CREATE INDEX idx_ce_patient        ON clinical_encounters(patient_id);
CREATE INDEX idx_ce_started_at     ON clinical_encounters(started_at);
CREATE INDEX idx_ce_status         ON clinical_encounters(encounter_status);
CREATE INDEX idx_ce_row_status      ON clinical_encounters(status);
CREATE INDEX idx_ce_provider_date  ON clinical_encounters(primary_provider_id, started_at);
-- GIN index on vital_data JSONB for fast key-based queries
CREATE INDEX idx_ce_vital_data_gin ON clinical_encounters USING gin(vital_data);

-- Back-fill encounter FK on bills
ALTER TABLE bills
    ADD CONSTRAINT fk_bills_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_encounters(id);

-- ─────────────────────────────────────────────────────────
-- 7. ROOM_CATEGORIES (replaces bed_types table)
-- ─────────────────────────────────────────────────────────
CREATE TABLE room_categories (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                    VARCHAR(100) NOT NULL,
    billing_cycle           SMALLINT     NOT NULL CHECK (billing_cycle IN (0, 1)),
    service_catalog_item_id UUID,
    status                  SMALLINT     NOT NULL DEFAULT 1,
    created_by              UUID,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by             UUID,
    modified_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_room_categories PRIMARY KEY (id),
    CONSTRAINT uq_room_categories_name UNIQUE (name)
);

COMMENT ON COLUMN room_categories.billing_cycle IS '0=HOURLY, 1=DAILY';

-- ─────────────────────────────────────────────────────────
-- 8. BEDS
-- ─────────────────────────────────────────────────────────
CREATE TABLE beds (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    name             VARCHAR(40) NOT NULL,
    room_category_id UUID        NOT NULL,
    bed_status       SMALLINT    NOT NULL DEFAULT 1 CHECK (bed_status IN (0, 1, 2)),
    floor            VARCHAR(10),
    ward             VARCHAR(40),
    status           SMALLINT    NOT NULL DEFAULT 1,
    created_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by      UUID,
    modified_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_beds PRIMARY KEY (id),
    CONSTRAINT fk_beds_category FOREIGN KEY (room_category_id) REFERENCES room_categories(id)
);

COMMENT ON COLUMN beds.bed_status IS '0=ALLOCATED, 1=AVAILABLE, 2=MAINTENANCE';

CREATE INDEX idx_beds_status   ON beds(bed_status);
CREATE INDEX idx_beds_category ON beds(room_category_id);

-- ─────────────────────────────────────────────────────────
-- 9. BED_OCCUPANCIES (replaces bed_allocation table)
--    Allocation persists for the entire stay; row closed on transfer/vacate.
--    PESSIMISTIC_WRITE lock at application layer via SELECT … FOR UPDATE on beds.
-- ─────────────────────────────────────────────────────────
CREATE TABLE bed_occupancies (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    bed_id        UUID        NOT NULL,
    encounter_id  UUID        NOT NULL,
    bill_id       UUID,
    from_datetime TIMESTAMPTZ NOT NULL,
    to_datetime   TIMESTAMPTZ,
    status        SMALLINT    NOT NULL DEFAULT 1 CHECK (status IN (0, 1)),
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_bed_occupancies PRIMARY KEY (id),
    CONSTRAINT fk_bo_bed       FOREIGN KEY (bed_id)       REFERENCES beds(id),
    CONSTRAINT fk_bo_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_encounters(id),
    CONSTRAINT fk_bo_bill      FOREIGN KEY (bill_id)      REFERENCES bills(id)
);

COMMENT ON COLUMN bed_occupancies.status IS '1=active, 0=closed';

CREATE INDEX idx_bo_bed       ON bed_occupancies(bed_id);
CREATE INDEX idx_bo_encounter ON bed_occupancies(encounter_id);
CREATE INDEX idx_bo_from      ON bed_occupancies(from_datetime);

-- ─────────────────────────────────────────────────────────
-- 10. ATTACHMENTS
--     Files stored on filesystem; only metadata in DB.
--     Content bytes removed from DB — stored at file_path.
-- ─────────────────────────────────────────────────────────
CREATE TABLE attachments (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    encounter_id    UUID,
    patient_id      UUID,
    provider_id     UUID,
    attachment_type SMALLINT     NOT NULL,
    status          SMALLINT     NOT NULL DEFAULT 1,
    category        VARCHAR(40),
    file_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    content_type    VARCHAR(80),
    meta_data       TEXT,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_attachments PRIMARY KEY (id)
);

CREATE INDEX idx_att_encounter ON attachments(encounter_id);
CREATE INDEX idx_att_patient   ON attachments(patient_id);
CREATE INDEX idx_att_type      ON attachments(attachment_type);

-- ─────────────────────────────────────────────────────────
-- 11. TEMPLATE_DATA (casesheet / discharge summary — content as JSONB)
-- ─────────────────────────────────────────────────────────
CREATE TABLE template_data (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    encounter_id  UUID,
    template_type VARCHAR(60) NOT NULL,
    content       JSONB,
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_template_data PRIMARY KEY (id)
);

CREATE INDEX idx_td_encounter        ON template_data(encounter_id);
CREATE INDEX idx_td_content_gin      ON template_data USING gin(content);
