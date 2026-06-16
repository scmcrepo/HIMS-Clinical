import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface Branch {
  id: string
  code: string
  name: string
  isDefault: boolean
  status: number // 1 = active, 0 = inactive
}

// Branch management within the caller's tenant (HOSPITAL_ADMIN, or SUPERADMIN impersonating a
// tenant). The active tenant is resolved server-side from the session, so no tenant id is passed.
export const branchApi = {
  getAll: (headers?: Record<string, string>) => 
    api.get<ApiResponse<Branch[]>>('/branches', { headers }).then(r => r.data),
  get: (id: string, headers?: Record<string, string>) => 
    api.get<ApiResponse<Branch>>(`/branches/${id}`, { headers }).then(r => r.data),
  create: (body: { code: string; name: string }, headers?: Record<string, string>) =>
    api.post<ApiResponse<Branch>>('/branches', body, { headers }).then(r => r.data),
  update: (id: string, body: { name?: string; status?: number }, headers?: Record<string, string>) =>
    api.put<ApiResponse<Branch>>(`/branches/${id}`, body, { headers }).then(r => r.data),
}
