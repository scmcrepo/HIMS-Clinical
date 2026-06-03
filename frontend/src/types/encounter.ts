export type EncounterStatus = 'CHECKED_IN' | 'CONSULTATION_STARTED' | 'CASESHEET_RECORDED' | 'BILLING_DONE'
export type VisitMode       = 'WALK_IN' | 'APPOINTMENT' | 'TELE_CONSULT'
export type EncounterType   = 'OUTPATIENT' | 'INPATIENT'

export interface ClinicalEncounter {
  id: string
  patientId: string
  patientNumber: string | null
  patientName: string | null
  primaryProviderId: string
  appointmentId: string | null
  encounterType: EncounterType
  status: EncounterStatus
  visitMode: VisitMode
  startedAt: string
  checkedInAt: string | null
  dischargedAt: string | null
  diagnosis: string | null
  diagnosisCodeId: string | null
  hasBed: boolean
  hasDraftBill: boolean
  cancelled: boolean
  casesheetRecordedAt: string | null
  vitalData: Record<string, unknown> | null
  consultantShareMap: Record<string, unknown> | null
}

export interface EncounterSummary {
  id: string
  patientId: string
  patientNumber: string | null
  patientName: string | null
  patientMobileNumber: string | null
  patientGender: string | null
  patientAge: string | null
  primaryProviderId: string
  providerName?: string
  encounterType: EncounterType
  status: EncounterStatus
  startedAt: string
  dischargedAt: string | null
  diagnosis: string | null
  hasBed: boolean
  hasDraftBill: boolean
  bedName?: string | null
}
