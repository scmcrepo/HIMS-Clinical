import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface Tenant {
  id: string
  slug: string
  name: string
  description?: string
  status: number // 1 = active, 0 = inactive
}

export interface PublicTenant {
  slug: string
  name: string
}

export const tenantApi = {
  // Public (login screen): active tenants only, no auth required.
  publicList: () =>
    api.get<ApiResponse<PublicTenant[]>>('/tenants/public').then(r => r.data),

  // SUPERADMIN platform management.
  getAll: () => api.get<ApiResponse<Tenant[]>>('/tenants').then(r => r.data),
  get: (id: string) => api.get<ApiResponse<Tenant>>(`/tenants/${id}`).then(r => r.data),
  create: (body: { name: string; description?: string;
                   adminUsername?: string; adminPassword?: string;
                   adminFirstName?: string; adminLastName?: string }) =>
    api.post<ApiResponse<Tenant>>('/tenants', body).then(r => r.data),
  update: (id: string, body: { name?: string; description?: string; status?: number }) =>
    api.put<ApiResponse<Tenant>>(`/tenants/${id}`, body).then(r => r.data),
  seedRbac: (id: string) =>
    api.post<ApiResponse<void>>(`/tenants/${id}/seed`).then(r => r.data),
}
