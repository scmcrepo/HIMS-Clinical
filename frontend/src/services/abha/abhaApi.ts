import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type {
  AbhaLinkage,
  StartEnrolmentRequest,
  VerifyOtpRequest,
} from '../../features/abha/types'

/**
 * ABHA verification & creation (WO-012 / Screen 1.1).
 *
 * <p>Note what is absent: no method returns an unmasked ABHA number, and none
 * accepts an Aadhaar number after the initial enrolment call. The Aadhaar sent
 * to `start` is forwarded to ABDM by the server and never persisted, so it must
 * not be cached in a query key or a store on this side either.
 */
export const abhaApi = {
  start: (cmd: StartEnrolmentRequest) =>
    api.post<ApiResponse<AbhaLinkage>>('/abha/enrolment', cmd).then(r => r.data.data!),

  verify: (linkageId: string, cmd: VerifyOtpRequest) =>
    api
      .post<ApiResponse<AbhaLinkage>>(`/abha/enrolment/${linkageId}/verify`, cmd)
      .then(r => r.data.data!),

  addressAvailable: (abhaAddress: string) =>
    api
      .get<ApiResponse<boolean>>('/abha/address-available', { params: { abhaAddress } })
      .then(r => r.data.data!),

  /**
   * Aadhaar demographic fallback, for patients with no mobile linked to their
   * Aadhaar. A separate call rather than a flag, mirroring the server.
   */
  verifyByDemographics: (
    linkageId: string,
    cmd: { aadhaar: string; name: string; gender: 'M' | 'F' | 'O'; yearOfBirth: string },
  ) =>
    api
      .post<ApiResponse<AbhaLinkage>>(`/abha/enrolment/${linkageId}/verify-demographics`, cmd)
      .then(r => r.data.data!),

  /**
   * Download the ABHA card as a PDF blob.
   *
   * <p>Requires the separate ABHA_CARD_VIEW permission and is audited server
   * side. The blob is handed straight to the browser and never cached here —
   * the response carries no-store for the same reason.
   */
  downloadCard: (patientId: string, purpose?: string) =>
    api
      .get(`/abha/patient/${patientId}/card`, {
        params: purpose ? { purpose } : undefined,
        responseType: 'blob',
      })
      .then(r => r.data as Blob),

  historyFor: (patientId: string) =>
    api.get<ApiResponse<AbhaLinkage[]>>(`/abha/patient/${patientId}`).then(r => r.data.data ?? []),

  /**
   * The patient's active identity, or null.
   *
   * <p>The server answers 204 when the patient has no ABHA, because that is an
   * ordinary state rather than a missing resource. Translating it to null here
   * keeps the badge component free of status-code handling.
   */
  linkedFor: (patientId: string) =>
    api
      .get<ApiResponse<AbhaLinkage>>(`/abha/patient/${patientId}/linked`)
      .then(r => (r.status === 204 ? null : r.data.data ?? null)),
}
