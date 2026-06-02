-- V001__create_base_schema.sql  (PostgreSQL 16)
-- Base shared tables: roles, features, role_features, departments, account_units,
-- users, user_roles, user_departments, user_account_units,
-- areas, patient_categories, sequence_generators, sequence_numbers,
-- system_settings, sms_templates, hospital_profile

-- PostgreSQL: enable pgcrypto for gen_random_uuid() if not already enabled
-- (available by default in PostgreSQL 13+; this is a no-op if already present)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─────────────────────────────────────────────────────────
-- 1. ROLES
-- ─────────────────────────────────────────────────────────
CREATE TABLE roles (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    status      SMALLINT     NOT NULL DEFAULT 1 CHECK (status IN (0, 1, 2)),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

COMMENT ON COLUMN roles.status IS '0=inactive, 1=active, 2=deleted';

-- ─────────────────────────────────────────────────────────
-- 2. FEATURES (permission feature keys — 114 keys from legacy Update.sql)
-- ─────────────────────────────────────────────────────────
CREATE TABLE features (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    feature_key VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    module      VARCHAR(60),
    CONSTRAINT pk_features PRIMARY KEY (id),
    CONSTRAINT uq_features_key UNIQUE (feature_key)
);

-- ─────────────────────────────────────────────────────────
-- 3. ROLE_FEATURES (many-to-many)
-- ─────────────────────────────────────────────────────────
CREATE TABLE role_features (
    role_id    UUID NOT NULL,
    feature_id UUID NOT NULL,
    CONSTRAINT pk_role_features PRIMARY KEY (role_id, feature_id),
    CONSTRAINT fk_rf_role    FOREIGN KEY (role_id)    REFERENCES roles(id)    ON DELETE CASCADE,
    CONSTRAINT fk_rf_feature FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────────────────
-- 4. DEPARTMENTS
-- ─────────────────────────────────────────────────────────
CREATE TABLE departments (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    code            VARCHAR(20),
    department_type VARCHAR(40),
    stock_access    VARCHAR(20),
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_departments PRIMARY KEY (id),
    CONSTRAINT uq_departments_name UNIQUE (name)
);

-- ─────────────────────────────────────────────────────────
-- 5. ACCOUNT_UNITS
-- ─────────────────────────────────────────────────────────
CREATE TABLE account_units (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    code        VARCHAR(20),
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_account_units PRIMARY KEY (id),
    CONSTRAINT uq_account_units_name UNIQUE (name)
);

-- ─────────────────────────────────────────────────────────
-- 6. USERS
-- ─────────────────────────────────────────────────────────
CREATE TABLE users (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    username              VARCHAR(20)  NOT NULL,
    password_hash         VARCHAR(72)  NOT NULL,
    first_name            VARCHAR(50)  NOT NULL,
    last_name             VARCHAR(30)  NOT NULL,
    email                 VARCHAR(120),
    status                SMALLINT     NOT NULL DEFAULT 1,
    account_locked        BOOLEAN      NOT NULL DEFAULT FALSE,
    department_visibility SMALLINT     NOT NULL DEFAULT 0,
    speech_language       VARCHAR(10)  NOT NULL DEFAULT 'en-IN',
    text_auto_suggest     BOOLEAN      NOT NULL DEFAULT TRUE,
    show_casesheet        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- PostgreSQL native JSONB: indexed, binary, faster than MySQL JSON
    user_rights           JSONB,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username)
);

COMMENT ON COLUMN users.password_hash IS 'BCrypt hash; never returned to client';
COMMENT ON COLUMN users.department_visibility IS '0=own_dept, 1=all';

CREATE INDEX idx_users_status ON users(status);

-- ─────────────────────────────────────────────────────────
-- 7. USER_ROLES / USER_DEPARTMENTS / USER_ACCOUNT_UNITS
-- ─────────────────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE user_departments (
    user_id       UUID NOT NULL,
    department_id UUID NOT NULL,
    CONSTRAINT pk_user_departments PRIMARY KEY (user_id, department_id),
    CONSTRAINT fk_ud_user FOREIGN KEY (user_id)       REFERENCES users(id)       ON DELETE CASCADE,
    CONSTRAINT fk_ud_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE
);

CREATE TABLE user_account_units (
    user_id         UUID NOT NULL,
    account_unit_id UUID NOT NULL,
    CONSTRAINT pk_user_account_units PRIMARY KEY (user_id, account_unit_id),
    CONSTRAINT fk_uau_user FOREIGN KEY (user_id)         REFERENCES users(id)         ON DELETE CASCADE,
    CONSTRAINT fk_uau_au   FOREIGN KEY (account_unit_id) REFERENCES account_units(id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────────────────
-- 8. AREAS
-- ─────────────────────────────────────────────────────────
CREATE TABLE areas (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    pin_code   VARCHAR(10),
    status     SMALLINT     NOT NULL DEFAULT 1,
    code       VARCHAR(20),
    created_by UUID,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by UUID,
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_areas PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────────────────
-- 9. PATIENT_CATEGORIES
-- ─────────────────────────────────────────────────────────
CREATE TABLE patient_categories (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(80) NOT NULL,
    prefix_string VARCHAR(10),
    status        SMALLINT    NOT NULL DEFAULT 1,
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_patient_categories PRIMARY KEY (id),
    CONSTRAINT uq_patient_categories_name UNIQUE (name)
);

-- ─────────────────────────────────────────────────────────
-- 10. SEQUENCE_GENERATORS (replaces prefixes table)
--     Row-level PESSIMISTIC locking via SELECT … FOR UPDATE
-- ─────────────────────────────────────────────────────────
CREATE TABLE sequence_generators (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    prefix_string       VARCHAR(20) NOT NULL,
    document_type       SMALLINT    NOT NULL,
    reset_policy        SMALLINT    NOT NULL DEFAULT 0,
    is_activated        BOOLEAN     NOT NULL DEFAULT FALSE,
    current_counter     BIGINT      NOT NULL DEFAULT 1,
    current_fiscal_year SMALLINT,
    activated_at        DATE,
    deactivated_at      DATE,
    created_by          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_sequence_generators PRIMARY KEY (id)
);

COMMENT ON COLUMN sequence_generators.document_type IS 'DocumentType enum ordinal';
COMMENT ON COLUMN sequence_generators.reset_policy   IS '0=never, 1=fiscal_year, 2=calendar_year';

CREATE INDEX idx_sg_document_type_active ON sequence_generators(document_type, is_activated);

-- ─────────────────────────────────────────────────────────
-- 11. SEQUENCE_NUMBERS
-- ─────────────────────────────────────────────────────────
CREATE TABLE sequence_numbers (
    entity_id       UUID        NOT NULL,
    formatted_value VARCHAR(40) NOT NULL,
    CONSTRAINT pk_sequence_numbers PRIMARY KEY (entity_id)
);

COMMENT ON COLUMN sequence_numbers.entity_id IS 'PK of the owning entity (bill, patient, etc.)';

-- ─────────────────────────────────────────────────────────
-- 12. SYSTEM_SETTINGS
-- ─────────────────────────────────────────────────────────
CREATE TABLE system_settings (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    setting_type  VARCHAR(60) NOT NULL,
    setting_key   VARCHAR(80) NOT NULL,
    setting_value TEXT,
    description   VARCHAR(255),
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_system_settings PRIMARY KEY (id),
    CONSTRAINT uq_system_settings_key UNIQUE (setting_type, setting_key)
);

-- ─────────────────────────────────────────────────────────
-- 13. SMS_TEMPLATES
-- ─────────────────────────────────────────────────────────
CREATE TABLE sms_templates (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    template_key VARCHAR(60) NOT NULL,
    body         TEXT        NOT NULL,
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_sms_templates PRIMARY KEY (id),
    CONSTRAINT uq_sms_templates_key UNIQUE (template_key)
);

COMMENT ON COLUMN sms_templates.body IS 'May contain $variableName$ placeholders';

-- ─────────────────────────────────────────────────────────
-- 14. HOSPITAL_PROFILE
-- ─────────────────────────────────────────────────────────
CREATE TABLE hospital_profile (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    address     TEXT,
    phone       VARCHAR(20),
    email       VARCHAR(120),
    logo_path   VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hospital_profile PRIMARY KEY (id)
);
