/** ABDM consent & external health records — Screens 3.1, 3.2 (WO-014). */

/**
 * ABDM purpose-of-request codes.
 *
 * <p>Fixed by ABDM, not by us. The labels are what a clinician recognises;
 * the codes are what the Consent Manager accepts.
 */
export const PURPOSE_CODES = {
  CAREMGT: 'Care management',
  BTG: 'Break the glass (emergency)',
  PUBHLTH: 'Public health',
  HPAYMT: 'Healthcare payment',
  DSRCH: 'Disease-specific research',
  PATRQT: 'Self requested',
} as const

export type PurposeCode = keyof typeof PURPOSE_CODES

/** ABDM health-information types, with clinician-facing labels. */
export const HI_TYPES = {
  DiagnosticReport: 'Lab & diagnostic reports',
  Prescription: 'Prescriptions',
  DischargeSummary: 'Discharge summaries',
  OPConsultation: 'OP consultations',
  ImmunizationRecord: 'Immunisations',
  HealthDocumentRecord: 'Other health documents',
  WellnessRecord: 'Wellness records',
} as const

export type HiType = keyof typeof HI_TYPES

export type ConsentRequestState =
  | 'REQUESTED'
  | 'PENDING_APPROVAL'
  | 'GRANTED'
  | 'DENIED'
  | 'EXPIRED'
  | 'REVOKED'

export const CONSENT_STATE_LABELS: Record<ConsentRequestState, string> = {
  REQUESTED: 'Sending to patient',
  PENDING_APPROVAL: 'Awaiting patient approval',
  GRANTED: 'Granted',
  DENIED: 'Declined by patient',
  EXPIRED: 'Expired',
  REVOKED: 'Withdrawn by patient',
}

export interface ConsentRequest {
  id: string
  requestState: ConsentRequestState
  purposeCode: PurposeCode
  hiTypes: string
  dateRangeFrom: string | null
  dateRangeTo: string | null
  expiresAt: string | null
  createdAt: string
}

export interface ExternalRecord {
  id: string
  hiType: HiType
  recordDate: string | null
  sourceHipName: string | null
  displayTitle: string | null
  imported: boolean
}

export interface ConsentFormState {
  purposeCode: PurposeCode | ''
  hiTypes: HiType[]
  dateRangeFrom: string
  dateRangeTo: string
  expiresAt: string
}

export interface ValidationResult {
  valid: boolean
  errors: string[]
}

/**
 * Validate a consent request before it reaches the Consent Manager.
 *
 * <p>The CM will happily forward a nonsensical request to the patient, who is
 * then asked to approve something that can return nothing. The hospital gets one
 * approval interaction per request, so spending it on a malformed range is worse
 * than a validation error.
 */
export function validateConsentForm(form: ConsentFormState, today = new Date()): ValidationResult {
  const errors: string[] = []

  if (!form.purposeCode) {
    errors.push('Select why the records are needed')
  }
  if (!form.hiTypes.length) {
    errors.push('Select at least one record type')
  }

  const from = form.dateRangeFrom ? new Date(form.dateRangeFrom) : null
  const to = form.dateRangeTo ? new Date(form.dateRangeTo) : null

  if (!from || !to) {
    errors.push('Set the period of records to request')
  } else {
    if (from > to) {
      errors.push('The start date must be on or before the end date')
    }
    if (from > today) {
      errors.push('The start date cannot be in the future')
    }
  }

  if (!form.expiresAt) {
    // Never defaulted: this decides how long the hospital keeps another
    // provider's records.
    errors.push('Set when this consent should expire')
  } else if (new Date(form.expiresAt) <= today) {
    errors.push('The expiry must be in the future')
  }

  return { valid: errors.length === 0, errors }
}

/**
 * Whether a consent is still live.
 *
 * <p>Mirrors ConsentArtifactRules on the server, including the awkward parts:
 * revocation beats a future expiry, and a missing expiry is not permission.
 */
export function isConsentLive(
  request: Pick<ConsentRequest, 'requestState' | 'expiresAt'>,
  now = new Date(),
): boolean {
  if (request.requestState !== 'GRANTED') return false
  if (!request.expiresAt) return false
  return new Date(request.expiresAt) > now
}

/**
 * The state to display, derived rather than taken from the stored value.
 *
 * <p>A request stored as GRANTED goes stale on its own when its expiry passes —
 * nothing writes to it — so showing the raw value would tell a clinician they
 * have access they no longer have.
 */
export function displayState(
  request: Pick<ConsentRequest, 'requestState' | 'expiresAt'>,
  now = new Date(),
): ConsentRequestState {
  if (
    request.requestState === 'GRANTED' &&
    request.expiresAt &&
    new Date(request.expiresAt) <= now
  ) {
    return 'EXPIRED'
  }
  return request.requestState
}

/** Whether the clinician can still act on this request. */
export function isActionable(request: ConsentRequest, now = new Date()): boolean {
  return displayState(request, now) === 'GRANTED'
}

/** Parse the server's comma-separated hi_types back into labels. */
export function hiTypeLabels(csv: string): string[] {
  return csv
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((t) => HI_TYPES[t as HiType] ?? t)
}

/** Group records by type for the viewer's sections. */
export function groupByType(records: ExternalRecord[]): Record<string, ExternalRecord[]> {
  return records.reduce<Record<string, ExternalRecord[]>>((acc, r) => {
    const key = HI_TYPES[r.hiType] ?? r.hiType
    ;(acc[key] ??= []).push(r)
    return acc
  }, {})
}

/**
 * Newest care first, undated last.
 *
 * <p>Undated records sort to the end rather than being treated as epoch, which
 * would float them to the top of a clinical timeline as if they were the oldest
 * events on record.
 */
export function sortByRecordDate(records: ExternalRecord[]): ExternalRecord[] {
  return [...records].sort((a, b) => {
    if (!a.recordDate && !b.recordDate) return 0
    if (!a.recordDate) return 1
    if (!b.recordDate) return -1
    return new Date(b.recordDate).getTime() - new Date(a.recordDate).getTime()
  })
}
