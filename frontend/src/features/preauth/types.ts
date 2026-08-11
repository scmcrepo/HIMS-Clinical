/** Cashless pre-authorisation — Screens 4.1 to 4.4 (WO-015). */

export const ESTIMATE_CATEGORIES = {
  ROOM: 'Room & nursing',
  OT: 'Operation theatre',
  IMPLANT: 'Implants',
  CONSUMABLE: 'Consumables',
  INVESTIGATION: 'Investigations',
  PROFESSIONAL: 'Professional fees',
  OTHER: 'Other',
} as const

export type EstimateCategory = keyof typeof ESTIMATE_CATEGORIES

export type PreAuthState = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'QUERY_RAISED'

export const PREAUTH_STATE_LABELS: Record<PreAuthState, string> = {
  SUBMITTED: 'Submitted — awaiting insurer',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  QUERY_RAISED: 'Insurer raised a query',
}

export interface EstimateLine {
  id?: string
  category: EstimateCategory
  description: string
  /** Decimal: half a day of room rent and 1.5 implant units are both real. */
  quantity: number
  unitAmount: number
  lineAmount?: number
  approvedAmount?: number | null
}

export interface PreAuthQuery {
  id: string
  roundNumber: number
  raisedAt: string
  queryCode: string | null
  queryText: string
  respondedAt: string | null
  responseText: string | null
  answered: boolean
}

export interface Enhancement {
  id: string
  sequenceNumber: number
  previousApproved: number
  revisedEstimate: number
  requestedDelta: number
  justification: string
  enhancementState: 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'QUERY_RAISED'
  approvedAmount: number | null
  respondedAt: string | null
}

/**
 * Extend one line, rounding half-up at the paise.
 *
 * <p>Rounds per line and sums the rounded values, matching the server. Summing
 * unrounded lines and rounding the total once produces a figure that does not
 * equal the lines displayed beside it.
 */
export function lineAmount(quantity: number, unitAmount: number): number {
  if (!(quantity > 0)) return 0
  if (unitAmount < 0) return 0
  return Math.round(quantity * unitAmount)
}

export function estimateTotal(lines: EstimateLine[]): number {
  return lines.reduce((sum, l) => sum + lineAmount(l.quantity, l.unitAmount), 0)
}

export function categoryTotal(lines: EstimateLine[], category: EstimateCategory): number {
  return lines
    .filter((l) => l.category === category)
    .reduce((sum, l) => sum + lineAmount(l.quantity, l.unitAmount), 0)
}

/**
 * Room charge the policy will not cover.
 *
 * <p>A null cap means the payer stated no limit, which is not a limit of zero —
 * returning the whole room charge there would quote the patient a shortfall
 * that does not exist.
 */
export function roomShortfall(
  roomCharge: number,
  roomCapPerDay: number | null | undefined,
  expectedLosDays: number,
): number {
  if (roomCapPerDay === null || roomCapPerDay === undefined) return 0
  if (expectedLosDays < 0) return 0
  return Math.max(0, roomCharge - roomCapPerDay * expectedLosDays)
}

/**
 * What the patient is likely to pay out of pocket.
 *
 * <p>Deductible first, then co-pay on what remains, then any room shortfall.
 * Applying co-pay to the pre-deductible figure overstates the patient's share,
 * and this is the number quoted at the desk before admission.
 */
export function patientLiability(
  estimate: number,
  coPayBasisPoints: number | null | undefined,
  deductible: number | null | undefined,
  roomShortfallAmount: number,
): number {
  if (estimate < 0) return 0
  const ded = Math.max(0, deductible ?? 0)
  const afterDeductible = Math.max(0, estimate - ded)
  const appliedDeductible = estimate - afterDeductible

  let coPay = 0
  if (coPayBasisPoints && coPayBasisPoints > 0 && coPayBasisPoints <= 10_000) {
    coPay = Math.round((afterDeductible * coPayBasisPoints) / 10_000)
  }

  return appliedDeductible + coPay + roomShortfallAmount
}

/** Unknown balance does not block: an unverified policy is not proof of no cover. */
export function exceedsAvailableBalance(estimate: number, balance: number | null): boolean {
  return balance !== null && estimate > balance
}

export function enhancementDelta(previousApproved: number, revisedEstimate: number): number {
  return Math.max(0, revisedEstimate - previousApproved)
}

export interface ValidationResult {
  valid: boolean
  errors: string[]
}

/** Screen 4.1 — validated before the insurer sees a malformed request. */
export function validatePreAuthForm(form: {
  diagnosisCode: string
  plannedProcedure: string
  expectedLosDays: number | null
  lines: EstimateLine[]
}): ValidationResult {
  const errors: string[] = []

  if (!form.diagnosisCode?.trim()) {
    // Payers reject undiagnosed pre-auths, and the rejection lands days later
    // with the patient already admitted.
    errors.push('Select an ICD-10 diagnosis')
  }
  if (!form.plannedProcedure?.trim()) {
    errors.push('Describe the planned procedure')
  }
  if (form.expectedLosDays !== null && form.expectedLosDays < 0) {
    errors.push('Expected length of stay cannot be negative')
  }
  if (!form.lines.length) {
    errors.push('Add at least one estimate line')
  }
  if (form.lines.some((l) => !l.description?.trim())) {
    errors.push('Every estimate line needs a description')
  }
  if (form.lines.some((l) => !(l.quantity > 0))) {
    errors.push('Every estimate line needs a quantity above zero')
  }

  return { valid: errors.length === 0, errors }
}

/** Screen 4.4 — an enhancement must ask for more than is already approved. */
export function validateEnhancement(
  previousApproved: number,
  revisedEstimate: number,
  justification: string,
): ValidationResult {
  const errors: string[] = []

  if (!(revisedEstimate > previousApproved)) {
    // Asking for less is a data-entry error; the right action is a claim.
    errors.push('The revised estimate must be higher than the amount already approved')
  }
  if (!justification?.trim()) {
    errors.push('Explain why more cover is needed')
  }

  return { valid: errors.length === 0, errors }
}

/** Rounds still waiting on the hospital, oldest first. */
export function pendingQueries(queries: PreAuthQuery[]): PreAuthQuery[] {
  return queries
    .filter((q) => !q.answered)
    .sort((a, b) => new Date(a.raisedAt).getTime() - new Date(b.raisedAt).getTime())
}

/** Whether the desk still owes the insurer an answer. */
export function awaitingHospital(queries: PreAuthQuery[]): boolean {
  return queries.some((q) => !q.answered)
}
