import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type {
  CaseSheetTemplateSummary,
  CaseSheetTemplateDetail,
  CaseSheetRecordResponse,
  CasesheetLoadResponse,
  SaveRecordRequest,
  CreateTemplateRequest,
  CaseSheetVisitType,
  DischargeTemplateSummary,
  DischargeTemplateDetail,
  DischargeRecordResponse,
  CreateDischargeTemplateRequest,
} from '../../types/casesheet'

// ─── Template API ──────────────────────────────────────────────────────────────
export const templateApi = {
  list: (specialization?: string, visitType?: CaseSheetVisitType, status?: 'ACTIVE' | 'INACTIVE' | 'DELETED') =>
    api.get<ApiResponse<CaseSheetTemplateSummary[]>>('/case-sheet-templates', {
      params: { specialization, visitType, status },
    }).then(r => r.data.data!),

  getDefault: (specialization: string, visitType: CaseSheetVisitType) =>
    api.get<ApiResponse<CaseSheetTemplateDetail>>('/case-sheet-templates/default', {
      params: { specialization, visitType },
    }).then(r => r.data.data!),

  getById: (id: string) =>
    api.get<ApiResponse<CaseSheetTemplateDetail>>(`/case-sheet-templates/${id}`)
      .then(r => r.data.data!),

  create: (req: CreateTemplateRequest) =>
    api.post<ApiResponse<CaseSheetTemplateDetail>>('/case-sheet-templates', req)
      .then(r => r.data.data!),

  update: (id: string, req: Partial<CreateTemplateRequest>) =>
    api.put<ApiResponse<CaseSheetTemplateDetail>>(`/case-sheet-templates/${id}`, req)
      .then(r => r.data.data!),

  delete: (id: string) =>
    api.delete(`/case-sheet-templates/${id}`),
}

// ─── Record API ────────────────────────────────────────────────────────────────
export const recordApi = {
  getByEncounter: (encounterId: string) =>
    api.get<ApiResponse<CaseSheetRecordResponse[]>>(
      `/encounters/${encounterId}/case-sheet-records`
    ).then(r => r.data.data!),

  save: (encounterId: string, req: SaveRecordRequest) =>
    api.post<ApiResponse<CaseSheetRecordResponse>>(
      `/encounters/${encounterId}/case-sheet-records`, req
    ).then(r => r.data.data!),

  delete: (encounterId: string, recordId: string) =>
    api.delete(`/encounters/${encounterId}/case-sheet-records/${recordId}`),
}

// ─── OP Queue API ──────────────────────────────────────────────────────────────
export const opQueueApi = {
  loadCasesheet: (encounterId: string, specialization?: string, visitType?: string) =>
    api.get<ApiResponse<CasesheetLoadResponse>>(`/op-queue/${encounterId}/casesheet`, {
      params: { specialization, visitType },
    }).then(r => r.data.data!),

  saveCasesheet: (encounterId: string, req: SaveRecordRequest) =>
    api.post<ApiResponse<CaseSheetRecordResponse>>(
      `/op-queue/${encounterId}/casesheet`, req
    ).then(r => r.data.data!),

  markConsulted: (encounterId: string) =>
    api.post<ApiResponse<unknown>>(`/op-queue/${encounterId}/mark-consulted`)
      .then(r => r.data),

  requestAdmission: (encounterId: string, payload: {
    reason?: string; adviceToPatient?: string; instructionsToNurses?: string
  }) =>
    api.post<ApiResponse<unknown>>(`/op-queue/${encounterId}/admit`, payload)
      .then(r => r.data),
}

// ─── IP CaseSheet API ─────────────────────────────────────────────────────────
export const ipCasesheetApi = {
  loadCasesheet: (encounterId: string, specialization = 'GENERAL') =>
    api.get<ApiResponse<CasesheetLoadResponse>>(`/ip-casesheet/${encounterId}/casesheet`, {
      params: { specialization },
    }).then(r => r.data.data!),

  saveCasesheet: (encounterId: string, req: SaveRecordRequest) =>
    api.post<ApiResponse<CaseSheetRecordResponse>>(
      `/ip-casesheet/${encounterId}/casesheet`, req
    ).then(r => r.data.data!),
}

// ─── Discharge Summary Template API ───────────────────────────────────────────
export const dischargeTemplateApi = {
  list: (specialization?: string, status?: 'ACTIVE' | 'INACTIVE' | 'DELETED') =>
    api.get<ApiResponse<DischargeTemplateSummary[]>>('/discharge-summary-templates', {
      params: { specialization, status },
    }).then(r => r.data.data!),

  getById: (id: string) =>
    api.get<ApiResponse<DischargeTemplateDetail>>(`/discharge-summary-templates/${id}`)
      .then(r => r.data.data!),

  create: (req: CreateDischargeTemplateRequest) =>
    api.post<ApiResponse<DischargeTemplateDetail>>('/discharge-summary-templates', req)
      .then(r => r.data.data!),

  update: (id: string, req: Partial<CreateDischargeTemplateRequest>) =>
    api.put<ApiResponse<DischargeTemplateDetail>>(`/discharge-summary-templates/${id}`, req)
      .then(r => r.data.data!),

  delete: (id: string) =>
    api.delete(`/discharge-summary-templates/${id}`),
}

// ─── Discharge Summary Record API ──────────────────────────────────────────────
export const dischargeRecordApi = {
  getByEncounter: (encounterId: string) =>
    api.get<ApiResponse<DischargeRecordResponse[]>>(
      `/encounters/${encounterId}/discharge-summary-records`
    ).then(r => r.data.data!),

  save: (encounterId: string, req: SaveRecordRequest) =>
    api.post<ApiResponse<DischargeRecordResponse>>(
      `/encounters/${encounterId}/discharge-summary-records`, req
    ).then(r => r.data.data!),
}
