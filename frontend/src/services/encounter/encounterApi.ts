import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { ClinicalEncounter, EncounterSummary, VisitMode } from '../../types/encounter'

export interface CreateEncounterCmd { patientId: string; primaryProviderId: string; appointmentId?: string | undefined; visitMode?: VisitMode | undefined }
export interface RecordVitalsCmd { vitals: Record<string, unknown> }
export interface RecordCasesheetCmd { chiefComplaint?: string | undefined; historyOfPresentIllness?: string | undefined; examination?: string | undefined; diagnosis?: string | undefined; diagnosisCodeId?: string | undefined; plan?: string | undefined; customFields?: Record<string, unknown> | undefined }
export interface DischargeCmd { dischargeAt?: string | undefined; dischargeNotes?: string | undefined }

const BASE = '/encounters'
export const encounterApi = {
  createOutpatient: (cmd: CreateEncounterCmd) => api.post<ApiResponse<ClinicalEncounter>>(`${BASE}/outpatient`, cmd).then(r => r.data.data!),
  createInpatient:  (cmd: CreateEncounterCmd) => api.post<ApiResponse<ClinicalEncounter>>(`${BASE}/inpatient`, cmd).then(r => r.data.data!),
  getById: (id: string) => api.get<ApiResponse<ClinicalEncounter>>(`${BASE}/${id}`).then(r => r.data.data!),
  getByPatient: (patientId: string, page = 0, size = 10) => api.get<ApiResponse<PageResponse<EncounterSummary>>>(`${BASE}/patient/${patientId}`, { params: { page, size } }).then(r => r.data.data!),
  getActiveInpatient: (patientId: string) => api.get<ApiResponse<ClinicalEncounter>>(`${BASE}/patient/${patientId}/active-inpatient`).then(r => r.data.data!),
  recordVitals: (id: string, cmd: RecordVitalsCmd) => api.post<ApiResponse<ClinicalEncounter>>(`${BASE}/${id}/vitals`, cmd).then(r => r.data.data!),
  recordCasesheet: (id: string, cmd: RecordCasesheetCmd) => api.post<ApiResponse<ClinicalEncounter>>(`${BASE}/${id}/casesheet`, cmd).then(r => r.data.data!),
  discharge: (id: string, cmd: DischargeCmd) => api.post<ApiResponse<ClinicalEncounter>>(`${BASE}/${id}/discharge`, cmd).then(r => r.data.data!),
  cancel: (id: string) => api.delete<ApiResponse<ClinicalEncounter>>(`${BASE}/${id}`).then(r => r.data.data!),
  updateConsultantShare: (id: string, consultantId: string, data: Record<string, unknown>) => api.put(`${BASE}/${id}/consultant-share/${consultantId}`, data),
  getAll: (query?: string, date?: string, page = 0, size = 10) => api.get<ApiResponse<PageResponse<EncounterSummary>>>(`${BASE}`, { params: { query, date, page, size } }).then(r => r.data.data!),
  getActiveInpatients: (query?: string, page = 0, size = 10, date?: string, consultantId?: string) => api.get<ApiResponse<PageResponse<EncounterSummary>>>(`${BASE}/active-inpatients`, { params: { query, page, size, date, consultantId } }).then(r => r.data.data!),
  getTodayOutpatients: (query?: string, date?: string, page = 0, size = 10, consultantId?: string, status?: string) => api.get<ApiResponse<PageResponse<EncounterSummary>>>(`${BASE}/today-outpatients`, { params: { query, date, page, size, consultantId, status } }).then(r => r.data.data!),
  getActiveInpatientsWithBeds: () => api.get<ApiResponse<EncounterSummary[]>>(`/lookup-service/inpatients`).then(r => r.data.data!),
  getPendingAdmissionRequests: (query?: string, consultantId?: string, page = 0, size = 5) => api.get<ApiResponse<PageResponse<EncounterSummary>>>(`${BASE}/admission-requests`, { params: { query, consultantId, page, size } }).then(r => r.data.data!),
}
