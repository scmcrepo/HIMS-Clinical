import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { Bill, BillSummary, BillType, EncounterType, PaymentMode, PaymentType } from '../../types/billing'

export interface CreateBillCmd { patientId: string; billType: BillType; encounterType: EncounterType; primaryProviderId?: string | undefined; encounterId?: string | undefined; payorId?: string | undefined }
export interface RecordPaymentCmd { payments: Array<{ amount: number; paymentMode: PaymentMode; paymentType: PaymentType; notes?: string | undefined }> }
export interface GenerateBillCmd { billDate: string; dischargeAt?: string | undefined }
export interface ApplyDiscountCmd { totalDiscount: number; lineDiscounts: Array<{ chargeLineItemId: string; amount: number }>; reason?: string | undefined }
export interface AddChargeCmd { serviceCatalogItemId: string; pricingTierId?: string | undefined; unitRate: number; quantity: number; bedChargeFrom?: string | undefined; bedChargeTo?: string | undefined }
export interface UpdateChargeCmd { lineItemId: string; rate: number; quantity: number; discount: number; reason?: string | undefined }
export interface RefundCmd { lineItemIds: string[]; amount: number; paymentMode: PaymentMode; notes?: string | undefined }

const BASE = '/bills'
export const billingApi = {
  getBillsByPatient: (patientId: string) =>
    api.get<ApiResponse<BillSummary[]>>(`${BASE}/patient/${patientId}`).then(r => r.data.data ?? []),
  getBillById: (billId: string) =>
    api.get<ApiResponse<Bill>>(`${BASE}/${billId}`).then(r => r.data.data!),
  createBill: (cmd: CreateBillCmd) =>
    api.post<ApiResponse<Bill>>(BASE, cmd).then(r => r.data.data!),
  recordPayment: (billId: string, cmd: RecordPaymentCmd) =>
    api.post<ApiResponse<Bill>>(`${BASE}/${billId}/payments`, cmd).then(r => r.data.data!),
  generateBill: (billId: string, cmd: GenerateBillCmd) =>
    api.post<ApiResponse<Bill>>(`${BASE}/${billId}/generate`, cmd).then(r => r.data.data!),
  applyDiscount: (billId: string, cmd: ApplyDiscountCmd) =>
    api.post<ApiResponse<Bill>>(`${BASE}/${billId}/discounts`, cmd).then(r => r.data.data!),
  cancelDiscount: (billId: string) =>
    api.delete<ApiResponse<Bill>>(`${BASE}/${billId}/discounts`).then(r => r.data.data!),
  addCharge: (billId: string, cmd: AddChargeCmd) =>
    api.post<ApiResponse<Bill>>(`${BASE}/${billId}/charges`, cmd).then(r => r.data.data!),
  removeCharge: (billId: string, lineItemId: string, reason?: string) =>
    api.delete<ApiResponse<Bill>>(`${BASE}/${billId}/charges/${lineItemId}`, { params: { reason } }).then(r => r.data.data!),
  updateCharge: (billId: string, cmd: UpdateChargeCmd) =>
    api.put<ApiResponse<Bill>>(`${BASE}/update-charge`, cmd, { params: { billId } }).then(r => r.data.data!),
  refund: (billId: string, cmd: RefundCmd) =>
    api.post<ApiResponse<Bill>>(`${BASE}/${billId}/refunds`, cmd).then(r => r.data.data!),
  getAll: () =>
    api.get<ApiResponse<BillSummary[]>>(`${BASE}`).then(r => r.data.data ?? []),
  searchBills: (q?: string, from?: string, to?: string, page = 0, size = 5) =>
    api.get<ApiResponse<PageResponse<BillSummary>>>(`${BASE}/history/search`, { 
      params: { q, from, to, page, size } 
    }).then(r => r.data.data ?? { content: [], totalElements: 0, totalPages: 0, number: 0, size: 5 }),
}

// ─── Visit-based endpoints ────────────────────────────────────────────────────
export const billingVisitApi = {
  /** GET /bills/by-visit?visit= — get the (draft) bill for an encounter */
  getBillByVisit: (encounterId: string) =>
    api.get<ApiResponse<Bill>>(`${BASE}/by-visit`, { params: { visit: encounterId } })
      .then(r => r.data.data!),

  /** PUT /bills/add-charge-by-visit?visitId= — add charge to IP draft bill */
  addChargeByVisit: (encounterId: string, cmd: AddChargeCmd) =>
    api.put<ApiResponse<Bill>>(`${BASE}/add-charge-by-visit`, cmd, { params: { visitId: encounterId } })
      .then(r => r.data.data!),
}

// ─── Unbilled diagnostics (OP) ────────────────────────────────────────────────
export const unbilledDiagnosticsApi = {
  /** GET /diagnostics/getUnbilledDiagnosticOrders?patientId= */
  getForPatient: (patientId: string) =>
    api.get<ApiResponse<any[]>>('/diagnostics/getUnbilledDiagnosticOrders', { params: { patientId } })
      .then(r => r.data.data ?? []),
}
