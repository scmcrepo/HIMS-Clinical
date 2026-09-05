import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface MfaStatus {
  /** OFF | OPTIONAL | REQUIRED — the deployment-wide setting. */
  mode: string
  enrolled: boolean
  /** Enrolment started AND proved with a code. An unconfirmed credential is not a second factor. */
  confirmed: boolean
  recoveryCodesRemaining: number
  /** Whether this account's roles fall under the REQUIRED policy. */
  privileged: boolean
}

export interface MfaEnrolment {
  /** Shown once, for manual entry when a camera will not focus. Never stored client-side. */
  secret: string
  /** otpauth:// URI to render as a QR code. */
  provisioningUri: string
}

/**
 * Multi-factor enrolment and administration (WO-029 / U-002).
 *
 * All of these need a session — unlike /auth/mfa/verify, which is the second half
 * of signing in and therefore unauthenticated.
 */
export const mfaApi = {
  status: () =>
    api.get<ApiResponse<MfaStatus>>('/security/mfa/status').then(r => r.data.data!),

  /** Begins enrolment and returns the secret. Refuses while the mode is OFF. */
  enrol: () =>
    api.post<ApiResponse<MfaEnrolment>>('/security/mfa/enrol').then(r => r.data.data!),

  /**
   * Confirms with a generated code and returns the recovery codes.
   *
   * This response is the only copy of those codes that will ever exist — the
   * server stores BCrypt hashes and no endpoint can show them again.
   */
  confirm: (code: string) =>
    api
      .post<ApiResponse<{ recoveryCodes: string[] }>>('/security/mfa/enrol/confirm', { code })
      .then(r => r.data.data!.recoveryCodes),

  /** Clears another user's second factor. Requires MFA_ADMIN; audited at WARN. */
  reset: (userId: string) =>
    api.delete<ApiResponse<void>>(`/security/mfa/user/${userId}`).then(r => r.data),
}
