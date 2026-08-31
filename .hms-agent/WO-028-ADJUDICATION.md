# WO-028 — Scanner Backlog Adjudication

| | |
|---|---|
| **Date** | 2026-08-30 |
| **Scope** | All 71 HIGH findings from `check_conventions.py` |
| **Result** | 71 HIGH → 67 HIGH · 582 findings → 579 |
| **Build status** | **NOT COMPILED** — no Gradle in this environment |

> This is an adjudication register, not a fix list. The point of a triage work
> order is deciding which findings are real; silencing a linter is not the goal,
> and neither is fixing everything it says. Where a finding is wrong, the reason
> is written down here so nobody re-litigates it in six months.

## Summary

| Category | Count | Real | False positive | Deferred |
|---|---|---|---|---|
| `pii.in-logs` | 18 | 3 | 15 | 0 |
| `tenant.entity-not-scoped` | 18 | 0 | 16 | 2 |
| `pii.unencrypted-column` | 15 | 4 | 10 | 1 |
| `rbac.feature-single-tenant` | 13 | 13 | 0 | 0 |
| `tenant.webhook-no-context` | 4 | **1 (critical)** | 3 | 0 |
| `tenant.threadlocal-hop` | 2 | 0 | 2 | 0 |
| `rbac.missing-preauthorize` | 1 | 0 | 1 | 0 |

Roughly 60% false positives, which is close to what the codebase map predicted
and is why this was scoped as its own gated work order rather than a cleanup
sprint.

---

## The one that matters

### `AbdmConsentCallbackController` — unauthenticated cross-tenant write

**Severity: critical. Fix before the next deployment.**

Three defects compounding:

1. **Nothing verified the caller.** The endpoint is `permitAll()` and took an
   arbitrary `JsonNode`. Its own class docstring asserted that "the protection is
   the gateway credential and the signature" — neither was ever checked. The
   method read `artifact.path("signature")` and wrote it to a column. Storing a
   signature is not verifying one.
2. **No tenant context.** `TenantFilterAspect` *disables* the tenant filter when
   `TenantContext` is null. On the callback path there is no session, so lookups
   by ABDM identifier ran across every tenant.
3. **Rows written with a null tenant.** `recordGrant` created artifacts while no
   tenant context was set, so `tenant_id` came out null — invisible to every
   tenant-filtered query afterwards.

Combined, an unauthenticated caller who could reach the path could deny or revoke
consent artifacts belonging to any patient in any tenant, and cause artifact rows
to be written that no tenant could subsequently see.

**Why review missed it:** the code looked defended. There was a docstring
claiming authentication and a variable called `signature`. A reviewer scanning
for "is this authenticated?" would find both and move on. This is also why the
regression test asserts at the *source* level that `verifier.verify` is called —
a behavioural test cannot distinguish "verified" from "looked verified".

**The contrast worth noting:** `NhcxCallbackController`, the sibling integration,
gets this exactly right. Its docstring explains that `decryptAndVerify` throwing
on a bad signature *is* the security boundary, and that tenant context is
resolved after verification rather than from an attacker-controlled header. The
ABDM controller was written to the same shape with none of the protection.

**Fixed:** `AbdmCallbackVerifier` (constant-time HMAC-SHA256 over the raw body,
fails closed with no secret configured), 401 for unverified callers, and
`withTenantOf(...)` in `AbdmConsentService` scoping every callback write to the
tenant that owns the resolved record, restoring context in a `finally`.

> **Deployment note.** This fails closed. ABDM callbacks will be rejected until
> `hms.abdm.callback.secret` is set. That is deliberate — an endpoint accepting
> unauthenticated writes to consent records is worse than one temporarily
> unreachable — but it means the secret must be configured *before* deploy, or
> patients approving consent on their phones will silently stop reaching you.
> `hms.abdm.callback.allow-unverified=true` exists as an escape hatch if your
> gateway contract genuinely doesn't sign callbacks. It defaults false and should
> stay there.

**Verify the exact signature scheme against your ABDM gateway contract.** I
implemented HMAC-SHA256 over the raw body with an `X-HMAC-Signature` header,
which is the common pattern, but I could not confirm ABDM's actual scheme from
this codebase. If it differs, `AbdmCallbackVerifier.verify` is the single method
to change.

---

## `pii.in-logs` — 3 real, 15 false positives

**Fixed** — `PiiMigrationRunner` lines 425, 444, 466 interpolated
`e.getMessage()` from a *decryption failure*. Those messages can quote the
ciphertext or a partial plaintext, and Promtail scrapes them into Loki. Now logs
the exception class; the row id was already there and is enough to investigate.

**False positives** — the rule matches on field-name keywords, not on what
reaches the log:

| Site | Why it's fine |
|---|---|
| `app.py:137`, `transport.py:96` | The match is on `mask_phone(...)` — the masking function itself |
| `AbhaService` ×4 | Logs `patientId` and `linkageId` (UUID surrogates). Matched the word "abha" in the event name |
| `HitlService:110` | Logs `entity.getId()` only. Matched "transcript" in the event name |
| `SmtpConfigService` ×3 | Already masked via `PiiMasking.email(...)` in WO-022. Matched the word "email" inside the mask call |
| `AbdmClient` ×2 | Logs the literal channel name `AADHAAR`/`MOBILE`, no value |
| `PiiMigrationRunner` 397, 452, 471 | Row counts only |

---

## `pii.unencrypted-column` — 4 real, 10 false positives, 1 deferred

**Fixed** — converters added, columns widened to `TEXT` in V208 (base64
ciphertext runs ~40% longer than plaintext plus IV and tag; without widening
first the app starts healthy and throws on the first write):

| Column | Why it matters |
|---|---|
| `visits.diagnosis` | Clinical diagnosis in plaintext, while `clinical_encounters.diagnosis` was **already encrypted**. The kind of gap that only surfaces when someone enumerates the schema rather than reading the code |
| `nhcx_transactions.diagnosis_code` | ICD-10 sent to the payer. Health data, identifying alongside the `patient_id` on the same row |
| `nhcx_transactions.diagnosis_text` | Free-text diagnosis |
| `pharmacy_sales.customer_phone` | A walk-in customer's only identifier. Not queried by value, so a plain converter suffices |

**Deferred with rationale — `PasswordResetOtpEntity.email`.** I encrypted this,
then reverted it. The reset flow queries `findFirstByEmailAndOtp...`, and
`EncryptedStringConverter` is non-deterministic — the same address encrypts
differently every time — so the converter would have made every lookup miss and
**silently broken password reset for every user**, with no error to explain why.
The correct fix is a deterministic search token alongside the ciphertext, as
`Patient.contactNumberToken` does. That is a schema plus migration plus query
change and belongs in its own card, not smuggled into a triage pass. Mitigating:
rows are short-lived and swept by the OTP expiry job, and hold no clinical data.

**False positives:**

- `contactNumberToken` (Patient, Consultant, PortalOtpChallenge), `contactToken`
  (Staff), `abhaNumberToken` (AbhaLinkage) — **these are blind indexes.** They
  must be deterministic and unencrypted or every lookup breaks. Encrypting them
  would break the exact mechanism that lets the real value stay encrypted. This
  is the false positive the codebase map warned about by name.
- `BranchEntity` / `TenantEntity` `address` and `contactNumber` — organisational
  contact details for the hospital, not personal data about a data principal.
- `SmtpConfig.fromEmail` — the hospital's own sending address, configuration
  rather than personal data.

---

## `rbac.feature-single-tenant` — 13 real

Thirteen historical migrations (V012 → V195) inserted feature rows without
iterating tenants.

**Consequence:** a tenant created before a feature existed, or missed by a
hand-written seed, silently has no row for that key. Every `hasPermission` check
returns false, so the feature is simply invisible — no error, just an absent
button, in one hospital and not another.

**Fixed forward, not in place.** Migrations are immutable, so V208 backfills any
feature key that exists for some tenant but not for others, taking the union
across all keys.

Role *grants* are deliberately not backfilled. Which roles hold which feature is
a per-hospital decision, and guessing would hand permissions to roles an
administrator never chose. The rows now exist to be granted; granting them is an
administrative act.

---

## `tenant.entity-not-scoped` — 16 false positives, 2 deferred

Sixteen are child/line tables — `BillDetailModified`, the various `*Line`
entities, `*TemplateField`, `OrderSetItem` — whose tenant is carried by their
parent through a foreign key. Adding a redundant `tenant_id` to each would create
two sources of truth for the same fact and a new way for them to disagree.
`PurchaseOrderJpaRepository.java` is a scanner misfire: it flagged a repository
as an `@Entity`.

`NumberSequenceEntity` and `SystemSettingEntity` are platform-level by design.

**Deferred, needs a decision:** `PasswordResetOtpEntity` has no `tenant_id` and
holds an email address. Password reset runs pre-authentication where there is no
tenant context, so global lookup may be correct — but two tenants with a user at
the same address is a case worth thinking about deliberately rather than
inheriting. Same card as the search-token work above.

**Not done:** the scanner asks for a per-file comment explaining each deliberate
exclusion, and I did not add 16 comments. This register is the documentation
instead, which means these 16 will keep appearing in every scan. That's a
conscious trade — I'd rather have the reasoning in one reviewable place than
scattered across sixteen files as boilerplate.

---

## `tenant.threadlocal-hop` — 2 false positives

- `BulkImportAsyncService` already does exactly what the rule asks: takes
  `tenantId`/`branchId` as explicit parameters, sets both at the top of the
  `@Async` method, clears both in a `finally`. Textbook. The rule fires on
  `@Async` and `TenantContext` appearing in the same file regardless of
  correctness.
- `FeaturePermissionCacheService` — the `TenantContext.get()` call is in
  `getCurrentUserFeatureMap`, which runs on a request thread. The
  `@EventListener(ApplicationReadyEvent)` that triggered the rule calls
  `rebuildAll()`, which iterates roles and reads `role.getTenantId()` explicitly.
  No ThreadLocal crosses a thread here.

---

## `rbac.missing-preauthorize` — 1 false positive

`AuthController` has no class-level `@PreAuthorize`, correctly. `/auth/login`,
`/auth/logout` and `/auth/forgot-password/**` are `permitAll` in `SecurityConfig`
— they must be reachable before a session exists. `/auth/me` and
`/auth/heartbeat` fall through to `.anyRequest().authenticated()`: they need a
session but no feature key, which is right for "who am I" endpoints. A feature
key on them would mean a valid user could be locked out of discovering their own
identity.

---

## Addendum — findings introduced by later work orders

Adjudicated on the same basis, so the register stays the single place these are
reasoned about.

### WO-027 (grievance) — 4 new HIGH, all false positives

| Finding | Verdict |
|---|---|
| `GrievanceService:288` `pii.in-logs` | The line is `log.info("event=compliance.contact.published is_dpo={}", isDpo)` — a boolean and nothing else. The rule matched the word "contact" in the event name |
| `ComplianceContactEntity.email` / `.phone` / `.postalAddress` `pii.unencrypted-column` | **Deliberately unencrypted.** The entire purpose of this record is to be published under s. 8(9), and `GET /compliance/grievances/contact/public` serves it without a session. Encrypting it would break the one endpoint that has to work unauthenticated. It is organisational contact information — a role or name, an email, a phone number the hospital chose to make public — not personal data about a data principal |

The `ComplianceContactEntity` case is the mirror image of the blind-index false
positives above: there, encryption would break lookup; here, it would break
publication. Both are cases where the linter's rule is right in general and wrong
for a field whose job is to be readable.

### Q-006 / Q-007 implementation — 3 new HIGH, all false positives

| Finding | Verdict |
|---|---|
| `PasswordResetOtpEntity.emailToken` `pii.unencrypted-column` | **Blind index.** Identical to the `contactNumberToken` case above — it must be deterministic or the lookup it exists to serve cannot work. Encrypting it would defeat the mechanism that lets the email itself be encrypted |
| `PiiMigrationRunner:430, 458, 484` `pii.in-logs` | Each logs a row **count**: `"visits: encrypted {} diagnosis rows", total`. The rule matches the column name in the message text. Same as the pre-existing `clinical_encounters` line at 404, already adjudicated |

Worth noting that both fall into classes already documented above rather than
introducing new kinds of finding. That is the signal one wants from a triage
baseline: new code producing only known-benign patterns.

### WO-029 (JSONB) / UIs / J-006 — 4 new HIGH, all false positives

`ComplianceContactCoverageCheck` lines 94, 101, 108, 111 — `pii.in-logs`. The
rule matches the word **"contact"** in the event names
(`compliance.contact.coverage`, `compliance.contact.missing`). Those statements
emit tenant UUIDs and counts and nothing else; the class exists precisely to
report that contact details are *absent*.

Same class as the `SmtpConfigService` and `PiiMigrationRunner` cases above: the
linter matches a field-name keyword in the message text rather than in an
interpolated value.

### Running total

| Work order | Findings added | HIGH added | Real |
|---|---|---|---|
| WO-026 (incidents) | +12 | 0 | — |
| WO-027 (grievance) | +18 | +4 | 0 |
| F-004 (annotations) | −18 | **−18** | — |
| Q-006 / Q-007 | +4 | +3 | 0 |
| WO-025 (retention) | +6 | 0 | — |
| WO-029 JSONB + 3 UIs + J-006 | +4 | +4 | 0 |

Scan now **605 findings / 60 HIGH**, from 582 / 71 at the start of WO-028.

The HIGH reduction is almost entirely F-004's annotations plus the 4 real fixes;
everything added since has been false positives in the two classes documented
above. **60 is the working baseline** — gate CI on new findings against it, not on
zero. Everything added since F-004 has been a false positive in one of the two
documented classes.

## F-004 outcome — exclusion comments *(done 2026-08-30)*

18 `tenant.entity-not-scoped` findings annotated with the `platform-level` marker
the scanner looks for. **Each comment states its own reason rather than repeating
one sentence 18 times** — boilerplate would satisfy the linter while teaching the
next reader nothing.

Three kinds of reason:

- **11 child/line tables** — tenant scope comes from the parent through its
  foreign key. A redundant `tenant_id` would be a second source of truth for the
  same fact, and a new way for the two to disagree.
- **5 genuinely platform-level** — `NumberSequenceEntity`, `SystemSettingEntity`,
  `DepartmentCategories`, `DepartmentTemplate`, `Packages`. `Packages` is flagged
  in its own comment as needing revisiting if per-tenant pricing is ever added.
- **2 with open questions recorded in the comment**, not resolved by it:
  `PasswordResetOtpEntity` (two tenants with a user at the same address has not
  been reasoned through — Q-006) and `PurchaseOrderJpaRepository` (a scanner
  misfire on a repository).

**Result: 71 HIGH → 53 HIGH, 609 → 591 findings.**

### The remaining 53 cannot be suppressed

`tenant.entity-not-scoped` is the **only** rule in `check_conventions.py` that
accepts a suppression comment. The other six have no such mechanism by design —
the script's own footer says these are "questions to answer, not rules to
silence."

So the remaining 53 will appear in every scan forever:

| Rule | Count | Status |
|---|---|---|
| `pii.in-logs` | 19 | All masked or non-PII — §"pii.in-logs" above |
| `pii.unencrypted-column` | 14 | Blind indexes, published contacts, organisational — above |
| `rbac.feature-single-tenant` | 13 | Historical migrations, fixed forward by V208 |
| `tenant.webhook-no-context` | 4 | 1 was real and is fixed; 3 are not callbacks |
| `tenant.threadlocal-hop` | 2 | Both already correct |
| `rbac.missing-preauthorize` | 1 | `AuthController`, correctly public or session-only |

**This document is their documentation.** A clean scan is not achievable and
should not be the goal — anyone reading a future scan should be pointed here
rather than re-deriving the same conclusions. If the scan is ever used as a CI
gate, gate on *new* findings against this baseline, not on zero.

## Follow-on cards

| Card | Scope |
|---|---|
| ~~Q-006~~ | **Done.** Email encrypted, `email_token` HMAC added, repository and reset flow switched to token lookup, V212 clears the short-lived rows. **The tenant-scoping question for this table remains open** |
| ~~Q-007~~ | **Done.** `migrateVisits`, `migrateNhcxTransactions`, `migratePharmacySales` added and wired into `migratePii()`; V212 adds the `pii_encrypted` progress flags they depend on |
| **F-003** | Confirm ABDM's actual callback signature scheme against the gateway contract |
| ~~F-004~~ | **Done.** 18 entities annotated; 71 → 53 HIGH. The remaining 53 have no suppression mechanism and are documented above |

**Q-007 is not optional.** Until it runs, the four columns hold a mix of
encrypted new rows and plaintext historical ones, and reads of the old rows will
fail decryption. Schedule it with the V208 deployment, not after.
