import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export const configApi = {
  getValues:   () => api.get<ApiResponse<Record<string,string>>>('/config/values').then(r => r.data.data ?? {}),
  save:        (type: string, key: string, value: string) => api.post('/config', { type, key, value }),
  saveBatch:   (entries: Array<{type?: string; key: string; value: string}>) => api.post('/config/batch', entries),
  getHospital: () => api.get<ApiResponse<Record<string,string>>>('/config/hospital').then(r => r.data.data ?? {}),
  saveHospital:(data: {name?: string; address?: string; phone?: string}) => api.post('/config/hospital', data),
  getCurrentDate: () => api.get<ApiResponse<string>>('/config/current-date').then(r => r.data.data!),
  getSessionTimeout: () => api.get<ApiResponse<number>>('/config/session-timeout').then(r => r.data.data!),
  uploadLogo: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/hospitalProfile/uploadImage', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },
}
