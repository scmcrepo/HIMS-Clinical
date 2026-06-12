-- ============================================================
-- V096 — Master Data: Recreate Areas Table
-- ============================================================

CREATE TABLE IF NOT EXISTS areas (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    pin_code    VARCHAR(10),
    status      SMALLINT     NOT NULL DEFAULT 1,    -- EntityStatus ordinal: 0=INACTIVE, 1=ACTIVE, 2=DELETED
    created_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by UUID,
    modified_at TIMESTAMPTZ
);
