import { useState, useEffect, useMemo, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { format, addDays, addMonths, parseISO, startOfMonth, endOfMonth, eachDayOfInterval, getDay, isSameMonth, isToday, isBefore, startOfDay } from 'date-fns'
import {
  useConsultantSlots,
  useSlotMutations,
  useDateSpecificSlots,
  useDateSlotMutations
} from '../../../hooks/consultant/useConsultant'
import { useConsultantLeavesById } from '../../../hooks/appointment/useAppointment'
import type { DayOfWeek, SlotUpsertItem } from '../../../services/slot/slotApi'
import { cn } from '../../../lib/utils'
import BackButton from '../../../components/shared/BackButton'
import DatePicker from '../../../components/shared/DatePicker'
import { toast } from '../../../hooks/useToast'
import { CalendarRange, CalendarPlus, Clock, Trash2, ChevronLeft, ChevronRight, Plus, X } from 'lucide-react'

const DAYS: DayOfWeek[] = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']
const CAL_DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']

const formatTimeShort = (t: string) => {
  if (!t) return '—'
  try {
    const [h, m] = t.split(':')
    const d = new Date()
    d.setHours(parseInt(h), parseInt(m), 0)
    return format(d, 'hh:mm a')
  } catch {
    return t
  }
}

interface TimeSlotRow {
  fromTime: string
  toTime: string
  numberOfPatients: string
}

export default function ConsultantSlotsPage() {
  const navigate = useNavigate()
  const { consultantId } = useParams<{ consultantId: string }>()
  const consultantName = new URLSearchParams(window.location.search).get('name') ?? 'Consultant'

  const [activeTab, setActiveTab] = useState<'WEEKLY' | 'DATE_WISE'>('WEEKLY')

  const { data: slots, isLoading: slotsLoading } = useConsultantSlots(consultantId!)
  const { upsert } = useSlotMutations(consultantId!)
  const { data: dateSlots, isLoading: dateSlotsLoading } = useDateSpecificSlots(consultantId!)
  const { saveDateSlots, deleteDateSlot } = useDateSlotMutations(consultantId!)
  // Fetch this consultant's leaves so calendar can block leave dates
  const { data: leavesData } = useConsultantLeavesById(consultantId)

  const [validityMode, setValidityMode] = useState<'ONGOING' | '1_MONTH' | '2_MONTHS' | '3_MONTHS' | 'CUSTOM'>('ONGOING')
  const [effectiveFrom, setEffectiveFrom] = useState<string>(format(new Date(), 'yyyy-MM-dd'))
  const [effectiveTo, setEffectiveTo] = useState<string>('')

  useEffect(() => {
    const today = new Date()
    setEffectiveFrom(format(today, 'yyyy-MM-dd'))
    if (validityMode === '1_MONTH') setEffectiveTo(format(addMonths(today, 1), 'yyyy-MM-dd'))
    else if (validityMode === '2_MONTHS') setEffectiveTo(format(addMonths(today, 2), 'yyyy-MM-dd'))
    else if (validityMode === '3_MONTHS') setEffectiveTo(format(addMonths(today, 3), 'yyyy-MM-dd'))
    else if (validityMode === 'ONGOING') setEffectiveTo('')
  }, [validityMode])

  const [localSlots, setLocalSlots] = useState<{
    fromTime: string; toTime: string; days: DayOfWeek[]; maxPatients: string
  }[]>([])
  const [isInitialized, setIsInitialized] = useState(false)

  useEffect(() => {
    if (slots && !isInitialized) {
      const groups: Record<string, { fromTime: string; toTime: string; days: DayOfWeek[]; maxPatients: string }> = {}
      slots.filter(s => !s.specificDate).forEach(s => {
        const key = `${s.fromTime}-${s.toTime}-${s.maxPatients}`
        if (!groups[key]) groups[key] = { fromTime: s.fromTime, toTime: s.toTime, days: [], maxPatients: String(s.maxPatients) }
        groups[key].days.push(s.dayOfWeek)
      })
      setLocalSlots(Object.values(groups))
      setIsInitialized(true)
    }
  }, [slots, isInitialized])

  // Calendar state
  const [calendarMonth, setCalendarMonth] = useState(new Date())
  const [selectedDates, setSelectedDates] = useState<Set<string>>(new Set())
  const [dateWiseSlots, setDateWiseSlots] = useState<TimeSlotRow[]>([
    { fromTime: '06:00', toTime: '08:00', numberOfPatients: '10' }
  ])

  const todayStart = startOfDay(new Date())

  const existingCustomDates = useMemo(() => {
    const s = new Set<string>()
    dateSlots?.forEach(ds => { if (ds.specificDate) s.add(ds.specificDate) })
    return s
  }, [dateSlots])

  // Build a set of all leave-covered date strings for fast lookup
  const leaveDates = useMemo(() => {
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

  const calendarDays = useMemo(() => {
    const start = startOfMonth(calendarMonth)
    const end = endOfMonth(calendarMonth)
    const days = eachDayOfInterval({ start, end })
    const startPad = getDay(start)
    const result: (Date | null)[] = Array(startPad).fill(null)
    days.forEach(d => result.push(d))
    while (result.length % 7 !== 0) result.push(null)
    return result
  }, [calendarMonth])

  const toggleDate = useCallback((d: Date) => {
    const str = format(d, 'yyyy-MM-dd')
    if (leaveDates.has(str)) return // Doctor is on leave, cannot select
    setSelectedDates(prev => {
      const next = new Set(prev)
      if (next.has(str)) next.delete(str)
      else next.add(str)
      return next
    })
  }, [leaveDates])

  const handleSaveDateWise = () => {
    if (selectedDates.size === 0) {
      toast({ title: 'No dates selected', description: 'Please select at least one date on the calendar.', variant: 'destructive' })
      return
    }
    for (const row of dateWiseSlots) {
      if (!row.fromTime || !row.toTime) {
        toast({ title: 'Invalid slot times', description: 'Please specify both From and To times.', variant: 'destructive' })
        return
      }
      if (row.fromTime >= row.toTime) {
        toast({ title: 'Invalid time range', description: 'From Time must be before To Time.', variant: 'destructive' })
        return
      }
      const p = parseInt(row.numberOfPatients, 10)
      if (isNaN(p) || p < 1) {
        toast({ title: 'Invalid patients', description: 'Enter a valid number of patients (min 1).', variant: 'destructive' })
        return
      }
    }
    saveDateSlots.mutate({
      dates: Array.from(selectedDates).sort(),
      slots: dateWiseSlots.map(r => ({ fromTime: r.fromTime, toTime: r.toTime, numberOfPatients: parseInt(r.numberOfPatients, 10) }))
    }, {
      onSuccess: () => {
        setSelectedDates(new Set())
        setDateWiseSlots([{ fromTime: '06:00', toTime: '08:00', numberOfPatients: '10' }])
      }
    })
  }

  const handleSaveWeekly = () => {
    for (const s of localSlots) {
      if (!s.fromTime || !s.toTime) { toast({ title: 'Invalid slot times', variant: 'destructive' }); return }
      if (s.fromTime >= s.toTime) { toast({ title: 'Invalid time range', description: 'From Time must be before To Time.', variant: 'destructive' }); return }
      if (s.days.length === 0) { toast({ title: 'No days selected', description: 'Select at least one day.', variant: 'destructive' }); return }
      if (!s.maxPatients || isNaN(parseInt(s.maxPatients, 10)) || parseInt(s.maxPatients, 10) < 1) {
        toast({ title: 'Invalid patients', variant: 'destructive' }); return
      }
    }
    const flat: SlotUpsertItem[] = []
    localSlots.forEach(s => s.days.forEach(day => flat.push({
      dayOfWeek: day, fromTime: s.fromTime, toTime: s.toTime, numberOfPatients: parseInt(s.maxPatients, 10) || 1
    })))
    const validity = {
      effectiveFrom: validityMode !== 'ONGOING' ? effectiveFrom : undefined,
      effectiveTo: validityMode !== 'ONGOING' && effectiveTo ? effectiveTo : undefined
    }
    upsert.mutate({ daysList: flat, validity }, { onSuccess: () => navigate(-1) })
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between bg-white p-6 rounded-2xl border border-gray-100 shadow-sm">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight flex items-center gap-2">
            <Clock className="h-6 w-6 text-neutral-600" />
            Manage Availability
          </h2>
          <p className="text-sm text-gray-500 font-medium">{consultantName}</p>
        </div>
        <BackButton />
      </div>

      {/* Tabs */}
      <div className="flex bg-gray-100 p-1.5 rounded-2xl max-w-md">
        <button onClick={() => setActiveTab('WEEKLY')} className={cn('flex-1 py-2.5 px-4 text-xs font-bold rounded-xl transition-all flex items-center justify-center gap-2', activeTab === 'WEEKLY' ? 'bg-white text-neutral-800 shadow-sm' : 'text-gray-500 hover:text-gray-800')}>
          <CalendarRange className="h-4 w-4" />Weekly Recurring Schedule
        </button>
        <button onClick={() => setActiveTab('DATE_WISE')} className={cn('flex-1 py-2.5 px-4 text-xs font-bold rounded-xl transition-all flex items-center justify-center gap-2', activeTab === 'DATE_WISE' ? 'bg-white text-neutral-800 shadow-sm' : 'text-gray-500 hover:text-gray-800')}>
          <CalendarPlus className="h-4 w-4" />Date-Wise Custom Slots
        </button>
      </div>

      {/* Tab 1: Weekly */}
      {activeTab === 'WEEKLY' && (
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm space-y-6 p-6">
          <div className="p-4 bg-neutral-50 rounded-2xl border border-neutral-200 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="space-y-1">
              <h4 className="text-xs font-bold text-neutral-700 uppercase tracking-wider flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5 text-neutral-600" />Schedule Active Duration / Validity Period
              </h4>
              <p className="text-xs text-neutral-500">Choose how long this recurring weekly schedule should stay active.</p>
            </div>
            <select value={validityMode} onChange={e => setValidityMode(e.target.value as any)} className="px-3 py-2 bg-white border border-neutral-300 rounded-xl text-xs font-bold text-neutral-800 outline-none focus:ring-2 focus:ring-neutral-500">
              <option value="ONGOING">Ongoing / Indefinite</option>
              <option value="1_MONTH">1 Month (from today)</option>
              <option value="2_MONTHS">2 Months (from today)</option>
              <option value="3_MONTHS">3 Months (from today)</option>
              <option value="CUSTOM">Custom Date Range</option>
            </select>
          </div>

          {validityMode === 'CUSTOM' && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 p-4 bg-gray-50 rounded-2xl border border-gray-100">
              <div className="space-y-1">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">Effective From</label>
                <DatePicker value={effectiveFrom} onChange={val => setEffectiveFrom(val || '')} size="sm" />
              </div>
              <div className="space-y-1">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-widest">Effective To</label>
                <DatePicker value={effectiveTo} onChange={val => setEffectiveTo(val || '')} size="sm" />
              </div>
            </div>
          )}

          {slotsLoading ? (
            <div className="text-center py-10 text-gray-500">Loading recurring slots…</div>
          ) : (
            <div className="space-y-4">
              <div className="grid grid-cols-[120px_120px_1fr_100px_50px] gap-4 px-2 text-xs font-bold text-gray-400 uppercase tracking-wider">
                <div>From Time</div><div>To Time</div><div>Encountering Days</div><div>Patients</div><div />
              </div>
              {localSlots.map((slot, idx) => (
                <div key={idx} className="grid grid-cols-[120px_120px_1fr_100px_50px] gap-4 items-center bg-gray-50 p-3 rounded-xl border border-gray-100">
                  <input type="time" value={slot.fromTime} onChange={e => setLocalSlots(prev => prev.map((s, i) => i === idx ? { ...s, fromTime: e.target.value } : s))} className="px-2 py-1.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-neutral-500 outline-none" />
                  <input type="time" value={slot.toTime} onChange={e => setLocalSlots(prev => prev.map((s, i) => i === idx ? { ...s, toTime: e.target.value } : s))} className="px-2 py-1.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-neutral-500 outline-none" />
                  <div className="flex flex-wrap gap-1">
                    {DAYS.map(day => (
                      <button key={day} type="button" onClick={() => setLocalSlots(prev => prev.map((s, i) => { if (i !== idx) return s; const days = s.days.includes(day) ? s.days.filter(d => d !== day) : [...s.days, day]; return { ...s, days } }))} className={cn('w-9 h-8 rounded text-[10px] font-bold border transition-all', slot.days.includes(day) ? 'bg-neutral-600 border-neutral-600 text-white shadow-sm' : 'bg-white border-gray-200 text-gray-400 hover:border-neutral-300 hover:text-neutral-500')}>
                        {day}
                      </button>
                    ))}
                  </div>
                  <input type="text" inputMode="numeric" pattern="[0-9]*" value={slot.maxPatients} onChange={e => { const raw = e.target.value; if (raw === '' || /^\d+$/.test(raw)) setLocalSlots(prev => prev.map((s, i) => i === idx ? { ...s, maxPatients: raw } : s)) }} className="px-2 py-1.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-neutral-500 outline-none text-center" />
                  <button type="button" onClick={() => setLocalSlots(prev => prev.filter((_, i) => i !== idx))} className="text-red-400 hover:text-red-600 transition-colors">
                    <Trash2 className="w-5 h-5" />
                  </button>
                </div>
              ))}
              <button type="button" onClick={() => setLocalSlots(prev => [...prev, { fromTime: '09:00', toTime: '12:00', days: [], maxPatients: '10' }])} className="w-full py-3 border-2 border-dashed border-gray-200 rounded-xl text-gray-400 hover:border-neutral-300 hover:text-neutral-500 hover:bg-neutral-50 transition-all text-sm font-medium">
                + Add New Recurring Slot
              </button>
            </div>
          )}

          <div className="pt-4 border-t border-gray-100 flex justify-end gap-3">
            <button type="button" onClick={() => navigate(-1)} className="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-800 border border-gray-200 rounded-xl">Cancel</button>
            <button type="button" onClick={handleSaveWeekly} disabled={upsert.isPending} className="px-6 py-2 bg-neutral-800 text-white text-sm font-bold rounded-xl hover:bg-neutral-700 disabled:opacity-50 shadow-md transition-all">
              {upsert.isPending ? 'Saving…' : 'Save Recurring Slots'}
            </button>
          </div>
        </div>
      )}

      {/* Tab 2: Date-Wise */}
      {activeTab === 'DATE_WISE' && (
        <div className="space-y-5">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            {/* Calendar panel */}
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5 space-y-4">
              <div>
                <h3 className="text-sm font-bold text-gray-900">Select Dates</h3>
                <p className="text-xs text-gray-500 mt-0.5">Click dates to select / deselect. Multiple dates can be chosen across months.</p>
              </div>

              {/* Month nav */}
              <div className="flex items-center justify-between">
                <button type="button" onClick={() => setCalendarMonth(m => addMonths(m, -1))} className="p-1.5 rounded-lg hover:bg-gray-100 transition-colors text-gray-600">
                  <ChevronLeft className="h-4 w-4" />
                </button>
                <h4 className="text-sm font-bold text-gray-800">{format(calendarMonth, 'MMMM yyyy')}</h4>
                <button type="button" onClick={() => setCalendarMonth(m => addMonths(m, 1))} className="p-1.5 rounded-lg hover:bg-gray-100 transition-colors text-gray-600">
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>

              {/* Day headers */}
              <div className="grid grid-cols-7 gap-0.5">
                {CAL_DAYS.map(d => (
                  <div key={d} className="text-center text-[10px] font-bold text-gray-400 uppercase pb-1">{d}</div>
                ))}
              </div>

              {/* Calendar grid */}
              <div className="grid grid-cols-7 gap-0.5">
                {calendarDays.map((d, i) => {
                  if (!d) return <div key={i} />
                  const str = format(d, 'yyyy-MM-dd')
                  const isPast = isBefore(d, todayStart)
                  const isLeave = leaveDates.has(str)
                  const isSelected = selectedDates.has(str)
                  const isCurrentMonth = isSameMonth(d, calendarMonth)
                  const hasExisting = existingCustomDates.has(str)
                  const isTodayDate = isToday(d)
                  return (
                    <button
                      key={str}
                      type="button"
                      disabled={isPast || isLeave}
                      onClick={() => !isPast && !isLeave && toggleDate(d)}
                      title={isLeave ? 'Doctor is on Leave / Unavailable' : hasExisting ? 'Has existing custom slot' : undefined}
                      className={cn(
                        'relative h-9 w-full rounded-lg text-xs font-semibold transition-all flex flex-col items-center justify-center',
                        !isCurrentMonth && 'opacity-30',
                        (isPast || isLeave) && 'cursor-not-allowed opacity-40',
                        isTodayDate && !isSelected && 'ring-2 ring-neutral-400 ring-inset',
                        isLeave
                          ? 'bg-neutral-50 hover:bg-neutral-50 border border-dashed border-neutral-300 text-neutral-400 line-through'
                          : isSelected
                            ? 'bg-black text-white shadow-md'
                            : hasExisting && !isPast
                              ? 'bg-neutral-100 text-neutral-800 hover:bg-neutral-200 border border-neutral-300'
                              : !isPast ? 'hover:bg-neutral-100 text-gray-700' : ''
                      )}
                    >
                      <span className={cn(isLeave && 'text-neutral-400 line-through font-bold')}>{format(d, 'd')}</span>
                      {isLeave ? (
                        <span className="text-[7px] font-bold uppercase tracking-wider text-neutral-400 -mt-0.5">LEAVE</span>
                      ) : hasExisting && !isSelected && (
                        <span className="absolute bottom-1 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-neutral-800" />
                      )}
                    </button>
                  )
                })}
              </div>

              {/* Legend */}
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 pt-1 border-t border-gray-50">
                <div className="flex items-center gap-1.5"><span className="inline-block w-3 h-3 rounded bg-black" /><span className="text-[10px] text-neutral-600 font-semibold">Selected</span></div>
                <div className="flex items-center gap-1.5"><span className="inline-block w-3 h-3 rounded bg-neutral-100 border border-neutral-300" /><span className="text-[10px] text-neutral-600 font-semibold">Has custom slot</span></div>
                <div className="flex items-center gap-1.5"><span className="inline-block w-3 h-3 rounded bg-neutral-50 border border-dashed border-neutral-300" /><span className="text-[10px] text-neutral-500 font-medium">On Leave (Blocked)</span></div>
                <div className="flex items-center gap-1.5"><span className="inline-block w-3 h-3 rounded ring-2 ring-neutral-400 ring-inset" /><span className="text-[10px] text-neutral-600 font-semibold">Today</span></div>
              </div>

              {/* Selected dates pills */}
              {selectedDates.size > 0 && (
                <div className="bg-neutral-50 rounded-xl p-3 border border-neutral-100">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-neutral-700">{selectedDates.size} date{selectedDates.size > 1 ? 's' : ''} selected</span>
                    <button type="button" onClick={() => setSelectedDates(new Set())} className="text-[10px] text-gray-400 hover:text-red-500 transition-colors font-medium">Clear all</button>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {Array.from(selectedDates).sort().map(str => (
                      <span key={str} className="inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 bg-neutral-800 text-white rounded-full">
                        {format(parseISO(str), 'dd MMM')}
                        <button type="button" onClick={() => setSelectedDates(prev => { const n = new Set(prev); n.delete(str); return n })} className="hover:text-red-300 transition-colors">
                          <X className="h-2.5 w-2.5" />
                        </button>
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Time slots + save panel */}
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5 space-y-4">
              <div>
                <h3 className="text-sm font-bold text-gray-900">Custom Time Slots</h3>
                <p className="text-xs text-gray-500 mt-0.5">These time slots apply to all selected dates, overriding the weekly schedule.</p>
              </div>

              <div className="space-y-3">
                <div className="grid grid-cols-[1fr_1fr_80px_32px] gap-2 px-1 text-[10px] font-bold text-gray-400 uppercase tracking-wider">
                  <div>From</div><div>To</div><div>Max Pts.</div><div />
                </div>
                {dateWiseSlots.map((row, idx) => (
                  <div key={idx} className="grid grid-cols-[1fr_1fr_80px_32px] gap-2 items-center bg-gray-50 rounded-xl p-2 border border-gray-100">
                    <input type="time" value={row.fromTime} onChange={e => setDateWiseSlots(prev => prev.map((r, i) => i === idx ? { ...r, fromTime: e.target.value } : r))} className="px-2 py-1.5 border border-gray-300 rounded-lg text-xs font-medium focus:ring-2 focus:ring-neutral-500 outline-none" />
                    <input type="time" value={row.toTime} onChange={e => setDateWiseSlots(prev => prev.map((r, i) => i === idx ? { ...r, toTime: e.target.value } : r))} className="px-2 py-1.5 border border-gray-300 rounded-lg text-xs font-medium focus:ring-2 focus:ring-neutral-500 outline-none" />
                    <input type="number" min="1" value={row.numberOfPatients} onChange={e => setDateWiseSlots(prev => prev.map((r, i) => i === idx ? { ...r, numberOfPatients: e.target.value } : r))} className="px-2 py-1.5 border border-gray-300 rounded-lg text-xs font-bold text-center focus:ring-2 focus:ring-neutral-500 outline-none" />
                    {dateWiseSlots.length > 1 ? (
                      <button type="button" onClick={() => setDateWiseSlots(prev => prev.filter((_, i) => i !== idx))} className="p-1 text-red-400 hover:text-red-600 transition-colors rounded-lg hover:bg-red-50">
                        <Trash2 className="h-4 w-4" />
                      </button>
                    ) : <div />}
                  </div>
                ))}
                <button type="button" onClick={() => setDateWiseSlots(prev => [...prev, { fromTime: '09:00', toTime: '12:00', numberOfPatients: '10' }])} className="w-full py-2.5 border-2 border-dashed border-gray-200 rounded-xl text-gray-400 hover:border-neutral-300 hover:text-neutral-500 hover:bg-neutral-50 transition-all text-xs font-medium flex items-center justify-center gap-1.5">
                  <Plus className="h-3.5 w-3.5" />Add Another Time Window
                </button>
              </div>

              {/* Summary */}
              <div className="bg-gradient-to-br from-neutral-50 to-gray-50 rounded-xl p-4 border border-neutral-100 space-y-1">
                <p className="text-[10px] font-bold text-neutral-500 uppercase tracking-wider">Summary</p>
                <p className="text-xs text-neutral-700"><span className="font-bold">{selectedDates.size}</span> date{selectedDates.size !== 1 ? 's' : ''} selected</p>
                <p className="text-xs text-neutral-700"><span className="font-bold">{dateWiseSlots.length}</span> time slot{dateWiseSlots.length !== 1 ? 's' : ''} configured</p>
                {selectedDates.size > 0 && (
                  <p className="text-xs text-neutral-500 pt-1">Will create <span className="font-bold text-neutral-800">{selectedDates.size * dateWiseSlots.length}</span> custom slot records.</p>
                )}
              </div>

              <button type="button" onClick={handleSaveDateWise} disabled={saveDateSlots.isPending || selectedDates.size === 0} className="w-full py-3 bg-neutral-800 text-white text-xs font-bold rounded-xl hover:bg-neutral-700 disabled:opacity-50 shadow-md transition-all flex items-center justify-center gap-2">
                <CalendarPlus className="h-4 w-4" />
                {saveDateSlots.isPending ? 'Saving…' : selectedDates.size === 0 ? 'Select dates on calendar to save' : `Save for ${selectedDates.size} Date${selectedDates.size > 1 ? 's' : ''}`}
              </button>
            </div>
          </div>

          {/* Active overrides list */}
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5 space-y-3">
            <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider">Active Date-Specific Overrides</h4>
            {dateSlotsLoading ? (
              <p className="text-xs text-gray-400">Loading custom date slots…</p>
            ) : !dateSlots || dateSlots.length === 0 ? (
              <div className="text-center py-6 text-gray-400 text-xs bg-gray-50 rounded-2xl border border-gray-100">
                No custom date-wise slots configured yet. Standard weekly schedule applies to all dates.
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                {dateSlots.map(ds => (
                  <div key={ds.id} className="flex items-center justify-between p-3 bg-neutral-50 rounded-xl border border-neutral-200">
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-neutral-900">{ds.specificDate ? format(parseISO(ds.specificDate), 'dd MMM yyyy (EEE)') : '—'}</span>
                        <span className="text-[10px] font-bold uppercase bg-neutral-800 text-white px-2 py-0.5 rounded-full">Custom</span>
                      </div>
                      <p className="text-xs text-neutral-600">{formatTimeShort(ds.fromTime)} – {formatTimeShort(ds.toTime)} ({ds.maxPatients} max)</p>
                    </div>
                    <button type="button" onClick={() => deleteDateSlot.mutate(ds.id)} disabled={deleteDateSlot.isPending} className="p-1.5 text-neutral-400 hover:bg-neutral-100 rounded-lg transition-colors" title="Remove date slot">
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      <PreviewPanel localSlots={localSlots} dateSlots={dateSlots || []} leaves={leavesData || []} />
    </div>
  )
}

function PreviewPanel({
  localSlots,
  dateSlots,
  leaves
}: {
  localSlots: { fromTime: string; toTime: string; days: DayOfWeek[]; maxPatients: string }[]
  dateSlots: { id: string; specificDate?: string | null; fromTime: string; toTime: string; maxPatients: number }[]
  leaves: any[]
}) {
  const DAY_MAP: Record<number, DayOfWeek> = { 0: 'SUN', 1: 'MON', 2: 'TUE', 3: 'WED', 4: 'THU', 5: 'FRI', 6: 'SAT' }

  const preview = useMemo(() => {
    const today = new Date()
    return Array.from({ length: 14 }, (_, i) => {
      const d = addDays(today, i)
      const dayKey = DAY_MAP[d.getDay()]
      const dateStr = format(d, 'yyyy-MM-dd')
      const isLeave = leaves?.some(l => dateStr >= l.startDate && dateStr <= l.endDate) ?? false
      const customSlots = dateSlots.filter(s => s.specificDate === dateStr)
      const daySlots = customSlots.length > 0
        ? customSlots.map(s => ({ fromTime: s.fromTime, toTime: s.toTime, maxPatients: s.maxPatients, isCustom: true }))
        : localSlots.filter(s => s.days.includes(dayKey)).map(s => ({ fromTime: s.fromTime, toTime: s.toTime, maxPatients: s.maxPatients, isCustom: false }))
      return { date: d, dateStr, dayLabel: format(d, 'EEE'), slots: daySlots, isLeave }
    })
  }, [localSlots, dateSlots, leaves])

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-4">
      <h3 className="text-sm font-bold text-gray-800 uppercase tracking-wider">Next 14 Days Live Schedule Preview</h3>
      <div className="divide-y divide-gray-50">
        {preview.map(p => (
          <div key={p.dateStr} className={cn('flex items-center gap-4 py-2.5 px-2 rounded-lg', p.isLeave && 'bg-neutral-50/50')}>
            <div className="w-24 shrink-0">
              <span className={cn('text-xs font-bold', p.isLeave ? 'text-neutral-400 line-through' : 'text-gray-800')}>{format(p.date, 'dd MMM')}</span>
              <span className="ml-1 text-[10px] font-semibold text-gray-400">{p.dayLabel}</span>
            </div>
            <div className="flex-1 flex flex-wrap gap-1.5">
              {p.isLeave ? (
                <span className="text-[10px] font-bold text-neutral-500 bg-neutral-100 border border-neutral-200 px-2 py-0.5 rounded-full line-through">ON LEAVE</span>
              ) : p.slots.length === 0 ? (
                <span className="text-[10px] font-bold text-gray-400 bg-gray-100 px-2 py-0.5 rounded-full">CLOSED</span>
              ) : p.slots.map((s, i) => (
                <span key={i} className={cn('text-[10px] font-semibold px-2.5 py-0.5 rounded-full border', s.isCustom ? 'bg-neutral-800 text-white border-neutral-800' : 'text-neutral-700 bg-neutral-100 border-neutral-200')}>
                  {formatTimeShort(s.fromTime)} – {formatTimeShort(s.toTime)} ({s.maxPatients || '?'} max)
                  {s.isCustom && <span className="ml-1 font-bold text-[9px] text-neutral-400">[Custom]</span>}
                </span>
              ))}
            </div>
            <div className="w-4 text-right">
              {p.isLeave ? <span className="inline-block w-2 h-2 rounded-full bg-neutral-300" />
                : p.slots.length > 0 ? <span className="inline-block w-2 h-2 rounded-full bg-black" />
                  : <span className="inline-block w-2 h-2 rounded-full bg-neutral-200" />}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
