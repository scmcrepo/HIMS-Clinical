import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'

export type DocumentType = 'BILL'|'RECEIPT'|'DEPOSIT'|'REFUND'|'LAB_ORDER'|'IP_ORDER'|'SAMPLE'|'PHARMACY_SALE'|'PAYMENT'|'PURCHASE_RECEIPT'|'PURCHASE_RETURN'|'PURCHASE_ORDER'|'PATIENT'|'REPLENISHMENT'|'INVENTORY_ISSUE'|'CONSUMPTION'|'ADVANCE_REFUND'
export type SequenceResetPolicy = 'NEVER'|'FISCAL_YEAR'|'CALENDAR_YEAR'
export interface SequenceGenerator { id: string|null; prefixString: string|null; documentType: DocumentType; resetPolicy: SequenceResetPolicy|null; activated: boolean; currentCounter: number; currentFiscalYear: number|null; activatedAt: string|null; deactivatedAt: string|null }

export const prefixApi = {
  getSummary: () => api.get<ApiResponse<SequenceGenerator[]>>('/prefix').then(r => r.data.data ?? []),
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) =>
    api.get<ApiResponse<PageResponse<SequenceGenerator>>>('/prefix/page', { params }).then(r => r.data.data!),
  getAll:     () => api.get<ApiResponse<SequenceGenerator[]>>('/prefix/all').then(r => r.data.data ?? []),
  getHistory: (documentType: DocumentType) => api.get<ApiResponse<SequenceGenerator[]>>(`/prefix/history/${documentType}`).then(r => r.data.data ?? []),
  create: (prefixString: string, documentType: DocumentType, resetPolicy: SequenceResetPolicy) =>
    api.post<ApiResponse<SequenceGenerator>>('/prefix', { prefixString, documentType, resetPolicy }).then(r => r.data.data!),
  update: (id: string, prefixString: string, documentType: DocumentType, resetPolicy: SequenceResetPolicy) =>
    api.put<ApiResponse<SequenceGenerator>>(`/prefix/${id}`, { prefixString, documentType, resetPolicy }).then(r => r.data.data!),
  activate:   (id: string) => api.post<ApiResponse<SequenceGenerator>>(`/prefix/${id}/activate`).then(r => r.data.data!),
  deactivate: (id: string) => api.post<ApiResponse<SequenceGenerator>>(`/prefix/${id}/deactivate`).then(r => r.data.data!),
}
