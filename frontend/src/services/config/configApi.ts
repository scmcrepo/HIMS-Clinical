import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

// Headers that explicitly skip branch scoping (for tenant-level operations)
const TENANT_LEVEL_HEADERS = { 'X-Branch-Id': '' }

export const configApi = {
  getValues:   () => api.get<ApiResponse<Record<string,string>>>('/config/values').then(r => r.data.data ?? {}),
  save:        (type: string, key: string, value: string) => api.post('/config', { type, key, value }),
  saveBatch:   (entries: Array<{type?: string; key: string; value: string}>) => api.post('/config/batch', entries),
  getHospital: () => api.get<ApiResponse<Record<string,string>>>('/config/hospital').then(r => r.data.data ?? {}),
  saveHospital:(data: {name?: string; address?: string; phone?: string}) => api.post('/config/hospital', data),
  /** Tenant-level hospital profile — bypasses branch scoping so Hospital Admin always reads/writes the hospital, not the branch. */
  getHospitalTenantLevel: () => api.get<ApiResponse<Record<string,string>>>('/config/hospital', { headers: TENANT_LEVEL_HEADERS }).then(r => r.data.data ?? {}),
  saveHospitalTenantLevel: (data: {name?: string; address?: string; phone?: string}) => api.post('/config/hospital', data, { headers: TENANT_LEVEL_HEADERS }),
  getCurrentDate: () => api.get<ApiResponse<string>>('/config/current-date').then(r => r.data.data!),
  getSessionTimeout: () => api.get<ApiResponse<number>>('/config/session-timeout').then(r => r.data.data!),
  uploadLogo: (file: File, tenantId?: string, branchId?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    let url = '/hospitalProfile/uploadImage'
    const params = new URLSearchParams()
    if (tenantId) params.append('tenantId', tenantId)
    if (branchId) params.append('branchId', branchId)
    const qs = params.toString()
    if (qs) {
      url += '?' + qs
    }
    return api.post(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },
}
