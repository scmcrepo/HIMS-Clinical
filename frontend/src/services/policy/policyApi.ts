import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type { DiscoveredPolicy, PolicyCoverage } from '../../features/policy/types'

/**
 * Policy discovery & coverage — Screens 1.2, 2.1.
 *
 * <p>Discovery is asynchronous end to end. `requestOtp` and `confirmDiscovery`
 * return a correlation id, not policies: NHCX acknowledges and the registry
 * answers later on a callback. Callers poll `discoveredFor`; anything that
 * awaits a result inline will work against a mock and hang in production.
 */
export const policyApi = {
  requestOtp: (patientId: string, identifier: string) =>
    api
      .post<ApiResponse<{ correlationId: string }>>('/policy/discovery/otp', {
        patientId,
        identifier,
      })
      .then(r => r.data.data!.correlationId),

  confirmDiscovery: (patientId: string, correlationId: string, otp: string) =>
    api
      .post<ApiResponse<{ correlationId: string }>>('/policy/discovery/confirm', {
        patientId,
        correlationId,
        otp,
      })
      .then(r => r.data.data!.correlationId),

  discoveredFor: (patientId: string) =>
    api
      .get<ApiResponse<DiscoveredPolicy[]>>(`/policy/discovery/patient/${patientId}`)
      .then(r => r.data.data ?? []),

  /** Accept a discovered policy against an insurance record. */
  link: (discoveredId: string, insuranceId: string) =>
    api
      .post<ApiResponse<DiscoveredPolicy>>(
        `/policy/discovery/${discoveredId}/link`,
        null,
        { params: { insuranceId } },
      )
      .then(r => r.data.data!),

  /** 204 when no check has run — an ordinary state, not an error. */
  latestCoverage: (patientId: string) =>
    api
      .get<ApiResponse<PolicyCoverage>>(`/policy/coverage/patient/${patientId}/latest`)
      .then(r => (r.status === 204 ? null : r.data.data ?? null)),

  coverageHistory: (patientId: string) =>
    api
      .get<ApiResponse<PolicyCoverage[]>>(`/policy/coverage/patient/${patientId}`)
      .then(r => r.data.data ?? []),
}
