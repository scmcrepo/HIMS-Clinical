import { useState } from 'react'
import { format, parseISO } from 'date-fns'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAvailabilityCheck, useAppointmentMutations } from '../../../hooks/appointment/useAppointment'
import DatePicker from '../../../components/shared/DatePicker'
import BackButton from '../../../components/shared/BackButton'
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
  const slots = availCheck?.slots
  const mutations = useAppointmentMutations()

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
          {/* Leave / No-Slots Warning */}
          {availCheck?.reason === 'ON_LEAVE' && (
            <div className="flex items-center gap-3 bg-red-50 border border-red-200 text-red-800 rounded-2xl p-4">
              <span className="text-lg">🚫</span>
              <div>
                <p className="text-sm font-bold">Doctor is on leave on {format(date, 'dd MMM yyyy')} ({availCheck.dayOfWeek})</p>
                <p className="text-xs text-red-600">Please select a different date.</p>
              </div>
            </div>
          )}
          {availCheck?.reason === 'NO_SLOTS' && (
            <div className="flex items-center gap-3 bg-amber-50 border border-amber-200 text-amber-800 rounded-2xl p-4">
              <span className="text-lg">⚠️</span>
              <div>
                <p className="text-sm font-bold">No availability configured for {availCheck.dayOfWeek}</p>
                <p className="text-xs text-amber-600">The doctor has no working hours set for this day of the week.</p>
              </div>
            </div>
          )}
          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">New Date</label>
            <DatePicker
              value={dateStr}
              minDate={format(new Date(), 'yyyy-MM-dd')}
              onChange={val => setDate(val ? new Date(val + 'T00:00:00') : new Date())}
              size="sm"
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">New Time Slot</label>
            <select
              value={selectedSlotId}
              onChange={e => setSelectedSlotId(e.target.value)}
              className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:ring-2 focus:ring-neutral-500 outline-none transition-all"
            >
              <option value="">Select New Slot</option>
              {slots?.filter(s => {
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
          disabled={!selectedSlotId || isSameDateAndSlot || mutations.reschedule.isPending}
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
