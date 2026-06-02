-- V055__create_template_tables.sql
-- Create template and department association tables for SCMC department logic alignment

CREATE TABLE IF NOT EXISTS template (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    templatename VARCHAR(255)  NOT NULL,
    template      TEXT,
    templatedata TEXT,
    templatetype  INT,
    sample_data   TEXT,
    status        SMALLINT      NOT NULL DEFAULT 1,
    created_by    UUID,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_template PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS department_categories (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    department_id UUID          NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    category_id   UUID          NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    CONSTRAINT pk_department_categories PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS department_template (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    department_id UUID          NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    template_id   UUID          NOT NULL REFERENCES template(id) ON DELETE CASCADE,
    CONSTRAINT pk_department_template PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS department_stock (
    department_id UUID          NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    access_type   INT           NOT NULL,
    CONSTRAINT pk_department_stock PRIMARY KEY (department_id, access_type)
);
