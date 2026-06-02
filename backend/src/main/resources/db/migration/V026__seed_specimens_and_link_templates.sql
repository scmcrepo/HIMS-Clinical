-- V027__seed_specimens_and_link_templates.sql
-- Seed specimens only if they don't exist
INSERT INTO specimens (id, name, description, created_at)
SELECT gen_random_uuid(), 'Blood', 'Whole blood specimen', NOW() WHERE NOT EXISTS (SELECT 1 FROM specimens WHERE name = 'Blood');
INSERT INTO specimens (id, name, description, created_at)
SELECT gen_random_uuid(), 'Serum', 'Serum specimen', NOW() WHERE NOT EXISTS (SELECT 1 FROM specimens WHERE name = 'Serum');
INSERT INTO specimens (id, name, description, created_at)
SELECT gen_random_uuid(), 'Plasma', 'Plasma specimen', NOW() WHERE NOT EXISTS (SELECT 1 FROM specimens WHERE name = 'Plasma');
INSERT INTO specimens (id, name, description, created_at)
SELECT gen_random_uuid(), 'Urine', 'Urine specimen', NOW() WHERE NOT EXISTS (SELECT 1 FROM specimens WHERE name = 'Urine');
INSERT INTO specimens (id, name, description, created_at)
SELECT gen_random_uuid(), 'Sputum', 'Sputum specimen', NOW() WHERE NOT EXISTS (SELECT 1 FROM specimens WHERE name = 'Sputum');

-- Link specimens to diagnostic templates
UPDATE diagnostic_templates SET specimen_id = (SELECT id FROM specimens WHERE name = 'Blood' LIMIT 1) 
WHERE name IN ('Hemoglobin', 'ESR', 'White Blood Cell Count', 'Platelet Count', 'Red Blood Cell Count', 'HbA1c');

UPDATE diagnostic_templates SET specimen_id = (SELECT id FROM specimens WHERE name = 'Serum' LIMIT 1) 
WHERE name IN ('CRP', 'T4', 'Blood Urea', 'Serum Creatinine', 'Sodium', 'Potassium', 'Chloride', 'Calcium', 'Total Cholesterol', 'HDL Cholesterol', 'LDL Cholesterol', 'Triglycerides');

UPDATE diagnostic_templates SET specimen_id = (SELECT id FROM specimens WHERE name = 'Blood' LIMIT 1) 
WHERE name LIKE '%Blood Sugar%';
