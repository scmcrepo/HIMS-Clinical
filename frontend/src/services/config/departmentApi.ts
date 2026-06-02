import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface Department {
  id: string
  name: string
  departmentType: string
  status: number
}

export const departmentApi = {
  getAll: () =>
    api.get<ApiResponse<Department[]>>('/department')
      .then(r => r.data.data ?? []),

  getById: (id: string) =>
    api.get<ApiResponse<Department>>(`/department/${id}`)
      .then(r => r.data.data!),
}
