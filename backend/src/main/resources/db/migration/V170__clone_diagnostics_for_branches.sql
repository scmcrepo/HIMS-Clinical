-- V170__clone_diagnostics_for_branches.sql
-- 1. Sync mismatched diagnostic_templates.charge_id to match service_catalog_items.id
UPDATE diagnostic_templates dt
SET charge_id = sci.id,
    modified_at = NOW()
FROM service_catalog_items sci
WHERE UPPER(TRIM(dt.name)) = UPPER(TRIM(sci.name))
  AND dt.tenant_id = sci.tenant_id
  AND (dt.branch_id = sci.branch_id OR (dt.branch_id IS NULL AND sci.branch_id IS NULL))
  AND dt.charge_id != sci.id;

-- 2. Clone specimens and diagnostic templates from default branch to non-default branches
DO $$
DECLARE
    rec RECORD;
    temp_rec RECORD;
    spec_rec RECORD;
    new_branch_id UUID;
    default_branch_id UUID;
    new_specimen_id UUID;
    new_template_id UUID;
    specimen_map jsonb; 
BEGIN
    -- Loop over all non-default branches
    FOR rec IN 
        SELECT b.id AS branch_id, b.tenant_id, db.id AS default_branch_id
        FROM branches b
        JOIN branches db ON db.tenant_id = b.tenant_id AND db.is_default = true
        WHERE b.is_default = false
    LOOP
        new_branch_id := rec.branch_id;
        default_branch_id := rec.default_branch_id;
        specimen_map := '{}'::jsonb;

        -- A. Clone specimens and populate specimen ID mapping
        FOR spec_rec IN 
            SELECT id, name, description, tenant_id, created_by, created_at, modified_at
            FROM specimens 
            WHERE branch_id = default_branch_id AND tenant_id = rec.tenant_id
        LOOP
            -- Check if specimen already exists to avoid duplicates
            SELECT id INTO new_specimen_id 
            FROM specimens 
            WHERE branch_id = new_branch_id AND tenant_id = rec.tenant_id AND name = spec_rec.name;

            IF new_specimen_id IS NULL THEN
                new_specimen_id := gen_random_uuid();
                INSERT INTO specimens (id, tenant_id, branch_id, name, description, created_by, created_at, modified_at)
                VALUES (new_specimen_id, spec_rec.tenant_id, new_branch_id, spec_rec.name, spec_rec.description, spec_rec.created_by, spec_rec.created_at, spec_rec.modified_at);
            END IF;

            specimen_map := specimen_map || jsonb_build_object(spec_rec.id::text, new_specimen_id::text);
        END LOOP;

        -- B. Clone diagnostic templates
        FOR temp_rec IN 
            SELECT * 
            FROM diagnostic_templates 
            WHERE branch_id = default_branch_id AND tenant_id = rec.tenant_id
        LOOP
            -- Check if template already exists to avoid duplicates
            IF NOT EXISTS (
                SELECT 1 FROM diagnostic_templates 
                WHERE branch_id = new_branch_id AND tenant_id = rec.tenant_id AND name = temp_rec.name
            ) THEN
                new_template_id := gen_random_uuid();
                new_specimen_id := (specimen_map->>(temp_rec.specimen_id::text))::uuid;

                INSERT INTO diagnostic_templates (
                    id, tenant_id, branch_id, name, diagnostic_type, format, charge_id, specimen_id, department_id, 
                    order_number, header, method, reference_range, unit, lab_template_type, template_html, 
                    status, created_by, created_at, modified_at
                )
                VALUES (
                    new_template_id, temp_rec.tenant_id, new_branch_id, temp_rec.name, temp_rec.diagnostic_type, temp_rec.format, 
                    (
                        SELECT sci.id FROM service_catalog_items sci 
                        WHERE UPPER(TRIM(sci.name)) = UPPER(TRIM(temp_rec.name)) 
                          AND sci.tenant_id = temp_rec.tenant_id 
                          AND (sci.branch_id = new_branch_id OR sci.branch_id IS NULL)
                        LIMIT 1
                    ), 
                    new_specimen_id, temp_rec.department_id, 
                    temp_rec.order_number, temp_rec.header, temp_rec.method, temp_rec.reference_range, temp_rec.unit, temp_rec.lab_template_type, temp_rec.template_html, 
                    temp_rec.status, temp_rec.created_by, temp_rec.created_at, temp_rec.modified_at
                );

                -- C. Clone template-lab details links
                INSERT INTO diagnostic_template_lab_template (diagnostic_template_id, lab_template_detail_id)
                SELECT new_template_id, lab_template_detail_id
                FROM diagnostic_template_lab_template
                WHERE diagnostic_template_id = temp_rec.id;
            END IF;
        END LOOP;
    END LOOP;
END $$;
