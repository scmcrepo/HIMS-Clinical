/**
 * PrescriptionTab.tsx
 * Prescription clinical tab — works for both OP (inline) and IP (modal).
 */
import { useState, useEffect, useRef, useMemo } from 'react'
import { Pencil, ChevronDown } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../hooks/useToast'
import { QuickAddPanel } from './QuickAddPanel'
import {
  opPrescriptionApi, ipPrescriptionApi, drugSearchApi, instructionApi, routeApi,
  frequencyApi,
  type PrescriptionLinePayload, type PrescriptionResponse, type DrugItem, type PrescriptionPayload,
} from '../../../services/opip/opipApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { cn } from '../../../lib/utils'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'

interface Props {
  encounterId:   string
  mode:          'OP' | 'IP'
  consultantId?: string | undefined   // for quick-add panels
  readOnly?:     boolean | undefined
}

const EMPTY_LINE: PrescriptionLinePayload = {
  drugItemId: '', drugName: '', frequency: '', duration: '',
  qty: 0, instructionId: '', instructionLabel: '', routeId: '', routeLabel: '', remarks: '',
  sellingUnit: '',
}

/**
 * QTY Calculation Logic (ported from old CharmHIMS project)
 *
 * Formula: sum_of_doses_per_day × duration_number × duration_multiplier
 * - frequency like "1-1-1" → dosesPerDay = 1+1+1 = 3
 * - frequency value == 0   → qty = 1 (SOS/as-needed)
 * - Bottle/Syrup/Gel/Cream/Lotion/Ointment → qty = 1 always
 * - duration multipliers: Days=1, Weeks=7, Months=30
 *
 * Bottle detection: checks sellingUnit first, then falls back to drug name keywords
 * (e.g. "1 AL 30ML SYP" → detected as Syrup → QTY = 1)
 */
// Only LIQUID/POURABLE forms get qty=1 (whole bottle dispensed)
// Topical forms (CREAM, GEL, OINTMENT, LOTION, PASTE) and solid forms
// (POWDER, NOS, Tablet, Strip, Box, Capsule) calculate normally.
const BOTTLE_UNIT_KEYWORDS = [
  'BOTTLE',
  'SYRUP', 'SYP',
  'SUSPENSION', 'SUSP',
  'LINCTUS',
  'MIXTURE',
  'LIQUID',
  'SOLUTION', 'SOLN',
  'DROPS', 'DROP',
  'ELIXIR',
  'TINCTURE',
]

function isBottleType(sellingUnit: string, drugName: string): boolean {
  const unit = (sellingUnit || '').trim().toUpperCase()
  if (unit && BOTTLE_UNIT_KEYWORDS.includes(unit)) return true

  // Fallback: scan drug name for bottle-type keywords
  const nameUpper = (drugName || '').toUpperCase()
  return BOTTLE_UNIT_KEYWORDS.some(kw => {
    // Match as whole word to avoid false positives (e.g. "SYRUP" in name)
    const re = new RegExp(`\\b${kw}\\b`)
    return re.test(nameUpper)
  })
}

function calculateQty(frequency: string, duration: string, sellingUnit: string, drugName: string = ''): number {
  // Bottle-type units/names always = 1
  if (isBottleType(sellingUnit, drugName)) {
    return 1
  }

  if (!frequency || !duration) return 0

  // Parse duration: "5 Days", "5 Weeks", "5 Months" or just "5"
  const durMatch = duration.trim().match(/^(\d+)\s*(days?|weeks?|months?)?$/i)
  if (!durMatch) return 0
  const durNumber = parseInt(durMatch[1]) || 0
  const durType   = (durMatch[2] || 'days').toLowerCase()
  const multiplier = durType.startsWith('month') ? 30
                   : durType.startsWith('week')  ? 7
                   : 1  // days

  // Parse frequency: "1-0-1" → dosesPerDay = 2
  const dosesPerDay = frequency.split('-').reduce((sum, v) => sum + (parseInt(v) || 0), 0)

  if (dosesPerDay === 0) return 1  // SOS / as-needed
  if (durNumber === 0)   return 0

  return dosesPerDay * durNumber * multiplier
}

// ─── Custom UI Select and ComboBox Components ─────────────────────────────────

function CustomComboBox({
  value,
  onChange,
  options,
  placeholder,
  className,
  disabled
}: {
  value: string
  onChange: (val: string) => void
  options: { value: string; label: string }[]
  placeholder?: string
  className?: string
  disabled?: boolean
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const filtered = useMemo(() => {
    if (!value) return options
    const q = value.toLowerCase()
    return options.filter(o =>
      o.value.toLowerCase().includes(q) || o.label.toLowerCase().includes(q)
    )
  }, [options, value])

  return (
    <div ref={ref} className={cn('relative w-full', open ? 'z-30' : 'z-0', className)}>
      <div className="relative">
        <input
          type="text"
          disabled={disabled}
          value={value}
          placeholder={placeholder}
          onFocus={() => setOpen(true)}
          onClick={() => setOpen(true)}
          onChange={e => {
            onChange(e.target.value)
          }}
          className={cn(
            "w-full pl-2 pr-7 py-1 border border-gray-300 rounded-lg text-xs focus:outline-none focus:border-neutral-500 focus:ring-1 focus:ring-neutral-500 bg-white transition-colors",
            open && "border-neutral-500 ring-1 ring-neutral-500",
            disabled && "bg-gray-50 text-gray-500 cursor-not-allowed"
          )}
        />
        <div className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none">
          <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </div>
      {open && !disabled && (
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-md max-h-40 overflow-y-auto">
          {filtered.length > 0 ? (
            <ul>
              {filtered.map(o => (
                <li
                  key={o.value}
                  onMouseDown={(e) => {
                    e.preventDefault()
                    onChange(o.value)
                    setOpen(false)
                  }}
                  className={cn(
                    "px-3 py-1.5 hover:bg-[#C25727] hover:text-white cursor-pointer text-xs transition-colors text-gray-900",
                    value === o.value ? "bg-[#C25727] text-white" : ""
                  )}
                >
                  <span className="font-medium">{o.value}</span>
                  {o.label !== o.value && (
                    <span className={cn("block text-[10px]", value === o.value ? "text-orange-100" : "text-gray-400")}>{o.label}</span>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <div className="px-3 py-2 text-[10px] text-gray-500 text-center">No options</div>
          )}
        </div>
      )}
    </div>
  )
}



function DurationComboBox({
  value,
  onChange,
  placeholder,
  className,
  disabled
}: {
  value: string
  onChange: (val: string) => void
  placeholder?: string
  className?: string
  disabled?: boolean
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const options = useMemo(() => {
    const trimmed = value.trim()
    if (!trimmed) return []
    const lower = trimmed.toLowerCase()
    if (
      lower.endsWith('days') ||
      lower.endsWith('months') ||
      lower.endsWith('weeks') ||
      lower.endsWith('day') ||
      lower.endsWith('month') ||
      lower.endsWith('week')
    ) {
      return []
    }
    const num = parseInt(trimmed)
    const isSingular = num === 1
    if (isSingular) {
      return [
        `${trimmed} Day`,
        `${trimmed} Week`,
        `${trimmed} Month`
      ]
    } else {
      return [
        `${trimmed} Days`,
        `${trimmed} Weeks`,
        `${trimmed} Months`
      ]
    }
  }, [value])

  return (
    <div ref={ref} className={cn('relative w-full', open && options.length > 0 ? 'z-30' : 'z-0', className)}>
      <input
        type="text"
        disabled={disabled}
        value={value}
        placeholder={placeholder}
        onFocus={() => setOpen(true)}
        onClick={() => setOpen(true)}
        onChange={e => {
          onChange(e.target.value)
          setOpen(true)
        }}
        className={cn(
          "w-full px-2 py-1 border border-gray-300 rounded-lg text-xs focus:outline-none focus:border-neutral-500 focus:ring-1 focus:ring-neutral-500 bg-white transition-colors",
          open && options.length > 0 && "border-neutral-500 ring-1 ring-neutral-500",
          disabled && "bg-gray-50 text-gray-500 cursor-not-allowed"
        )}
      />
      {open && !disabled && options.length > 0 && (
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-md max-h-40 overflow-y-auto">
          <ul>
            {options.map(opt => (
              <li
                key={opt}
                onMouseDown={(e) => {
                  e.preventDefault()
                  onChange(opt)
                  setOpen(false)
                }}
                className={cn(
                  "px-3 py-1.5 hover:bg-[#C25727] hover:text-white cursor-pointer text-xs transition-colors text-gray-900",
                  value === opt ? "bg-[#C25727] text-white" : ""
                )}
              >
                {opt}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────

export function PrescriptionTab({ encounterId, mode, consultantId, readOnly }: Props) {
  const qc = useQueryClient()
  const { data: prescriptions = [], isLoading } = useQuery({
    queryKey: ['prescriptions', encounterId],
    queryFn:  () => mode === 'OP' ? opPrescriptionApi.list(encounterId) : ipPrescriptionApi.list(encounterId),
  })
  const invalidate = () => qc.invalidateQueries({ queryKey: ['prescriptions', encounterId] })

  // Flatten all saved items across all prescription groups
  const allSavedItems: PrescriptionLinePayload[] = useMemo(() =>
    prescriptions.flatMap(rx => rx.items.map(item => ({
      drugItemId: item.drugItemId ?? '', drugName: item.drugName ?? '',
      frequency: item.frequency ?? '', duration: item.duration ?? '',
      qty: item.qty ?? 0, instructionId: item.instructionId ?? '',
      instructionLabel: item.instructionLabel ?? '', routeId: item.routeId ?? '',
      routeLabel: item.routeLabel ?? '', remarks: item.remarks ?? '',
      sellingUnit: (item as any).sellingUnit ?? '',
    }))), [prescriptions])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-gray-800">Prescription</h3>
      </div>

      {/* Unified inline form for both OP and IP editable modes */}
      {!readOnly && (
        <InlinePrescriptionForm encounterId={encounterId} mode={mode} consultantId={consultantId}
          savedItems={allSavedItems} isLoading={isLoading} onSaved={invalidate} />
      )}

      {/* Saved items summary */}
      {(readOnly || prescriptions.length > 0) && (
        <div className="space-y-3 pt-2">
          {isLoading ? <p className="text-sm text-gray-400 text-center py-6">Loading…</p>
          : readOnly && prescriptions.length === 0 ? (
            <div className="bg-neutral-50 border border-neutral-200 rounded-lg px-4 py-3 text-xs text-neutral-800">No Prescription for this Encounter.</div>
          ) : (
            [...prescriptions]
              .sort((a, b) => {
                const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0
                const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0
                return timeB - timeA
              })
              .map((rx, i) => (
                <PrescriptionCard key={rx.id ?? i} rx={rx} />
              ))
          )}
        </div>
      )}
    </div>
  )
}

// ─── Prescription Card ────────────────────────────────────────────────────────

function PrescriptionCard({ rx }: { rx: PrescriptionResponse }) {
  return (
    <div className="border border-gray-200 rounded-xl bg-white overflow-hidden">
      <div className="px-4 py-2 bg-gray-50 border-b border-gray-100 flex items-center justify-between">
        <span className="text-xs text-gray-500">{formatDateTime(rx.createdAt)}</span>
        {rx.requestedByName && (
          <span className="text-xs text-neutral-600 font-medium">
            {rx.requestedByName.trim().toLowerCase().startsWith('dr')
              ? rx.requestedByName.trim()
              : `Dr. ${rx.requestedByName.trim()}`}
          </span>
        )}
      </div>
      <table className="w-full text-xs">
        <thead>
          <tr className="border-b border-gray-100">
            {['Drug', 'Freq.', 'Duration', 'Qty', 'Instruction', 'Route'].map(h => (
              <th key={h} className="px-3 py-1.5 text-left text-gray-500 font-medium">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-50">
          {rx.items.map((line, i) => (
            <tr key={line.id ?? i} className="hover:bg-gray-50">
              <td className="px-3 py-1.5 font-medium text-gray-900">{line.drugName}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.frequency}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.duration}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.qty}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.instructionLabel ?? '—'}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.routeLabel ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ─── Inline Form (OP) ────────────────────────────────────────────────────────

function InlinePrescriptionForm({ encounterId, mode = 'OP', consultantId, savedItems, isLoading: _parentLoading, onSaved }:
  { encounterId: string; mode?: 'OP' | 'IP'; consultantId?: string | undefined; savedItems: PrescriptionLinePayload[]; isLoading: boolean; onSaved: () => void }) {

  const [lines, setLines] = useState<PrescriptionLinePayload[]>([{ ...EMPTY_LINE }])
  const [drugQuery, setDrugQuery] = useState('')
  const [activeLine, setActiveLine] = useState(0)
  const [editingIndices, setEditingIndices] = useState<Set<number>>(new Set())
  const [expandedLines, setExpandedLines] = useState<Set<number>>(new Set())
  const toggleExpand = (idx: number) => setExpandedLines(prev => { const n = new Set(prev); n.has(idx) ? n.delete(idx) : n.add(idx); return n })

  const isUpdateMode = savedItems.length > 0
  const activeApi = mode === 'OP' ? opPrescriptionApi : ipPrescriptionApi

  const { data: drugResults = [] } = useQuery({
    queryKey: ['drug-search', drugQuery],
    queryFn:  () => drugSearchApi.search(drugQuery),
    enabled:  drugQuery.length >= 2,
  })
  const { data: instructions = [] } = useQuery({ queryKey: ['instructions'], queryFn: instructionApi.list })
  const { data: routes = [] } = useQuery({ queryKey: ['routes'], queryFn: routeApi.list })
  const { data: frequencies = [] } = useQuery({ queryKey: ['frequencies'], queryFn: frequencyApi.list })

  const saveMut = useMutation({
    mutationFn: () => activeApi.save(encounterId, {
      items: lines.filter(l => l.drugName).map(l => {
        const { originalSavedIndex, ...rest } = l as any
        return rest
      })
    }),
    onSuccess: () => { toast({ title: 'Prescription saved', variant: 'success' }); setLines([{ ...EMPTY_LINE }]); onSaved() },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const updateMut = useMutation({
    mutationFn: () => {
      const remaining = savedItems.filter((_, i) => !editingIndices.has(i))
      const newItems = lines.filter(l => l.drugName.trim()).map(l => {
        const { originalSavedIndex, ...rest } = l as any
        return rest
      })
      return activeApi.update(encounterId, { items: [...remaining, ...newItems] })
    },
    onSuccess: () => { toast({ title: 'Prescription updated', variant: 'success' }); setLines([{ ...EMPTY_LINE }]); setEditingIndices(new Set()); onSaved() },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const updateLine = (idx: number, patch: Partial<PrescriptionLinePayload>) =>
    setLines(ls => ls.map((l, i) => i === idx ? { ...l, ...patch } : l))
  const addLine = () => setLines(ls => [...ls, { ...EMPTY_LINE }])
  const removeLine = (idx: number) => {
    const lineToRemove = lines[idx] as any
    if (lineToRemove && typeof lineToRemove.originalSavedIndex === 'number') {
      setEditingIndices(prev => {
        const next = new Set(prev)
        next.delete(lineToRemove.originalSavedIndex)
        return next
      })
    }
    setLines(ls => {
      const filtered = ls.filter((_, i) => i !== idx)
      if (filtered.length === 0) {
        return [{ ...EMPTY_LINE }]
      }
      return filtered
    })
  }

  const editSavedItem = (savedIdx: number) => {
    const item = savedItems[savedIdx]
    setEditingIndices(prev => {
      const next = new Set(prev)
      next.add(savedIdx)
      return next
    })
    setLines(ls => [
      ...ls.filter(l => l.drugName.trim()),
      { ...item, originalSavedIndex: savedIdx } as any,
      { ...EMPTY_LINE }
    ])
  }

  const allExistingDrugIds = useMemo(() => {
    const savedIds = savedItems.filter((_, i) => !editingIndices.has(i)).map(s => s.drugItemId)
    return [...savedIds, ...lines.map(l => l.drugItemId)]
  }, [savedItems, editingIndices, lines])

  const handleQuickAddDrug = (drug: Partial<PrescriptionLinePayload> & { qty?: number }) => {
    if (drug.drugItemId && allExistingDrugIds.includes(drug.drugItemId)) {
      toast({ title: `${drug.drugName ?? 'Drug'} is already added`, description: 'Same drug cannot be prescribed twice.', variant: 'destructive' })
      return
    }
    setLines(ls => {
      const activeLines = ls.filter(l => l.drugName.trim())
      return [...activeLines, {
        ...EMPTY_LINE, drugItemId: drug.drugItemId ?? '', drugName: drug.drugName ?? '',
        frequency: drug.frequency ?? '', duration: drug.duration ?? '', qty: drug.qty ?? 1,
        instructionId: drug.instructionId ?? '', instructionLabel: drug.instructionLabel ?? '',
        routeId: drug.routeId ?? '', routeLabel: drug.routeLabel ?? '', remarks: drug.remarks ?? '',
      }]
    })
  }

  // Auto-calculate qty when freq/duration/sellingUnit/drugName change
  useEffect(() => {
    setLines(ls => ls.map(l => {
      if (!l.drugName.trim() || !l.frequency || !l.duration) {
        return { ...l, qty: 0 }
      }
      const newQty = calculateQty(l.frequency, l.duration, l.sellingUnit ?? '', l.drugName)
      return newQty > 0 ? { ...l, qty: newQty } : { ...l, qty: 0 }
    }))
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lines.map(l => `${l.frequency}|${l.duration}|${l.sellingUnit}|${l.drugName}`).join(',')])

  return (
    <div className="pt-2">
      <div className="flex gap-4">
        {/* Drug entry area */}
        <div className="flex-1 min-w-0 space-y-3">

          {/* ── Saved items (compact pill cards) ── */}
          {isUpdateMode && (
            <div className="space-y-1.5">
              <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-widest px-1">Saved Drugs</p>
              {savedItems.map((item, idx) => {
                if (editingIndices.has(idx)) return null
                return (
                  <div key={idx} className="flex items-center gap-2 bg-gray-50 border border-gray-200 rounded-xl px-3 py-2 group hover:bg-white hover:shadow-sm transition-all">
                    <div className="w-5 h-5 rounded-full bg-neutral-600 text-white flex items-center justify-center text-[9px] font-bold shrink-0">{idx + 1}</div>
                    <p className="flex-1 text-xs font-semibold text-gray-900 truncate">{item.drugName}</p>
                    <div className="flex items-center gap-1.5 flex-shrink-0">
                      {item.frequency && <span className="text-[10px] bg-neutral-600/10 text-neutral-600 border border-neutral-600/30 rounded-full px-2 py-0.5 font-medium">{item.frequency}</span>}
                      {item.duration  && <span className="text-[10px] bg-purple-50 text-purple-600 border border-purple-200 rounded-full px-2 py-0.5 font-medium">{item.duration}</span>}
                      {item.qty > 0   && <span className="text-[10px] bg-emerald-50 text-emerald-600 border border-emerald-200 rounded-full px-2 py-0.5 font-medium">Qty: {item.qty}</span>}
                      {item.instructionLabel && <span className="text-[10px] bg-amber-50 text-amber-600 border border-amber-200 rounded-full px-2 py-0.5 font-medium hidden sm:inline">{item.instructionLabel}</span>}
                    </div>
                    <button onClick={() => editSavedItem(idx)}
                      className="opacity-0 group-hover:opacity-100 text-neutral-400 hover:text-neutral-700 transition-all p-1 rounded-lg hover:bg-neutral-100" title="Edit">
                      <Pencil className="h-3 w-3" />
                    </button>
                  </div>
                )
              })}
            </div>
          )}

          {/* ── Section header top-right ── */}
          <div className="border border-gray-200 rounded-2xl bg-white overflow-visible">
            {isUpdateMode && (
              <div className="flex items-center justify-between px-4 pt-3 pb-0">
                <span className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">
                  Add More Drugs
                </span>
              </div>
            )}

          {/* ── Drug entry cards ── */}
          {lines.map((line, idx) => (
            <div key={idx} className="overflow-visible">

              {/* Card header: number + drug search */}
              <div className="flex items-center gap-3 px-4 pt-3 pb-2">
                <div className="w-6 h-6 rounded-full bg-neutral-600 text-white flex items-center justify-center text-[10px] font-bold shrink-0">
                  {(isUpdateMode ? savedItems.filter((_, i) => !editingIndices.has(i)).length : 0) + idx + 1}
                </div>

                {/* Drug search */}
                <div className="relative flex-1 flex items-center gap-2">
                  <div className="relative flex-1">
                    <svg className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400 pointer-events-none" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                    <input
                      value={line.drugName}
                      onChange={e => {
                        const val = e.target.value
                        if (!val.trim()) {
                          updateLine(idx, { ...EMPTY_LINE, drugName: '' })
                        } else {
                          updateLine(idx, { drugName: val, drugItemId: '' })
                        }
                        setDrugQuery(val)
                        setActiveLine(idx)
                      }}
                      placeholder="Search drug name (min. 2 chars)…"
                      className="w-full pl-8 pr-3 py-1.5 bg-gray-50 border border-gray-200 rounded-xl text-sm font-medium placeholder:text-gray-400 focus:outline-none focus:border-neutral-400 focus:bg-white focus:ring-2 focus:ring-neutral-100 transition-all"
                    />
                    {activeLine === idx && drugQuery.length >= 2 && drugResults.length > 0 && (
                      <ul className="absolute z-20 top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-xl shadow-lg max-h-44 overflow-y-auto divide-y divide-gray-50">
                        {drugResults.map((d: DrugItem) => {
                          const isDuplicate = allExistingDrugIds.some(id => id === d.id && id !== lines[idx]?.drugItemId)
                          return (
                            <li key={d.id}>
                              <button
                                className={cn(
                                  "w-full text-left px-3 py-2 text-xs transition-colors flex items-center justify-between gap-2",
                                  isDuplicate
                                    ? "opacity-50 cursor-not-allowed text-gray-400"
                                    : "hover:bg-[#C25727] hover:text-white text-gray-900"
                                )}
                                onClick={() => {
                                  if (isDuplicate) {
                                    toast({ title: `${d.name} is already added`, description: 'Same drug cannot be prescribed twice.', variant: 'destructive' })
                                    return
                                  }
                                  updateLine(idx, { drugItemId: d.id, drugName: d.name, sellingUnit: d.sellingUnit ?? '', currentStock: d.currentStock } as any)
                                  setDrugQuery('')
                                }}>
                                <div className="min-w-0 flex-1 truncate">
                                  <span className="font-semibold">{d.name}</span>
                                  {d.genericName && <span className="opacity-70"> · {d.genericName}</span>}
                                  {d.sellingUnit && <span className="ml-1.5 text-[10px] border border-current rounded px-1 opacity-60">{d.sellingUnit}</span>}
                                  {isDuplicate && <span className="ml-1 text-[10px] text-red-400 font-medium">(already added)</span>}
                                </div>
                                <div className="shrink-0">
                                  {typeof d.currentStock === 'number' && d.currentStock <= 0 && (
                                    <span className="text-[10px] font-bold text-black">
                                      No Stock
                                    </span>
                                  )}
                                </div>
                              </button>
                            </li>
                          )
                        })}
                      </ul>
                    )}
                  </div>
                  {typeof (line as any).currentStock === 'number' && (line as any).currentStock <= 0 && (
                    <span className="px-2 py-0.5 bg-neutral-100 text-black border border-neutral-300 rounded-full text-[10px] font-bold whitespace-nowrap shrink-0">
                      No Stock
                    </span>
                  )}
                </div>

                {(lines.length > 1 || typeof (line as any).originalSavedIndex === 'number') && (
                  <button type="button" onClick={() => removeLine(idx)}
                    className="text-gray-300 hover:text-red-400 transition-colors p-1 rounded-full hover:bg-red-50 shrink-0">
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                )}
              </div>

              {/* Divider */}
              <div className="mx-4 border-t border-gray-100" />

              {/* Fields row */}
              <div className="grid grid-cols-4 gap-3 px-4 py-3">
                <div className="space-y-1">
                  <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Frequency</label>
                  <CustomComboBox
                    value={line.frequency}
                    onChange={val => updateLine(idx, { frequency: val })}
                    options={frequencies.map((f: any) => ({ value: f.name, label: f.name }))}
                    placeholder="e.g. 1-0-1"
                    className="[&_input]:bg-gray-50 [&_input]:rounded-xl"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Duration</label>
                  <DurationComboBox
                    value={line.duration}
                    onChange={val => updateLine(idx, { duration: val })}
                    placeholder="e.g. 5 Days"
                    className="[&_input]:bg-gray-50 [&_input]:rounded-xl"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">QTY</label>
                  <input type="number" min="1" value={line.qty || ''}
                    onChange={e => updateLine(idx, { qty: parseInt(e.target.value) || 0 })}
                    className="w-full px-2.5 py-1 bg-gray-50 border border-gray-200 rounded-xl text-xs font-medium focus:outline-none focus:border-neutral-400 focus:bg-white focus:ring-2 focus:ring-neutral-100 transition-all" />
                </div>
                <div className="space-y-1">
                  <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Instruction</label>
                  <CustomComboBox
                    value={line.instructionLabel ?? ''}
                    placeholder="Select"
                    onChange={val => {
                      const ins = instructions.find(i => i.name === val)
                      updateLine(idx, { instructionId: ins?.id ?? '', instructionLabel: val })
                    }}
                    options={instructions.map(i => ({ value: i.name, label: i.name }))}
                    className="[&_input]:bg-gray-50 [&_input]:rounded-xl"
                  />
                </div>
              </div>

              {/* Accordion: Route & Remarks */}
              <div className="px-4 pb-3">
                <button type="button" onClick={() => toggleExpand(idx)}
                  className="flex items-center gap-1.5 text-[10px] font-semibold text-neutral-600 hover:text-neutral-500 uppercase tracking-wider transition-colors group">
                  <ChevronDown className={cn("h-3 w-3 transition-transform duration-200 text-neutral-600", expandedLines.has(idx) && "rotate-180")} />
                  Route & Precautions/Remarks
                </button>
                {expandedLines.has(idx) && (
                  <div className="grid grid-cols-2 gap-3 mt-2">
                    <div className="space-y-1">
                      <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Route</label>
                      <CustomComboBox
                        value={line.routeLabel ?? ''}
                        placeholder="Select Route"
                        onChange={val => {
                          const r = routes.find(rt => rt.name === val)
                          updateLine(idx, { routeId: r?.id ?? '', routeLabel: val })
                        }}
                        options={routes.map(r => ({ value: r.name, label: r.name }))}
                        className="[&_input]:bg-gray-50 [&_input]:rounded-xl"
                      />
                    </div>
                    <div className="space-y-1">
                      <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Precautions / Remarks</label>
                      <input value={line.remarks ?? ''}
                        onChange={e => updateLine(idx, { remarks: e.target.value })}
                        placeholder="Any notes…"
                        className="w-full px-2.5 py-1 bg-gray-50 border border-gray-200 rounded-xl text-xs focus:outline-none focus:border-neutral-400 focus:bg-white focus:ring-2 focus:ring-neutral-100 transition-all" />
                    </div>
                  </div>
                )}
              </div>
            </div>
          ))}
          </div>{/* end card wrapper */}

          {/* ── Footer actions ── */}
          <div className="flex items-center justify-between pt-2">
            <button onClick={addLine}
              className="flex items-center gap-1.5 text-xs font-semibold text-neutral-600 hover:text-neutral-500 bg-white border border-neutral-600 hover:border-neutral-500 rounded-xl px-4 py-2 transition-all">
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
              </svg>
              Add another drug
            </button>
            <button
              onClick={() => isUpdateMode ? updateMut.mutate() : saveMut.mutate()}
              disabled={(saveMut.isPending || updateMut.isPending) || (!lines.some(l => l.drugName.trim()) && editingIndices.size === 0)}
              className="flex items-center gap-2 px-5 py-2 text-xs font-bold text-white rounded-xl disabled:opacity-40 transition-all bg-neutral-600 hover:bg-neutral-700 shadow-sm hover:shadow-md">
              {(saveMut.isPending || updateMut.isPending) ? (
                <><svg className="w-3.5 h-3.5 animate-spin" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z"/></svg>{isUpdateMode ? 'Updating…' : 'Saving…'}</>
              ) : (
                <>{isUpdateMode ? '✓ UPDATE PRESCRIPTION' : '✓ SAVE PRESCRIPTION'}</>
              )}
            </button>
          </div>
        </div>

        {/* Quick-add panel */}
        <QuickAddPanel
          mode="DRUG"
          consultantId={consultantId}
          encounterId={encounterId}
          onAddDrug={handleQuickAddDrug}
        />
      </div>
    </div>
  )
}

// ─── IP Modal ────────────────────────────────────────────────────────────────

export function PrescriptionModal({ encounterId, consultantId, onClose, onSaved }:
  { encounterId: string; consultantId?: string | undefined; onClose: () => void; onSaved: () => void }) {

  const [lines, setLines] = useState<PrescriptionLinePayload[]>([{ ...EMPTY_LINE }])
  const [requestedById, setRequestedById] = useState(consultantId ?? '')
  const [drugQuery, setDrugQuery] = useState('')
  const [activeLine, setActiveLine] = useState(0)
  const [expandedLines, setExpandedLines] = useState<Set<number>>(new Set())
  const toggleExpand = (idx: number) => setExpandedLines(prev => { const n = new Set(prev); n.has(idx) ? n.delete(idx) : n.add(idx); return n })

  const { data: drugResults = [] } = useQuery({
    queryKey: ['drug-search', drugQuery],
    queryFn:  () => drugSearchApi.search(drugQuery),
    enabled:  drugQuery.length >= 2,
  })
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'], queryFn: consultantApi.getAll,
  })
  const { data: instructions = [] } = useQuery({
    queryKey: ['instructions'], queryFn: instructionApi.list,
  })
  const { data: routes = [] } = useQuery({
    queryKey: ['routes'], queryFn: routeApi.list,
  })
  const { data: frequencies = [] } = useQuery({
    queryKey: ['frequencies'], queryFn: frequencyApi.list,
  })

  const saveMut = useMutation({
    mutationFn: () => {
      const payload: PrescriptionPayload = {
        items: lines.filter(l => l.drugName),
      }
      if (requestedById) {
        payload.requestedById = requestedById
      }
      return ipPrescriptionApi.add(encounterId, payload)
    },
    onSuccess: () => {
      toast({ title: 'Prescription added', variant: 'success' })
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const updateLine = (idx: number, patch: Partial<PrescriptionLinePayload>) =>
    setLines(ls => ls.map((l, i) => i === idx ? { ...l, ...patch } : l))

  const addLine = () => setLines(ls => [...ls, { ...EMPTY_LINE }])

  const handleQuickAddDrug = (drug: Partial<PrescriptionLinePayload> & { qty?: number }) => {
    setLines(ls => [...ls.filter(l => l.drugName), {
      ...EMPTY_LINE,
      drugItemId:      drug.drugItemId ?? '',
      drugName:        drug.drugName ?? '',
      frequency:       drug.frequency ?? '',
      duration:        drug.duration ?? '',
      qty:             drug.qty ?? 1,
      instructionId:   drug.instructionId ?? '',
      instructionLabel: drug.instructionLabel ?? '',
      routeId:         drug.routeId ?? '',
      routeLabel:      drug.routeLabel ?? '',
      remarks:         drug.remarks ?? '',
    }])
  }
  // Auto-calculate qty when freq/duration/sellingUnit/drugName change
  useEffect(() => {
    setLines(ls => ls.map(l => {
      if (!l.drugName.trim() || !l.frequency || !l.duration) {
        return { ...l, qty: 0 }
      }
      const newQty = calculateQty(l.frequency, l.duration, l.sellingUnit ?? '', l.drugName)
      return newQty > 0 ? { ...l, qty: newQty } : { ...l, qty: 0 }
    }))
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lines.map(l => `${l.frequency}|${l.duration}|${l.sellingUnit}|${l.drugName}`).join(',')])

  return (
   <div className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200" style={{ marginTop: 0 }} >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl my-8 flex flex-col max-h-[90vh] overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h3 className="text-base font-bold text-gray-900">Add Prescription</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>

        <div className="p-6 flex gap-6 flex-1 min-h-0 overflow-hidden">
          {/* Drug lines */}
          <div className="flex-1 min-w-0 flex flex-col gap-3">
            {/* Requested By */}
            <div className="flex items-center gap-3">
              <label className="text-xs font-medium text-gray-600 shrink-0">Requested By</label>
              <ConsultantSearchInput
                consultants={consultants}
                value={requestedById}
                onChange={setRequestedById}
                className="flex-1"
                size="sm"
              />
            </div>

            {/* Drug entries */}
            <div className="flex-1 min-h-0 overflow-y-auto pr-2 space-y-3">
              <div className="border border-gray-200 rounded-2xl bg-white overflow-visible">
                {lines.map((line, idx) => (
                  <div key={idx} className="overflow-visible">
                    {idx > 0 && <div className="border-t border-gray-100" />}
                    
                    {/* Card header: number + drug search */}
                    <div className="flex items-center gap-3 px-4 pt-3 pb-2">
                      <div className="w-6 h-6 rounded-full bg-neutral-600 text-white flex items-center justify-center text-[10px] font-bold shrink-0">
                        {idx + 1}
                      </div>

                      {/* Drug search */}
                      <div className="relative flex-1">
                        <svg className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400 pointer-events-none" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                        <input
                          value={line.drugName}
                          onChange={e => {
                            const val = e.target.value
                            if (!val.trim()) {
                              updateLine(idx, { ...EMPTY_LINE, drugName: '' })
                            } else {
                              updateLine(idx, { drugName: val, drugItemId: '' })
                            }
                            setDrugQuery(val)
                            setActiveLine(idx)
                          }}
                          placeholder="Search drug name (min. 2 chars)…"
                          className="w-full pl-8 pr-3 py-1.5 bg-gray-50 border border-gray-200 rounded-xl text-sm font-medium placeholder:text-gray-400 focus:outline-none focus:border-neutral-400 focus:bg-white focus:ring-2 focus:ring-neutral-100 transition-all"
                        />
                        {activeLine === idx && drugQuery.length >= 2 && drugResults.length > 0 && (
                          <ul className="absolute z-20 top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-xl shadow-lg max-h-44 overflow-y-auto divide-y divide-gray-50">
                            {drugResults.map((d: DrugItem) => {
                              const isDuplicate = lines.some((l, i) => i !== idx && l.drugItemId === d.id)
                              return (
                                <li key={d.id}>
                                  <button
                                    className={cn(
                                      "w-full text-left px-3 py-2 text-xs transition-colors",
                                      isDuplicate
                                        ? "opacity-50 cursor-not-allowed text-gray-400"
                                        : "hover:bg-[#C25727] hover:text-white text-gray-900"
                                    )}
                                    onClick={() => {
                                      if (isDuplicate) {
                                        toast({ title: `${d.name} is already added`, description: 'Same drug cannot be prescribed twice.', variant: 'destructive' })
                                        return
                                      }
                                      updateLine(idx, {
                                        drugItemId: d.id,
                                        drugName: d.name,
                                        sellingUnit: d.sellingUnit ?? ''
                                      })
                                      setDrugQuery('')
                                    }}>
                                    <span className="font-semibold">{d.name}</span>
                                    {d.genericName && <span className="opacity-70"> · {d.genericName}</span>}
                                    {d.sellingUnit && <span className="ml-1.5 text-[10px] border border-current rounded px-1 opacity-60">{d.sellingUnit}</span>}
                                    {isDuplicate && <span className="ml-1 text-[10px] text-red-400 font-medium">(already added)</span>}
                                  </button>
                                </li>
                              )
                            })}
                          </ul>
                        )}
                      </div>

                      {lines.length > 1 && (
                        <button
                          type="button"
                          onClick={() => setLines(ls => ls.filter((_, i) => i !== idx))}
                          className="text-gray-300 hover:text-red-400 transition-colors p-1 rounded-full hover:bg-red-50 shrink-0"
                        >
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                          </svg>
                        </button>
                      )}
                    </div>

                    {/* Divider */}
                    <div className="mx-4 border-t border-gray-100" />

                    {/* Fields row */}
                    <div className="grid grid-cols-4 gap-3 px-4 py-3">
                      <div className="space-y-1">
                        <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Frequency</label>
                        <CustomComboBox
                          value={line.frequency}
                          onChange={val => updateLine(idx, { frequency: val })}
                          options={frequencies.map((f: any) => ({ value: f.name, label: f.name }))}
                          placeholder="e.g. 1-0-1"
                          className="[&_input]:bg-gray-50 [&_input]:rounded-xl"
                        />
                      </div>
                      <div className="space-y-1">
                        <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Duration</label>
                        <DurationComboBox
                          value={line.duration}
                          onChange={val => updateLine(idx, { duration: val })}
                          placeholder="e.g. 5 Days"
                          className="[&_input]:bg-gray-50 [&_input]:rounded-xl"
                        />
                      </div>
                      <div className="space-y-1">
                        <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">QTY</label>
                        <input type="number" min="1" value={line.qty || ''}
                          onChange={e => updateLine(idx, { qty: parseInt(e.target.value) || 0 })}
                          className="w-full px-2.5 py-1 bg-gray-50 border border-gray-200 rounded-xl text-xs font-medium focus:outline-none focus:border-neutral-400 focus:bg-white focus:ring-2 focus:ring-neutral-100 transition-all" />
                      </div>
                      <div className="space-y-1">
                        <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Instruction</label>
                        <CustomComboBox
                          value={line.instructionLabel ?? ''}
                          placeholder="Select"
                          onChange={val => {
                            const ins = instructions.find(i => i.name === val)
                            updateLine(idx, { instructionId: ins?.id ?? '', instructionLabel: val })
                          }}
                          options={instructions.map(i => ({ value: i.name, label: i.name }))}
                          className="[&_input]:bg-gray-50 [&_input]:rounded-xl"
                        />
                      </div>
                    </div>

                    {/* Accordion toggle for Route & Remarks */}
                    <div className="px-4 pb-3">
                      <button type="button" onClick={() => toggleExpand(idx)}
                        className="flex items-center gap-1.5 text-[10px] font-semibold text-neutral-600 hover:text-neutral-500 uppercase tracking-wider transition-colors group">
                        <ChevronDown className={cn("h-3 w-3 transition-transform duration-200 text-neutral-600", expandedLines.has(idx) && "rotate-180")} />
                        Route & Precautions/Remarks
                      </button>
                      {expandedLines.has(idx) && (
                        <div className="grid grid-cols-2 gap-3 mt-2">
                          <div className="space-y-1">
                            <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Route</label>
                            <CustomComboBox
                              value={line.routeLabel ?? ''}
                              placeholder="Select Route"
                              onChange={val => {
                                const r = routes.find(rt => rt.name === val)
                                updateLine(idx, { routeId: r?.id ?? '', routeLabel: val })
                              }}
                              options={routes.map(r => ({ value: r.name, label: r.name }))}
                              className="[&_input]:bg-gray-50 [&_input]:rounded-xl"
                            />
                          </div>
                          <div className="space-y-1">
                            <label className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Precautions / Remarks</label>
                            <input value={line.remarks ?? ''}
                              onChange={e => updateLine(idx, { remarks: e.target.value })}
                              placeholder="Any notes…"
                              className="w-full px-2.5 py-1 bg-gray-50 border border-gray-200 rounded-xl text-xs focus:outline-none focus:border-neutral-400 focus:bg-white focus:ring-2 focus:ring-neutral-100 transition-all" />
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              {/* Footer actions inside left column */}
              <div className="pt-2">
                <button onClick={addLine}
                  className="flex items-center gap-1.5 text-xs font-semibold text-neutral-600 hover:text-neutral-500 bg-white border border-neutral-600 hover:border-neutral-500 rounded-xl px-4 py-2 transition-all">
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M12 4v16m8-8H4" />
                  </svg>
                  Add another drug
                </button>
              </div>
            </div>
          </div>

          {/* Quick-add panel */}
          <QuickAddPanel
            mode="DRUG"
            consultantId={consultantId}
            encounterId={encounterId}
            onAddDrug={handleQuickAddDrug}
          />
        </div>

        <div className="flex justify-end gap-2 px-6 py-4 border-t border-gray-200">
          <button onClick={onClose}
            className="px-5 py-2 text-xs font-bold text-neutral-600 hover:text-neutral-500 bg-white border border-neutral-600 hover:border-neutral-500 rounded-xl transition-all">
            CANCEL
          </button>
          <button
            onClick={() => saveMut.mutate()}
            disabled={saveMut.isPending || !lines.some(l => l.drugName)}
            className="flex items-center gap-2 px-5 py-2 text-xs font-bold text-white rounded-xl disabled:opacity-40 transition-all bg-neutral-600 hover:bg-neutral-700 shadow-sm hover:shadow-md">
            {saveMut.isPending ? 'Saving…' : 'ADD PRESCRIPTION'}
          </button>
        </div>
      </div>
    </div>
  )
}
