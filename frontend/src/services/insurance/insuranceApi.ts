import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'
import type {
  ChecklistItem,
  ChequeReceipt,
  CourierVendor,
  InsuranceDesk,
  ModeOfCommunication,
  ModeOfDispatch,
  TpaDecision,
  WorkflowStage,
} from '../../features/insurance/insuranceDesk'

export type InsuranceStatus = 'ACTIVE' | 'PRE_AUTH_REQUESTED' | 'PRE_AUTH_RECEIVED' | 'SETTLED' | 'REJECTED'
export type InsurancePreAuthType = 'PLANNED' | 'EMERGENCY' | 'DAY_CARE' | 'OPD' | 'MATERNITY'

export interface InsuranceRecord {
  id: string
  patientId: string | null
  billId: string | null
  encounterId: string | null
  insurerName: string
  policyNumber: string | null
  preAuthType: InsurancePreAuthType | null
  preAuthNumber: string | null
  preAuthAmount: number | null
  preAuthDate: string | null
  communication: string | null
  insuranceStatus: InsuranceStatus
  rejectionReason: string | null
}

export interface CreateInsuranceCmd {
  patientId?: string
  billId?: string
  encounterId?: string
  insurerName: string
  policyNumber?: string
  /** Member / card id from the health card — Screen 1.3. */
  memberId?: string
  /** Third-party administrator handling the claim. */
  tpaName?: string
  /** INDIVIDUAL | FAMILY_FLOATER | PM_JAY | GROUP */
  policyType?: string
  preAuthType?: InsurancePreAuthType
  communication?: string
}

export const insuranceApi = {
  create: (cmd: CreateInsuranceCmd) =>
    api.post<ApiResponse<InsuranceRecord>>('/insurance', cmd).then(r => r.data.data!),

  getById: (id: string) =>
    api.get<ApiResponse<InsuranceRecord>>(`/insurance/${id}`).then(r => r.data.data!),

  getByPatient: (patientId: string) =>
    api.get<ApiResponse<InsuranceRecord[]>>(`/insurance/patient/${patientId}`)
      .then(r => r.data.data ?? []),

  getByBill: (billId: string) =>
    api.get<ApiResponse<InsuranceRecord[]>>(`/insurance/bill/${billId}`)
      .then(r => r.data.data ?? []),

  getPending: () =>
    api.get<ApiResponse<InsuranceRecord[]>>('/insurance/pending').then(r => r.data.data ?? []),

  receivePreAuth: (id: string, preAuthNumber: string, amount: number, receivedDate: string) =>
    api.post<ApiResponse<InsuranceRecord>>(`/insurance/${id}/pre-auth`, {
      preAuthNumber, amount, receivedDate,
    }).then(r => r.data.data!),

  settle: (id: string) =>
    api.post<ApiResponse<InsuranceRecord>>(`/insurance/${id}/settle`).then(r => r.data.data!),

  reject: (id: string, reason: string) =>
    api.post<ApiResponse<InsuranceRecord>>(`/insurance/${id}/reject`, { reason })
      .then(r => r.data.data!),

  // ── Manual TPA desk, seven stages (WO-020) ───────────────────────────────
  //
  //  Every stage POST returns the WHOLE desk payload, not just the fields it
  //  wrote. The screen shows all seven stages at once, and returning a partial
  //  record would leave the timeline showing a stage that no longer matches
  //  what was just saved. Callers replace their state with the response.

  getDesk: (id: string) =>
    api.get<ApiResponse<InsuranceDesk>>(`/insurance/${id}/desk`).then(r => r.data.data!),

  /**
   * The desk's landing query. Both dates optional — the server defaults to the
   * last 30 days rather than to today, so the grid is not empty every morning.
   */
  search: (params: { searchFromDate?: string; searchToDate?: string; stage?: WorkflowStage }) =>
    api.get<ApiResponse<InsuranceDesk[]>>('/insurance', { params })
      .then(r => r.data.data ?? []),

  submitPreauth: (id: string, cmd: SubmitPreauthCmd) =>
    api.post<ApiResponse<InsuranceDesk>>(`/insurance/${id}/stages/preauth`, cmd)
      .then(r => r.data.data!),

  submitPreauthApproval: (id: string, cmd: SubmitPreauthApprovalCmd) =>
    api.post<ApiResponse<InsuranceDesk>>(`/insurance/${id}/stages/preauth-approval`, cmd)
      .then(r => r.data.data!),

  /** 400 with INSURANCE_BILL_NOT_LINKED when no credit bill is bound yet. */
  submitEnhancement: (id: string, cmd: SubmitEnhancementCmd) =>
    api.post<ApiResponse<InsuranceDesk>>(`/insurance/${id}/stages/enhancement`, cmd)
      .then(r => r.data.data!),

  submitEnhancementApproval: (id: string, cmd: SubmitEnhancementApprovalCmd) =>
    api.post<ApiResponse<InsuranceDesk>>(`/insurance/${id}/stages/enhancement-approval`, cmd)
      .then(r => r.data.data!),

  /** Replaces the stored manifest wholesale — send the complete list. */
  submitChecklist: (id: string, checklists: ChecklistItem[]) =>
    api.post<ApiResponse<InsuranceDesk>>(`/insurance/${id}/stages/checklist`, { checklists })
      .then(r => r.data.data!),

  submitDispatch: (id: string, cmd: SubmitDispatchCmd) =>
    api.post<ApiResponse<InsuranceDesk>>(`/insurance/${id}/stages/dispatch`, cmd)
      .then(r => r.data.data!),

  /**
   * Cheques and itemised deductions together. The cheque list is reconciled by
   * id, so rows dropped from the grid are deleted — always send the full list.
   */
  submitDisallowance: (id: string, cmd: SubmitDisallowanceCmd) =>
    api.post<ApiResponse<InsuranceDesk>>(`/insurance/${id}/stages/disallowance`, cmd)
      .then(r => r.data.data!),

  linkBill: (id: string, billId: string) =>
    api.put<ApiResponse<InsuranceDesk>>('/insurance/updateBillId', { id, billId })
      .then(r => r.data.data!),

  // ── Reference data ───────────────────────────────────────────────────────

  getCourierVendors: () =>
    api.get<ApiResponse<CourierVendor[]>>('/insurance/courierVendors')
      .then(r => r.data.data ?? []),

  getModesOfCommunication: () =>
    api.get<ApiResponse<ModeOfCommunication[]>>('/insurance/modeOfCommunication')
      .then(r => r.data.data ?? []),

  getModesOfDispatch: () =>
    api.get<ApiResponse<ModeOfDispatch[]>>('/insurance/modeOfDispatch')
      .then(r => r.data.data ?? []),

  getWorkflowStages: () =>
    api.get<ApiResponse<Array<{ id: WorkflowStage; name: string; label: string }>>>(
      '/insurance/getStatus',
    ).then(r => r.data.data ?? []),

  getAgeingCriteria: () =>
    api.get<ApiResponse<Array<{ id: string; name: string }>>>('/insurance/getAgeingCriteria')
      .then(r => r.data.data ?? []),
}

// ── Stage command shapes. All amounts in PAISE. ────────────────────────────

export interface SubmitPreauthCmd {
  cardNo?: string
  /** ISO date. Recorded even when in the past — an expired card is a warning, not an error. */
  cardValidity?: string
  policyNumber?: string
  preAuthType?: InsurancePreAuthType
  communicationToTpa: ModeOfCommunication
  /** Required when communicationToTpa is FAX. */
  faxNo?: string
  /** Required when communicationToTpa is MAIL. */
  mailId?: string
  /** ISO instant. Defaults to now server-side. */
  appliedDate?: string
  requestedAmount?: number
}

export interface SubmitPreauthApprovalCmd {
  /** The TPA's own docket number. Encrypted at rest. */
  claimNo: string
  approvalStatus: TpaDecision
  dateOfApproval?: string
  communicationByTpa?: ModeOfCommunication
  approveFaxNo?: string
  approveMailId?: string
  /** Required when APPROVED. */
  approvedLimit?: number
  /** Required when REJECTED. */
  rejectionReason?: string
}

export interface SubmitEnhancementCmd {
  enhancementType?: InsurancePreAuthType
  appliedDate?: string
  requestedAmount: number
  communicationToTpa: ModeOfCommunication
  faxNo?: string
  mailId?: string
  /** Mandatory — an unexplained enhancement earns a TPA query. */
  reasonForEnhancement: string
}

export interface SubmitEnhancementApprovalCmd {
  approvalStatus: TpaDecision
  dateOfApproval?: string
  communicationByTpa?: ModeOfCommunication
  approvedLimit?: number
  rejectionReason?: string
}

export interface SubmitDispatchCmd {
  modeOfDispatch: ModeOfDispatch
  /** Required for COURIER. */
  courier?: CourierVendor
  /** Required for COURIER — the only proof of delivery. */
  podNo?: string
  /** Required for EMAIL. */
  dispatchMailId?: string
  dispatchDate?: string
  dispatchedBy?: string
  reasonForDelay?: string
}

export interface SubmitDisallowanceCmd {
  cheques: ChequeReceipt[]
  disallowances: Array<{ chargeLineItemId: string; disallowedAmount: number }>
}
