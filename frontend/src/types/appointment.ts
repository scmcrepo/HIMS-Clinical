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
  patientAge?: string | null
  patientGender?: string | null
}

export type BlockType = 'FULL_DAY' | 'TIME_RANGE'

export interface ConsultantLeave {
  id: string
  consultantId: string
  startDate: string
  endDate: string
  reason: string
  status: string
  /** FULL_DAY takes the whole of each date; TIME_RANGE only startTime..endTime. */
  blockType: BlockType
  /** "HH:mm[:ss]" — present only on a TIME_RANGE block. */
  startTime: string | null
  /** "HH:mm[:ss]" — exclusive; present only on a TIME_RANGE block. */
  endTime: string | null
}

/** One window to block, as the time-block modal collects it. */
export interface TimeRangeInput {
  startTime: string
  endTime: string
}

export interface DateStatus {
  date: string
  status: 'AVAILABLE' | 'HAS_APPOINTMENTS' | 'FULLY_BOOKED' | 'LEAVE' | 'PARTIAL' | 'UNAVAILABLE'
  bookedCount: number
  maxCapacity: number
}

export interface DoctorCalendar {
  appointments: Appointment[]
  leaves: ConsultantLeave[]
  dateStatuses: DateStatus[]
}

export interface AvailabilityCheck {
  slots: AppointmentSlot[]
  reason: 'ON_LEAVE' | 'NO_SLOTS' | 'TIME_BLOCKED' | null
  dayOfWeek: string // "MONDAY", "TUESDAY", etc.
}

export interface DayBoardSession {
  slotId: string
  fromTime: string
  toTime: string
  maxPatients: number
  bookedCount: number
  availableCount: number
}

export interface DayBoardDoctor {
  consultantId: string
  name: string
  speciality: string | null
  onLeave: boolean
  leaveReason: string | null
  sessions: DayBoardSession[]
}

export interface DayBoard {
  date: string
  dayOfWeek: string
  doctors: DayBoardDoctor[]
}
