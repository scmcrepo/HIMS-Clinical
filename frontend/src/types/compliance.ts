/**
 * DPDP compliance types — consent capture and data principal rights.
 *
 * Mirrors the backend contracts added in WO-022 and WO-024.
 */

/** Purposes a patient can consent to. Mirrors `ConsentPurpose` on the server. */
export type ConsentPurpose =
  | 'TREATMENT'
  | 'AGENT_MESSAGING'
  | 'AGENT_VOICE'
  | 'INSURANCE_CLAIM'
  | 'ABHA_LINKAGE'
  | 'PORTAL_SELF_ACCESS'
  | 'MARKETING'

/** Where a consent record came from, and therefore whether it can be relied on. */
export type ConsentProvenance =
  | 'STAFF_ATTESTED'
  | 'PATIENT_DIGITAL'
  /** Written by the pre-V205 self-granting defect. Not valid consent. */
  | 'SYSTEM_INFERRED'
  | 'IMPORTED'

/** One consent record, as returned by the history endpoint. */
export interface ConsentRecord {
  id: string
  patientId: string
  purpose: ConsentPurpose
  state: 'GRANTED' | 'WITHDRAWN' | 'EXPIRED'
  provenance: ConsentProvenance
  /** False for SYSTEM_INFERRED — shown so an operator can see why a patient is being re-asked. */
  reliable: boolean
  noticeVersion?: string
  noticeLanguage?: string
  noticeTextHash?: string
  captureChannel?: string
  capturedBy?: string
  grantedAt?: string
  expiresAt?: string
  withdrawnAt?: string
  withdrawalChannel?: string
  minor: boolean
  guardianVerified: boolean
}

/** Live state for one purpose, with the notice that would be shown if asked now. */
export interface PurposeStatus {
  purpose: ConsentPurpose
  summary: string
  requiredForCare: boolean
  granted: boolean
  noticeVersion?: string
  noticeLanguage?: string
  noticeText?: string
  /** The notice is a V205/V207 placeholder, not counsel-approved wording. */
  noticeIsDraft: boolean
  noticeMissing: boolean
}

/**
 * The staff attestation sent back after the patient has been shown the notice.
 *
 * `capturedBy` is deliberately absent: the server reads the capturing user from
 * the session. A client that could name its own capturer could name anyone,
 * which would make the audit field worthless.
 */
export interface ConsentAttestation {
  noticeVersion: string
  noticeLanguage: string
  /** Must be true. The server rejects false rather than treating it as a no-op. */
  patientAgreed: boolean
  minor: boolean
  guardianVerified: boolean
}

/** Body of the 409 the server returns when consent is missing. */
export interface ConsentRequiredPayload {
  code: 'CONSENT_REQUIRED'
  purpose: ConsentPurpose
  requiredForCare: boolean
  noticeVersion?: string
  noticeLanguage?: string
  noticeText?: string
}

export type RightsRequestType = 'ERASURE' | 'CORRECTION'

export type RightsRequestState =
  | 'RECEIVED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'REJECTED'

export type VerificationMethod =
  | 'PORTAL_OTP'
  | 'IN_PERSON_ID'
  | 'ABHA_VERIFIED'
  | 'REGISTERED_POST'
  | 'STAFF_OVERRIDE'

/**
 * Note what this does not contain: no patient name, no contact detail. A queue
 * of erasure requests is a list of people who asked to be forgotten, and putting
 * their names on an admin screen would be its own small irony. Operators work
 * from the id and open the patient record separately, where that access is
 * audited.
 */
export interface RightsRequest {
  id: string
  patientId: string
  requestType: RightsRequestType
  state: RightsRequestState
  requestedAt: string
  requestedVia?: string
  requestedByPatient: boolean
  requesterVerifiedAt?: string
  verificationMethod?: VerificationMethod
  dueAt?: string
  overdue: boolean
  completedAt?: string
  rejectionReason?: string
  retainedReason?: string
}

/** What happened in one store. ERASED | ANONYMISED | RETAINED | FAILED | PENDING */
export interface TargetOutcome {
  store: string
  outcome: 'PENDING' | 'ERASED' | 'ANONYMISED' | 'RETAINED' | 'FAILED'
  rowsAffected?: number
  detail?: string
  processedAt?: string
}

/**
 * The patient-facing evidence that an erasure was real, and the account of
 * anything kept. A refusal to erase is only lawful if the patient is told it
 * happened and why, which is what `retained` and `retainedReason` carry.
 */
export interface ErasureReceipt {
  request: RightsRequest
  targets: TargetOutcome[]
  erased: number
  anonymised: number
  retained: number
  failed: number
}

// ── Security incidents (WO-026) ──────────────────────────────────────────

export type IncidentCategory =
  | 'CROSS_TENANT_ACCESS' | 'UNAUTHORISED_ACCESS' | 'DATA_LOSS' | 'DATA_EXPOSURE'
  | 'CREDENTIAL_COMPROMISE' | 'INTEGRITY_COMPROMISE' | 'AVAILABILITY' | 'OTHER'

export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export type IncidentState = 'OPEN' | 'CONTAINED' | 'NOTIFIED' | 'CLOSED' | 'DISMISSED'

export interface SecurityIncident {
  id: string
  incidentRef: string
  category: IncidentCategory
  severity: IncidentSeverity
  detectedAt: string
  occurredAt?: string
  detectionSource: string
  summary: string
  detail?: string
  dataCategories?: string
  affectedPrincipalCount: number
  /** The blast radius could not be established. Not the same as zero. */
  scopeUncertain: boolean
  state: IncidentState
  containedAt?: string
  boardNotifiedAt?: string
  boardDetailReportAt?: string
  boardReference?: string
  principalsNotifiedAt?: string
  remediation?: string
  rootCause?: string
}

// ── Grievances (WO-027) ──────────────────────────────────────────────────

export type GrievanceState =
  | 'RECEIVED' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'WITHDRAWN'

export interface Grievance {
  id: string
  grievanceRef: string
  patientId?: string
  category: string
  channel: string
  subject: string
  body?: string
  receivedAt: string
  /** Internal target, earlier than dueAt so the statutory ceiling isn't the norm. */
  targetAt: string
  /** Statutory ceiling. */
  dueAt: string
  state: GrievanceState
  acknowledgedAt?: string
  resolvedAt?: string
  resolution?: string
  escalatedToBoard: boolean
  boardReference?: string
  incidentId?: string
}

export interface GrievanceEvent {
  id: string
  eventType: string
  note?: string
  /** Whether the complainant was told, not just the file updated. */
  communicated: boolean
  occurredAt: string
}

export interface ComplianceContact {
  id: string
  displayName: string
  designation?: string
  email: string
  phone?: string
  postalAddress?: string
  isDpo: boolean
  basedInIndia: boolean
}

// ── Retention (WO-025) ───────────────────────────────────────────────────

export interface RetentionPolicy {
  id: string
  targetStore: string
  dateColumn: string
  retentionDays: number
  action: 'DELETE' | 'ANONYMISE'
  anonymiseColumn: string
  justification: string
  statutoryBasis?: string
  /** Both default false/true. A policy only destroys data when enabled && !dryRun. */
  enabled: boolean
  dryRun: boolean
  maxRowsPerRun: number
  lastRunAt?: string
  lastRunAffected?: number
}

export interface RetentionRun {
  id: string
  startedAt: string
  completedAt?: string
  state: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'ABORTED'
  dryRun: boolean
  policiesEvaluated: number
  rowsAffected: number
  errorDetail?: string
}

export interface RetentionRunItem {
  id: string
  targetStore: string
  action: string
  cutoffAt: string
  rowsMatched: number
  rowsAffected: number
  capped: boolean
  outcome: 'DRY_RUN' | 'APPLIED' | 'SKIPPED' | 'FAILED' | 'CAPPED'
  detail?: string
}
