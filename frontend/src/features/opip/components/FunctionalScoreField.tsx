import { Controller, useFormContext } from 'react-hook-form'
import { cn } from '../../../lib/utils'
import DatePicker from '../../../components/shared/DatePicker'
import type { FunctionalScoreEntry } from '../../../types/casesheet'

interface Props {
  fieldKey: string
  readOnly?: boolean
}

const SCORE_TYPES = [
  // Knee
  { value: 'oxford_knee',     label: 'Oxford Knee Score (OKS)',          range: '0–48',  direction: 'higher = better' },
  { value: 'koos',            label: 'KOOS (subscale or total)',          range: '0–100', direction: 'higher = better' },
  { value: 'kss_functional',  label: 'KSS — Functional Score',           range: '0–100', direction: 'higher = better' },
  { value: 'kss_knee',        label: 'KSS — Knee Score',                 range: '0–100', direction: 'higher = better' },
  // Hip
  { value: 'oxford_hip',      label: 'Oxford Hip Score (OHS)',           range: '0–48',  direction: 'higher = better' },
  { value: 'harris_hip',      label: 'Harris Hip Score (HHS)',           range: '0–100', direction: 'higher = better' },
  { value: 'womac',           label: 'WOMAC (total)',                    range: '0–96',  direction: 'lower = better' },
  // Shoulder
  { value: 'constant_murley', label: 'Constant–Murley Score',           range: '0–100', direction: 'higher = better' },
  { value: 'dash',            label: 'DASH Score',                      range: '0–100', direction: 'lower = better' },
  { value: 'ases',            label: 'ASES Score',                      range: '0–100', direction: 'higher = better' },
  // Spine
  { value: 'odi',             label: 'Oswestry Disability Index (ODI)',  range: '0–100%', direction: 'lower = better' },
  { value: 'vas_back',        label: 'VAS — Back Pain',                 range: '0–10',  direction: 'lower = better' },
  { value: 'vas_leg',         label: 'VAS — Leg Pain / Radiculopathy',  range: '0–10',  direction: 'lower = better' },
  { value: 'ndii',            label: 'NDI — Neck Disability Index',     range: '0–50',  direction: 'lower = better' },
  // General
  { value: 'eq5d',            label: 'EQ-5D Index Score',               range: '–0.59–1', direction: 'higher = better' },
]

const EMPTY_ENTRY = (): FunctionalScoreEntry => ({
  scoreType: '', value: '', date: new Date().toISOString().split('T')[0], notes: '',
})

/**
 * FUNCTIONAL_SCORE field renderer.
 * Lets the doctor add multiple validated outcome scores in one field.
 * Stored as: { [fieldKey]: FunctionalScoreEntry[] }
 */
export function FunctionalScoreField({ fieldKey, readOnly }: Props) {
  const { control } = useFormContext()

  return (
    <Controller name={fieldKey} control={control} defaultValue={[]}
      render={({ field }) => {
        const entries: FunctionalScoreEntry[] = Array.isArray(field.value)
          ? field.value as FunctionalScoreEntry[]
          : []

        const update = (idx: number, key: keyof FunctionalScoreEntry, val: string) => {
          const next = entries.map((e, i) => i === idx ? { ...e, [key]: val } : e)
          field.onChange(next)
        }
        const add = () => field.onChange([...entries, EMPTY_ENTRY()])
        const remove = (idx: number) => field.onChange(entries.filter((_, i) => i !== idx))

        const inputCls = cn(
          'px-2 py-1.5 border border-gray-300 rounded-lg text-xs focus:outline-none focus:ring-1 focus:ring-neutral-500',
          readOnly && 'bg-gray-50 cursor-not-allowed'
        )

        return (
          <div className="space-y-3">
            {entries.map((e, idx) => {
              const meta = SCORE_TYPES.find(s => s.value === e.scoreType)
              return (
                <div key={idx} className="grid grid-cols-12 gap-2 items-start bg-neutral-50/40 border border-neutral-100 rounded-lg p-3">
                  {/* Score type */}
                  <div className="col-span-4">
                    <label className="block text-xs font-medium text-gray-600 mb-0.5">Score Type</label>
                    <select value={e.scoreType} disabled={readOnly}
                      onChange={ev => update(idx, 'scoreType', ev.target.value)}
                      className={cn(inputCls, 'w-full')}>
                      <option value="">— Select —</option>
                      {SCORE_TYPES.map(s => (
                        <option key={s.value} value={s.value}>{s.label}</option>
                      ))}
                    </select>
                    {meta && (
                      <p className="text-xs text-gray-400 mt-0.5">
                        Range: {meta.range} · {meta.direction}
                      </p>
                    )}
                  </div>
                  {/* Score value */}
                  <div className="col-span-2">
                    <label className="block text-xs font-medium text-gray-600 mb-0.5">Score</label>
                    <input type="text" placeholder={meta?.range ?? 'Value'} disabled={readOnly}
                      value={e.value} onChange={ev => update(idx, 'value', ev.target.value)}
                      className={cn(inputCls, 'w-full')} />
                  </div>
                  {/* Date */}
                  <div className="col-span-2">
                    <label className="block text-xs font-medium text-gray-600 mb-0.5">Date</label>
                    <DatePicker value={e.date} onChange={val => update(idx, 'date', val || '')} disabled={readOnly} size="sm" />
                  </div>
                  {/* Notes */}
                  <div className="col-span-3">
                    <label className="block text-xs font-medium text-gray-600 mb-0.5">Notes</label>
                    <input type="text" placeholder="Optional notes…" disabled={readOnly}
                      value={e.notes} onChange={ev => update(idx, 'notes', ev.target.value)}
                      className={cn(inputCls, 'w-full')} />
                  </div>
                  {/* Remove */}
                  {!readOnly && (
                    <div className="col-span-1 flex items-end pb-1">
                      <button type="button" onClick={() => remove(idx)}
                        className="text-red-400 hover:text-red-600 text-lg leading-none" title="Remove">×</button>
                    </div>
                  )}
                </div>
              )
            })}

            {!readOnly && (
              <button type="button" onClick={add}
                className="flex items-center gap-1.5 text-xs font-semibold text-neutral-600 hover:text-neutral-800 transition-colors">
                <span className="text-base leading-none">+</span> Add Score
              </button>
            )}
            {entries.length === 0 && readOnly && (
              <p className="text-xs text-gray-400 italic">No functional scores recorded.</p>
            )}
          </div>
        )
      }}
    />
  )
}
