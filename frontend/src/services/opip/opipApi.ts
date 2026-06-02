/**
 * opipApi.ts
 * ─────────────────────────────────────────────────────────────────────────────
 * API service for OP/IP clinical tabs:
 *   - Vitals (OP modal + IP multiple)
 *   - Prescription (OP inline + IP modal)
 *   - Diagnostic Order (OP inline + IP modal)
 *   - Progress Notes (IP)
 *   - Nurse Notes (IP)
 *   - Other Charges (IP)
 *   - Favorites / Frequently Used / Last Prescribed (quick-add panels)
 *   - Consultants list (Requested By dropdown)
 */
import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

// ─── Vitals ───────────────────────────────────────────────────────────────────

export interface VitalsPayload {
  weight?:           number | string
  height?:           number | string
  bpSystolic?:       number | string
  bpDiastolic?:      number | string
  pulseRate?:        number | string
  respiratoryRate?:  number | string
  temperature?:      number | string
  headCircumference?: number | string
  spo2?:             number | string
  remark?:           string
}

export interface VitalEntry extends VitalsPayload {
  id:         string
  recordedAt: string
}

export const opVitalsApi = {
  /** OP: record vitals — once per visit (status → CONSULTATION_STARTED) */
  record: (encounterId: string, vitals: VitalsPayload) =>
    api.post<ApiResponse<unknown>>(`/op-queue/${encounterId}/vitals`, { vitals })
       .then(r => r.data),

  /** OP: get recorded vitals */
  get: (encounterId: string) =>
    api.get<ApiResponse<unknown>>(`/op-queue/${encounterId}/vitals`)
       .then(r => r.data.data),
}

export const ipVitalsApi = {
  /** IP: append a new vital-signs entry (multiple allowed) */
  add: (encounterId: string, vitals: VitalsPayload) =>
    api.post<ApiResponse<unknown>>(`/ip-casesheet/${encounterId}/vitals`, { vitals })
       .then(r => r.data),

  /** IP: list all vital-sign entries */
  list: (encounterId: string) =>
    api.get<ApiResponse<VitalEntry[]>>(`/ip-casesheet/${encounterId}/vitals`)
       .then(r => r.data.data ?? []),
}

// ─── Prescription ─────────────────────────────────────────────────────────────

export interface PrescriptionLinePayload {
  drugItemId:       string
  drugName:         string
  frequency:        string
  duration:         string
  qty:              number
  instructionId?:   string
  instructionLabel?: string
  routeId?:         string
  routeLabel?:      string
  remarks?:         string
}

export interface PrescriptionPayload {
  items:          PrescriptionLinePayload[]
  requestedById?: string
}

export interface PrescriptionLineResponse extends PrescriptionLinePayload {
  id: string
}

export interface PrescriptionResponse {
  id:                string
  encounterId:       string
  requestedById?:    string
  requestedByName?:  string
  createdAt:         string
  items:             PrescriptionLineResponse[]
}

export const opPrescriptionApi = {
  /** OP: save/update prescription (inline, stored via casesheet record) */
  save: (encounterId: string, payload: PrescriptionPayload) =>
    api.post<ApiResponse<PrescriptionResponse>>(`/op-queue/${encounterId}/prescription`, payload)
       .then(r => r.data.data!),

  list: (encounterId: string) =>
    api.get<ApiResponse<PrescriptionResponse[]>>(`/op-queue/${encounterId}/prescription`)
       .then(r => r.data.data ?? []),
}

export const ipPrescriptionApi = {
  /** IP: add a new prescription (modal) */
  add: (encounterId: string, payload: PrescriptionPayload) =>
    api.post<ApiResponse<PrescriptionResponse>>(`/ip-casesheet/${encounterId}/prescription`, payload)
       .then(r => r.data.data!),

  list: (encounterId: string) =>
    api.get<ApiResponse<PrescriptionResponse[]>>(`/ip-casesheet/${encounterId}/prescription`)
       .then(r => r.data.data ?? []),
}

// ─── Diagnostic Order ─────────────────────────────────────────────────────────

export interface DiagnosticOrderLinePayload {
  diagnosticTestId?: string
  testName:          string
  category?:         string
}

export interface DiagnosticOrderPayload {
  items:          DiagnosticOrderLinePayload[]
  requestedById?: string
}

export interface DiagnosticOrderLineResponse extends DiagnosticOrderLinePayload {
  id:     string
  status: string
}

export interface DiagnosticOrderResponse {
  id:               string
  encounterId:      string
  requestedById?:   string
  requestedByName?: string
  orderedAt:        string
  items:            DiagnosticOrderLineResponse[]
}

export const opDiagnosticApi = {
  save: (encounterId: string, payload: DiagnosticOrderPayload) =>
    api.post<ApiResponse<DiagnosticOrderResponse>>(`/op-queue/${encounterId}/diagnostic-order`, payload)
       .then(r => r.data.data!),

  list: (encounterId: string) =>
    api.get<ApiResponse<DiagnosticOrderResponse[]>>(`/op-queue/${encounterId}/diagnostic-order`)
       .then(r => r.data.data ?? []),
}

export const ipDiagnosticApi = {
  add: (encounterId: string, payload: DiagnosticOrderPayload) =>
    api.post<ApiResponse<DiagnosticOrderResponse>>(`/ip-casesheet/${encounterId}/diagnostic-order`, payload)
       .then(r => r.data.data!),

  list: (encounterId: string) =>
    api.get<ApiResponse<DiagnosticOrderResponse[]>>(`/ip-casesheet/${encounterId}/diagnostic-order`)
       .then(r => r.data.data ?? []),
}

// ─── Progress Notes (IP only) ─────────────────────────────────────────────────

export interface ClinicalNotePayload {
  notes:           string
  noteAt?:         string   // ISO instant; defaults to now
  requestedById?:  string
}

export interface ClinicalNoteResponse {
  id:               string
  encounterId:      string
  notes:            string
  noteAt:           string
  requestedById?:   string
  requestedByName?: string
  createdAt:        string
}

export const progressNotesApi = {
  list: (encounterId: string) =>
    api.get<ApiResponse<ClinicalNoteResponse[]>>(`/ip-casesheet/${encounterId}/progress-notes`)
       .then(r => r.data.data ?? []),

  add: (encounterId: string, payload: ClinicalNotePayload) =>
    api.post<ApiResponse<ClinicalNoteResponse>>(`/ip-casesheet/${encounterId}/progress-notes`, payload)
       .then(r => r.data.data!),
}

// ─── Nurse Notes (IP only) ────────────────────────────────────────────────────

export const nurseNotesApi = {
  list: (encounterId: string) =>
    api.get<ApiResponse<ClinicalNoteResponse[]>>(`/ip-casesheet/${encounterId}/nurse-notes`)
       .then(r => r.data.data ?? []),

  add: (encounterId: string, payload: ClinicalNotePayload) =>
    api.post<ApiResponse<ClinicalNoteResponse>>(`/ip-casesheet/${encounterId}/nurse-notes`, payload)
       .then(r => r.data.data!),
}

// ─── Other Charges (IP only) ──────────────────────────────────────────────────

export interface OtherChargePayload {
  chargeLabel:          string
  serviceCatalogItemId?: string
  amount:               number
  qty?:                 number
  remarks?:             string
}

export interface OtherChargeResponse {
  id:                    string
  encounterId:           string
  chargeLabel:           string
  serviceCatalogItemId?: string
  amount:                number
  qty:                   number
  remarks?:              string
  createdAt:             string
}

export const otherChargesApi = {
  list: (encounterId: string) =>
    api.get<ApiResponse<OtherChargeResponse[]>>(`/ip-casesheet/${encounterId}/other-charges`)
       .then(r => r.data.data ?? []),

  add: (encounterId: string, payload: OtherChargePayload) =>
    api.post<ApiResponse<OtherChargeResponse>>(`/ip-casesheet/${encounterId}/other-charges`, payload)
       .then(r => r.data.data!),
}

// ─── Quick-Add: Favorites ─────────────────────────────────────────────────────

export interface FavoriteItem {
  id:             string
  itemId:         string
  itemName:       string
  favoriteType:   'DRUG' | 'TEST'
  consultantId:   string
  // drug extras
  frequency?:     string
  duration?:      string
  instructionLabel?: string
  routeLabel?:    string
}

export const favoritesApi = {
  list: (consultantId: string, type: 'DRUG' | 'TEST') =>
    api.get<ApiResponse<FavoriteItem[]>>('/op-ip/favorites', {
      params: { consultantId, type },
    }).then(r => r.data.data ?? []),

  /** POST /op-ip/favorites/item — persist a single drug/test as a favorite */
  add: (payload: Omit<FavoriteItem, 'id'> & { consultantId: string }) =>
    api.post<ApiResponse<FavoriteItem>>('/op-ip/favorites/item', payload)
       .then(r => r.data.data!),

  remove: (id: string) =>
    api.delete(`/op-ip/favorites/${id}`),
}

// ─── Quick-Add: Frequently Used ───────────────────────────────────────────────

export interface FrequentItem {
  itemId:   string
  itemName: string
  count:    number
  // drug extras
  frequency?:     string
  duration?:      string
  instructionLabel?: string
  routeLabel?:    string
}

export const frequentlyUsedApi = {
  drugs: (consultantId: string) =>
    api.get<ApiResponse<FrequentItem[]>>('/op-ip/frequently-used', {
      params: { consultantId, type: 'DRUG' },
    }).then(r => r.data.data ?? []),

  tests: (consultantId: string) =>
    api.get<ApiResponse<FrequentItem[]>>('/op-ip/frequently-used', {
      params: { consultantId, type: 'TEST' },
    }).then(r => r.data.data ?? []),
}

// ─── Quick-Add: Last Prescribed ───────────────────────────────────────────────

export const lastPrescribedApi = {
  get: (encounterId: string) =>
    api.get<ApiResponse<PrescriptionLineResponse[]>>('/op-ip/last-prescribed', {
      params: { encounterId },
    }).then(r => r.data.data ?? []),
}

// ─── Drug autocomplete ────────────────────────────────────────────────────────

export interface DrugItem {
  id:         string
  name:       string
  genericName?: string
  dosageForm?: string
  strength?:  string
}

export const drugSearchApi = {
  search: (query: string) =>
    api.get<ApiResponse<DrugItem[]>>('/item', {
      params: { value: query, limit: 20 },
    }).then(r => {
      const d = r.data.data as any
      return Array.isArray(d) ? d : (d?.content ?? [])
    }),
}

// ─── Diagnostic Test autocomplete ────────────────────────────────────────────

export interface DiagTest {
  id:       string
  name:     string
  testCode?: string
  category?: string
}

export const diagTestSearchApi = {
  search: (query: string) =>
    api.get<ApiResponse<DiagTest[]>>('/diagnostic/test-catalog', {
      params: { search: query, limit: 20 },
    }).then(r => r.data.data ?? []),
}

// ─── Instruction & Route masters ──────────────────────────────────────────────

export interface InstructionMaster { id: string; name: string }
export interface RouteMaster        { id: string; name: string }

export const instructionApi = {
  list: () =>
    api.get<ApiResponse<InstructionMaster[]>>('/instruction')
       .then(r => r.data.data ?? []),
}

export const routeApi = {
  list: () =>
    api.get<ApiResponse<RouteMaster[]>>('/route')
       .then(r => r.data.data ?? []),
}

// ─── Frequency Master ─────────────────────────────────────────────────────────

export interface FrequencyMaster { id: string; name: string; value: number }

export const frequencyApi = {
  list: () =>
    api.get<ApiResponse<FrequencyMaster[]>>('/frequency')
       .then(r => r.data.data ?? []),
}

// ─── Prescription Orders (Pharmacy view) ─────────────────────────────────────

export interface PrescriptionOrderRow {
  encounterId:    string
  encounterType:  string   // OP | INPATIENT
  patientId:      string | null
  patientName:    string | null
  patientNumber:  string | null
  consultantName: string | null
  prescribedAt:   string | null
  items: PrescriptionResponse['items']
}

export const prescriptionOrdersApi = {
  getPending: (params?: { patientId?: string; type?: 'OP' | 'IP' | 'ALL' }) =>
    api.get<ApiResponse<PrescriptionOrderRow[]>>('/prescription-orders', { params })
       .then(r => r.data.data ?? []),
  getForEncounter: (encounterId: string) =>
    api.get<ApiResponse<PrescriptionOrderRow[]>>(`/prescription-orders/encounter/${encounterId}`)
       .then(r => r.data.data ?? []),
}
