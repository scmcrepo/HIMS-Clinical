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
} from '../../types/casesheet'

// ─── Template API ──────────────────────────────────────────────────────────────
export const templateApi = {
  list: (specialization?: string, visitType?: CaseSheetVisitType) =>
    api.get<ApiResponse<CaseSheetTemplateSummary[]>>('/case-sheet-templates', {
      params: { specialization, visitType },
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
