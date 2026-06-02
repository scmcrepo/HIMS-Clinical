import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type { DiagnosticTemplate, DiagnosticDepartment } from '../../types/diagnostic'

const BASE = '/diagTemplate'

export const diagTemplateApi = {
  getAll: () =>
    api.get<ApiResponse<DiagnosticTemplate[]>>(`${BASE}/all`).then(r => r.data.data ?? []),

  getByCharge: (chargeId: string) =>
    api.get<ApiResponse<DiagnosticTemplate[]>>(`${BASE}/getLabDetailsByCharge`, { params: { chargeId } }).then(r => r.data.data ?? []),

  getDepartments: () =>
    api.get<ApiResponse<DiagnosticDepartment[]>>(`${BASE}/departments`).then(r => r.data.data ?? []),

  getTypes: () =>
    api.get<ApiResponse<string[]>>(`${BASE}/labTemplateTypes`).then(r => r.data.data ?? []),
}
