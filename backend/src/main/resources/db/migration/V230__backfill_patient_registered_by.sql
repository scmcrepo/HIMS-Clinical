-- Backfill created_by for patients uploaded via CSV bulk import or missing created_by
UPDATE patients p
SET created_by = (
    SELECT j.created_by
    FROM bulk_import_jobs j
    WHERE j.tenant_id = p.tenant_id
      AND j.entity_type = 'patient'
      AND j.created_by IS NOT NULL
    ORDER BY j.created_at DESC
    LIMIT 1
)
WHERE p.created_by IS NULL
  AND EXISTS (
    SELECT 1
    FROM bulk_import_jobs j
    WHERE j.tenant_id = p.tenant_id
      AND j.entity_type = 'patient'
      AND j.created_by IS NOT NULL
  );

-- Fallback for any remaining patients without created_by: assign tenant active user
UPDATE patients p
SET created_by = (
    SELECT u.id
    FROM users u
    WHERE (u.tenant_id = p.tenant_id OR u.tenant_id IS NULL)
      AND u.status = 1
    ORDER BY u.created_at ASC
    LIMIT 1
)
WHERE p.created_by IS NULL;
