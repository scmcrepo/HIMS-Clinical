-- V051__create_packages_table.sql
-- Add quantitative (editable quantity) field to charges table
ALTER TABLE charges ADD COLUMN quantitative BOOLEAN NOT NULL DEFAULT FALSE;

-- Create packages table to store package configurations (sub-charges or categories included/excluded)
CREATE TABLE packages (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    package_id      UUID        NOT NULL REFERENCES charges(id) ON DELETE CASCADE,
    charge_id       UUID        REFERENCES charges(id) ON DELETE CASCADE,
    charge_category UUID        REFERENCES categories(id) ON DELETE CASCADE,
    quantity        INTEGER     NOT NULL DEFAULT 0,
    amount          BIGINT      NOT NULL DEFAULT 0,
    mode            BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_packages PRIMARY KEY (id)
);

CREATE INDEX idx_packages_package ON packages(package_id);
