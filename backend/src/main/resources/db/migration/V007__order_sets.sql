-- V007__order_sets.sql
-- Clinical order set templates — named bundles of diagnostic + pharmacy orders
-- that a provider can apply to a patient in one action.

CREATE TABLE order_sets (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(150) NOT NULL,
    description     TEXT,
    is_outpatient   BOOLEAN      NOT NULL DEFAULT TRUE,
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_order_sets PRIMARY KEY (id)
);

CREATE TABLE order_set_items (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    order_set_id            UUID        NOT NULL,
    item_type               VARCHAR(20) NOT NULL
        CHECK (item_type IN ('DIAGNOSTIC','PHARMACY','PROCEDURE')),
    service_catalog_item_id UUID,
    diagnostic_type         SMALLINT,
    quantity                INTEGER     NOT NULL DEFAULT 1,
    instruction             VARCHAR(255),
    CONSTRAINT pk_osi    PRIMARY KEY (id),
    CONSTRAINT fk_osi_os FOREIGN KEY (order_set_id)
        REFERENCES order_sets(id) ON DELETE CASCADE
);

CREATE INDEX idx_os_name ON order_sets(name);
CREATE INDEX idx_osi_os  ON order_set_items(order_set_id);
