export type DiagnosticType        = 'LAB' | 'RADIOLOGY'
export type DiagnosticPaymentStatus = 'ORDERED' | 'BILLED' | 'PART_PAID'
export type DiagnosticTestStatus    = 'PENDING' | 'RECORDED' | 'RESULTED' | 'CANCELLED'

export interface DiagnosticOrderLine {
  id: string
  serviceCatalogItemId: string
  itemName: string | null
  specimenId: string | null
  specimenName: string | null
  instruction: string | null
  paymentStatus: DiagnosticPaymentStatus
  testStatus: DiagnosticTestStatus
  resultValue: string | null
  resultUnit: string | null
  referenceRange: string | null
  resultRecordedAt: string | null
  hasResult: boolean
}

export interface DiagnosticOrder {
  id: string
  encounterId: string
  patientId: string
  patientNumber?: string
  patientName?: string
  patientGender?: string
  patientAge?: string
  providerId: string | null
  diagnosticType: DiagnosticType
  sequenceNumber: string | null
  orderDate: string
  paymentStatus: DiagnosticPaymentStatus
  testStatus: DiagnosticTestStatus
  lines: DiagnosticOrderLine[]
}

export interface DiagnosticDepartment {
  id: string
  name: string
  type: string
  displayOrder: number
}

export interface LabTemplateDetail {
  id: string
  resultName: string
  normalRange: string | null
  normalRangeExp: string | null
  unit: string | null
  labType: string
  orderNumber: number
}

export interface DiagnosticTemplate {
  id: string
  name: string
  diagnosticType: DiagnosticType | string
  format: string
  chargeId: string | null
  specimenId: string | null
  department: DiagnosticDepartment | null
  orderNumber: number
  header: string | null
  referenceRange: string | null
  unit: string | null
  labTemplateType: string | null
  labTemplateDetails: LabTemplateDetail[]
}

export interface DiagnosticReport {
  id: string
  diagnosticOrderLineId: string
  diagnosticTemplateId: string | null
  labTemplateDetailId: string | null
  value: string | null
  result: string | null
  isApproved: boolean
  templateData: string | null
}

export interface SpecimenCollection {
  id: string
  diagnosticId: string
  specimenId: string | null
  orderLineId: string | null
  sampleNumber: string | null
  collectionNotes: string | null
  collectedAt: string
}
