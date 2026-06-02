-- V053__seed_diagnostic_print_template.sql
-- Seed the default DIAGNOSTIC_TEMPLATE print template so custom template formats can resolve it

INSERT INTO print_templates (id, name, document_type, content, is_default, status)
SELECT gen_random_uuid(), 'DIAGNOSTIC_TEMPLATE', 'DIAGNOSTICS', '<div><h3>Diagnostic Report</h3><p>Report template placeholder</p></div>', true, 1
WHERE NOT EXISTS (
    SELECT 1 FROM print_templates WHERE name = 'DIAGNOSTIC_TEMPLATE' AND document_type = 'DIAGNOSTICS'
);
