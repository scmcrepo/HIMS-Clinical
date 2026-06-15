import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useConsultantSlots, useSlotMutations } from '../../../hooks/consultant/useConsultant'
import type { DayOfWeek, SlotUpsertItem } from '../../../services/slot/slotApi'
import { cn } from '../../../lib/utils'
import BackButton from '../../../components/shared/BackButton'
import { toast } from '../../../hooks/useToast'

const DAYS: DayOfWeek[] = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']

export default function ConsultantSlotsPage() {
  const navigate = useNavigate()
  const { consultantId } = useParams<{ consultantId: string }>()
  const consultantName = new URLSearchParams(window.location.search).get('name') ?? 'Consultant'

  const { data: slots, isLoading } = useConsultantSlots(consultantId!)
  const { upsert } = useSlotMutations(consultantId!)

  const [localSlots, setLocalSlots] = useState<{
    fromTime: string
    toTime: string
    days: DayOfWeek[]
    maxPatients: number
  }[]>([])

  const [isInitialized, setIsInitialized] = useState(false)

  useEffect(() => {
    if (slots && !isInitialized) {
      const groups: Record<string, { fromTime: string; toTime: string; days: DayOfWeek[]; maxPatients: number }> = {}
      slots.forEach(s => {
        const key = `${s.fromTime}-${s.toTime}-${s.maxPatients}`
        if (!groups[key]) {
          groups[key] = { fromTime: s.fromTime, toTime: s.toTime, days: [], maxPatients: s.maxPatients }
        }
        groups[key].days.push(s.dayOfWeek)
      })
      setLocalSlots(Object.values(groups))
      setIsInitialized(true)
    }
  }, [slots, isInitialized])

  const handleAddSlot = () => {
    setLocalSlots(prev => [...prev, { fromTime: '09:00', toTime: '12:00', days: [], maxPatients: 10 }])
  }

  const handleRemoveSlot = (idx: number) => {
    setLocalSlots(prev => prev.filter((_, i) => i !== idx))
  }

  const toggleDay = (idx: number, day: DayOfWeek) => {
    setLocalSlots(prev => prev.map((s, i) => {
      if (i !== idx) return s
      const days = s.days.includes(day) ? s.days.filter(d => d !== day) : [...s.days, day]
      return { ...s, days }
    }))
  }

  const handleSave = () => {
    for (const s of localSlots) {
      if (!s.fromTime || !s.toTime) {
        toast({ title: 'Invalid slot times', description: 'Please specify both From and To times.', variant: 'destructive' })
        return
      }
      if (s.fromTime >= s.toTime) {
        toast({ title: 'Invalid time range', description: 'From Time must be before To Time.', variant: 'destructive' })
        return
      }
      if (s.days.length === 0) {
        toast({ title: 'No days selected', description: 'Please select at least one day for each slot.', variant: 'destructive' })
        return
      }
    }

    const flat: SlotUpsertItem[] = []
    localSlots.forEach(s => {
      s.days.forEach(day => {
        flat.push({
          dayOfWeek: day,
          fromTime: s.fromTime,
          toTime: s.toTime,
          numberOfPatients: s.maxPatients
        })
      })
    })
    upsert.mutate(flat, { onSuccess: () => navigate(-1) })
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Manage Availability</h2>
          <p className="text-sm text-gray-500">{consultantName}</p>
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm">
        <div className="p-6 space-y-6">
          {isLoading ? (
            <div className="text-center py-10 text-gray-500">Loading slots…</div>
          ) : (
            <div className="space-y-4">
              <div className="grid grid-cols-[120px_120px_1fr_100px_50px] gap-4 px-2 text-xs font-bold text-gray-400 uppercase tracking-wider">
                <div>From Time</div>
                <div>To Time</div>
                <div>Encountering Days</div>
                <div>Patients</div>
                <div />
              </div>

              {localSlots.map((slot, idx) => (
                <div key={idx} className="grid grid-cols-[120px_120px_1fr_100px_50px] gap-4 items-center bg-gray-50 p-3 rounded-xl border border-gray-100">
                  <input type="time" value={slot.fromTime}
                    onChange={e => setLocalSlots(prev => prev.map((s, i) => i === idx ? { ...s, fromTime: e.target.value } : s))}
                    className="px-2 py-1.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-neutral-500 outline-none" />

                  <input type="time" value={slot.toTime}
                    onChange={e => setLocalSlots(prev => prev.map((s, i) => i === idx ? { ...s, toTime: e.target.value } : s))}
                    className="px-2 py-1.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-neutral-500 outline-none" />

                  <div className="flex flex-wrap gap-1">
                    {DAYS.map(day => (
                      <button key={day} type="button"
                        onClick={() => toggleDay(idx, day)}
                        className={cn('w-9 h-8 rounded text-[10px] font-bold border transition-all',
                          slot.days.includes(day)
                            ? 'bg-neutral-600 border-neutral-600 text-white shadow-sm'
                            : 'bg-white border-gray-200 text-gray-400 hover:border-neutral-300 hover:text-neutral-500')}>
                        {day}
                      </button>
                    ))}
                  </div>

                  <input type="number" min={1} value={slot.maxPatients}
                    onChange={e => {
                      const raw = e.target.value
                      const num = raw === '' ? 0 : parseInt(raw, 10)
                      if (!isNaN(num)) {
                        setLocalSlots(prev => prev.map((s, i) => i === idx ? { ...s, maxPatients: num } : s))
                      }
                    }}
                    onBlur={() => {
                      setLocalSlots(prev => prev.map((s, i) => i === idx ? { ...s, maxPatients: Math.max(1, s.maxPatients) } : s))
                    }}
                    className="px-2 py-1.5 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-neutral-500 outline-none text-center" />

                  <button type="button" onClick={() => handleRemoveSlot(idx)} className="text-red-400 hover:text-red-600 transition-colors">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                </div>
              ))}

              <button type="button" onClick={handleAddSlot}
                className="w-full py-3 border-2 border-dashed border-gray-200 rounded-xl text-gray-400 hover:border-neutral-300 hover:text-neutral-500 hover:bg-neutral-50 transition-all text-sm font-medium">
                + Add New Time Slot
              </button>
            </div>
          )}
        </div>

        <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-3">
          <button type="button" onClick={() => navigate(-1)} className="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-800 border border-gray-200 rounded-xl">Cancel</button>
          <button type="button" onClick={handleSave} disabled={upsert.isPending}
            className="px-6 py-2 bg-neutral-600 text-white text-sm font-bold rounded-lg hover:bg-neutral-700 disabled:opacity-50 shadow-lg shadow-neutral-200 transition-all">
            {upsert.isPending ? 'Saving…' : 'Save All Slots'}
          </button>
        </div>
      </div>
    </div>
  )
}
