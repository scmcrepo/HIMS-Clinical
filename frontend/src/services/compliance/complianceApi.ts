import type { AxiosError } from 'axios'
import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type {
  ComplianceContact,
  ConsentPurpose,
  ConsentRecord,
  ConsentRequiredPayload,
  PurposeStatus,
  ErasureReceipt,
  RightsRequest,
  RightsRequestState,
  RightsRequestType,
  VerificationMethod,
  Grievance,
  GrievanceEvent,
  RetentionPolicy,
  RetentionRun,
  RetentionRunItem,
  SecurityIncident,
} from '../../types/compliance'

/**
 * Data principal rights — WO-024.
 *
 * There is deliberately no bulk endpoint here, mirroring the server. Erasure is
 * irreversible for the DELETE targets, and a call that erases a list is one
 * malformed request away from erasing a hospital.
 */
export const complianceApi = {
  raise: (body: {
    patientId: string
    requestType: RightsRequestType
    requestedVia?: string
    requestedByPatient?: boolean
    correctionPayload?: Record<string, unknown>
  }) =>
    api
      .post<ApiResponse<RightsRequest>>('/compliance/rights', body)
      .then(r => r.data.data!),

  /** Records that the requester was proved to be the patient. Unlocks the sweep. */
  verify: (id: string, method: VerificationMethod) =>
    api
      .post<ApiResponse<RightsRequest>>(`/compliance/rights/${id}/verify`, { method })
      .then(r => r.data.data!),

  /** Runs the sweep. Irreversible. Returns the per-store receipt. */
  execute: (id: string) =>
    api
      .post<ApiResponse<ErasureReceipt>>(`/compliance/rights/${id}/execute`, {})
      .then(r => r.data.data!),

  reject: (id: string, reason: string) =>
    api
      .post<ApiResponse<RightsRequest>>(`/compliance/rights/${id}/reject`, { reason })
      .then(r => r.data.data!),

  get: (id: string) =>
    api
      .get<ApiResponse<ErasureReceipt>>(`/compliance/rights/${id}`)
      .then(r => r.data.data!),

  queue: (state?: RightsRequestState) =>
    api
      .get<ApiResponse<RightsRequest[]>>('/compliance/rights', {
        params: state ? { state } : undefined,
      })
      .then(r => r.data.data ?? []),

  historyFor: (patientId: string) =>
    api
      .get<ApiResponse<RightsRequest[]>>(`/compliance/rights/patient/${patientId}`)
      .then(r => r.data.data ?? []),
}

/**
 * Consent management — WO-023.
 *
 * Withdrawal is deliberately the plainest call here: a purpose and a channel,
 * no confirmation body, no reason field. Consent harder to withdraw than to give
 * is not freely given.
 */
export const consentApi = {
  historyFor: (patientId: string) =>
    api
      .get<ApiResponse<ConsentRecord[]>>(`/compliance/consent/patient/${patientId}`)
      .then(r => r.data.data ?? []),

  statusFor: (patientId: string, language = 'en') =>
    api
      .get<ApiResponse<PurposeStatus[]>>(
        `/compliance/consent/patient/${patientId}/status`,
        { params: { language } },
      )
      .then(r => r.data.data ?? []),

  grant: (
    patientId: string,
    body: {
      purpose: ConsentPurpose
      noticeVersion: string
      noticeLanguage: string
      captureChannel: string
      patientAgreed: true
      minor: boolean
      guardianVerified: boolean
    },
  ) =>
    api
      .post<ApiResponse<ConsentRecord>>(
        `/compliance/consent/patient/${patientId}/grant`,
        body,
      )
      .then(r => r.data.data!),

  /**
   * The notice text for a purpose in a given language.
   *
   * Backs the language selector in ConsentGateModal (WO-023 / E-005). The text
   * is fetched rather than translated in the browser: the hash stored against
   * the consent record is computed server-side over the server's copy of the
   * text for that exact language and version, so anything assembled on the
   * client would drift from what the record claims was shown.
   *
   * Rejects when the hospital has no notice on file for that language, which
   * the caller must surface rather than silently falling back to English.
   */
  notice: (purpose: ConsentPurpose, language: string) =>
    api
      .get<ApiResponse<{ purpose: ConsentPurpose; version: string; language: string; bodyText: string; draft: boolean }>>(
        '/compliance/consent/notice',
        { params: { purpose, language } },
      )
      .then(r => r.data.data!),

  withdraw: (patientId: string, purpose: ConsentPurpose, channel = 'STAFF_PORTAL') =>
    api
      .post<ApiResponse<void>>(`/compliance/consent/patient/${patientId}/withdraw`, {
        purpose,
        channel,
      })
      .then(r => r.data),
}

/**
 * Recognise the 409 the consent gate raises.
 *
 * Before WO-022 this response could not occur: three services granted the
 * consent they were about to check, so the gate never failed. Now it can, and
 * any caller that touches ABHA, policy discovery or pre-auth has to handle it.
 *
 * Returns the payload when the error is a consent refusal, or null for every
 * other failure — so callers can branch without inspecting status codes
 * themselves.
 */
export function asConsentRequired(error: unknown): ConsentRequiredPayload | null {
  const axiosError = error as AxiosError<ApiResponse<ConsentRequiredPayload>>
  if (axiosError?.response?.status !== 409) return null

  const payload = axiosError.response.data?.data
  return payload?.code === 'CONSENT_REQUIRED' ? payload : null
}

/** Security incident register — WO-026. */
export const incidentApi = {
  queue: (state?: string) =>
    api
      .get<ApiResponse<SecurityIncident[]>>('/compliance/incidents', {
        params: state ? { state } : undefined,
      })
      .then(r => r.data.data ?? []),

  get: (id: string) =>
    api
      .get<ApiResponse<SecurityIncident>>(`/compliance/incidents/${id}`)
      .then(r => r.data.data!),

  raise: (body: {
    category: string
    severity: string
    summary: string
    detail?: string
    dataCategories?: string
    detectedAt?: string
    scopeUncertain: boolean
  }) =>
    api
      .post<ApiResponse<SecurityIncident>>('/compliance/incidents', body)
      .then(r => r.data.data!),

  contain: (id: string, remediation: string) =>
    api
      .post<ApiResponse<SecurityIncident>>(`/compliance/incidents/${id}/contain`, {
        remediation,
      })
      .then(r => r.data.data!),

  /** Records that a human filed with the Board. Does not file anything itself. */
  recordBoardNotification: (id: string, boardReference: string, detailReport: boolean) =>
    api
      .post<ApiResponse<SecurityIncident>>(
        `/compliance/incidents/${id}/board-notification`,
        { boardReference, detailReport },
      )
      .then(r => r.data.data!),

  draftNotice: (id: string, contactPoint?: string) =>
    api
      .get<ApiResponse<{ notice: string }>>(`/compliance/incidents/${id}/notice`, {
        params: contactPoint ? { contactPoint } : undefined,
      })
      .then(r => r.data.data!.notice),

  dismiss: (id: string, reason: string) =>
    api
      .post<ApiResponse<SecurityIncident>>(`/compliance/incidents/${id}/dismiss`, { reason })
      .then(r => r.data.data!),
}

/** Grievance redressal — WO-027. */
export const grievanceApi = {
  queue: (state?: string) =>
    api
      .get<ApiResponse<Grievance[]>>('/compliance/grievances', {
        params: state ? { state } : undefined,
      })
      .then(r => r.data.data ?? []),

  get: (id: string) =>
    api
      .get<ApiResponse<{ grievance: Grievance; timeline: GrievanceEvent[]; overdue: boolean }>>(
        `/compliance/grievances/${id}`,
      )
      .then(r => r.data.data!),

  raise: (body: {
    patientId?: string
    complainantContact?: string
    category: string
    channel: string
    subject: string
    body?: string
  }) =>
    api
      .post<ApiResponse<Grievance>>('/compliance/grievances', body)
      .then(r => r.data.data!),

  acknowledge: (id: string, note?: string) =>
    api
      .post<ApiResponse<Grievance>>(`/compliance/grievances/${id}/acknowledge`, { note })
      .then(r => r.data.data!),

  resolve: (id: string, resolution: string) =>
    api
      .post<ApiResponse<Grievance>>(`/compliance/grievances/${id}/resolve`, { resolution })
      .then(r => r.data.data!),

  recordEscalation: (id: string, boardReference: string) =>
    api
      .post<ApiResponse<Grievance>>(`/compliance/grievances/${id}/escalation`, {
        boardReference,
      })
      .then(r => r.data.data!),

  currentContact: () =>
    api
      .get<ApiResponse<ComplianceContact>>('/compliance/grievances/contact')
      .then(r => r.data.data!),

  publishContact: (body: {
    displayName: string
    designation?: string
    email: string
    phone?: string
    postalAddress?: string
    isDpo: boolean
    basedInIndia: boolean
  }) =>
    api
      .post<ApiResponse<ComplianceContact>>('/compliance/grievances/contact', body)
      .then(r => r.data.data!),
}

/**
 * Retention policy administration — WO-025.
 *
 * There is deliberately no "run now" call. `preview` forces dry-run server-side
 * regardless of how policies are configured, so it is safe against armed ones.
 * Destruction happens on the nightly schedule, after someone read a preview.
 */
export const retentionApi = {
  policies: () =>
    api
      .get<ApiResponse<RetentionPolicy[]>>('/compliance/retention/policies')
      .then(r => r.data.data ?? []),

  update: (
    id: string,
    body: {
      retentionDays?: number
      enabled?: boolean
      dryRun?: boolean
      maxRowsPerRun?: number
      justification?: string
    },
  ) =>
    api
      .put<ApiResponse<RetentionPolicy>>(`/compliance/retention/policies/${id}`, body)
      .then(r => r.data.data!),

  preview: () =>
    api
      .post<ApiResponse<RetentionRun>>('/compliance/retention/preview', {})
      .then(r => r.data.data!),

  runs: () =>
    api
      .get<ApiResponse<RetentionRun[]>>('/compliance/retention/runs')
      .then(r => r.data.data ?? []),

  runDetail: (runId: string) =>
    api
      .get<ApiResponse<RetentionRunItem[]>>(`/compliance/retention/runs/${runId}`)
      .then(r => r.data.data ?? []),
}
