import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export interface SmsTemplate { key: string; body: string; isStandard: string }
export interface SmsPlaceholder { variable: string; description: string }

export const smsTemplateApi = {
  getAll:          () => api.get<ApiResponse<SmsTemplate[]>>('/sms-templates').then(r => r.data.data ?? []),
  getByKey:        (key: string) => api.get<ApiResponse<{key:string;body:string}>>(`/sms-templates/${key}`).then(r => r.data.data!),
  save:            (key: string, body: string) => api.put(`/sms-templates/${key}`, { body }),
  getPlaceholders: () => api.get<ApiResponse<SmsPlaceholder[]>>('/sms-templates/placeholders').then(r => r.data.data ?? []),
}
