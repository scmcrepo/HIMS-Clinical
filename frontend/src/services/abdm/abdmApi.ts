import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type { ConsentRequest, ExternalRecord, HiType, PurposeCode } from '../../features/abdm/types'

/**
 * ABDM consent & external health records — Screens 3.1, 3.2.
 *
 * <p>Note what {@link listRecords} does not return: payloads. A thirty-record
 * index should not ship thirty decrypted clinical bundles, and each open is
 * separately audited server-side — a bulk fetch would bypass that.
 */
export const abdmApi = {
  requestConsent: (cmd: {
    patientId: string
    encounterId?: string
    purposeCode: PurposeCode
    hiTypes: HiType[]
    dateRangeFrom: string
    dateRangeTo: string
    expiresAt: string
  }) =>
    api
      .post<ApiResponse<ConsentRequest>>('/abdm/consent-requests', cmd)
      .then(r => r.data.data!),

  consentRequestsFor: (patientId: string) =>
    api
      .get<ApiResponse<ConsentRequest[]>>(`/abdm/consent-requests/patient/${patientId}`)
      .then(r => r.data.data ?? []),

  /** Metadata only. Records under an expired or revoked consent are absent. */
  listRecords: (patientId: string) =>
    api
      .get<ApiResponse<ExternalRecord[]>>(`/abdm/records/patient/${patientId}`)
      .then(r => r.data.data ?? []),

  /** Opens one record. Re-checks consent and writes a disclosure audit row. */
  openRecord: (recordId: string) =>
    api
      .get<ApiResponse<{ hiType: string; sourceHipName: string; payload: string }>>(
        `/abdm/records/${recordId}`,
      )
      .then(r => r.data.data!),

  importRecord: (recordId: string, caseSheetId: string) =>
    api
      .post<ApiResponse<ExternalRecord>>(`/abdm/records/${recordId}/import`, null, {
        params: { caseSheetId },
      })
      .then(r => r.data.data!),
}
