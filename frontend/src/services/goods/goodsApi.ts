import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface ReceiveLine {
  itemId: string
  batchNumber?: string
  quantity: number
  purchaseRate: number
  taxRate?: number
  maximumRetailPrice: number
  sellingRate: number
  expiryDate?: string
  freeQty?: number
  tempQuantity?: number
}

export interface PurchaseReceiptResponse {
  id: string
  supplierId: string | null
  departmentId: string
  receiptDate: string
  invoiceNumber: string | null
  invoiceDate?: string
  notes?: string
  sequenceNumber: string | null
  discount: number
  roundOff: number
  lines: Array<{
    id: string
    itemId: string
    batchNumber: string | null
    quantity: number
    purchaseRate: number
    maximumRetailPrice: number
    sellingRate: number
    expiryDate: string | null
    taxRate: number | null
  }>
}

export const goodsApi = {
  receiveGoods: (
    supplierId: string | undefined,
    purchaseOrderId: string | undefined,
    departmentId: string,
    invoiceNumber: string | undefined,
    invoiceDate: string | undefined,
    notes: string | undefined,
    discount: number,
    roundOff: number,
    lines: ReceiveLine[]
  ) =>
    api.post<ApiResponse<PurchaseReceiptResponse>>('/goods-received', {
      departmentId,
      purchaseOrderId,
      supplierId,
      invoiceNumber,
      invoiceDate,
      notes,
      discount,
      roundOff,
      lines,
    }).then(r => r.data.data!),

  getByDate: (date: string, supplierId?: string, page = 0, size = 10) =>
    api.get<ApiResponse<{ content: PurchaseReceiptResponse[], totalPages: number, totalElements: number }>>('/goods-received', { 
      params: { 
        date: date || undefined, 
        supplierId: (supplierId && supplierId !== 'ALL') ? supplierId : undefined,
        page, 
        size 
      } 
    }).then(r => r.data.data!),

  getById: (id: string) =>
    api.get<ApiResponse<PurchaseReceiptResponse>>(`/goods-received/${id}`)
      .then(r => r.data.data!),
}
