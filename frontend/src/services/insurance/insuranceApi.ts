import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

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
}
