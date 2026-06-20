-- V142__clone_templates_for_existing_branches.sql
-- Clones the default superadmin templates to all existing branches of other tenants

DO $$
DECLARE
    b RECORD;
    t RECORD;
    new_template_id UUID;
BEGIN
    -- Loop over all non-default-hospital branches
    FOR b IN 
        SELECT id AS branch_id, tenant_id 
        FROM branches 
        WHERE tenant_id != '00000000-0000-0000-0000-000000000001'
    LOOP
        -- 1. Clone case_sheet_templates
        FOR t IN 
            SELECT * FROM case_sheet_templates 
            WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
        LOOP
            -- Check if already exists for this branch to avoid duplicates
            IF NOT EXISTS (
                SELECT 1 FROM case_sheet_templates 
                WHERE tenant_id = b.tenant_id 
                  AND branch_id = b.branch_id 
                  AND name = t.name 
                  AND specialization = t.specialization 
                  AND visit_type = t.visit_type
            ) THEN
                new_template_id := gen_random_uuid();
                
                INSERT INTO case_sheet_templates (
                    id, name, specialization, visit_type, description, is_default, 
                    status, created_at, modified_at, tenant_id, branch_id
                ) VALUES (
                    new_template_id, t.name, t.specialization, t.visit_type, t.description, t.is_default, 
                    t.status, NOW(), NOW(), b.tenant_id, b.branch_id
                );
                
                -- Clone corresponding case_sheet_template_fields
                INSERT INTO case_sheet_template_fields (
                    id, template_id, field_key, label, field_type, section, display_order, 
                    is_required, placeholder, help_text, options, validation, default_value, 
                    is_visible, status, created_at, modified_at
                )
                SELECT 
                    gen_random_uuid(), new_template_id, field_key, label, field_type, section, display_order, 
                    is_required, placeholder, help_text, options, validation, default_value, 
                    is_visible, status, NOW(), NOW()
                FROM case_sheet_template_fields
                WHERE template_id = t.id;
            END IF;
        END LOOP;

        -- 2. Clone discharge_summary_templates
        FOR t IN 
            SELECT * FROM discharge_summary_templates 
            WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
        LOOP
            -- Check if already exists for this branch to avoid duplicates
            IF NOT EXISTS (
                SELECT 1 FROM discharge_summary_templates 
                WHERE tenant_id = b.tenant_id 
                  AND branch_id = b.branch_id 
                  AND name = t.name 
                  AND specialization = t.specialization
            ) THEN
                new_template_id := gen_random_uuid();
                
                INSERT INTO discharge_summary_templates (
                    id, name, specialization, description, is_default, 
                    status, created_at, modified_at, tenant_id, branch_id
                ) VALUES (
                    new_template_id, t.name, t.specialization, t.description, t.is_default, 
                    t.status, NOW(), NOW(), b.tenant_id, b.branch_id
                );
                
                -- Clone corresponding discharge_summary_template_fields
                INSERT INTO discharge_summary_template_fields (
                    id, template_id, field_key, label, field_type, section, display_order, 
                    is_required, placeholder, help_text, options, validation, default_value, 
                    is_visible, status, created_at, modified_at
                )
                SELECT 
                    gen_random_uuid(), new_template_id, field_key, label, field_type, section, display_order, 
                    is_required, placeholder, help_text, options, validation, default_value, 
                    is_visible, status, NOW(), NOW()
                FROM discharge_summary_template_fields
                WHERE template_id = t.id;
            END IF;
        END LOOP;
    END LOOP;
END $$;
