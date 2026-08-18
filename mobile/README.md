# HIMS Patient — mobile app

Cross-platform (iOS + Android) patient self-service client for HIMS-Clinical.
Single codebase, Expo + React Native + TypeScript.

Requirements: `hims_patient_self_service_portal.md` and
`patient_portal_workflow_for_approval.md`.
Work orders: `.hms-agent/work-orders/WO-017`, `WO-018`, `WO-019`.

---

## Read this first: the app cannot work yet

The `/api/portal/**` endpoints this client calls **do not exist in the backend**.
WO-017 and WO-018 define them; neither has been implemented. `src/core/contracts.ts`
is the agreed contract in the meantime.

So: the core logic is tested and green, the auth screens are written, and nothing
end-to-end runs until the backend cards land.

## Status

| Card | Scope | State |
|---|---|---|
| M-001 | Workspace, tooling, tokens, i18n, contracts | **Done, verified** |
| M-002 | API client, transport, session, secure store | Core verified; native store uncompiled |
| M-003 | Resolution machine + booking rules | **Done, verified** |
| M-004 | Auth screens (login, OTP, hospital, profile, branch) | Written, not run |
| M-005 | Dashboard, appointments, cancel/reschedule | Dashboard only |
| M-006 | Booking flow | Not started |
| M-007 | Visit history, 4-tab detail, viewer | Not started |
| M-008 | Registration, settings, consent withdrawal | Not started |

"Verified" means `tsc --noEmit` clean and 121 vitest assertions green under Node.
"Written, not run" means it has never been compiled — the Expo SDK is not
installed in the environment where it was authored, and no simulator has opened it.
Treat every `.tsx` file as first-draft until `npx tsc --noEmit` passes with
dependencies present.

## Setup

```bash
cd mobile
npm install
npx tsc --noEmit          # full typecheck, needs the Expo SDK installed
npm test                  # core logic, no native deps needed
npx expo start            # then press i (iOS) or a (Android)
```

Point the app at a backend by editing `expo.extra.apiBaseUrl` in `app.json`, or
by overriding it in `createContainer({ baseUrl })`. There is no default that
works — a default pointing at a demo server is how a test build ends up talking
to production data.

Builds: `eas build --platform ios` / `--platform android`. Requires the
hospital's Apple Developer and Google Play accounts; nothing here has been run
against real credentials.

## Layout

```
src/
  core/      Pure TypeScript. No react, react-native or expo imports —
             enforced by __tests__/invariants.test.ts. This is the layer that
             decides what a patient sees, so it is the layer with tests.
  state/     Zustand stores, secure token storage, composition root.
             Native modules live here and only here.
  ui/        Design tokens and primitives.
  app/       expo-router file routes.
  i18n/      Message packs. Every user-facing string goes through t().
__tests__/   Runs against core/ only, under plain Node.
```

## Things worth knowing before you change something

**Tokens go to `expo-secure-store`, never AsyncStorage.** A test scans every
import in `src/` and fails the build if AsyncStorage appears anywhere. AsyncStorage
is a plaintext file that any device backup or rooted dump reads straight out.

**Refresh is single-flight.** WO-017 rotates refresh tokens and treats reuse of a
consumed one as credential theft — it revokes the whole chain and alerts. A
dashboard firing five queries that all 401 must therefore produce one refresh
call, not five, or the patient's own phone trips the theft detector. See
`SessionManager.refresh` and the concurrency test in `__tests__/session.test.ts`.

**Clinical data is never written to disk.** `core/cachePolicy.ts` is an
allowlist: profile, appointments and the visit *list* persist; the visit
*detail*, casesheets, lab values, radiology findings and attachments do not. A new
query key is non-persistable until someone adds it deliberately. This is why a
patient's diagnosis is not sitting in app storage after a phone is stolen, and
why a DPDP erasure request does not have to reach the handset.

**No offline booking queue, deliberately.** The PRD asks for one (§10.3). A
booking queued at 09:00 and delivered at 13:00 against a slot that filled at
10:00 produces a patient who believes they have an appointment and a hospital
that has never heard of them. The app shows an offline state instead. Reverse
this only with a server-side hold mechanism.

**No third-party analytics or crash SDK.** A crash reporter uploading a stack
trace with a patient name in a local variable is a cross-border PHI transfer.
If crash reporting is added, it must be India-region and PII-scrubbed.

**Booking sends a mandatory `Idempotency-Key`.** It is a required parameter of
`PortalApi.bookAppointment`, not an option, because a double-tap on a slow
connection is the single most likely real-world defect in this app.

## Release checklist (not yet done)

- [ ] Privacy policy URL — both stores reject health apps without one
- [ ] Apple: health-data questionnaire in App Store Connect
- [ ] Google Play: Data Safety form, declaring health data collection
- [ ] `FLAG_SECURE` on visit detail and report viewer (Android; iOS has no equivalent)
- [ ] Confirm `apiBaseUrl` points at the intended environment per build profile
- [ ] Accessibility pass with TalkBack and VoiceOver
- [ ] Tamil and Hindi packs — `registerPack()`, then `missingKeys()` should be empty
