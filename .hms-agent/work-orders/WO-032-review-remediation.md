# WO-032 — Independent review remediation (F1–F4)

| | |
|---|---|
| **Status** | CONFIRMED (user instruction "fix all the findings", 2026-08-31) |
| **Source** | `DPDP-REVIEW-2026-08-31.md` — second-opinion review, findings F1–F4 |
| **Phase** | Phase 6 — compliance hardening |

## Scope

1. **F1** — `branch_id` missing from six tables whose entities extend `AuditableEntity`,
   making the retention, erasure, grievance-audit and breach-scoping subsystems throw
   at runtime. Migration + entity filter declarations.
2. **F1b** — `ddl-auto: validate` against a migrated schema, so this bug class cannot recur.
3. **F2** — grievance contact absent for all four tenants; make it impossible to onboard
   a tenant without one. Cannot back-fill existing four (data we do not hold).
4. **F3** — `minor` is a client-supplied boolean; derive it from `patients.date_of_birth`.
5. **F4** — `org.springframework.web: DEBUG` in base `application.yml`; move to dev profile.
   Add `logs/` to `.gitignore`.

## Explicit non-scope

- Arming any retention policy. Still `enabled=false, dry_run=true`. That is L-005 / counsel.
- MFA (U-002) and key rotation (U-003). Unchanged, still deferred.
- Notice text approval and translation. Counsel.
- Back-filling the four tenants' grievance contacts. Nobody but each hospital holds them.
- Any change to `ErasureService.TARGETS` retention judgements. Those are legal calls.

## Decisions taken (recorded, reversible)

**D1 — the six tables get `branch_id`, and their entities are declared tenant-wide
(`branchFilter` = `1=1`).**

The column must exist because `AuditableEntity` maps the field, so Hibernate emits it in
every SELECT and INSERT regardless of filters. The filter is a separate question, and the
answer is tenant-wide for all six:

- `retention_policies` / `retention_runs` / `retention_run_items` — a retention period is a
  hospital-level legal determination, not a per-location setting.
- `erasure_requests` — a data-principal request is made to the hospital. If it were
  branch-scoped, a request raised at one branch would be invisible to the compliance
  officer at another, and the statutory clock would run against a record nobody can see.
- `incident_affected_principals` — this is the "who must we notify" list under Rule 7.
  A partially visible list means under-notification, which is the failure mode that
  matters.
- `grievance_events` — the parent `grievances` row stays branch-scoped and remains the
  access control. A partially visible *audit trail* on a visible grievance is worse than
  either fully visible or fully hidden.

Consequence: `branch_id` stays NULL on all six, because `AuditableEntity.stampScope()`
skips branch stamping when the entity declares `branchFilter = 1=1`. The column exists to
make the mapping valid, not to carry data.

**D2 — `tenantFilter` is redeclared on all six entities as well.**
Identical to the superclass condition, so a no-op if mapped-superclass filters are
inherited and a fix if they are not. 48 of the 88 `AuditableEntity` subclasses already
redeclare it, so the duplicate is proven safe at runtime.

**D3 — `minor` derived from DOB is enforcing, not advisory.**
A client-supplied `minor=false` that contradicts the patient's date of birth is rejected,
not silently corrected. Silent correction would make the audit record disagree with what
the desk actually saw and attested to.

## Compliance impact

Positive on ss. 8(6), 8(7), 8(9), 9 and 12 — each currently has code that cannot execute.
No new personal data is collected. No new consent purpose. `branch_id` is not personal data.

## Observability plan

- `RetentionService.validatePoliciesAtStartup` must stop emitting
  `event=retention.startup.validation_failed`. That log line going quiet is the acceptance
  test for F1.
- New counter `hms_consent_minor_dob_mismatch_total{purpose}` for F3 — an attestation
  contradicting the DOB is a training signal, not just an error.
- `hms_tenants_without_contact` gauge already exists; F2 stops it growing.

## Acceptance criteria

1. All 202+1 migrations replay into a clean Postgres with `ON_ERROR_STOP=1`.
2. Every `AuditableEntity` subclass's table has both `tenant_id` and `branch_id` — asserted
   by a script, not by eye.
3. A boot produces no `retention.startup.validation_failed`.
4. Capturing consent for a patient whose DOB makes them a minor, with `minor=false`,
   is rejected.
5. Creating a tenant without a compliance contact is rejected.
6. A profile-less boot does not log response bodies.

## Risks

- **Unverifiable here.** No `javac` and Maven Central is outside the sandbox allowlist, so
  nothing below is compiled. Static checks and a real Postgres migration replay only.
- Adding a FK column to six tables is additive and reversible; the rollback is six
  `DROP COLUMN` statements.
- F3 changes a write path used by three services. If any caller passes `minor=false` for a
  paediatric patient today, it will now get a 400 where it used to get a silent bad record.
  That is the intended behaviour and needs to be in the release note.

---

## Scanner delta — adjudicated

`check_conventions.py` moved from 604 findings / 60 HIGH to **606 / 61**. Both new
findings are false positives, adjudicated here rather than silenced:

| File | Rule | Verdict |
|---|---|---|
| `V215__add_branch_id_to_compliance_tables.sql:1` | `migration.destructive` (HIGH) | **False positive.** Fires on the string `DROP COLUMN`, which appears only inside the rollback instructions in the header comment. Every statement in the migration is `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`. Additive and idempotent; no data is read, written or destroyed. Leaving the rollback documented in the file is worth one false positive. |
| `GrievanceService.java:337` | `pii.in-logs` | **False positive.** Fires on the substring "contact" in the event name `compliance.contact.published`. The line logs `tenant_id` and `is_dpo` and nothing else. The record itself is organisational contact information published deliberately under s. 8(9), not personal data about a data principal. Identical to the pre-existing finding on line 288. |

## Verification performed

Offline only — there is no `javac` in this environment and Maven Central is
outside the sandbox allowlist, so **none of the Java has been compiled**.

- `verify_java_static.py` — 960 files parsed, 0 syntax errors, all `com.hms`
  imports resolve.
- **All 203 migrations (V001–V215) replayed into a fresh Postgres 16.15** with
  `psql -v ON_ERROR_STOP=1`. Zero failures, 151 tables.
- `check_entity_schema.py` against that replayed schema: all six F1 tables now
  pass. Only the two pre-existing F5 defects remain.
- Frontend `npx tsc --noEmit`: clean for every file touched. The 7 remaining
  errors are all in `DoctorCalendarPage.tsx`, which this work order did not open.
- `check_conventions.py`: 606 / 61, delta adjudicated above.

**Not verified:** that the application boots, that
`retention.startup.validation_failed` stops appearing, that the consent mismatch
throws, or that onboarding rejects a missing contact. Those need a compiler and
a running instance. Every card stays `IN_PROGRESS`.

## Follow-ups raised, not taken

- **F5 / card X-006** — `AreaEntity` maps `areas`, dropped by V046, with a live
  `AreaController`; `customers` is missing `status`, `modified_by` and
  `modified_at` with a live `CustomerController`. Same bug class as F1, found by
  the new check. Restoring versus deleting the Areas feature is a product
  decision, so this is raised rather than folded in.
- The two existing `ConsentService` tests were updated for the new constructor
  argument and stub `MinorDetermination` to `Optional.empty()` — "no date of
  birth on file" — which is the only state that leaves their original assertions
  meaning what they were written to mean.

---

# Addendum — U-002, MFA for privileged users (WO-029)

Completed in the same session. Recorded here because it shares the verification
constraints; the card lives in WO-029.

## Scanner delta

610 / 63 HIGH, up from 606 / 61. Four new findings, all on new files:

| File | Rule | Verdict |
|---|---|---|
| `V218__mfa_totp_for_privileged_users.sql:1` | `migration.destructive` (HIGH) | **False positive.** Fires on `DROP TABLE` inside the rollback instructions in the header comment. Every statement in the file is `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS` or an `INSERT ... ON CONFLICT DO NOTHING`. |
| `MfaChallengeEntity.java:1` | `tenant.entity-not-scoped` | **Deliberate.** Keyed by `user_id`, which is already tenant-specific, and read during login when no `TenantContext` exists. A tenant filter here would be inert, and `@PrePersist` would stamp a null. Documented in the class javadoc. Same precedent as `password_reset_otps`. |
| `MfaRecoveryCodeEntity.java:1` | `tenant.entity-not-scoped` | **Deliberate.** Keyed by `credential_id`, which is keyed by `user_id`. Same reasoning. |
| `MfaController.java:105` | `rbac.feature-key` | **Answered, and it caught a real gap.** `MFA_ADMIN` is seeded by V218 for existing tenants — but `TenantService.FEATURES` seeds *new* tenants from a hardcoded array, so tenant number five would never have received it. Added to the array. This is the Q-005 bug class and the scanner was right to ask. |

## Verification

- All **206 migrations (V001–V218) replay clean** into a fresh Postgres 16.15;
  the three MFA tables are created.
- The three new entities were reconciled column-by-column against the replayed
  schema, in both directions, by hand — they are not `AuditableEntity`
  subclasses, so `check_entity_schema.py` does not cover them.
- The TOTP algorithm was checked against all six **RFC 6238 SHA-1 test vectors**
  before anything was built on it, and the RFC seed's Base32 encoding matches the
  documented `GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ`.
- `verify_java_static`: 968 files, 0 syntax errors, all imports resolve.
- `application.yml` re-parsed after editing: one top-level `hms:` key, all seven
  sub-keys intact.

## Two things that went wrong and were caught

1. **A second top-level `hms:` key.** The first attempt appended a new `hms:`
   block rather than merging into the existing one, which in YAML means the later
   key wins and every existing `hms.*` setting — security, sms, storage, portal,
   gov, reports — silently disappears. Caught by parsing the file and diffing the
   key set rather than by reading the diff. Merged properly.
2. **Nested repository interfaces.** The three repositories were first written as
   static nested interfaces inside one class. Spring Data can scan those, but
   whether it does depends on the `@EnableJpaRepositories` base packages, and the
   failure mode is a missing bean at startup in the authentication path. Split
   into three conventional files.

## Not done

- **No frontend.** The login screen does not yet handle `MFA_REQUIRED` or
  `MFA_ENROLMENT_REQUIRED`, and there is no enrolment page. This is why the mode
  must stay `OFF`: turning it on without the UI would produce an interstitial the
  client does not understand. Backend and API are complete and documented.
- **No integration test of the login path.** `MfaServiceTest` covers the service
  with in-memory repositories; the `AuthController` split itself is unexercised.

---

# Addendum — U-003, key rotation (WO-029)

## The card is NOT discharged

U-003 says *exercise* key rotation. That has not happened and cannot happen here:
it needs a running application, a populated database and two keys. What this work
did was read the existing implementation before running it, find that running it
would have destroyed the database, and rebuild it so that the exercise is
survivable when someone does it.

## What the old PiiKeyRotationUtil would have done

| # | Defect | Consequence |
|---|---|---|
| 1 | The batch loop re-issued the same `SELECT ... LIMIT 100` with no cursor and no done-marker | After rotating the first 100 rows it selected the same 100 — now under the NEW key — and decrypted them with the OLD one. Any table of 100+ rows failed partway, leaving it half-rotated with **no record of which rows used which key**. Rotated and unrotated ciphertext are indistinguishable, so the state was unrecoverable |
| 2 | `@Transactional` imported, never applied | Every row committed alone; a failure left mixed-key state |
| 3 | Covered `patients`, `users`, `consultants` — 3 tables of 23 | Rotating and then swapping the key in config would have made **20 tables permanently undecryptable**: every diagnosis, every insurance claim, every grievance. Discovered only on the first read afterwards |

## What replaced it

- **`PiiEncryptedColumnRegistry`** — discovers targets by reflection over
  `@Convert(converter = Encrypted*)`, so coverage cannot drift behind the entity
  model. 23 tables from reflection plus 3 declared explicitly
  (`patient_pediatric`, `sms_logs`, `template_data`) which have no entity for
  reflection to find. **26 tables, 74 columns.**
- **Keyset cursor** (`WHERE id > ? ORDER BY id LIMIT 200`), committed in the same
  transaction as the batch it describes.
- **`V221 pii_key_rotation_progress`** — resumability and an audit trail. Holds no
  key material and no personal data.
- **`plan()`** — dry run: decrypt-old, encrypt-new, decrypt-new, compare, write
  nothing. The second decryption is the point; encrypting successfully proves
  nothing about readability.
- **`verify()`** — reads every value back under the new key alone, to be run
  before the config swap.
- Already-new values are detected and skipped, so a resumed run is safe.
- Identical old and new keys are refused — it would march the cursor to the end
  of every table and record COMPLETED, so a later real rotation reusing that
  runId would skip everything and report success.

## Verified here

- **All 209 migrations (V001–V221) replay clean**; the progress table is created.
- **Every one of the 26 targets validated against the replayed schema** — all 74
  encrypted columns and all 26 id columns exist. A missing column would otherwise
  have surfaced mid-rotation.
- `verify_java_static`: 971 files, 0 syntax errors, all imports resolve.

## A bug avoided, worth recording

The first attempt at the registry derived columns by regex over the source and
produced `pharmacy_sales.sale_status` — an `@Enumerated(ORDINAL)` column, matched
because a `@Convert` on an *earlier field* happened to precede it. Rotation would
have tried to decrypt an integer. That is why discovery is reflection over field
annotations rather than text near a field, and there is a test pinning it.

## Scanner delta

| File | Rule | Verdict |
|---|---|---|
| `V221:1` | `migration.destructive` (HIGH) | **False positive.** `DROP TABLE` appears only in the rollback comment. |
| `V221:1` | `tenant.table-not-scoped` | **Deliberate.** Rotation is deployment-wide and spans every tenant; a tenant column would be meaningless. |

## Still required before anyone rotates

1. Take a backup. Nothing here can undo a rotation — the old ciphertext is gone
   once a batch commits.
2. **Stop the application.** Rotation is not online: rows written while it runs are
   encrypted with the key the running application holds, which is still the old
   one, and rotation may already have passed their table. This class cannot
   enforce that.
3. `plan()`, expect zero failures, then `rotate()`, then `verify()`, and only then
   swap the key in configuration.

---

# Addendum — F-003, ABDM callback signature scheme (WO-029)

## The card is NOT closed, and cannot be closed from inside the codebase

F-003 asks for the scheme to be confirmed against the gateway contract. I do not
have your NHA onboarding pack. What I could do was check the implementation
against ABDM's *public* documentation, and the answer is that it almost certainly
does not match.

## Finding

| What the code does | What ABDM appears to do |
|---|---|
| Expects a **shared secret**; computes `HMAC-SHA256(secret, rawBody)` hex-encoded | Public NHA material does not describe issuing a per-HIP callback HMAC secret |
| Reads the signature from **`X-HMAC-Signature`** | Callbacks carry `Authorization: Bearer`, `X-HIP-ID`, `X-CM-ID`. No published material mentions `X-HMAC-Signature` |
| Verifies the transport | The **consent artefact** carries its own signature, verifiable against the Consent Manager's public key — a different mechanism at a different layer |

**And `hms.abdm.callback.secret` appeared in no configuration file at all.** It was
unset, so `AbdmCallbackVerifier` was rejecting *every* callback. The ABDM consent
integration has been silently receiving nothing.

Failing closed was the right call in WO-028. But the practical consequence is an
integration that looks configured and receives nothing, and the natural reaction
of whoever debugs that is `allow-unverified=true` — which reopens precisely the
hole WO-028 closed: an unauthenticated endpoint that writes to consent records.

## What I did NOT do

I did not rewrite the verifier to a guessed scheme. A guessed signature check is
worse than none, because it reads as verification in every review that follows and
provides nothing. Detached JWS is the pattern used elsewhere in the Indian health
and account-aggregator stack, which makes it a plausible guess — plausible is not
a basis for an authentication mechanism.

## What I did

- **`AbdmCallbackConfigCheck`** — a startup check and gauge
  (`hms_abdm_callback_auth_state`) making the three states impossible to misread:
  `1` open (bypass on, ERROR every boot), `2` closed-and-dead (ABDM configured, no
  secret, ERROR), `0` verified. Alert on `1`.
- **Documented the config keys in `application.yml`** — they existed only as
  `@Value` defaults, so nobody reading configuration could tell the endpoint was
  shut. The comment states the consequence and warns against the bypass.

## Three questions that close F-003

For whoever holds the NHA onboarding pack:

1. **Which header carries the signature** on inbound gateway callbacks?
2. **What exactly is signed** — the raw body, a canonicalised body (JCS/RFC 8785),
   or a detached JWS over selected headers?
3. **Which key verifies it** — a shared secret issued at onboarding, or the
   Consent Manager's public key fetched from the gateway (and if so, from which
   endpoint, and how is it cached and rotated)?

With those three answers the verifier is a short change. Without them, this
endpoint is either shut or unprotected, and no code change alters that.

## Verified

- `verify_java_static`: 972 files, 0 syntax errors, all imports resolve.
- `application.yml` re-parsed after editing: one top-level `hms:` key, all eight
  sub-keys intact — checked explicitly, having made exactly that mistake during
  U-002.
