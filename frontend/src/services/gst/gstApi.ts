import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export type GstEnvironment = 'SANDBOX' | 'PRODUCTION'
export type GstSyncFrequency = 'MANUAL' | 'DAILY' | 'MONTHLY'
export type GstReturnType = 'GSTR1' | 'GSTR3B'
export type GstFilingStatus = 'DRAFT' | 'GENERATED' | 'SUBMITTED' | 'FILED' | 'ERROR'

export interface GstConfig {
  gstin: string | null
  stateCode: string | null
  legalName: string | null
  sandboxBaseUrl: string | null
  productionBaseUrl: string | null
  activeEnvironment: GstEnvironment
  autoSyncFrequency: GstSyncFrequency
  integrationEnabled: boolean
  /** The secrets themselves are never sent to the browser — only whether they are set. */
  apiKeySet: boolean
  apiSecretSet: boolean
  submittable: boolean
}

export interface GstFilingErrorRow {
  code: string
  field: string | null
  invoiceNumber: string | null
  message: string
}

export interface GstFilingRecord {
  id: string
  returnType: GstReturnType
  returnPeriod: string
  periodStart: string
  periodEnd: string
  filingStatus: GstFilingStatus
  environment: GstEnvironment
  totalTaxableValue: number
  totalCgst: number
  totalSgst: number
  totalIgst: number
  lineItemCount: number
  referenceId: string | null
  submittedAt: string | null
  createdAt: string
  errors: GstFilingErrorRow[]
}

export const gstApi = {
  getConfig: () => api.get<ApiResponse<GstConfig>>('/gst/config').then(r => r.data.data!),

  saveConfig: (body: Partial<GstConfig> & { apiKey?: string; apiSecret?: string }, confirmProduction = false) =>
    api.put<ApiResponse<GstConfig>>('/gst/config', body, { params: { confirmProduction } })
      .then(r => r.data.data!),

  setIntegration: (enabled: boolean) =>
    api.put<ApiResponse<GstConfig>>('/gst/config/integration', { enabled }).then(r => r.data.data!),

  generate: (returnType: GstReturnType, period: string) =>
    api.post<ApiResponse<GstFilingRecord>>('/gst/filing/generate', null, { params: { returnType, period } })
      .then(r => r.data.data!),

  history: () => api.get<ApiResponse<GstFilingRecord[]>>('/gst/filing').then(r => r.data.data ?? []),

  submit: (id: string) =>
    api.post<ApiResponse<GstFilingRecord>>(`/gst/filing/${id}/submit`).then(r => r.data.data!),

  downloadPayload: async (id: string, returnType: GstReturnType, period: string) => {
    const res = await api.get(`/gst/filing/${id}/payload`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([res.data], { type: 'application/json' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `${returnType}_${period}.json`
    a.click()
    URL.revokeObjectURL(url)
  },
}
