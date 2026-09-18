import { format, parseISO } from 'date-fns'
import { CalendarDays, CalendarRange, Clock, Trash2, User, Phone } from 'lucide-react'
import { Modal } from '../../../components/ui/Modal'
import { cn } from '../../../lib/utils'
import type { Appointment, ConsultantLeave } from '../../../types/appointment'
import { formatAgeGender } from './AppointmentDetailModal'

interface DateScheduleModalProps {
  dateStr: string
  appointments: Appointment[]
  leaves: ConsultantLeave[]
  onBlockFullDay: () => void
  onBlockHours: () => void
  onDeleteLeave: (leaveId: string) => void
  onViewAppointment: (appointment: Appointment) => void
  onClose: () => void
}

const formatTime = (timeStr?: string | null) => {
  if (!timeStr) return '—'
  try {
    const [hours, minutes] = timeStr.split(':')
    const date = new Date()
    date.setHours(parseInt(hours), parseInt(minutes), 0)
    return format(date, 'hh:mm a')
  } catch {
    return timeStr
  }
}

export function DateScheduleModal({
  dateStr,
  appointments,
  leaves,
  onBlockFullDay,
  onBlockHours,
  onDeleteLeave,
  onViewAppointment,
  onClose,
}: DateScheduleModalProps) {
  const formattedDate = (() => {
    try {
      return format(parseISO(dateStr), 'EEEE, dd MMMM yyyy')
    } catch {
      return dateStr
    }
  })()

  // Leaves active on this date
  const activeLeaves = leaves.filter(l => !dateStr.localeCompare(l.startDate) || (dateStr >= l.startDate && dateStr <= l.endDate))

  return (
    <Modal
      isOpen={Boolean(dateStr)}
      onClose={onClose}
      title={formattedDate}
      description="Availability & Appointments Schedule"
      size="2xl"
      showCloseButton={true}
    >
      <div className="flex flex-col max-h-[85vh] overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-neutral-100 flex items-center justify-between bg-neutral-50/70 pr-12">
          <div className="flex items-center gap-2.5">
            <div className="h-9 w-9 rounded-xl bg-neutral-900 text-white flex items-center justify-center shadow-xs">
              <CalendarDays className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-neutral-900">{formattedDate}</h3>
              <p className="text-xs text-neutral-500 font-medium">Availability & Appointments Schedule</p>
            </div>
          </div>
        </div>

        {/* Action Toolbar to Block Dates */}
        <div className="px-6 py-3 bg-white border-b border-neutral-100 flex items-center justify-between gap-3 flex-wrap">
          <span className="text-xs font-semibold text-neutral-500">Quick Availability Actions:</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onBlockFullDay}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-red-600 hover:bg-red-700 text-white text-xs font-bold rounded-lg transition-all shadow-xs cursor-pointer"
            >
              <CalendarRange className="h-3.5 w-3.5" />
              Block Full Day
            </button>
            <button
              type="button"
              onClick={onBlockHours}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-neutral-800 hover:bg-neutral-900 text-white text-xs font-bold rounded-lg transition-all shadow-xs cursor-pointer"
            >
              <Clock className="h-3.5 w-3.5" />
              Block Specific Hours
            </button>
          </div>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-5 overflow-y-auto flex-1">
          {/* Section 1: Active Leaves / Blocks on this date */}
          {activeLeaves.length > 0 && (
            <div className="space-y-2.5">
              <h4 className="text-xs font-bold text-neutral-700 uppercase tracking-wider flex items-center gap-1.5">
                <CalendarRange className="h-3.5 w-3.5 text-neutral-500" />
                Active Leaves & Blocked Windows on this Date
              </h4>
              <div className="space-y-2">
                {activeLeaves.map(leave => {
                  const isTimeRange = leave.blockType === 'TIME_RANGE' && leave.startTime && leave.endTime
                  return (
                    <div
                      key={leave.id}
                      className={cn(
                        'p-3 rounded-xl border flex items-center justify-between transition-colors',
                        isTimeRange ? 'bg-orange-50/70 border-orange-200' : 'bg-red-50/70 border-red-200'
                      )}
                    >
                      <div className="space-y-0.5">
                        <div className="flex items-center gap-2">
                          <span
                            className={cn(
                              'text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded',
                              isTimeRange ? 'bg-orange-200 text-orange-900' : 'bg-red-200 text-red-900'
                            )}
                          >
                            {isTimeRange
                              ? `${formatTime(leave.startTime)} – ${formatTime(leave.endTime)}`
                              : 'Full Day Leave'}
                          </span>
                          {leave.startDate !== leave.endDate && (
                            <span className="text-[11px] text-neutral-500">
                              ({format(parseISO(leave.startDate), 'dd MMM')} – {format(parseISO(leave.endDate), 'dd MMM yyyy')})
                            </span>
                          )}
                        </div>
                        {leave.reason && (
                          <p className="text-xs text-neutral-700 italic mt-0.5 font-medium">{leave.reason}</p>
                        )}
                      </div>
                      <button
                        type="button"
                        onClick={() => onDeleteLeave(leave.id)}
                        className="p-2 text-red-600 hover:bg-white rounded-lg border border-transparent hover:border-red-200 transition-all cursor-pointer flex items-center gap-1 text-xs font-semibold"
                        title="Unblock / Remove leave"
                      >
                        <Trash2 className="h-4 w-4" />
                        <span>Unblock</span>
                      </button>
                    </div>
                  )
                })}
              </div>
            </div>
          )}

          {/* Section 2: Appointments on this date */}
          <div className="space-y-2.5">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-bold text-neutral-700 uppercase tracking-wider flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5 text-neutral-500" />
                Appointments on this Date
              </h4>
              <span className="text-xs font-semibold text-neutral-500 bg-neutral-100 px-2 py-0.5 rounded-full">
                {appointments.length} {appointments.length === 1 ? 'appointment' : 'appointments'}
              </span>
            </div>

            {appointments.length === 0 ? (
              <div className="p-8 text-center text-neutral-400 bg-neutral-50/60 rounded-xl border border-neutral-100 flex flex-col items-center gap-2">
                <CalendarDays className="h-8 w-8 text-neutral-300" />
                <p className="text-xs font-medium">No appointments booked on this date.</p>
              </div>
            ) : (
              <div className="space-y-2">
                {appointments.map(appt => (
                  <div
                    key={appt.id}
                    onClick={() => onViewAppointment(appt)}
                    className="p-3 bg-white rounded-xl border border-neutral-200 hover:border-neutral-400 hover:shadow-xs transition-all flex items-center justify-between gap-3 cursor-pointer group"
                  >
                    <div className="space-y-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-mono font-bold text-neutral-800 bg-neutral-100 px-2 py-0.5 rounded">
                          {formatTime(appt.appointmentTime)} – {formatTime(appt.appointmentEndTime)}
                        </span>
                        <span
                          className={cn(
                            'text-[9px] font-bold uppercase px-1.5 py-0.5 rounded border',
                            appt.status === 'BOOKED' && 'bg-blue-50 text-blue-700 border-blue-200',
                            appt.status === 'CHECKED_IN' && 'bg-emerald-50 text-emerald-700 border-emerald-200',
                            appt.status === 'RESCHEDULED' && 'bg-amber-50 text-amber-700 border-amber-200',
                            appt.status === 'CANCELLED' && 'bg-neutral-100 text-neutral-500 border-neutral-200'
                          )}
                        >
                          {appt.status}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 text-sm font-semibold text-neutral-900 flex-wrap">
                        <User className="h-3.5 w-3.5 text-neutral-400 shrink-0" />
                        <span className="truncate">{appt.patientName || appt.tempPatientName || 'Walk-in Patient'}</span>
                        {appt.patientNumber && appt.patientNumber !== 'N/A' && (
                          <span className="text-[10px] font-mono text-neutral-400 font-normal">({appt.patientNumber})</span>
                        )}
                        {(() => {
                          const apptAge = appt.patientAge || (appt.tempPatientAge ? `${appt.tempPatientAge} yrs` : null)
                          const apptGender = appt.patientGender || appt.tempPatientGender
                          const ageGender = formatAgeGender(apptAge, apptGender)
                          return ageGender ? (
                            <span className="text-[10px] font-semibold text-neutral-700 bg-neutral-100 px-1.5 py-0.5 rounded">
                              {ageGender}
                            </span>
                          ) : null
                        })()}
                      </div>
                      {appt.patientPhone && (
                        <p className="text-[11px] text-neutral-500 flex items-center gap-1 ml-5">
                          <Phone className="h-3 w-3 text-neutral-400" />
                          {appt.patientPhone}
                        </p>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={e => {
                        e.stopPropagation()
                        onViewAppointment(appt)
                      }}
                      className="px-3 py-1.5 text-xs font-semibold text-neutral-700 bg-neutral-100 group-hover:bg-neutral-900 group-hover:text-white rounded-lg transition-colors shrink-0"
                    >
                      View Details
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-3 bg-neutral-50 border-t border-neutral-100 flex items-center justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg border border-neutral-200 text-xs font-semibold text-neutral-700 hover:bg-white transition-colors cursor-pointer"
          >
            Close
          </button>
        </div>
      </div>
    </Modal>
  )
}

export default DateScheduleModal
