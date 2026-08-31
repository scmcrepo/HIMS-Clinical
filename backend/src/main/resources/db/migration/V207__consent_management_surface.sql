-- ---------------------------------------------------------------------------
--  V207 — Consent management surface  (WO-023)
--
--  Two things this closes.
--
--  1. PORTAL_SELF_ACCESS was referenced by PortalProperties from the beginning
--     and was never a member of ConsentPurpose. Portal self-registration read a
--     consent_version off the request, wrote it to a log line, and dropped it.
--     The patient agreed to something the system never stored, so there is no
--     record that consent was given and nothing to withdraw. V205's seed loop
--     covered six purposes; this adds the seventh.
--
--  2. CONSENT_MANAGE was seeded by V179 and provisioned by TenantService for a
--     controller that did not exist until WO-023. This grants it to the roles
--     that actually need it, and adds CONSENT_VIEW for the read-only case —
--     seeing what a patient consented to is a much smaller privilege than
--     changing it, and a clinician checking whether they may send an automated
--     reminder should not need the ability to grant consent on the patient's
--     behalf.
--
--  ROLLBACK
--    DELETE FROM consent_notices WHERE purpose = 'PORTAL_SELF_ACCESS';
--    DELETE FROM role_features rf USING features f
--      WHERE rf.feature_id = f.id AND f.feature_key = 'CONSENT_VIEW';
--    DELETE FROM features WHERE feature_key = 'CONSENT_VIEW';
--  Consent records already written against PORTAL_SELF_ACCESS would be orphaned
--  from their notice text by the first statement. Withdraw rather than delete if
--  any exist.
-- ---------------------------------------------------------------------------

-- ── 1. Notice text for the seventh purpose ────────────────────────────────
--
-- DRAFT for the same reason as V205's seeds: this is a UI label, not a notice.
-- It states no retention period, no recipients and no withdrawal method, and it
-- exists only in English. Counsel-approved wording replaces it as a data change.

INSERT INTO consent_notices (id, tenant_id, purpose, version, language, body_text, notice_state)
SELECT gen_random_uuid(), t.id, 'PORTAL_SELF_ACCESS', 'v1.0', 'en',
       'Viewing your own records in the patient portal',
       'DRAFT'
FROM tenants t
ON CONFLICT (tenant_id, purpose, version, language) DO NOTHING;

-- ── 2. Read-only consent visibility ───────────────────────────────────────

INSERT INTO features (id, feature_key, module, description, tenant_id)
SELECT gen_random_uuid(), 'CONSENT_VIEW', 'COMPLIANCE',
       'View a patient''s consent record and history', t.id
FROM tenants t
ON CONFLICT (tenant_id, feature_key) DO NOTHING;

-- CONSENT_VIEW goes wide: anyone who might act on a patient's data needs to be
-- able to check whether they may.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE f.feature_key = 'CONSENT_VIEW'
  AND UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN', 'RECEPTION', 'DOCTOR', 'NURSE')
ON CONFLICT DO NOTHING;

-- CONSENT_MANAGE stays narrow: capturing and withdrawing consent on a patient's
-- behalf is a materially different act from reading it.
INSERT INTO role_features (role_id, feature_id)
SELECT r.id, f.id
FROM roles r
JOIN features f ON f.tenant_id = r.tenant_id
WHERE f.feature_key = 'CONSENT_MANAGE'
  AND UPPER(r.name) IN ('HOSPITAL_ADMIN', 'ADMIN', 'RECEPTION')
ON CONFLICT DO NOTHING;

-- ── 3. Index for the per-patient consent view ─────────────────────────────

CREATE INDEX IF NOT EXISTS ix_consent_patient_history
    ON consent_records (tenant_id, patient_id, granted_at DESC);
