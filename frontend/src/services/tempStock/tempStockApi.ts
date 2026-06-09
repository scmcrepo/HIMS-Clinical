import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface TempStockReq {
  itemId: string
  departmentId: string
  batchNumber?: string
  quantity: number
  purchaseRate: number
  mrp: number
  sellingRate: number
  expiryDate?: string
  taxRate?: number
}

export interface TempStock {
  id: string
  itemId: string
  departmentId: string
  batchNumber?: string
  quantity: number
  purchaseRate: number
  mrp: number
  sellingRate: number
  expiryDate?: string
  taxRate?: number
  createdAt?: string
}

export const tempStockApi = {
  createBulk: (reqs: TempStockReq[]) =>
    api.post<ApiResponse<TempStock[]>>('/tempStock', reqs)
      .then(r => r.data.data ?? []),

  getQuantity: (itemId: string, batch?: string) =>
    api.get<ApiResponse<number>>(`/tempStock/detail/item/${itemId}/quantity`, { params: { batch } })
      .then(r => r.data.data ?? 0),

  getByItem: (itemId: string, batch?: string) =>
    api.get<ApiResponse<TempStock[]>>(`/tempStock/detail/item/${itemId}`, { params: { batch } })
      .then(r => r.data.data ?? []),
}
