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
  
  upsertSlots: (consultantId: string, daysList: SlotUpsertItem[]) => 
    api.post<ApiResponse<AppointmentSlot[]>>(BASE, { consultantId, daysList }).then(r => r.data.data),
  
  updateSlots: (consultantId: string, daysList: SlotUpsertItem[]) => 
    api.put<ApiResponse<void>>(BASE, { consultantId, daysList }).then(r => r.data.data),

  deleteSlotGroup: (consultantId: string, fromTime: string, toTime: string) =>
    api.delete<ApiResponse<boolean>>(BASE, { params: { consultantId, fromTime, toTime } }).then(r => r.data.data),
}
