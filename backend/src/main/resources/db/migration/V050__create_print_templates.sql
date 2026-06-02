-- V050__create_print_templates.sql
-- Create database table for print templates master data

CREATE TABLE print_templates (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    name          VARCHAR(100)  NOT NULL,
    document_type VARCHAR(50)   NOT NULL,
    content       TEXT,
    is_default    BOOLEAN       NOT NULL DEFAULT FALSE,
    status        SMALLINT      NOT NULL DEFAULT 1,
    created_by    UUID,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    modified_by   UUID,
    modified_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_print_templates PRIMARY KEY (id)
);

COMMENT ON TABLE print_templates IS 'HTML templates for printed reports, bills, receipts, etc.';
