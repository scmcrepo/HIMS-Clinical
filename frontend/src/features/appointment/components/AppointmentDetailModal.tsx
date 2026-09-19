import { format, parseISO } from 'date-fns'
import { Modal } from '../../../components/ui/Modal'
import { cn } from '../../../lib/utils'
import type { Appointment } from '../../../types/appointment'
import { EVENT_STATUS_STYLES } from './AppointmentEventContent'

interface AppointmentDetailModalProps {
  appointment: Appointment
  consultantLabel?: (providerId: string, fallback: string | null) => string
  /** Check-in is only offered on the day itself, matching the table's rule. */
  isToday?: boolean
  isBusy?: boolean
  onReschedule?: () => void
  onCheckIn?: () => void
  onCancel?: () => void
  onClose: () => void
}

const formatTime = (timeStr?: string | null) => {
  if (!timeStr) return '—'
  const [hours, minutes] = timeStr.split(':')
  const date = new Date()
  date.setHours(Number(hours), Number(minutes), 0)
  return format(date, 'hh:mm a')
}

export const formatAgeGender = (age?: string | number | null, gender?: string | null): string | null => {
  let ageStr = ''
  if (age !== undefined && age !== null && age !== '') {
    const raw = String(age).trim()
    ageStr = raw.replace(/\s*yrs$/i, '').replace(/\s*years?$/i, '').trim()
  }

  let genderStr = ''
  if (gender) {
    const g = gender.toUpperCase()
    if (g.startsWith('M')) genderStr = 'M'
    else if (g.startsWith('F')) genderStr = 'F'
    else if (g.startsWith('O')) genderStr = 'Other'
    else genderStr = gender
  }

  if (ageStr && genderStr) return `${ageStr}/${genderStr}`
  if (ageStr) return ageStr
  if (genderStr) return genderStr
  return null
}

const Row = ({ label, value }: { label: string; value: string }) => (
  <div className="flex items-start justify-between gap-4 py-2 border-b border-gray-100 last:border-0">
    <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider shrink-0">{label}</span>
    <span className="text-sm text-gray-900 font-medium text-right">{value}</span>
  </div>
)

export function AppointmentDetailModal({
  appointment,
  consultantLabel,
  isToday,
  isBusy,
  onReschedule,
  onCheckIn,
  onCancel,
  onClose,
}: AppointmentDetailModalProps) {
  const style = EVENT_STATUS_STYLES[appointment.status]
  const patientName = appointment.patientName || appointment.tempPatientName || 'Walk-in'
  const age = appointment.patientAge || (appointment.tempPatientAge ? `${appointment.tempPatientAge} yrs` : null)
  const gender = appointment.patientGender || appointment.tempPatientGender
  const ageGender = formatAgeGender(age, gender)
  const todayStr = format(new Date(), 'yyyy-MM-dd')
  const isSameDay = isToday !== undefined ? isToday : (appointment.appointmentDate === todayStr)

  return (
    <Modal
      isOpen={Boolean(appointment)}
      onClose={onClose}
      title="Appointment Details"
      description={`Details for ${patientName}`}
      size="md"
      showCloseButton={true}
    >
      <div className="flex flex-col overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100 flex items-start justify-between gap-4 pr-12 bg-neutral-50/50">
          <div>
            <h3 className="text-base font-bold text-gray-900">{patientName}</h3>
            {appointment.patientNumber && appointment.patientNumber !== 'N/A' && (
              <p className="text-[11px] font-mono text-gray-400 mt-0.5">{appointment.patientNumber}</p>
            )}
          </div>
          <span className={cn('inline-flex items-center px-2.5 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-wider border', style.chip)}>
            {style.label}
          </span>
        </div>

        <div className="px-6 py-4 space-y-1">
          {ageGender && <Row label="Age / Gender" value={ageGender} />}
          <Row label="Date" value={appointment.appointmentDate ? format(parseISO(appointment.appointmentDate), 'EEEE, dd MMM yyyy') : '—'} />
          <Row label="Slot" value={`${formatTime(appointment.appointmentTime)} – ${formatTime(appointment.appointmentEndTime)}`} />
          <Row label="Consultant" value={consultantLabel ? consultantLabel(appointment.providerId, appointment.providerName) : (appointment.providerName || '—')} />
          <Row label="Contact" value={appointment.patientPhone || appointment.tempPatientPhone || '—'} />
          {appointment.notes && <Row label="Notes" value={appointment.notes} />}
        </div>

        <div className="px-6 py-3.5 bg-gray-50 border-t border-gray-100 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-gray-600 hover:text-gray-900 rounded-lg hover:bg-gray-200/60 transition-colors cursor-pointer"
          >
            Close
          </button>
          {appointment.status === 'BOOKED' && (
            <>
              {onReschedule && (
                <button
                  type="button"
                  onClick={onReschedule}
                  className="px-3.5 py-2 rounded-lg text-xs font-semibold text-neutral-700 border border-neutral-200 hover:bg-white transition-colors cursor-pointer"
                >
                  Reschedule
                </button>
              )}
              {isSameDay && onCheckIn && (
                <button
                  type="button"
                  onClick={onCheckIn}
                  disabled={isBusy}
                  className="px-3.5 py-2 rounded-lg text-xs font-semibold text-white bg-green-600 hover:bg-green-700 disabled:opacity-40 transition-colors cursor-pointer shadow-xs"
                >
                  Check In
                </button>
              )}
              {onCancel && (
                <button
                  type="button"
                  onClick={onCancel}
                  disabled={isBusy}
                  className="px-3.5 py-2 rounded-lg text-xs font-semibold text-white bg-red-500 hover:bg-red-600 disabled:opacity-40 transition-colors cursor-pointer shadow-xs"
                >
                  Cancel
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </Modal>
  )
}

export default AppointmentDetailModal
