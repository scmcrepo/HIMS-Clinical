import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'

export interface Consultant {
  id: string
  salutation: string | null
  firstName: string
  lastName: string
  specialisation: string | null
  contact: string | null
  email: string | null
  consultantType: string
  qualification?: string | null
  address?: string | null
  departmentId?: string | null
  status?: 'ACTIVE' | 'INACTIVE' | 'DELETED'
  photoAttachmentId?: string | null
}

const BASE = '/consultant'

export const consultantApi = {
  getAll: () => api.get<ApiResponse<Consultant[]>>(BASE).then(r => r.data.data ?? []),
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) =>
    api.get<ApiResponse<PageResponse<Consultant>>>(`${BASE}/page`, { params }).then(r => r.data.data!),
  getById: (id: string) => api.get<ApiResponse<Consultant>>(`${BASE}/${id}`).then(r => r.data.data!),
  getTypes: () => api.get<ApiResponse<string[]>>(`${BASE}/types`).then(r => r.data.data ?? []),
  searchByName: (name: string) => api.get<ApiResponse<Consultant[]>>(`${BASE}/getConsultantByName`, { params: { name } }).then(r => r.data.data ?? []),
  
  create: (consultant: Omit<Consultant, 'id'>, photo?: File) => {
    const fd = new FormData()
    fd.append('consultant', new Blob([JSON.stringify(consultant)], { type: 'application/json' }))
    if (photo) fd.append('photo', photo)
    return api.post<ApiResponse<Consultant>>(BASE, fd, { headers: { 'Content-Type': 'multipart/form-data' } }).then(r => r.data.data!)
  },

  update: (id: string, consultant: Partial<Consultant>, photo?: File) => {
    const fd = new FormData()
    fd.append('consultant', new Blob([JSON.stringify(consultant)], { type: 'application/json' }))
    if (photo) fd.append('photo', photo)
    return api.put<ApiResponse<Consultant>>(`${BASE}/${id}`, fd, { headers: { 'Content-Type': 'multipart/form-data' } }).then(r => r.data.data!)
  },

  delete: (id: string) => api.delete<ApiResponse<void>>(`${BASE}/${id}`).then(r => r.data),
}
