

export const BillStatusValues = ['DRAFT','SETTLED','WITH_DUE','REFUNDED','CANCELLED'] as const
export const BillTypeValues    = ['CASH','CREDIT','INSURANCE'] as const
export const EncounterTypeValues = ['OUTPATIENT','INPATIENT'] as const
export const PaymentModeValues = ['CASH','CARD','CHEQUE','TRANSFER','UPI','INSURANCE'] as const
export const PaymentTypeValues = ['DEPOSIT','PAYMENT','REFUND','ADVANCE_REFUND'] as const
export const ChargeLineStatusValues = ['CANCELLED','MODIFIED','REFUNDED'] as const

export type BillStatus    = typeof BillStatusValues[number]
export type BillType      = typeof BillTypeValues[number]
export type EncounterType = typeof EncounterTypeValues[number]
export type PaymentMode   = typeof PaymentModeValues[number]
export type PaymentType   = typeof PaymentTypeValues[number]
export type ChargeLineStatus = typeof ChargeLineStatusValues[number]

export interface ChargeLineItem {
  id: string
  serviceCatalogItemId: string
  itemName: string
  pricingTierId: string | null
  amount: number
  unitRate: number
  quantity: number
  quantitative: boolean
  discountAmount: number
  disallowedAmount: number
  status: ChargeLineStatus | null
  bedChargeFrom: string | null
  bedChargeTo: string | null
  cancelReason: string | null
  createdAt: string
}

export interface Payment {
  id: string
  amount: number
  paymentMode: PaymentMode
  paymentType: PaymentType
  recordedAt: string
  sequenceNumber: string | null
  notes: string | null
}

export interface Bill {
  id: string
  patientId: string
  encounterId: string | null
  primaryProviderId: string | null
  payorId: string | null
  patientName?: string
  patientNumber?: string
  patientGender?: string
  consultantName?: string
  billAmount: number
  discountTotal: number
  paymentTotal: number
  serviceRefundTotal: number
  refundTotal: number
  dueAmount: number
  status: BillStatus
  billType: BillType
  encounterType: EncounterType
  billDate: string | null
  admissionAt: string | null
  dischargeAt: string | null
  bedNumber: string | null
  billNumber: string | null
  cancelledAt: string | null
  createdAt: string
  chargeLineItems: ChargeLineItem[]
  payments: Payment[]
}

export interface BillSummary {
  id: string
  patientId: string
  patientName?: string
  patientNumber?: string
  encounterId: string | null
  billAmount: number
  dueAmount: number
  refundTotal: number
  discountTotal: number
  status: BillStatus
  billType: BillType
  encounterType: EncounterType
  billDate: string | null
  billNumber: string | null
  createdAt: string
}
