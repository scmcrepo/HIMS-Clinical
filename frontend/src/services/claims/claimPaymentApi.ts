import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type { DeductionLine, PaymentAdvice } from '../../features/claims/types'

/**
 * Claim disbursal and bank reconciliation — Screens 5.2, 5.3.
 *
 * <p>Guarded server-side by CLAIM_PAYMENTS rather than NHCX_CLAIMS: confirming
 * money reached the hospital is an accounts function, and the person who files
 * claims should not also certify that they were paid.
 */
export const claimPaymentApi = {
  /** Advices awaiting a bank confirmation — the accounts work queue. */
  pendingReconciliation: () =>
    api
      .get<ApiResponse<PaymentAdvice[]>>('/insurance/claims/payments/pending')
      .then(r => r.data.data ?? []),

  advicesFor: (transactionId: string) =>
    api
      .get<ApiResponse<PaymentAdvice[]>>(`/insurance/claims/${transactionId}/payments`)
      .then(r => r.data.data ?? []),

  deductionsFor: (transactionId: string) =>
    api
      .get<ApiResponse<DeductionLine[]>>(`/insurance/claims/${transactionId}/deductions`)
      .then(r => r.data.data ?? []),

  /**
   * Confirm what the bank actually credited.
   *
   * <p>The amount is always sent, even when it matches the advice. Making the
   * accountant enter the figure they read off the statement is the control;
   * defaulting it would turn reconciliation into a rubber stamp.
   */
  reconcile: (adviceId: string, bankCreditedPaise: number, note?: string) =>
    api
      .post<ApiResponse<PaymentAdvice>>(
        `/insurance/claims/payments/${adviceId}/reconcile`,
        { bankCreditedPaise, note },
      )
      .then(r => r.data.data!),

  /** Challenge one disallowed line — Screen 5.2. */
  disputeLine: (lineId: string, note?: string) =>
    api
      .post<ApiResponse<DeductionLine>>(
        `/insurance/claims/deductions/${lineId}/dispute`,
        null,
        { params: note ? { note } : undefined },
      )
      .then(r => r.data.data!),
}
