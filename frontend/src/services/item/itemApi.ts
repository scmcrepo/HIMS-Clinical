import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type { InventoryItem } from '../../types/inventory'

export const itemApi = {
  search: (name: string) =>
    api.get<ApiResponse<InventoryItem[]>>(`/item/getItemByName`, { params: { name } })
      .then(r => r.data.data ?? []),

  getById: (id: string) =>
    api.get<ApiResponse<InventoryItem>>(`/item/getItemById/${id}`)
      .then(r => r.data.data!),
}
