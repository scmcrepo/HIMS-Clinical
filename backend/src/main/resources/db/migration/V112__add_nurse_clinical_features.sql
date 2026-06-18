-- V112__add_nurse_clinical_features.sql
INSERT INTO features (id, feature_key, module, description) VALUES
    (gen_random_uuid(), 'NURSE_OP_QUEUE', 'CLINICAL', 'Access nurse outpatient queue'),
    (gen_random_uuid(), 'NURSE_IN_PATIENT', 'CLINICAL', 'Access nurse inpatient list')
ON CONFLICT (feature_key) DO NOTHING;

-- Grant NURSE_OP_QUEUE and NURSE_IN_PATIENT to roles that have the name 'NURSE'
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
CROSS JOIN features f
WHERE r.name = 'NURSE' AND f.feature_key IN ('NURSE_OP_QUEUE', 'NURSE_IN_PATIENT')
ON CONFLICT DO NOTHING;
