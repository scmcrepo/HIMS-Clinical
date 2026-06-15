import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'

export interface UserRoleSummary { id: string; name: string }
export interface UserRecord { id: string; username: string; firstName: string; lastName: string; fullName: string; email: string|null; status: string; accountLocked: boolean; roles: UserRoleSummary[]; departmentIds: string[]; consultantId: string|null; showCasesheet: boolean; speechLanguage: string; textAutoSuggest: boolean; salutation?: string; phoneNo?: string }
export interface RoleRecord { id: string; name: string; description: string|null; status: number; features: Array<{ id: string; featureKey: string; description: string|null; module: string|null }> }
export interface CreateUserCmd { username: string; password?: string; firstName: string; lastName: string; email?: string; roleIds: string[]; departmentIds?: string[]; consultantId?: string | null; showCasesheet?: boolean; speechLanguage?: string; salutation?: string; phoneNo?: string; status?: string }
export interface CreateRoleCmd { name: string; description?: string; featureIds: string[] }

const BASE = '/users'
export const userApi = {
  getAll:  () => api.get<ApiResponse<UserRecord[]>>(BASE).then(r => r.data.data ?? []),
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) =>
    api.get<ApiResponse<PageResponse<UserRecord>>>(`${BASE}/page`, { params }).then(r => r.data.data!),
  getById: (id: string) => api.get<ApiResponse<UserRecord>>(`${BASE}/${id}`).then(r => r.data.data!),
  getMe:   () => api.get<ApiResponse<UserRecord>>(`${BASE}/me`).then(r => r.data.data!),
  create:  (cmd: CreateUserCmd) => api.post<ApiResponse<UserRecord>>(BASE, cmd).then(r => r.data.data!),
  update:  (id: string, cmd: Partial<CreateUserCmd> & { status?: string }) => api.put<ApiResponse<UserRecord>>(`${BASE}/${id}`, cmd).then(r => r.data.data!),
  resetPassword: (id: string, newPassword: string) => api.put(`${BASE}/${id}/password`, null, { params: { newPassword } }),
  changeOwnPassword: (currentPassword: string, newPassword: string) => api.put(`${BASE}/me/password`, { currentPassword, newPassword }),
  checkCurrentPassword: (currentPassword: string) => api.get<ApiResponse<boolean>>(`${BASE}/check-password`, { params: { currentPassword } }).then(r => r.data.data),
}

export const roleApi = {
  getAll:    () => api.get<ApiResponse<RoleRecord[]>>('/roles').then(r => r.data.data ?? []),
  getPaginated: (params?: { start?: number; limit?: number; value?: string }) =>
    api.get<ApiResponse<PageResponse<RoleRecord>>>('/roles/page', { params }).then(r => r.data.data!),
  create:    (cmd: CreateRoleCmd) => api.post<ApiResponse<RoleRecord>>('/roles', cmd).then(r => r.data.data!),
  update:    (id: string, cmd: CreateRoleCmd) => api.put<ApiResponse<RoleRecord>>(`/roles/${id}`, cmd).then(r => r.data.data!),
  getFeatures: () => api.get<ApiResponse<Array<{id:string;featureKey:string;description:string|null;module:string|null}>>>('/roles/features').then(r => r.data.data ?? []),
  getMyFeatures: (module?: string) => api.get<ApiResponse<Record<string,boolean>>>('/roles/features/me', { params: module ? { module } : {} }).then(r => r.data.data ?? {}),
}
