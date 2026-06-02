import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export type OrderSetType  = 'PRESCRIPTION' | 'DIAGNOSTICS' | 'BOTH'
export type OrderSetScope = 'GLOBAL' | 'DEPARTMENT' | 'CONSULTANT'
export type OrderSetItemType = 'PHARMACY' | 'DIAGNOSTIC' | 'PROCEDURE'

export interface OrderSetItem {
  id?:                   string
  itemType:              OrderSetItemType
  serviceCatalogItemId?: string
  itemName?:             string       // display name
  diagnosticType?:       string
  quantity:              number
  instruction?:          string
  frequency?:            string
  duration?:             string
  routeLabel?:           string
}

export interface OrderSet {
  id:           string
  name:         string
  description:  string | null
  setType:      OrderSetType
  isOutpatient: boolean
  isFavorite:   boolean
  scope:        OrderSetScope
  consultantId?: string
  departmentId?: string
  items:        OrderSetItem[]
}

const BASE = '/order-sets'

export const orderSetApi = {
  search:     (q?: string) =>
    api.get<ApiResponse<OrderSet[]>>(BASE, { params: q ? { q } : {} }).then(r => r.data.data ?? []),
  getById:    (id: string) =>
    api.get<ApiResponse<OrderSet>>(`${BASE}/${id}`).then(r => r.data.data!),
  getFavorites: (consultantId: string) =>
    api.get<ApiResponse<OrderSet[]>>(`${BASE}/favorites`, { params: { consultantId } }).then(r => r.data.data ?? []),
  create:     (data: Omit<OrderSet, 'id'>) =>
    api.post<ApiResponse<OrderSet>>(BASE, data).then(r => r.data.data!),
  update:     (id: string, data: Partial<OrderSet>) =>
    api.put<ApiResponse<OrderSet>>(`${BASE}/${id}`, data).then(r => r.data.data!),
  deactivate: (id: string) =>
    api.delete(`${BASE}/${id}`),
  markFavorite: (id: string, consultantId: string) =>
    api.post<ApiResponse<OrderSet>>(`${BASE}/${id}/favorite`, null, { params: { consultantId } }).then(r => r.data.data!),
  addFavoriteItem: (payload: {
    consultantId: string; favoriteType: 'DRUG' | 'TEST';
    itemId?: string; itemName?: string;
    frequency?: string; duration?: string; instructionLabel?: string; routeLabel?: string;
  }) =>
    api.post<ApiResponse<any>>('/op-ip/favorites/item', payload).then(r => r.data.data),
}
