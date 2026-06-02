import api from '../../lib/axios'
import type { DiagnosticReport } from '../../types/diagnostic'

const BASE = '/diagReport'

export const diagnosticReportApi = {
  saveLabReports: (orderLineId: string, templateId: string | null, reports: Record<string, string>) =>
    api.post<{ data: DiagnosticReport[] }>(BASE, { orderLineId, templateId, reports })
      .then(r => r.data.data),

  getReportsByOrderLine: (orderLineId: string) =>
    api.get<{ data: DiagnosticReport[] }>(BASE, { params: { orderLineId } })
      .then(r => r.data.data),

  getReportsByEncounter: (encounterId: string) =>
    api.get<{ data: DiagnosticReport[] }>(`${BASE}/encounter/${encounterId}`)
      .then(r => r.data.data),

  saveCustomReport: (orderLineId: string, templateId: string, templateData: string) =>
    api.post<{ data: DiagnosticReport }>(`${BASE}/saveCustomReport`, { orderLineId, templateId, templateData })
      .then(r => r.data.data),

  getCustomReport: (orderLineId: string, templateId: string) =>
    api.get<{ data: DiagnosticReport }>(`${BASE}/getCustomReport`, { params: { orderLineId, templateId } })
      .then(r => r.data.data),
}
