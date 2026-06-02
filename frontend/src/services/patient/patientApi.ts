import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { Patient, Gender } from '../../types/patient'

export interface RegisterPatientCmd { firstName: string; lastName: string; gender: Gender; estimatedDateOfBirth: string; salutation?: string | undefined; dateOfBirth?: string | undefined; contactNumber?: string | undefined; email?: string | undefined; bloodGroup?: string | undefined; address?: string | undefined; primaryProviderId?: string | undefined; areaId?: string | undefined; categoryId?: string | undefined; isClinicalTrial?: boolean | undefined; createEncounter?: boolean | undefined }
export interface UpdatePatientCmd { firstName?: string | undefined; lastName?: string | undefined; contactNumber?: string | undefined; email?: string | undefined; bloodGroup?: string | undefined; address?: string | undefined; estimatedDateOfBirth?: string | undefined; dateOfBirth?: string | undefined }

const BASE = '/patients'
export const patientApi = {
  register: (cmd: RegisterPatientCmd) =>
    api.post<ApiResponse<Patient>>(BASE, cmd).then(r => r.data.data!),
  getById: (patientId: string) =>
    api.get<ApiResponse<Patient>>(`${BASE}/${patientId}`).then(r => r.data.data!),
  update: (patientId: string, cmd: UpdatePatientCmd) =>
    api.put<ApiResponse<Patient>>(`${BASE}/${patientId}`, cmd).then(r => r.data.data!),
  search: (q: string, page = 0, size = 20) =>
    api.get<ApiResponse<PageResponse<Patient>>>(`${BASE}/search`, { params: { q, page, size } }).then(r => r.data.data!),
  toggleClinicalTrial: (patientId: string) =>
    api.patch(`${BASE}/${patientId}/clinical-trial`),
}
