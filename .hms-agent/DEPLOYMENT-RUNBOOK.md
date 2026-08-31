# Deployment Runbook — DPDP Remediation (V205 → V213)

| | |
|---|---|
| **Covers** | WO-022, WO-023, WO-024, WO-026, WO-027, WO-028, WO-030 |
| **Migrations** | V205 through V213 |
| **Date** | 2026-08-30 |
| **Status** | **Untested.** No part of this has been executed |

---

## Read this first

Nothing in this release has been compiled, typechecked, or run against a
database. Twenty-nine cards are implemented and unverified. This runbook assumes
you have completed step 0 and it passed; if it did not, stop and fix the build
rather than working around it.

There are **three ways this release breaks production if deployed carelessly**,
and all three are silent — the application starts healthy and fails later:

1. Deploying without `hms.abdm.callback.secret` → ABDM consent callbacks are
   rejected. Patients approve consent on their phones and it never reaches you.
   Nothing errors on your side.
2. Deploying V208 without V212 and a successful `migratePii()` run → four columns
   hold a mix of encrypted and plaintext rows. Writes succeed; reads of
   historical rows throw on decryption. **The backfill now exists (Q-007), but it
   still has to actually run and be checked.**
3. Running the erasure sweep before testing it → over-deletion or
   under-deletion, on data you cannot recover.

Each has a gate below. Do not skip them because the deployment looks fine.

---

## Step 0 — Build and test *(blocking)*

```bash
cd backend  && ./gradlew clean test
cd frontend && npm ci && npx tsc --noEmit && npm test
```

Expect failures. These files were written without a compiler; import and
signature errors are likely, particularly in `ConsentService`, `ErasureService`,
`DataPrincipalRightsService`, `SecurityIncidentService` and `GrievanceService`.

**Do not proceed until green.** Consent and erasure code is precisely where a
passing build lies most convincingly, and a partial deployment of it is worse
than none.

Also run:

```bash
python3 .hms-agent/scripts/build_inventory.py     # expect 0 missing, 0 stale
python3 -c "import yaml; yaml.safe_load(open('alerts-dpdp.yml'))"
```

---

## Step 1 — Configuration *(before any deploy)*

| Property | Value | If unset |
|---|---|---|
| `hms.abdm.callback.secret` | Shared secret agreed with the ABDM gateway | **ABDM callbacks silently rejected.** `AbdmCallbackVerifier` fails closed |
| `hms.abdm.callback.allow-unverified` | `false` | Defaults false. Leave it |
| `hms.consent.enforcement` | `enforce` | Defaults enforce. Use `warn` only for a measurement window |
| `hms.security.cross-tenant.incident-threshold` | `3` | Defaults 3 |
| `HMS_ENCRYPTION_KEY` | Existing key, unchanged | Do not rotate during this release |

**Before setting `hms.abdm.callback.secret`, confirm the signature scheme
(card F-003).** I implemented HMAC-SHA256 over the raw request body with an
`X-HMAC-Signature` header because that is the common pattern. **I could not verify
ABDM's actual scheme.** If it differs, `AbdmCallbackVerifier.verify` is the single
method to change — and until it matches, every callback is rejected.

If you cannot confirm the scheme before the deploy window, set
`hms.abdm.callback.allow-unverified=true` **as a documented temporary measure with
an owner and a date**, and raise it as a security incident in your own register.
That leaves the endpoint open, which is what it was before this release — but now
you know.

---

## Step 2 — Migrations

Flyway runs these at startup, before the entity manager. That ordering is what
makes step 3 safe.

| Migration | Adds | Risk |
|---|---|---|
| V205 | `provenance` on `consent_records`, backfill to `SYSTEM_INFERRED`, `consent_notices` + v1.0 seeds | Backfill is one-way |
| V206 | `patient_id` on `hitl_escalations` and `agent_idempotency_keys`, erasure lifecycle columns | Low |
| V207 | `PORTAL_SELF_ACCESS` notice, `CONSENT_VIEW` key | Low |
| V208 | **Widens 4 columns to TEXT**, backfills features across tenants | See step 3 |
| V209 | Incident register | Low |
| V210 | Grievance register, `compliance_contacts` | Low |
| V211 | v2.0-draft notice text | Low |
| V212 | `pii_encrypted` flags, OTP `email_token`, **clears `password_reset_otp`** | See below |
| V213 | Retention policy engine, 6 policies **all disabled and dry-run** | Low — inert on arrival |

**Rehearse on a restored production copy first.** Verify:

```sql
-- V205: only defect-written rows marked inferred
SELECT provenance, COUNT(*) FROM consent_records GROUP BY provenance;
-- expect SYSTEM_INFERRED == the pre-migration count of captured_by IS NULL

-- V208: four columns now TEXT
SELECT table_name, column_name, data_type FROM information_schema.columns
WHERE (table_name, column_name) IN
  (('visits','diagnosis'), ('nhcx_transactions','diagnosis_code'),
   ('nhcx_transactions','diagnosis_text'), ('pharmacy_sales','customer_phone'));

-- V208: every tenant has every feature
SELECT tenant_id, COUNT(DISTINCT feature_key) FROM features GROUP BY tenant_id;
-- expect identical counts across tenants
```

Also confirm a clean replay from V001 on an empty database.

---

## Step 3 — Encrypt existing plaintext *(card Q-007, blocking)*

**This is the step most likely to be skipped and the most damaging to skip.**

V208 widens `visits.diagnosis`, `nhcx_transactions.diagnosis_code`,
`nhcx_transactions.diagnosis_text` and `pharmacy_sales.customer_phone` so
ciphertext fits. It does **not** encrypt what is already there — SQL cannot reach
the application's encryption service, and doing it in SQL would mean putting the
key in a migration file.

Until the backfill runs:

- New writes are encrypted. Old rows are plaintext.
- Reads of old rows **throw on decryption**.
- A patient's older diagnoses become unreadable while newer ones work.

**Q-007 is now implemented.** `PiiMigrationRunner` gained `migrateVisits`,
`migrateNhcxTransactions` and `migratePharmacySales`, wired into `migratePii()`,
and V212 adds the `pii_encrypted` progress flags they read. The runner executes
on startup via `PiiMigrationStartupRunner`.

**It has still never been run.** Rehearse on a restored copy and confirm:

```sql
-- expect zero rows pending after startup completes
SELECT 'visits' t, COUNT(*) FROM visits WHERE pii_encrypted = FALSE
UNION ALL SELECT 'nhcx', COUNT(*) FROM nhcx_transactions WHERE pii_encrypted = FALSE
UNION ALL SELECT 'pharmacy', COUNT(*) FROM pharmacy_sales WHERE pii_encrypted = FALSE;
```

Then read one historical row through the application in each table and confirm
the value comes back readable rather than throwing.

The backfill is idempotent — `encryptIfPlaintext` checks `looksEncrypted()`
first — so a re-run is safe. A partial run followed by a restart resumes rather
than double-encrypting.

---

### V212 and password reset

V212 **deletes every row in `password_reset_otp`**. Those rows have a five-minute
TTL, so the practical cost is that anyone holding an unissued OTP at the moment
of deployment must press "resend". The alternative — migrating them — risks a
half-encrypted authentication table, which locks users out of their own accounts.

Deploy this outside a period of heavy password-reset traffic if you can.

After deploy, verify a full reset cycle end to end: request OTP → verify → reset.
The lookup path changed from email to `email_token` (Q-006), and this is the one
flow where a mistake locks people out rather than erroring visibly.

## Step 4 — Deploy backend and frontend together

The frontend is **not optional** for this release:

- Without `ConsentGateModal` (C-006), the desk receives a 409 `CONSENT_REQUIRED`
  it cannot act on, and ABHA enrolment, policy discovery and pre-auth all block.
- Without the rights queue (D-006), erasure requests have no screen.

If the backend must ship alone, set `hms.consent.enforcement=warn` first. It
meters refusals without blocking. **It also means the consent gate is not
enforcing** — set a date to turn it back on, and note that
`ConsentEnforcementInWarnMode` fires after seven days.

Incident (I-006) and grievance (J-005) UIs do not exist at all. Those APIs work;
staff cannot reach them.

---

### V213 and retention — nothing deletes yet

Every seeded policy arrives `enabled=false, dry_run=true`. The nightly job at
03:20 finds nothing to do. **That is the intended state on deploy**, and it means
storage limitation under s. 8(7) is still unmet until someone acts.

Do not arm anything during this deployment. The sequence afterwards is:

1. `POST /compliance/retention/preview` — forces dry-run regardless of config and
   reports what each policy would affect. Safe at any time.
2. Read the per-store counts. A number far larger than expected means the policy
   is matching more than intended, usually a wrong date column.
3. Have counsel set the periods (card L-005). The seeded values are engineering
   defaults derived from what the data is for, not from a legal source.
4. Arm one policy at a time via `PUT /compliance/retention/policies/{id}` with
   `dryRun: false`, starting with `portal_sessions` — the lowest-consequence
   store on the list.
5. Check `/compliance/retention/runs` the following morning against the preview.

Clinical records are deliberately not covered by any policy and are on a
never-sweep list the service enforces regardless of what a policy row says.

## Step 5 — Observability

Mount the alert rules and reload:

```yaml
# docker-compose.logging.yml — already wired
- ./alerts-dpdp.yml:/etc/prometheus/alerts-dpdp.yml
```

21 rules across 5 groups. Confirm they load in the Prometheus UI, then verify
these appear at `/actuator/prometheus`:

```
hms_consent_checks_total          hms_rights_requests_overdue
hms_consent_inferred_remaining    hms_security_incidents_open
hms_consent_notice_draft_served_total
hms_abdm_callback_verifications_total
hms_grievances_overdue            hms_cross_tenant_blocked_total
hms_retention_policies_invalid    hms_retention_policies_live
```

**Expect `RetentionNeverArmed` to fire after 90 days** if no policy is armed.
That is the alert telling you s. 8(7) is still unmet — do not silence it, arm the
policies.

**`RetentionPolicyInvalid` firing at startup** means a policy names a column that
does not exist. Those policies are skipped, so the data they cover is not being
retention-limited at all.

**Expect `DraftConsentNoticeInUse` to fire immediately.** That is correct — all
seven notices are drafts. It stops when counsel approves them (K-004), not
before. Do not silence it.

**Expect `AbdmCallbackSecretMissing` if step 1 was skipped.** Treat as critical.

Retention is now 400d across logback, Loki and Prometheus, per Rule 6(e)'s
one-year floor. **Check your storage sizing** — this is a 13× increase on the
previous 30 days.

---

## Step 6 — Post-deployment, before announcing

**Test the erasure sweep on a restored production copy. Never first in
production.** All three defects found in that code were invisible until the SQL
met the real schema, and there is no basis for assuming the rewrite is the first
correct version.

```
1. Pick a test patient in the restored copy
2. POST /compliance/rights            → raise ERASURE
3. POST /{id}/verify                  → IN_PERSON_ID
4. POST /{id}/execute
5. Read the receipt: 26 stores, expect ~8 ERASED, ~8 ANONYMISED, ~7 RETAINED, 0 FAILED
6. Confirm no other patient's rows changed  ← the one that catches over-deletion
```

Step 6 is the important one. The original `hitl_escalations` anonymisation
matched every run in the tenant.

Then:

- [ ] **Seed a contact for every tenant (J-006).** Until this is done,
      `GET /compliance/grievances/contact/public` returns 404 and **s. 8(9) is
      unmet for that hospital.** The table exists with no rows.
- [ ] Re-consent patients carrying `SYSTEM_INFERRED` grants. They are blocked from
      ABHA linkage and insurance claims until asked properly. Watch
      `hms_consent_inferred_remaining` trend down; `InferredConsentNotBurningDown`
      fires if it stays flat for 30 days.
- [ ] Run a breach drill against the incident register. A notification path nobody
      has exercised fails on the night.

---

## Rollback

| Migration | Rollback | Notes |
|---|---|---|
| V211 | `DELETE FROM consent_notices WHERE version = 'v2.0-draft'` | Clean. v1.0 becomes served again |
| V210 | `DROP TABLE grievance_events, grievances, compliance_contacts` | **Only if no grievance filed** |
| V209 | `DROP TABLE incident_affected_principals, security_incidents` | **Only if no incident filed** |
| V213 | `DROP TABLE retention_run_items, retention_runs, retention_policies` | Safe — with dry-run on, nothing was removed by them |
| V212 | Drop `pii_encrypted` and `email_token` | **Does not decrypt rows the runner already encrypted** |
| V208 | Not reversible | Column widening cannot be undone without truncating ciphertext |
| V207 | `DELETE FROM consent_notices WHERE purpose='PORTAL_SELF_ACCESS'` | |
| V206 | Drop the added columns | All nullable and new |
| V205 | `ALTER TABLE consent_records DROP COLUMN provenance` | Backfill not reversible |

**Application rollback without database rollback is safe for V206, V209, V210 and
V211** — those add nullable columns and new tables the old code ignores.

**It is not safe for V205 and V208.** Old code has no `provenance` column in its
entity mapping, and the four widened columns will contain ciphertext the old code
cannot decrypt. Rolling the application back past V208 requires restoring from
backup.

---

## Deferred to a later release

| Card | Consequence of the gap |
|---|---|
| F-003 | ABDM signature scheme unconfirmed — **do step 1 first** |
| I-006, J-005 | Incident and grievance APIs unreachable by staff |
| E-005 | Notices English-only in a Tamil Nadu deployment |
| L-005 | Retention periods unreviewed by counsel; **all policies inert until armed** |
| WO-029 | No MFA for privileged users (Rule 6 names it); `pediatric_data` unencrypted |
