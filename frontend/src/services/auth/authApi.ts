import api from '../../lib/axios'
import type { AuthUser } from '../../store/authStore'
import type { ApiResponse } from '../../types/api'

export const authApi = {
  // Login takes only username + password. The tenant and branch are identified from the
  // authenticated user server-side — users never pick a hospital/branch at login.
  login: (username: string, password: string) =>
    api
      .post<ApiResponse<AuthUser>>('/auth/login', { username, password })
      .then(r => r.data),
  logout: () => api.post('/auth/logout'),
  me: () => api.get<ApiResponse<AuthUser>>('/auth/me').then(r => r.data),
  heartbeat: () => api.get('/auth/heartbeat'),
}
