-- ── INSURANCE_REPORTS feature (Separate key for Insurance Tab Reports) ───────
INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'INSURANCE_REPORTS', 'INSURANCE',
       'Insurance Reports (Insurance Tab)', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- Grant to ADMIN, HOSPITAL_ADMIN, BRANCH_ADMIN, and INSURANCE roles
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE UPPER(r.name) IN ('ADMIN', 'HOSPITAL_ADMIN', 'BRANCH_ADMIN', 'INSURANCE')
  AND f.feature_key = 'INSURANCE_REPORTS'
ON CONFLICT DO NOTHING;
