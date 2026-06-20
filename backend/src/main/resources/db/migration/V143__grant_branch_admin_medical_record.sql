-- V143__grant_branch_admin_medical_record.sql
-- Grants MEDICAL_RECORD and ATTACHMENT features to BRANCH_ADMIN role across all tenants.
-- BRANCH_ADMIN users need these features to save case sheets and manage attachments
-- from the OP queue workflow.

INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE r.name = 'BRANCH_ADMIN'
  AND r.status = 1
  AND f.feature_key IN ('MEDICAL_RECORD', 'ATTACHMENT')
  AND NOT EXISTS (
      SELECT 1 FROM role_features rf
      WHERE rf.role_id = r.id AND rf.feature_id = f.id
  );
