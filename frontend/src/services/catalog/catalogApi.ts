import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { ServiceItem, ServiceCategory, ServiceCategoryType, ServiceType, BillType } from '../../types/catalog'

export interface CreateItemCmd { name: string; categoryId: string; serviceType: ServiceType; requiresOrder?: boolean; pricingTiers: Array<{ billType: BillType; unitRate: number }> }

const BASE = '/catalog'
export const catalogApi = {
  createItem: (cmd: CreateItemCmd) => api.post<ApiResponse<ServiceItem>>(`${BASE}/items`, cmd).then(r => r.data.data!),
  getById: (id: string) => api.get<ApiResponse<ServiceItem>>(`${BASE}/items/${id}`).then(r => r.data.data!),
  search: (q: string, page = 0, size = 20, excludeRoomCharges = false, diagnosticsAndConsultationsOnly = false) => api.get<ApiResponse<PageResponse<ServiceItem>>>(`${BASE}/items/search`, { params: { q, page, size, excludeRoomCharges, diagnosticsAndConsultationsOnly } }).then(r => r.data.data!),
  getByCategory: (categoryId: string) => api.get<ApiResponse<ServiceItem[]>>(`${BASE}/items/category/${categoryId}`).then(r => r.data.data ?? []),
  updatePricing: (itemId: string, tierId: string, billType: BillType, unitRate: number) => api.put<ApiResponse<ServiceItem>>(`${BASE}/items/${itemId}/pricing`, { tierId, billType, unitRate }).then(r => r.data.data!),
  updateItem: (itemId: string, cmd: CreateItemCmd) => api.put<ApiResponse<ServiceItem>>(`${BASE}/items/${itemId}`, cmd).then(r => r.data.data!),
  deactivate: (itemId: string) => api.delete(`${BASE}/items/${itemId}`),
  activate: (itemId: string) => api.post(`${BASE}/items/${itemId}/activate`),
  getCategories: () => api.get<ApiResponse<ServiceCategory[]>>(`${BASE}/categories`).then(r => r.data.data ?? []),
  createCategory: (name: string, type: ServiceCategoryType) => api.post<ApiResponse<ServiceCategory>>(`${BASE}/categories`, null, { params: { name, type } }).then(r => r.data.data!),
}
