import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { Bed, BedOccupancy, BedStatusSummary, RoomCategory } from '../../types/bed'

const BASE = '/beds'

export interface InpatientSearchResult {
  encounterId: string
  patientNumber: string
  patientName: string
  patientId: string
  contactNumber: string
}

export const bedApi = {
  getBedTypes: () =>
    api.get<ApiResponse<RoomCategory[]>>('/bedType').then(r => r.data.data ?? []),

  getAll: () =>
    api.get<ApiResponse<Bed[]>>(BASE).then(r => r.data.data ?? []),

  getPaginated: (params?: { start?: number; limit?: number; value?: string }) =>
    api.get<ApiResponse<PageResponse<Bed>>>(`${BASE}/page`, { params }).then(r => r.data.data!),


  getAvailable: (roomCategoryId?: string) =>
    api.get<ApiResponse<Bed[]>>(`${BASE}/available`, {
      params: roomCategoryId ? { roomCategoryId } : {},
    }).then(r => r.data.data ?? []),

  getSummary: () =>
    api.get<ApiResponse<BedStatusSummary>>(`${BASE}/summary`).then(r => r.data.data!),

  searchInpatients: (q: string) =>
    api.get<ApiResponse<InpatientSearchResult[]>>(`${BASE}/search-inpatients`, { params: { q } })
      .then(r => r.data.data ?? []),

  allocate: (bedId: string, encounterId: string, consultantId?: string, billId?: string, billType?: string, payorId?: string) =>
    api.post<ApiResponse<BedOccupancy>>(`${BASE}/allocate`, { bedId, encounterId, consultantId, billId, billType, payorId })
      .then(r => r.data.data!),

  release: (bedId: string) =>
    api.post(`${BASE}/release/${bedId}`),

  setMaintenance: (bedId: string) =>
    api.post<ApiResponse<Bed>>(`${BASE}/${bedId}/maintenance`).then(r => r.data.data!),

  clearMaintenance: (bedId: string) =>
    api.delete<ApiResponse<Bed>>(`${BASE}/${bedId}/maintenance`).then(r => r.data.data!),

  getOccupancyHistory: (encounterId: string) =>
    api.get<ApiResponse<BedOccupancy[]>>(`${BASE}/occupancy/${encounterId}`)
      .then(r => r.data.data ?? []),

  create: (name: string, roomCategoryId: string, status?: 'ACTIVE' | 'INACTIVE') =>
    api.post<ApiResponse<Bed>>(BASE, { name, roomCategoryId, status }).then(r => r.data.data!),

  update: (id: string, name: string, roomCategoryId: string, status?: 'ACTIVE' | 'INACTIVE') =>
    api.put<ApiResponse<Bed>>(`${BASE}/${id}`, { name, roomCategoryId, status }).then(r => r.data.data!),

  transfer: (encounterId: string, newBedId: string, fromDate?: string) =>
    api.post<ApiResponse<BedOccupancy>>(`${BASE}/transferBed`, { encounterId, newBedId, fromDate })
      .then(r => r.data.data!),

  vacate: (encounterId: string, dischargeDate?: string) =>
    api.post(`${BASE}/vacateBed`, { encounterId, dischargeDate }),
}
