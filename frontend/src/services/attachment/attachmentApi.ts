import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface Attachment {
  id: string
  encounterId?: string
  patientId?: string
  providerId?: string
  attachmentType: string
  category?: string
  fileName: string
  filePath: string
  contentType?: string
  metaData?: string
  createdAt?: string
}

export const attachmentApi = {
  upload: (file: File, type: string, encounterId?: string, patientId?: string, providerId?: string, category?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    
    const params = new URLSearchParams()
    params.append('attachmentType', type)
    if (encounterId) params.append('encounterId', encounterId)
    if (patientId) params.append('patientId', patientId)
    if (providerId) params.append('providerId', providerId)
    if (category) params.append('category', category)

    return api.post<ApiResponse<Attachment>>(`/attachment?${params.toString()}`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }).then(r => r.data.data!)
  },

  getByEncounter: (encounterId: string) =>
    api.get<ApiResponse<Attachment[]>>(`/attachment/encounter/${encounterId}`).then(r => r.data.data ?? []),

  getByPatient: (patientId: string) =>
    api.get<ApiResponse<Attachment[]>>(`/attachment/patient/${patientId}`).then(r => r.data.data ?? []),

  getDownloadUrl: (attachmentId: string) =>
    `/api/attachment/download/${attachmentId}`,

  delete: (attachmentId: string) =>
    api.delete<ApiResponse<void>>(`/attachment/${attachmentId}`).then(r => r.data.data)
}
