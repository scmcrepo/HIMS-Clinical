import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface SaleLine { inventoryBatchId: string; quantity: number; unitRate: number }
export interface CreateSaleCmd { id?: string | undefined; patientId?: string | undefined; customerName?: string | undefined; customerPhone?: string | undefined; consultantName?: string | undefined; encounterId?: string | undefined; departmentId: string; isDraft: boolean; discountAmount?: number | undefined; lines: SaleLine[]; paymentMode?: string | undefined; cardType?: string | undefined; cardNumber?: string | undefined; bankName?: string | undefined; paidAmount?: number | undefined; }
export interface PharmacySaleResponse { id: string; patientId: string|null; patientName: string; customerName: string|null; customerPhone: string|null; consultantName: string|null; encounterId: string|null; departmentId: string; sequenceNumber: string|null; saleDate: string; totalAmount: number; discountAmount: number; status: string; lines: Array<{id: string; inventoryBatchId: string; itemName: string; quantity: number; unitRate: number; amount: number; discountAmount: number}>; createdAt?: string; paymentMode?: string|null; cardType?: string|null; cardNumber?: string|null; bankName?: string|null; paidAmount: number; dueAmount: number; payments?: Array<{ id: string; amount: number; paymentMode: string; cardType?: string|null; cardNumber?: string|null; bankName?: string|null; createdAt: string }>; patientNumber?: string|null; }

export const salesApi = {
  create:   (cmd: CreateSaleCmd) => api.post<ApiResponse<PharmacySaleResponse>>('/sales', cmd).then(r => r.data.data!),
  getById:  (id: string) => api.get<ApiResponse<PharmacySaleResponse>>(`/sales/${id}`).then(r => r.data.data!),
  getByPatient: (patientId: string) => api.get<ApiResponse<PharmacySaleResponse[]>>(`/sales/patient/${patientId}`).then(r => r.data.data ?? []),
  getByDate:    (date: string) => api.get<ApiResponse<PharmacySaleResponse[]>>('/sales', { params: { date } }).then(r => r.data.data ?? []),
  getDrafts:    (departmentId: string) => api.get<ApiResponse<PharmacySaleResponse[]>>(`/sales/draft/department/${departmentId}`).then(r => r.data.data ?? []),
  delete:       (id: string) => api.delete<ApiResponse<void>>(`/sales/${id}`).then(r => r.data.data),
  collectPayment: (saleId: string, cmd: { amount: number; paymentMode?: string | undefined; bankName?: string | undefined; cardType?: string | undefined; cardNumber?: string | undefined }) =>
    api.put<ApiResponse<PharmacySaleResponse>>(`/sales/collectPayment`, cmd, { params: { saleId } }).then(r => r.data.data!),
}
