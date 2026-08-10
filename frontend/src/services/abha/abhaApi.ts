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
