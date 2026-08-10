/** NHCX claims & payment control tower — Screens 5.2, 5.3 (WO-016). */

/** The five financial statuses from the flow document, in lifecycle order. */
export const FINANCIAL_STATES = [
  'CLAIM_SUBMITTED',
  'CLAIM_APPROVED',
  'PAYMENT_INITIATED',
  'AMOUNT_RECEIVED_IN_BANK',
  'CLAIM_DISPUTED',
] as const;

export type FinancialState = (typeof FINANCIAL_STATES)[number];

export const FINANCIAL_STATE_LABELS: Record<FinancialState, string> = {
  CLAIM_SUBMITTED: 'Submitted — pending insurer review',
  CLAIM_APPROVED: 'Approved / adjudicated',
  PAYMENT_INITIATED: 'Payment initiated by insurer',
  AMOUNT_RECEIVED_IN_BANK: 'Received in hospital bank',
  CLAIM_DISPUTED: 'Disputed / deducted',
};

export type DeductionCategory =
  | 'NON_MEDICAL'
  | 'NOT_COVERED'
  | 'EXCEEDS_LIMIT'
  | 'DOCUMENT_MISSING'
  | 'TDS'
  | 'OTHER';

export interface DeductionLine {
  id: string;
  reasonCategory: DeductionCategory;
  reasonCode: string | null;
  description: string;
  amount: number;
  disputed: boolean;
}

export interface PaymentAdvice {
  id: string;
  utrNumber: string;
  paymentDate: string | null;
  grossAmount: number;
  tdsAmount: number;
  deductionAmount: number;
  netDisbursedAmount: number;
  reconciled: boolean;
  bankCreditedAmount: number | null;
}

export interface ClaimRow {
  id: string;
  correlationId: string;
  payerCode: string;
  financialState: FinancialState;
  claimedAmount: number | null;
  approvedAmount: number | null;
  disallowedAmount: number | null;
  patientCopayAmount: number | null;
  advices: PaymentAdvice[];
}

/**
 * The five metric cards at the top of Screen 5.2.
 *
 * <p>"Received" counts only reconciled advices — money the accounts team has
 * confirmed against a bank statement. An insurer asserting it paid is not the
 * same as the hospital having the money, and a dashboard that conflates them
 * reports revenue the hospital does not hold.
 */
export interface ControlTowerMetrics {
  totalClaimed: number;
  totalApproved: number;
  totalReceivedInBank: number;
  totalPendingDisbursal: number;
  totalDisallowed: number;
}

export function computeMetrics(claims: ClaimRow[]): ControlTowerMetrics {
  let totalClaimed = 0;
  let totalApproved = 0;
  let totalReceivedInBank = 0;
  let totalDisallowed = 0;

  for (const c of claims) {
    totalClaimed += c.claimedAmount ?? 0;
    totalApproved += c.approvedAmount ?? 0;
    totalDisallowed += c.disallowedAmount ?? 0;

    for (const a of c.advices) {
      if (a.reconciled) {
        // The bank figure, not the advised one — this card answers "what do we
        // actually hold", and those differ precisely when something went wrong.
        totalReceivedInBank += a.bankCreditedAmount ?? 0;
      }
    }
  }

  return {
    totalClaimed,
    totalApproved,
    totalReceivedInBank,
    // Approved but not yet in the bank. Never negative: an over-credit is a
    // reconciliation exception, not negative money owed to the hospital.
    totalPendingDisbursal: Math.max(0, totalApproved - totalDisallowed - totalReceivedInBank),
    totalDisallowed,
  };
}

/** Signed gap between what the insurer advised and what the bank credited. */
export function reconciliationGap(advice: PaymentAdvice): number | null {
  if (advice.bankCreditedAmount === null) return null;
  return advice.netDisbursedAmount - advice.bankCreditedAmount;
}

/**
 * Whether an advice reconciles exactly.
 *
 * <p>No tolerance. Absorbing a small shortfall silently is how a hospital loses
 * a material sum across a year of claims.
 */
export function reconcilesExactly(advice: PaymentAdvice): boolean {
  return reconciliationGap(advice) === 0;
}

/** Net the insurer should have sent, from its own stated components. */
export function expectedNet(advice: PaymentAdvice): number {
  return Math.max(0, advice.grossAmount - advice.tdsAmount - advice.deductionAmount);
}

/**
 * Whether the payer's own arithmetic is internally consistent.
 *
 * <p>Payers do send advices where gross minus TDS minus deductions does not
 * equal the stated net. Flagging it before an accountant tries to match it to a
 * bank line saves the investigation.
 */
export function adviceIsSelfConsistent(advice: PaymentAdvice): boolean {
  return expectedNet(advice) === advice.netDisbursedAmount;
}

/** TDS alone is statutory withholding, not something to dispute with the payer. */
export function warrantsDispute(claim: ClaimRow): boolean {
  return (claim.disallowedAmount ?? 0) > 0;
}

/** Claims needing someone's attention, worst first. */
export function needsAttention(claims: ClaimRow[]): ClaimRow[] {
  return claims.filter(
    (c) =>
      c.financialState === 'CLAIM_DISPUTED' ||
      c.advices.some((a) => !a.reconciled) ||
      c.advices.some((a) => !adviceIsSelfConsistent(a)),
  );
}

/**
 * Progress through the lifecycle, for the timeline badge.
 * CLAIM_DISPUTED is off the happy path and returns -1 rather than a position.
 */
export function lifecycleIndex(state: FinancialState): number {
  if (state === 'CLAIM_DISPUTED') return -1;
  return FINANCIAL_STATES.indexOf(state);
}
