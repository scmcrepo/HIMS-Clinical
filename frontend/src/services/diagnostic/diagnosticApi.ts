import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { DiagnosticOrder, DiagnosticType } from '../../types/diagnostic'

export interface PlaceOrderCmd { encounterId?: string | undefined; patientId: string; providerId?: string | undefined; diagnosticType: DiagnosticType; lines: Array<{ serviceCatalogItemId: string; itemName?: string | undefined; specimenId?: string | undefined; instruction?: string | undefined }> }
export interface RecordResultCmd { lineId: string; resultValue: string; resultUnit?: string | undefined; referenceRange?: string | undefined }

const BASE = '/diagnostics'
export const diagnosticApi = {
  placeOrder: (cmd: PlaceOrderCmd) => api.post<ApiResponse<DiagnosticOrder>>(`${BASE}/orders`, cmd).then(r => r.data.data!),
  getById: (id: string) => api.get<ApiResponse<DiagnosticOrder>>(`${BASE}/orders/${id}`).then(r => r.data.data!),
  getByEncounter: (encounterId: string) => api.get<ApiResponse<DiagnosticOrder[]>>(`${BASE}/orders/encounter/${encounterId}`).then(r => r.data.data ?? []),
  getByPatient: (patientId: string, page = 0) => api.get<ApiResponse<PageResponse<DiagnosticOrder>>>(`${BASE}/orders/patient/${patientId}`, { params: { page } }).then(r => r.data.data!),
  getPending: (type: DiagnosticType, from: string, to: string) => api.get<ApiResponse<DiagnosticOrder[]>>(`${BASE}/orders/pending`, { params: { type, from, to } }).then(r => r.data.data ?? []),
  recordResult: (orderId: string, cmd: RecordResultCmd) => api.post<ApiResponse<DiagnosticOrder>>(`${BASE}/orders/${orderId}/results`, cmd).then(r => r.data.data!),
  markBilled: (orderId: string) => api.post<ApiResponse<DiagnosticOrder>>(`${BASE}/orders/${orderId}/bill`).then(r => r.data.data!),
  cancelOrder: (orderId: string) => api.delete<ApiResponse<DiagnosticOrder>>(`${BASE}/orders/${orderId}`).then(r => r.data.data!),
  recordSpecimenCollection: (cmd: { diagnosticId: string; specimenId?: string | undefined; orderLineId?: string | undefined; notes?: string | undefined }) =>
    api.post<ApiResponse<any>>(`${BASE}/recordSpecimenCollection`, cmd).then(r => r.data.data!),
  getSpecimenCollections: (diagnosticId: string) =>
    api.get<ApiResponse<any[]>>(`${BASE}/getSpecimenCollection`, { params: { diagnosticsId: diagnosticId } }).then(r => r.data.data ?? []),
}
