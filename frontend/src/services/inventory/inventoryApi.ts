import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type { InventoryBatch } from '../../types/inventory'

const BASE = '/inventory'

export interface ReceiveLine {
  itemId: string
  batchNumber?: string
  quantity: number
  purchaseRate: number
  maximumRetailPrice: number
  sellingRate: number
  expiryDate?: string
}

export const inventoryApi = {
  receiveGoods: (departmentId: string, lines: ReceiveLine[]) =>
    api.post<ApiResponse<InventoryBatch[]>>(`${BASE}/receive`, { departmentId, lines })
      .then(r => r.data.data ?? []),

  getAvailableBatches: (itemId: string, departmentId: string) =>
    api.get<ApiResponse<InventoryBatch[]>>(`${BASE}/batches`, { params: { itemId, departmentId } })
      .then(r => r.data.data ?? []),

  getBatch: (batchId: string) =>
    api.get<ApiResponse<InventoryBatch>>(`${BASE}/batches/${batchId}`).then(r => r.data.data!),

  adjustStock: (batchId: string, adjustmentQty: number, reason?: string) =>
    api.patch<ApiResponse<InventoryBatch>>(`${BASE}/adjust`, { batchId, adjustmentQty, reason })
      .then(r => r.data.data!),

  consumeStock: (batchId: string, quantity: number) =>
    api.post<ApiResponse<InventoryBatch>>(`${BASE}/consume/${batchId}`, null, { params: { quantity } })
      .then(r => r.data.data!),

  getExpired: (departmentId: string) =>
    api.get<ApiResponse<InventoryBatch[]>>(`${BASE}/expired`, { params: { departmentId } })
      .then(r => r.data.data ?? []),

  getAllBatches: () =>
    api.get<ApiResponse<InventoryBatch[]>>(`${BASE}/batches/all`)
      .then(r => r.data.data ?? []),
}
