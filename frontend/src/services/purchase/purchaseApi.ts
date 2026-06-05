import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface PurchaseOrderLine {
  itemId: string
  quantity: number
  unitRate?: number | undefined
}

export interface PurchaseOrderResponse {
  id: string
  supplierId: string | null
  departmentId: string
  orderDate: string
  sequenceNumber: string | null
  orderStatus: 'ORDERED' | 'PARTIALLY_RECEIVED' | 'RECEIVED'
  notes: string | null
  lines: Array<{
    id: string
    itemId: string
    quantity: number
    receivedQuantity: number
    unitRate: number | null
  }>
}

export interface PurchaseRequestResponse {
  id: string
  departmentId: string
  requestDate: string
  sequenceNumber: string | null
  requestStatus: string
  notes: string | null
  createdBy?: string
}

export interface GoodsReturnResponse {
  id: string
  supplierId: string | null
  departmentId: string
  returnDate: string
  sequenceNumber: string | null
  notes: string | null
  createdBy?: string
  lines?: Array<{
    id: string
    batchId: string
    quantity: number
    purchaseRate: number
  }>
}

export const purchaseApi = {
  // --- Purchase Orders ---
  create: (departmentId: string, supplierId: string | null, lines: PurchaseOrderLine[], notes?: string) =>
    api.post<ApiResponse<PurchaseOrderResponse>>('/purchase-orders', {
      departmentId, supplierId, lines, notes,
    }).then(r => r.data.data!),

  getByDate: (date: string) =>
    api.get<ApiResponse<PurchaseOrderResponse[]>>('/purchase-orders', { params: { date } })
      .then(r => r.data.data ?? []),

  getById: (id: string) =>
    api.get<ApiResponse<PurchaseOrderResponse>>(`/purchase-orders/${id}`)
      .then(r => r.data.data!),

  updateOrder: (id: string, orderStatus: string, notes: string, lines: any[]) =>
    api.put<ApiResponse<PurchaseOrderResponse>>(`/purchase-orders/${id}`, {
      orderStatus, notes, lines
    }).then(r => r.data.data!),

  // --- Purchase Requests ---
  createRequest: (departmentId: string, notes?: string) =>
    api.post<ApiResponse<PurchaseRequestResponse>>('/purchaseRequest', {
      departmentId, notes,
    }).then(r => r.data.data!),

  getAllRequests: () =>
    api.get<ApiResponse<PurchaseRequestResponse[]>>('/purchaseRequest')
      .then(r => r.data.data ?? []),

  updateRequest: (id: string, requestStatus: string, notes?: string) =>
    api.put<ApiResponse<PurchaseRequestResponse>>(`/purchaseRequest/${id}`, {
      requestStatus, notes
    }).then(r => r.data.data!),

  // --- Purchase Returns ---
  createReturn: (supplierId: string | null, departmentId: string, lines: { batchId: string; quantity: number }[], notes?: string) =>
    api.post<ApiResponse<GoodsReturnResponse>>('/goodsReturn', {
      supplierId, departmentId, lines, notes,
    }).then(r => r.data.data!),

  getReturnsByDate: (date?: string) =>
    api.get<ApiResponse<GoodsReturnResponse[]>>('/goodsReturn', { params: { date } })
      .then(r => r.data.data ?? []),
}

