import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface SmtpConfigDto {
  id?: string
  smtpHost: string
  smtpPort: number
  username: string
  password?: string
  protocol: string
  tlsEnabled: boolean
  sslEnabled: boolean
  fromEmail: string
  fromName: string
  active: boolean
  createdAt?: string
  modifiedAt?: string
}

export interface SmtpTestDto {
  smtpHost: string
  smtpPort: number
  username: string
  password: string
  protocol: string
  tlsEnabled: boolean
  sslEnabled: boolean
  fromEmail: string
  fromName: string
  toEmail: string
}

export const smtpConfigApi = {
  getAll: () =>
    api.get<ApiResponse<SmtpConfigDto[]>>('/smtp-config').then(r => r.data.data ?? []),

  getById: (id: string) =>
    api.get<ApiResponse<SmtpConfigDto>>(`/smtp-config/${id}`).then(r => r.data.data!),

  create: (data: SmtpConfigDto) =>
    api.post<ApiResponse<SmtpConfigDto>>('/smtp-config', data).then(r => r.data),

  update: (id: string, data: SmtpConfigDto) =>
    api.put<ApiResponse<SmtpConfigDto>>(`/smtp-config/${id}`, data).then(r => r.data),

  remove: (id: string) =>
    api.delete<ApiResponse<void>>(`/smtp-config/${id}`).then(r => r.data),

  testConnection: (data: SmtpTestDto) =>
    api.post<ApiResponse<void>>('/smtp-config/test', data).then(r => r.data),
}
