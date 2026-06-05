import api from '../../lib/axios'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { Appointment, AppointmentSlot } from '../../types/appointment'

export interface BookAppointmentCmd {
  patientId?: string | undefined
  providerId: string
  slotId: string
  appointmentDate: string
  notes?: string | undefined
  tempPatientName?: string | undefined
  tempPatientSalutation?: string | undefined
  tempPatientGender?: string | undefined
  tempPatientPhone?: string | undefined
  tempPatientAge?: number | undefined
}
export interface RescheduleCmd { newDate: string; newTime: string; newSlotId?: string | undefined }

const BASE = '/appointments'
export const appointmentApi = {
  book: (cmd: BookAppointmentCmd) =>
    api.post<ApiResponse<Appointment>>(BASE, cmd).then(r => r.data.data!),

  getById: (id: string) =>
    api.get<ApiResponse<Appointment>>(`${BASE}/${id}`).then(r => r.data.data!),

  reschedule: (id: string, cmd: RescheduleCmd) =>
    api.put<ApiResponse<Appointment>>(`${BASE}/${id}/reschedule`, cmd).then(r => r.data.data!),

  checkIn: (id: string) =>
    api.post<ApiResponse<Appointment>>(`${BASE}/${id}/check-in`).then(r => r.data.data!),

  linkPatient: (id: string, patientId: string) =>
    api.put<ApiResponse<Appointment>>(`${BASE}/${id}/patient/${patientId}`).then(r => r.data.data!),

  cancel: (id: string) =>
    api.delete<ApiResponse<Appointment>>(`${BASE}/${id}`).then(r => r.data.data!),

  getByProvider: (providerId: string, date: string) =>
    api.get<ApiResponse<Appointment[]>>(`${BASE}/provider/${providerId}`, { params: { date } })
      .then(r => r.data.data ?? []),
  
  getByDate: (date: string) =>
    api.get<ApiResponse<Appointment[]>>(`${BASE}/by-date`, { params: { date } })
      .then(r => r.data.data ?? []),

  getByPatient: (patientId: string, page = 0) =>
    api.get<ApiResponse<PageResponse<Appointment>>>(`${BASE}/patient/${patientId}`, { params: { page } })
      .then(r => r.data.data!),

  getAvailability: (providerId: string, date: string) =>
    api.get<ApiResponse<AppointmentSlot[]>>(
      `${BASE}/provider/${providerId}/availability`, { params: { date } })
      .then(r => r.data.data ?? []),

  createSlot: (cmd: {
    providerId: string; dayOfWeek: number
    fromTime: string; toTime: string; maxPatients: number
  }) => api.post(`${BASE}/slots`, cmd),
}
