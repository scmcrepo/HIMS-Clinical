// ─── Template Types ────────────────────────────────────────────────────────────
export type CaseSheetVisitType = 'OP' | 'IP' | 'BOTH'

export interface FieldOption {
  value: string
  label: string
}

export type FieldType =
  | 'TEXT'
  | 'TEXTAREA'
  | 'NUMBER'
  | 'SELECT'
  | 'MULTI_SELECT'
  | 'DATE'
  | 'CHECKBOX'
  | 'RADIO'
  | 'HEADING'
  | 'ROM_GRID'           // Structured Range of Motion grid (degrees per movement)
  | 'FUNCTIONAL_SCORE'   // Validated ortho outcome scores (Oxford, Harris, KOOS etc.)
  | 'IMPLANT_LOG'        // Multi-row implant record (name, mfr, batch, size)
  | 'PREOP_CHECKLIST'    // Pre-operative safety checklist

export interface FieldResponse {
  id: string
  fieldKey: string
  label: string
  fieldType: FieldType
  section: string | null
  displayOrder: number
  required: boolean
  placeholder: string | null
  helpText: string | null
  options: FieldOption[] | null
  validation: Record<string, unknown> | null
  defaultValue: string | null
  visible: boolean
}

export interface CaseSheetTemplateSummary {
  id: string
  name: string
  specialization: string
  visitType: CaseSheetVisitType
  description: string | null
  defaultTemplate: boolean
  fieldCount: number
}

export interface CaseSheetTemplateDetail extends CaseSheetTemplateSummary {
  fields: FieldResponse[]
  createdAt: string
  modifiedAt: string
}

// ─── Structured field value types ────────────────────────────────────────────
export interface RomRow {
  joint: string
  active_flexion: string
  active_extension: string
  active_abduction: string
  active_adduction: string
  active_ir: string
  active_er: string
  notes: string
}

export interface FunctionalScoreEntry {
  scoreType: string    // e.g. "Oxford Knee Score"
  value: string        // e.g. "32"
  date: string
  notes: string
}

export interface ImplantEntry {
  component: string    // e.g. "Femoral stem"
  name: string
  manufacturer: string
  batchLot: string
  size: string
  notes: string
}

export interface PreopChecklistData {
  [key: string]: boolean
}

// ─── Record Types ─────────────────────────────────────────────────────────────
export type CaseSheetData = Record<string, unknown>

export interface CaseSheetRecordResponse {
  id: string
  encounterId: string
  template: CaseSheetTemplateSummary
  data: CaseSheetData
  recordedBy: string | null
  recordedAt: string
  modifiedAt: string
}

// ─── Request Types ────────────────────────────────────────────────────────────
export interface SaveRecordRequest {
  templateId?: string
  data: CaseSheetData
}

export interface CreateTemplateRequest {
  name: string
  specialization: string
  visitType: CaseSheetVisitType
  description?: string
  defaultTemplate: boolean
  fields: FieldRequest[]
}

export interface FieldRequest {
  id?: string
  fieldKey: string
  label: string
  fieldType: string
  section?: string | null
  displayOrder: number
  required: boolean
  placeholder?: string | null
  helpText?: string | null
  options?: FieldOption[] | null
  validation?: Record<string, unknown> | null
  defaultValue?: string | null
  visible: boolean
}

export interface CasesheetLoadResponse {
  template: CaseSheetTemplateDetail | null
  records: CaseSheetRecordResponse[]
}
