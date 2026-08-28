import { useState, useMemo } from 'react'
import { format, parseISO, addDays } from 'date-fns'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAvailabilityCheck, useAppointmentMutations, useConsultantLeavesById } from '../../../hooks/appointment/useAppointment'
import DatePicker from '../../../components/shared/DatePicker'
import BackButton from '../../../components/shared/BackButton'
import { CalendarOff, Clock } from 'lucide-react'
import type { Appointment } from '../../../types/appointment'
import { formatDate } from '../../../lib/dateUtils'

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

export default function RescheduleAppointmentPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const appointment = location.state?.appointment as Appointment | undefined

  const [date, setDate] = useState<Date>(new Date())
  const [selectedSlotId, setSelectedSlotId] = useState<string>('')

  const dateStr = format(date, 'yyyy-MM-dd')
  const { data: availCheck } = useAvailabilityCheck(appointment?.providerId, dateStr)
  const { data: leavesData } = useConsultantLeavesById(appointment?.providerId)
  const slots = availCheck?.slots
  const mutations = useAppointmentMutations()

  // Build a set of all leave date strings (YYYY-MM-DD) for this doctor
  const leaveDatesSet = useMemo(() => {
    const s = new Set<string>()
    if (!leavesData) return s
    leavesData.forEach(l => {
      let cur = parseISO(l.startDate as unknown as string)
      const end = parseISO(l.endDate as unknown as string)
      while (cur <= end) {
        s.add(format(cur, 'yyyy-MM-dd'))
        cur = addDays(cur, 1)
      }
    })
    return s
  }, [leavesData])

  const isDoctorOnLeave = availCheck?.reason === 'ON_LEAVE' || leaveDatesSet.has(dateStr)

  const isSameDateAndSlot =
    appointment
      ? appointment.slotId === selectedSlotId &&
        format(parseISO(appointment.appointmentDate), 'yyyy-MM-dd') === dateStr
      : false

  if (!appointment) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-6 text-center text-gray-500">
        No appointment selected. <button onClick={() => navigate('/appointments')} className="text-neutral-600 underline">Go back</button>
      </div>
    )
  }

  return (
    <div className="max-w-2xl mx-auto px-4 py-6 space-y-6">
      <div className="flex justify-between items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Reschedule Appointment</h2>
          <p className="text-sm text-gray-500">Choose a new date and time slot</p>
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-6">
        <div className="p-4 bg-neutral-50 rounded-2xl border border-neutral-100">
          <p className="text-xs font-bold text-neutral-600 uppercase tracking-widest mb-1">Patient</p>
          <p className="text-sm font-bold text-neutral-900">{appointment.patientName}</p>
          <p className="text-xs text-neutral-700 mt-1">Current: {formatDate(appointment.appointmentDate)} at {appointment.appointmentTime}</p>
        </div>

        <div className="space-y-4">
          {/* Leave / No-Slots Minimal Notice */}
          {isDoctorOnLeave && (
            <div className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-3 text-xs">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-neutral-100 text-neutral-700">
                <CalendarOff className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-neutral-900">
                  Doctor is unavailable on {format(date, 'dd MMM yyyy')} ({availCheck?.dayOfWeek || format(date, 'EEEE')})
                </p>
                <p className="text-neutral-500 text-[11px] mt-0.5">
                  The doctor is marked on leave for this date. Please select another date.
                </p>
              </div>
            </div>
          )}
          {!isDoctorOnLeave && availCheck?.reason === 'NO_SLOTS' && (
            <div className="flex items-center gap-3 rounded-xl border border-neutral-200 bg-neutral-50 px-4 py-3 text-xs">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-neutral-100 text-neutral-700">
                <Clock className="h-4 w-4" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-neutral-900">
                  No slots configured for {availCheck.dayOfWeek}
                </p>
                <p className="text-neutral-500 text-[11px] mt-0.5">
                  The doctor has no working hours scheduled on this day of the week.
                </p>
              </div>
            </div>
          )}
          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">New Date</label>
            <DatePicker
              value={dateStr}
              minDate={format(new Date(), 'yyyy-MM-dd')}
              onChange={val => setDate(val ? new Date(val + 'T00:00:00') : new Date())}
              getDayProps={d => {
                const formatted = format(d, 'yyyy-MM-dd')
                const isLeave = leaveDatesSet.has(formatted)
                return {
                  disabled: isLeave,
                  className: isLeave ? 'bg-red-50 text-red-400 font-semibold line-through cursor-not-allowed' : undefined
                }
              }}
              size="sm"
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">New Time Slot</label>
            <select
              value={selectedSlotId}
              onChange={e => setSelectedSlotId(e.target.value)}
              disabled={isDoctorOnLeave}
              className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-neutral-500 outline-none disabled:opacity-50 transition-all"
            >
              <option value="">{isDoctorOnLeave ? 'Doctor is on leave on this date' : 'Select New Slot'}</option>
              {!isDoctorOnLeave && slots?.filter(s => {
                if (!s.isAvailable) return false
                const isToday = dateStr === format(new Date(), 'yyyy-MM-dd')
                if (isToday) {
                  const currentTime = format(new Date(), 'HH:mm:ss')
                  return s.toTime > currentTime
                }
                return true
              }).map(s => (
                <option key={s.slotId} value={s.slotId}>
                  {formatTime(s.fromTime)} – {formatTime(s.toTime)}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {isSameDateAndSlot && (
        <div className="flex justify-end">
          <p className="text-xs text-red-500 font-semibold bg-red-50 border border-red-100 rounded-lg px-3 py-1.5 shadow-sm">
            Cannot reschedule to the same date and slot
          </p>
        </div>
      )}

      <div className="flex justify-end gap-3">
        <button
          onClick={() => navigate('/appointments')}
          className="px-4 py-2 text-sm font-bold text-gray-500 hover:text-gray-700 transition-colors border border-gray-200 rounded-xl"
        >
          Cancel
        </button>
        <button
          disabled={isDoctorOnLeave || !selectedSlotId || isSameDateAndSlot || mutations.reschedule.isPending}
          onClick={() => {
            const slot = slots?.find(s => s.slotId === selectedSlotId)
            if (!slot) return
            mutations.reschedule.mutate({
              id: appointment.id,
              cmd: {
                newDate: dateStr,
                newTime: slot.fromTime,
                newSlotId: selectedSlotId
              }
            }, {
              onSuccess: () => navigate('/appointments')
            })
          }}
          className="px-6 py-2 bg-neutral-600 text-white font-bold rounded-xl hover:bg-neutral-700 shadow-md disabled:opacity-50 transition-all"
        >
          {mutations.reschedule.isPending ? 'Updating...' : 'Update Appointment'}
        </button>
      </div>
    </div>
  )
}
