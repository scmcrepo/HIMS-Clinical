/**
 * What may be written to disk, and what may not.
 *
 * WO-019 §4.5. The PRD asks for offline caching of the profile, upcoming
 * appointments and recent visits. That is fine. What is not fine is the natural
 * next step of persisting whatever the query cache happens to hold, because the
 * query cache also holds casesheet free text, potassium results and radiology
 * findings — and a persisted cache is a copy of a medical record sitting in app
 * storage, outside the reach of a DPDP erasure request and readable from a
 * backup of an unlocked phone.
 *
 * So persistence is an allowlist, not a denylist. A new query key is
 * non-persistable until someone adds it here and says why, which is the right
 * default for a health app: forgetting to exclude is silent, forgetting to
 * include is a missing offline tile someone notices.
 */

export const QueryKeys = {
  profile: ["profile"] as const,
  appointments: (scope: "upcoming" | "past") => ["appointments", scope] as const,
  consultants: (q?: string) => ["consultants", q ?? ""] as const,
  availability: (consultantId: string, date: string) =>
    ["availability", consultantId, date] as const,
  visits: (page: number) => ["visits", page] as const,
  visitDetail: (encounterId: string) => ["visit", encounterId] as const,
  casesheet: (encounterId: string) => ["casesheet", encounterId] as const,
  dischargeSummary: (encounterId: string) => ["dischargeSummary", encounterId] as const,
  prescriptions: (encounterId: string) => ["prescriptions", encounterId] as const,
  bills: (encounterId: string) => ["bills", encounterId] as const,
  labReports: (encounterId: string) => ["labReports", encounterId] as const,
  diagnosticReports: (encounterId: string) =>
    ["diagnosticReports", encounterId] as const,
  attachments: (encounterId: string) => ["attachments", encounterId] as const,
};

/**
 * Root query keys permitted in the persisted cache.
 *
 * `visits` is here and `visit` is not, deliberately: the *list* is date, doctor,
 * type and status, which is roughly what a paper appointment card already shows.
 * The *detail* is the diagnosis.
 */
const PERSISTABLE_ROOTS: ReadonlySet<string> = new Set([
  "profile",
  "appointments",
  "visits",
]);

/** Roots that must never be persisted, named so the test can assert on them. */
export const CLINICAL_ROOTS: readonly string[] = [
  "visit",
  "casesheet",
  "dischargeSummary",
  "prescriptions",
  "bills",
  "labReports",
  "diagnosticReports",
  "attachments",
];

export function isPersistable(queryKey: readonly unknown[]): boolean {
  const root = queryKey[0];
  if (typeof root !== "string") return false;
  return PERSISTABLE_ROOTS.has(root);
}

/**
 * How stale a cached answer may be before the UI stops presenting it as current.
 * Appointments get a short window because the front desk can cancel one at any
 * time and a patient trusting a stale card travels to the hospital for nothing.
 */
export const STALE_AFTER_MS = {
  profile: 24 * 60 * 60 * 1000,
  appointments: 5 * 60 * 1000,
  visits: 60 * 60 * 1000,
} as const;

export function isStale(
  root: keyof typeof STALE_AFTER_MS,
  cachedAt: number,
  now: number,
): boolean {
  return now - cachedAt > STALE_AFTER_MS[root];
}
