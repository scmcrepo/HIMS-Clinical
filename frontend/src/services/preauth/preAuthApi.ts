import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type { Enhancement, EstimateLine, PreAuthQuery } from '../../features/preauth/types'

/**
 * Cashless pre-authorisation — Screens 4.1 to 4.4.
 *
 * <p>Submission returns a correlation id, not a decision. The insurer answers on
 * a callback minutes to days later; a caller written to expect an inline outcome
 * works against a mock and hangs in production.
 */
export const preAuthApi = {
  submit: (cmd: {
    patientId: string
    encounterId?: string
    insuranceId?: string
    payerCode: string
    diagnosisCode: string
    diagnosisText?: string
    plannedProcedure: string
    expectedLosDays?: number | null
    roomType?: string
    lines: Array<{
      category: string
      description: string
      quantity: number
      unitAmountPaise: number
    }>
  }) =>
    api
      .post<ApiResponse<{ id: string; correlationId: string; estimatedAmount: number }>>(
        '/preauth',
        cmd,
      )
      .then(r => r.data.data!),

  estimateFor: (transactionId: string) =>
    api
      .get<ApiResponse<EstimateLine[]>>(`/preauth/${transactionId}/estimate`)
      .then(r => r.data.data ?? []),

  queriesFor: (transactionId: string) =>
    api
      .get<ApiResponse<PreAuthQuery[]>>(`/preauth/${transactionId}/queries`)
      .then(r => r.data.data ?? []),

  /** Everything the insurer is still waiting on — the desk's work queue. */
  unansweredQueries: () =>
    api
      .get<ApiResponse<PreAuthQuery[]>>('/preauth/queries/unanswered')
      .then(r => r.data.data ?? []),

  respondToQuery: (queryId: string, responseText: string, attachmentIds?: string) =>
    api
      .post<ApiResponse<PreAuthQuery>>(`/preauth/queries/${queryId}/respond`, {
        responseText,
        attachmentIds,
      })
      .then(r => r.data.data!),

  enhancementsFor: (transactionId: string) =>
    api
      .get<ApiResponse<Enhancement[]>>(`/preauth/${transactionId}/enhancements`)
      .then(r => r.data.data ?? []),

  requestEnhancement: (transactionId: string, revisedEstimatePaise: number, justification: string) =>
    api
      .post<ApiResponse<Enhancement>>(`/preauth/${transactionId}/enhancements`, {
        revisedEstimatePaise,
        justification,
      })
      .then(r => r.data.data!),
}
