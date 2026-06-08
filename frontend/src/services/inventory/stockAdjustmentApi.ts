import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { StockAdjustment, CreateStockAdjustmentPayload } from '../../types/stockAdjustment'

const BASE = '/stock-adjustment'

export const stockAdjustmentApi = {
  create: (payload: CreateStockAdjustmentPayload) =>
    api.post<ApiResponse<StockAdjustment>>(BASE, payload)
      .then(r => r.data.data!),

  getAll: (q = '', page = 0, size = 10) =>
    api.get<ApiResponse<PageResponse<StockAdjustment>>>(BASE, { params: { q, page, size } })
      .then(r => r.data.data ?? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 }),

  getById: (id: string) =>
    api.get<ApiResponse<StockAdjustment>>(`${BASE}/${id}`)
      .then(r => r.data.data!)
}
