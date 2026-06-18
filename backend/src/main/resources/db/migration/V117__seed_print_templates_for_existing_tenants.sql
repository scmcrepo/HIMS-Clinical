-- V100__seed_print_templates_for_existing_tenants.sql
-- Seeds default print templates for all existing tenants that do not have them yet.

DO $$
DECLARE
    t_id UUID;
BEGIN
    FOR t_id IN SELECT id FROM tenants WHERE id <> '00000000-0000-0000-0000-000000000001' LOOP
        -- Seed print templates for this tenant if they have none
        IF NOT EXISTS (SELECT 1 FROM print_templates WHERE tenant_id = t_id) THEN
            INSERT INTO print_templates (
                id, name, document_type, print_mode, height, width, 
                margin_top, margin_bottom, margin_left, margin_right, 
                margin, page_size, pug_template, content, default_printer, 
                is_default, status, tenant_id, branch_id
            )
            SELECT 
                gen_random_uuid(), name, document_type, print_mode, height, width, 
                margin_top, margin_bottom, margin_left, margin_right, 
                margin, page_size, pug_template, content, default_printer, 
                is_default, status, t_id, null
            FROM print_templates
            WHERE tenant_id = '00000000-0000-0000-0000-000000000001';
        END IF;
    END LOOP;
END $$;
