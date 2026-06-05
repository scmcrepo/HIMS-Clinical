export type AppointmentStatus = 'BOOKED' | 'RESCHEDULED' | 'CHECKED_IN' | 'CANCELLED'
export type VisitMode = 'WALK_IN' | 'APPOINTMENT' | 'TELE_CONSULT'
export const DAY_NAMES = ['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday']

export interface AppointmentSlot {
  slotId: string
  fromTime: string
  toTime: string
  maxPatients: number
  bookedCount: number
  availableCount: number
  isAvailable: boolean
}

export interface Appointment {
  id: string
  patientId: string | null
  patientNumber: string | null
  patientName: string | null
  providerId: string
  providerName: string | null
  slotId: string | null
  status: AppointmentStatus
  appointmentDate: string
  appointmentTime: string
  visitMode: VisitMode
  notes: string | null
  tempPatientName: string | null
  tempPatientSalutation: string | null
  tempPatientGender: string | null
  tempPatientPhone: string | null
  tempPatientAge: number | null
  patientPhone: string | null
  appointmentEndTime: string | null
  bookedCount: number
  maxPatients: number
}
