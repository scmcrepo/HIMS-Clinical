import api from '../../lib/axios'
import type { ApiResponse } from '../../types/api'

export type DayOfWeek = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN'

export interface AppointmentSlot {
  id: string
  consultantId: string
  dayOfWeek: DayOfWeek
  fromTime: string
  toTime: string
  maxPatients: number
  status: number
  specificDate?: string | null
  effectiveFrom?: string | null
  effectiveTo?: string | null
}

export interface SlotUpsertItem {
  dayOfWeek: DayOfWeek
  fromTime: string
  toTime: string
  numberOfPatients: number
}

const BASE = '/appointmentSlot'

export const slotApi = {
  getByConsultant: (consultantId: string) => 
    api.get<ApiResponse<AppointmentSlot[]>>(`${BASE}/${consultantId}`).then(r => r.data.data ?? []),
  
  upsertSlots: (consultantId: string, daysList: SlotUpsertItem[], validity?: { effectiveFrom?: string; effectiveTo?: string }) => 
    api.post<ApiResponse<AppointmentSlot[]>>(BASE, { consultantId, daysList, ...validity }).then(r => r.data.data),
  
  updateSlots: (consultantId: string, daysList: SlotUpsertItem[], validity?: { effectiveFrom?: string; effectiveTo?: string }) => 
    api.put<ApiResponse<void>>(BASE, { consultantId, daysList, ...validity }).then(r => r.data.data),

  deleteSlotGroup: (consultantId: string, fromTime: string, toTime: string) =>
    api.delete<ApiResponse<boolean>>(BASE, { params: { consultantId, fromTime, toTime } }).then(r => r.data.data),

  getDateSpecificSlots: (consultantId: string) =>
    api.get<ApiResponse<AppointmentSlot[]>>(`${BASE}/date-slots/${consultantId}`).then(r => r.data.data ?? []),

  saveDateSpecificSlots: (consultantId: string, dates: string[], slots: { fromTime: string; toTime: string; numberOfPatients: number }[]) =>
    api.post<ApiResponse<AppointmentSlot[]>>(`${BASE}/date-slots`, { consultantId, dates, slots }).then(r => r.data.data),

  deleteDateSpecificSlot: (slotId: string) =>
    api.delete<ApiResponse<void>>(`${BASE}/date-slots/${slotId}`).then(r => r.data.data),
}
