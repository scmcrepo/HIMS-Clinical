import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface SalesReturnLine {
  id?: string
  inventoryBatchId: string
  quantity: number
  returnAmount?: number
}

export interface SalesReturn {
  id: string
  saleId: string
  patientId: string | null
  departmentId: string
  returnDate: string
  sequenceNumber: string
  totalReturnAmount: number
  lines: SalesReturnLine[]
  createdAt?: string
}

export interface CreateReturnCmd {
  saleId: string
  lines: Array<{
    inventoryBatchId: string
    quantity: number
  }>
}

export const salesReturnApi = {
  create: (cmd: CreateReturnCmd) =>
    api.post<ApiResponse<SalesReturn>>('/salesReturn', cmd).then(r => r.data.data!),
  getByDate: (date: string, search?: string) =>
    api.get<ApiResponse<SalesReturn[]>>('/salesReturn', { params: { date, search } }).then(r => r.data.data ?? []),
  getByPatient: (patientId: string) =>
    api.get<ApiResponse<SalesReturn[]>>(`/salesReturn/patient/${patientId}`).then(r => r.data.data ?? []),
  getBySale: (saleId: string) =>
    api.get<ApiResponse<SalesReturn[]>>(`/salesReturn/sale/${saleId}`).then(r => r.data.data ?? []),
}
