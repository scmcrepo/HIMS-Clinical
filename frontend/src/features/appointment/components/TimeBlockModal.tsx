import { useMemo, useState } from 'react'
import { format, parseISO, isValid } from 'date-fns'
import { Plus, Trash2, AlertTriangle, Clock } from 'lucide-react'
import { Modal } from '../../../components/ui/Modal'
import { ConfirmModal } from '../../../components/ui/ConfirmModal'
import { cn } from '../../../lib/utils'
import type { Appointment, TimeRangeInput } from '../../../types/appointment'

interface TimeBlockModalProps {
  /** Pre-fills the date pickers with whatever day the calendar is on. */
  defaultDate: string
  todayStr: string
  /** Every appointment the calendar already holds for this consultant. */
  appointments: Appointment[]
  isSubmitting: boolean
  onSubmit: (payload: { startDate: string; endDate: string; reason: string; timeRanges: TimeRangeInput[] }) => void
  onClose: () => void
  initialTimeRange?: TimeRangeInput
}

/** 30-minute granularity, across clinic hours. */
const TIME_OPTIONS: string[] = (() => {
  const options: string[] = []
  for (let minutes = 6 * 60; minutes <= 22 * 60; minutes += 30) {
    const h = Math.floor(minutes / 60)
    const m = minutes % 60
    options.push(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`)
  }
  return options
})()

const label12h = (value: string) => {
  const [h, m] = value.split(':')
  const d = new Date()
  d.setHours(Number(h), Number(m), 0)
  return format(d, 'hh:mm a')
}

const overlaps = (a: TimeRangeInput, b: TimeRangeInput) =>
  a.startTime < b.endTime && b.startTime < a.endTime

export function TimeBlockModal({
  defaultDate,
  todayStr,
  appointments,
  isSubmitting,
  onSubmit,
  onClose,
  initialTimeRange,
}: TimeBlockModalProps) {
  const [startDate, setStartDate] = useState(defaultDate < todayStr ? todayStr : defaultDate)
  const [endDate, setEndDate] = useState(defaultDate < todayStr ? todayStr : defaultDate)
  const [reason, setReason] = useState('')
  const [ranges, setRanges] = useState<TimeRangeInput[]>(
    initialTimeRange ? [initialTimeRange] : [{ startTime: '09:00', endTime: '12:00' }]
  )
  const [showDisplacedWarning, setShowDisplacedWarning] = useState(false)

  const updateRange = (index: number, patch: Partial<TimeRangeInput>) => {
    setRanges(prev => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))
  }

  const rangeErrors = useMemo(() => {
    return ranges.map((range, i) => {
      if (range.startTime >= range.endTime) return 'End must be after start'
      const clash = ranges.findIndex((other, j) => j !== i && overlaps(range, other))
      if (clash !== -1) return 'Overlaps another range'
      return null
    })
  }, [ranges])

  const affectedCount = useMemo(() => {
    if (startDate > endDate) return 0
    const ids = new Set<string>()
    appointments.forEach(appt => {
      if (appt.status !== 'BOOKED') return
      if (appt.appointmentDate < startDate || appt.appointmentDate > endDate) return
      const time = (appt.appointmentTime ?? '').slice(0, 5)
      if (!time) return
      if (ranges.some(r => time >= r.startTime && time < r.endTime)) ids.add(appt.id)
    })
    return ids.size
  }, [appointments, ranges, startDate, endDate])

  const datesValid =
    Boolean(startDate) &&
    Boolean(endDate) &&
    startDate >= todayStr &&
    endDate >= startDate &&
    isValid(parseISO(startDate)) &&
    isValid(parseISO(endDate))
  const canSubmit = datesValid && ranges.length > 0 && rangeErrors.every(e => e === null) && !isSubmitting

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    if (affectedCount > 0) {
      setShowDisplacedWarning(true)
      return
    }
    onSubmit({ startDate, endDate, reason, timeRanges: ranges })
  }

  const handleConfirmDisplaced = () => {
    setShowDisplacedWarning(false)
    onSubmit({ startDate, endDate, reason, timeRanges: ranges })
  }

  return (
    <>
      <Modal
        isOpen={true}
        onClose={onClose}
        title="Block Specific Hours"
        description="Mark specific time windows as unavailable"
        size="lg"
        showCloseButton={true}
      >
        <form onSubmit={handleSubmit} className="flex flex-col max-h-[85vh] overflow-hidden">
          <div className="px-6 py-4 border-b border-neutral-100 flex items-center justify-between pr-12 bg-neutral-50/50">
            <div className="flex items-center gap-2.5">
              <div className="h-9 w-9 rounded-xl bg-neutral-900 text-white flex items-center justify-center shadow-xs">
                <Clock className="h-5 w-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-neutral-900">Block Specific Hours</h3>
                <p className="text-xs text-neutral-500">Mark specific hours as unavailable for booking</p>
              </div>
            </div>
          </div>

          <div className="px-6 py-4 space-y-4 overflow-y-auto">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label htmlFor="block-start-date" className="block text-xs font-medium text-neutral-600 mb-1">
                  Start Date
                </label>
                <input
                  id="block-start-date"
                  type="date"
                  required
                  value={startDate}
                  min={todayStr}
                  onChange={e => {
                    setStartDate(e.target.value)
                    if (endDate < e.target.value) setEndDate(e.target.value)
                  }}
                  className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-xs bg-white focus:border-neutral-900 focus:outline-none"
                />
              </div>
              <div>
                <label htmlFor="block-end-date" className="block text-xs font-medium text-neutral-600 mb-1">
                  End Date
                </label>
                <input
                  id="block-end-date"
                  type="date"
                  required
                  value={endDate}
                  min={startDate || todayStr}
                  onChange={e => setEndDate(e.target.value)}
                  className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-xs bg-white focus:border-neutral-900 focus:outline-none"
                />
              </div>
            </div>

            <p className="text-[11px] text-neutral-500">
              Each range below is blocked on <span className="font-semibold text-neutral-700">every</span> date in this period.
            </p>

            <div className="space-y-2.5">
              {ranges.map((range, index) => (
                <div key={index} className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium text-neutral-600 w-10">From</span>
                    <select
                      value={range.startTime}
                      onChange={e => updateRange(index, { startTime: e.target.value })}
                      aria-label={`Range ${String(index + 1)} start time`}
                      className="flex-1 rounded-lg border border-neutral-200 px-2.5 py-2 text-xs bg-white focus:border-neutral-900 focus:outline-none cursor-pointer"
                    >
                      {TIME_OPTIONS.map(t => (
                        <option key={t} value={t}>
                          {label12h(t)}
                        </option>
                      ))}
                    </select>
                    <span className="text-xs font-medium text-neutral-600 w-6 text-center">To</span>
                    <select
                      value={range.endTime}
                      onChange={e => updateRange(index, { endTime: e.target.value })}
                      aria-label={`Range ${String(index + 1)} end time`}
                      className="flex-1 rounded-lg border border-neutral-200 px-2.5 py-2 text-xs bg-white focus:border-neutral-900 focus:outline-none cursor-pointer"
                    >
                      {TIME_OPTIONS.map(t => (
                        <option key={t} value={t}>
                          {label12h(t)}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      onClick={() => setRanges(prev => prev.filter((_, i) => i !== index))}
                      disabled={ranges.length === 1}
                      title="Remove this range"
                      className="p-2 rounded-lg text-neutral-400 hover:text-red-600 hover:bg-red-50 disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer transition-colors"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                  {rangeErrors[index] && (
                    <p className="text-[11px] text-red-600 pl-12 font-medium">{rangeErrors[index]}</p>
                  )}
                </div>
              ))}
            </div>

            <button
              type="button"
              onClick={() => setRanges(prev => [...prev, { startTime: '15:00', endTime: '16:00' }])}
              className="flex items-center gap-1.5 text-xs font-semibold text-neutral-700 hover:text-neutral-900 cursor-pointer"
            >
              <Plus className="h-3.5 w-3.5" /> Add another time range
            </button>

            <div>
              <label htmlFor="block-reason" className="block text-xs font-medium text-neutral-600 mb-1">
                Reason (Optional)
              </label>
              <input
                id="block-reason"
                type="text"
                placeholder="e.g. Theatre, Ward round, Personal"
                value={reason}
                onChange={e => setReason(e.target.value)}
                className="w-full rounded-lg border border-neutral-200 px-3 py-2 text-xs bg-white focus:border-neutral-900 focus:outline-none"
              />
            </div>

            <div
              className={cn(
                'rounded-xl border p-3 flex items-start gap-2.5',
                affectedCount > 0 ? 'bg-amber-50 border-amber-200 text-amber-900' : 'bg-neutral-50 border-neutral-200 text-neutral-600'
              )}
            >
              <AlertTriangle
                className={cn('h-4 w-4 mt-0.5 shrink-0', affectedCount > 0 ? 'text-amber-600' : 'text-neutral-400')}
              />
              <p className="text-xs">
                {affectedCount > 0 ? (
                  <>
                    <span className="font-bold text-amber-950">{affectedCount}</span> booked appointment(s) fall inside these hours and will be cancelled.
                  </>
                ) : (
                  'No booked appointments fall inside these hours.'
                )}
              </p>
            </div>
          </div>

          <div className="px-6 py-3.5 bg-neutral-50 border-t border-neutral-100 flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-neutral-600 hover:text-neutral-900 rounded-lg hover:bg-neutral-200/50 transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!canSubmit}
              className="px-4 py-2 rounded-lg bg-red-600 hover:bg-red-700 text-xs font-semibold text-white disabled:opacity-50 transition-colors cursor-pointer shadow-xs"
            >
              {isSubmitting ? 'Blocking…' : 'Block These Hours'}
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
        message={`${String(affectedCount)} booked appointment(s) fall inside the hours you are blocking and will be CANCELLED. Do you want to proceed?`}
        confirmText="Cancel Appointments & Block"
        cancelText="Go Back"
        variant="danger"
        isLoading={isSubmitting}
      />
    </>
  )
}

export default TimeBlockModal
