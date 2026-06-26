CREATE TABLE IF NOT EXISTS bulk_import_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID,
    entity_type VARCHAR(255) NOT NULL,
    job_status VARCHAR(50) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    total_rows INT NOT NULL DEFAULT 0,
    created_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    errors JSONB,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by UUID,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bulk_import_jobs_tenant_id ON bulk_import_jobs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_bulk_import_jobs_branch_id ON bulk_import_jobs(branch_id);
