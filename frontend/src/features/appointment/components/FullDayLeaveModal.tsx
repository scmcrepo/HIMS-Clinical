import { useMemo, useState, useEffect } from 'react'
import { parseISO, isValid, eachDayOfInterval, format } from 'date-fns'
import { AlertTriangle, CalendarRange } from 'lucide-react'
import { Modal } from '../../../components/ui/Modal'
import { ConfirmModal } from '../../../components/ui/ConfirmModal'
import type { Appointment } from '../../../types/appointment'

interface FullDayLeaveModalProps {
  defaultDate: string
  defaultEndDate?: string
  todayStr: string
  appointments: Appointment[]
  isSubmitting: boolean
  onSubmit: (payload: { startDate: string; endDate: string; reason: string }) => void
  onClose: () => void
}

export function FullDayLeaveModal({
  defaultDate,
  defaultEndDate,
  todayStr,
  appointments,
  isSubmitting,
  onSubmit,
  onClose,
}: FullDayLeaveModalProps) {
  const initialStart = defaultDate < todayStr ? todayStr : defaultDate
  const initialEnd = defaultEndDate ? (defaultEndDate < initialStart ? initialStart : defaultEndDate) : initialStart
  const [startDate, setStartDate] = useState(initialStart)
  const [endDate, setEndDate] = useState(initialEnd)
  const [reason, setReason] = useState('')
  const [showDisplacedWarning, setShowDisplacedWarning] = useState(false)

  useEffect(() => {
    const s = defaultDate < todayStr ? todayStr : defaultDate
    setStartDate(s)
    const e = defaultEndDate ? (defaultEndDate < s ? s : defaultEndDate) : s
    setEndDate(e)
  }, [defaultDate, defaultEndDate, todayStr])

  const dayCount = useMemo(() => {
    if (!startDate || !endDate || startDate > endDate) return 0
    try {
      const days = eachDayOfInterval({ start: parseISO(startDate), end: parseISO(endDate) })
      return days.length
    } catch {
      return 0
    }
  }, [startDate, endDate])

  const affectedCount = useMemo(() => {
    if (startDate > endDate) return 0
    const start = parseISO(startDate)
    const end = parseISO(endDate)
    return appointments.filter(appt => {
      if (appt.status !== 'BOOKED' && appt.status !== 'CHECKED_IN') return false
      const d = parseISO(appt.appointmentDate)
      return d >= start && d <= end
    }).length
  }, [appointments, startDate, endDate])

  const datesValid =
    Boolean(startDate) &&
    Boolean(endDate) &&
    startDate >= todayStr &&
    endDate >= startDate &&
    isValid(parseISO(startDate)) &&
    isValid(parseISO(endDate))

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!datesValid || isSubmitting) return

    if (affectedCount > 0) {
      setShowDisplacedWarning(true)
      return
    }

    onSubmit({ startDate, endDate, reason })
  }

  const handleConfirmDisplaced = () => {
    setShowDisplacedWarning(false)
    onSubmit({ startDate, endDate, reason })
  }

  return (
    <>
      <Modal
        isOpen={true}
        onClose={onClose}
        title="Block Full Day(s)"
        description="Mark one or multiple dates as unavailable"
        size="md"
        showCloseButton={true}
      >
        <form onSubmit={handleSubmit} className="flex flex-col overflow-hidden">
          <div className="px-6 py-4 border-b border-neutral-100 flex items-center justify-between pr-12 bg-neutral-50/50">
            <div className="flex items-center gap-2.5">
              <div className="h-9 w-9 rounded-xl bg-red-50 text-red-600 flex items-center justify-center border border-red-100">
                <CalendarRange className="h-5 w-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-neutral-900">Block Full Day(s)</h3>
                <p className="text-xs text-neutral-500">
                  {dayCount > 1
                    ? `Selected ${dayCount} consecutive days to block`
                    : 'Mark one or multiple dates as unavailable'}
                </p>
              </div>
            </div>
          </div>

          <div className="p-6 space-y-4">
            {dayCount > 1 && (
              <div className="flex items-center gap-2.5 p-3 bg-red-50 border border-red-200/80 rounded-xl text-xs text-red-900">
                <CalendarRange className="h-4 w-4 text-red-600 shrink-0" />
                <div>
                  <span className="font-bold">Blocking {dayCount} entire days:</span>{' '}
                  <span className="font-semibold text-red-800">
                    {format(parseISO(startDate), 'dd MMM yyyy')} – {format(parseISO(endDate), 'dd MMM yyyy')}
                  </span>
                </div>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-[11px] font-semibold text-neutral-600 uppercase tracking-wider block mb-1">
                  Start Date
                </label>
                <input
                  type="date"
                  min={todayStr}
                  value={startDate}
                  onChange={e => {
                    setStartDate(e.target.value)
                    if (e.target.value > endDate) setEndDate(e.target.value)
                  }}
                  className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-xs bg-white focus:border-neutral-900 focus:outline-none"
                  required
                />
              </div>
              <div>
                <label className="text-[11px] font-semibold text-neutral-600 uppercase tracking-wider block mb-1">
                  End Date
                </label>
                <input
                  type="date"
                  min={startDate || todayStr}
                  value={endDate}
                  onChange={e => setEndDate(e.target.value)}
                  className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-xs bg-white focus:border-neutral-900 focus:outline-none"
                  required
                />
              </div>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-neutral-600 uppercase tracking-wider block mb-1">
                Reason for Leave (Optional)
              </label>
              <input
                type="text"
                placeholder="e.g. Annual Vacation, Conference, Medical Leave"
                value={reason}
                onChange={e => setReason(e.target.value)}
                className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-xs bg-white focus:border-neutral-900 focus:outline-none"
              />
            </div>

            {affectedCount > 0 && (
              <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl flex items-start gap-2.5 text-amber-800">
                <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-amber-600" />
                <div className="text-xs">
                  <p className="font-semibold">
                    {affectedCount} active appointment(s) in this range will be cancelled.
                  </p>
                  <p className="text-[11px] text-amber-700 mt-0.5">
                    Affected patients will have their appointments marked as CANCELLED with this reason.
                  </p>
                </div>
              </div>
            )}
          </div>

          <div className="px-6 py-3.5 bg-neutral-50 border-t border-neutral-100 flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg border border-neutral-200 text-xs font-semibold text-neutral-700 hover:bg-white transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!datesValid || isSubmitting}
              className="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-xs font-semibold text-white transition-colors disabled:opacity-50 cursor-pointer shadow-xs"
            >
              {isSubmitting
                ? 'Marking...'
                : dayCount > 1
                ? `Block All ${dayCount} Days`
                : 'Mark as Unavailable'}
            </button>
          </div>
        </form>
      </Modal>

      {/* Warning Confirmation if booked appointments are affected */}
      <ConfirmModal
        isOpen={showDisplacedWarning}
        onClose={() => setShowDisplacedWarning(false)}
        onConfirm={handleConfirmDisplaced}
        title="Active Appointments Conflict"
        message={`There are ${affectedCount} active booked appointment(s) within this date range. Marking these dates as unavailable will automatically CANCEL these appointments. Do you want to proceed?`}
        confirmText="Cancel Appointments & Block"
        cancelText="Go Back"
        variant="danger"
        isLoading={isSubmitting}
      />
    </>
  )
}
