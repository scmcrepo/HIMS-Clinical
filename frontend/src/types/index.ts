import { z } from 'zod'

// ── Enums ─────────────────────────────────────────────────────────────────────

export const BillStatusSchema = z.enum(['DRAFT','SETTLED','PARTIALLY_SETTLED','REFUNDED','CANCELLED'])
export const BillTypeSchema   = z.enum(['CASH','CREDIT','INSURANCE'])
export const EncounterTypeSchema = z.enum(['OUTPATIENT','INPATIENT'])
export const PaymentTypeSchema   = z.enum(['DEPOSIT','PAYMENT','REFUND','ADVANCE_REFUND'])
export const PaymentModeSchema   = z.enum(['CASH','CARD','CHEQUE','TRANSFER','UPI'])
export const ChargeLineStatusSchema = z.enum(['CANCELLED','MODIFIED','REFUNDED']).nullable()
export const EncounterStatusSchema  = z.enum(['CHECKED_IN','CONSULTATION_STARTED','CASESHEET_RECORDED','BILLING_DONE'])
export const GenderSchema     = z.enum(['MALE','FEMALE'])
export const VisitModeSchema  = z.enum(['WALK_IN','APPOINTMENT','TELE_CONSULT'])

export type BillStatus     = z.infer<typeof BillStatusSchema>
export type BillType       = z.infer<typeof BillTypeSchema>
export type EncounterType  = z.infer<typeof EncounterTypeSchema>
export type PaymentType    = z.infer<typeof PaymentTypeSchema>
export type PaymentMode    = z.infer<typeof PaymentModeSchema>
export type EncounterStatus = z.infer<typeof EncounterStatusSchema>
export type Gender         = z.infer<typeof GenderSchema>
export type VisitMode      = z.infer<typeof VisitModeSchema>

// ── Bill ──────────────────────────────────────────────────────────────────────

export const ChargeLineItemSchema = z.object({
  id:                    z.string().uuid(),
  serviceCatalogItemId:  z.string().uuid(),
  serviceName:           z.string().nullable(),
  amount:                z.number(),
  unitRate:              z.number(),
  quantity:              z.number(),
  quantitative:          z.boolean(),
  discountAmount:        z.number(),
  disallowedAmount:      z.number(),
  total:                 z.number(),
  status:                ChargeLineStatusSchema,
  bedChargeFrom:         z.string().nullable(),
  bedChargeTo:           z.string().nullable(),
  cancelledAt:           z.string().nullable(),
  cancelReason:          z.string().nullable(),
})

export const PaymentSchema = z.object({
  id:             z.string().uuid(),
  amount:         z.number(),
  paymentMode:    PaymentModeSchema,
  paymentType:    PaymentTypeSchema,
  recordedAt:     z.string(),
  sequenceNumber: z.string().nullable(),
  notes:          z.string().nullable(),
})

export const BillSchema = z.object({
  id:                   z.string().uuid(),
  patientId:            z.string().uuid(),
  encounterId:          z.string().uuid().nullable(),
  primaryProviderId:    z.string().uuid().nullable(),
  payorId:              z.string().uuid().nullable(),
  billAmount:           z.number(),
  discountTotal:        z.number(),
  paymentTotal:         z.number(),
  serviceRefundTotal:   z.number(),
  refundTotal:          z.number(),
  dueAmount:            z.number(),
  status:               BillStatusSchema,
  billType:             BillTypeSchema,
  encounterType:        EncounterTypeSchema,
  billDate:             z.string().nullable(),
  admissionAt:          z.string().nullable(),
  dischargeAt:          z.string().nullable(),
  bedNumber:            z.string().nullable(),
  chargeLineItems:      z.array(ChargeLineItemSchema),
  payments:             z.array(PaymentSchema),
  createdAt:            z.string(),
  modifiedAt:           z.string(),
})

export const BillSummarySchema = BillSchema.pick({
  id: true, encounterId: true, billAmount: true, discountTotal: true,
  paymentTotal: true, dueAmount: true, status: true, billType: true,
  encounterType: true, billDate: true, createdAt: true,
})

export type Bill        = z.infer<typeof BillSchema>
export type BillSummary = z.infer<typeof BillSummarySchema>
export type ChargeLineItem = z.infer<typeof ChargeLineItemSchema>
export type Payment     = z.infer<typeof PaymentSchema>

// ── Patient ───────────────────────────────────────────────────────────────────

export const PatientSchema = z.object({
  id:                     z.string().uuid(),
  salutation:             z.string().nullable(),
  firstName:              z.string(),
  lastName:               z.string(),
  fullName:               z.string(),
  age:                    z.string(),
  gender:                 GenderSchema,
  dateOfBirth:            z.string().nullable(),
  estimatedDateOfBirth:   z.string(),
  contactNumber:          z.string().nullable(),
  address:                z.string().nullable(),
  primaryProviderId:      z.string().uuid().nullable(),
  areaId:                 z.string().uuid().nullable(),
  categoryId:             z.string().uuid().nullable(),
  patientNumber:          z.string(),
  isClinicalTrial:        z.boolean(),
  createdAt:              z.string(),
  modifiedAt:             z.string(),
})

export type Patient = z.infer<typeof PatientSchema>

// ── Encounter ─────────────────────────────────────────────────────────────────

export const EncounterSchema = z.object({
  id:                   z.string().uuid(),
  patientId:            z.string().uuid(),
  primaryProviderId:    z.string().uuid(),
  appointmentId:        z.string().uuid().nullable(),
  encounterType:        EncounterTypeSchema,
  status:               EncounterStatusSchema,
  visitMode:            VisitModeSchema,
  startedAt:            z.string(),
  checkedInAt:          z.string().nullable(),
  dischargedAt:         z.string().nullable(),
  diagnosis:            z.string().nullable(),
  diagnosisCodeId:      z.string().uuid().nullable(),
  lastBedId:            z.string().uuid().nullable(),
  hasBed:               z.boolean(),
  hasDraftBill:         z.boolean(),
  casesheetRecordedAt:  z.string().nullable(),
  vitalData:            z.record(z.unknown()).nullable(),
  consultantShareMap:   z.record(z.unknown()).nullable(),
  cancelled:            z.boolean(),
  createdAt:            z.string(),
  modifiedAt:           z.string(),
})

export type Encounter = z.infer<typeof EncounterSchema>

// ── ApiResponse wrapper ───────────────────────────────────────────────────────

export const ApiResponseSchema = <T extends z.ZodTypeAny>(dataSchema: T) =>
  z.object({ message: z.string(), data: dataSchema })

export const PageSchema = <T extends z.ZodTypeAny>(itemSchema: T) =>
  z.object({
    content:          z.array(itemSchema),
    totalElements:    z.number(),
    totalPages:       z.number(),
    size:             z.number(),
    number:           z.number(),
  })
