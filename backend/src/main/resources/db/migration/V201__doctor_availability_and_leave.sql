CREATE TABLE consultant_leaves (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID,
    branch_id       UUID,
    consultant_id   UUID          NOT NULL,
    start_date      DATE          NOT NULL,
    end_date        DATE          NOT NULL,
    reason          VARCHAR(255),
    status          SMALLINT      NOT NULL DEFAULT 1, -- 1 = ACTIVE, 2 = DELETED (corresponds to EntityStatus.ACTIVE/DELETED)
    created_by      UUID,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    
    CONSTRAINT pk_consultant_leaves PRIMARY KEY (id),
    CONSTRAINT fk_cl_consultant FOREIGN KEY (consultant_id) REFERENCES consultants(id)
);

CREATE INDEX idx_cl_consultant ON consultant_leaves(consultant_id);
CREATE INDEX idx_cl_range ON consultant_leaves(consultant_id, start_date, end_date);
CREATE INDEX idx_cl_status ON consultant_leaves(status);
