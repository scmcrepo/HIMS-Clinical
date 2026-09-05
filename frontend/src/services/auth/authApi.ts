import api from '../../lib/axios'
import type { AuthUser } from '../../store/authStore'
import type { ApiResponse } from '../../types/api'

export const authApi = {
  // Login takes only username + password. The tenant and branch are identified from the
  // authenticated user server-side — users never pick a hospital/branch at login.
  login: (username: string, password: string, branchId?: string | null, forceLogout?: boolean) =>
    api
      .post<ApiResponse<any>>('/auth/login', { username, password, branchId, forceLogout })
      .then(r => r.data),
  /**
   * Second step of a multi-factor login (WO-029 / U-002).
   *
   * Unauthenticated, like /auth/login: the password has been accepted but no
   * session exists yet. The challenge id is the only link between the two steps.
   * The username is deliberately NOT sent — the server takes it from the stored
   * challenge, so a caller cannot pair someone else's challenge with their own
   * account.
   */
  verifyMfa: (challengeId: string, code: string) =>
    api
      .post<ApiResponse<any>>('/auth/mfa/verify', { challengeId, code })
      .then(r => r.data),
  logout: () => api.post('/auth/logout'),
  me: () => api.get<ApiResponse<AuthUser>>('/auth/me').then(r => r.data),
  heartbeat: () => api.get('/auth/heartbeat'),
  forgotPasswordRequest: (email: string) =>
    api.post<ApiResponse<void>>('/auth/forgot-password/request', { email }).then(r => r.data),
  forgotPasswordVerify: (email: string, otp: string) =>
    api.post<ApiResponse<void>>('/auth/forgot-password/verify', { email, otp }).then(r => r.data),
  forgotPasswordReset: (email: string, otp: string, newPassword: String, confirmPassword: String) =>
    api.post<ApiResponse<void>>('/auth/forgot-password/reset', { email, otp, newPassword, confirmPassword }).then(r => r.data),
}
