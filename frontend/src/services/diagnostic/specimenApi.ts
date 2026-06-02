import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'

export interface Specimen {
  id: string
  name: string
  description?: string
  status: 'ACTIVE' | 'INACTIVE' | 'DELETED'
}

const BASE = '/specimen'

export const specimenApi = {
  /** Management page — all non-deleted (ACTIVE + INACTIVE) */
  getAll: () => 
    api.get<ApiResponse<Specimen[]>>(BASE).then(r => r.data.data ?? []),

  getPaginated: (params?: { start?: number; limit?: number; value?: string }) =>
    api.get<ApiResponse<PageResponse<Specimen>>>(`${BASE}/page`, { params }).then(r => r.data.data!),


  /** Clinical dropdowns — only ACTIVE */
  getActive: () =>
    api.get<ApiResponse<Specimen[]>>(`${BASE}/active`).then(r => r.data.data ?? []),
    
  create: (specimen: Partial<Specimen>) => 
    api.post<ApiResponse<Specimen>>(BASE, specimen).then(r => r.data.data!),
    
  update: (specimen: Specimen) => 
    api.put<ApiResponse<Specimen>>(BASE, specimen).then(r => r.data.data!),
}
