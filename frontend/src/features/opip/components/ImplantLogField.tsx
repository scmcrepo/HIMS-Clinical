import { Controller, useFormContext } from 'react-hook-form'
import { cn } from '../../../lib/utils'
import type { ImplantEntry } from '../../../types/casesheet'

interface Props {
  fieldKey: string
  readOnly?: boolean
}

const COMPONENTS = [
  'Femoral stem', 'Acetabular cup', 'Femoral head', 'Acetabular liner',
  'Femoral component (TKR)', 'Tibial component (TKR)', 'Tibial insert (TKR)',
  'Patella button', 'Humeral stem', 'Glenoid component',
  'Intramedullary nail', 'Proximal locking screw', 'Distal locking screw',
  'Dynamic hip screw (plate)', 'Lag screw', 'Blade plate',
  'Cortical screw', 'Cancellous screw', 'Washer',
  'Locking plate', 'Reconstruction plate', 'Iliosacral screw',
  'Pedicle screw', 'Rod', 'Cage / interbody device',
  'Bone cement (batch)', 'Other',
]

const EMPTY_ENTRY = (): ImplantEntry => ({
  component: '', name: '', manufacturer: '', batchLot: '', size: '', notes: '',
})

/**
 * IMPLANT_LOG field renderer.
 * Records each implant component with medico-legal traceability fields.
 * Stored as: { [fieldKey]: ImplantEntry[] }
 */
export function ImplantLogField({ fieldKey, readOnly }: Props) {
  const { control } = useFormContext()

  return (
    <Controller name={fieldKey} control={control} defaultValue={[]}
      render={({ field }) => {
        const entries: ImplantEntry[] = Array.isArray(field.value)
          ? field.value as ImplantEntry[]
          : []

        const update = (idx: number, key: keyof ImplantEntry, val: string) => {
          field.onChange(entries.map((e, i) => i === idx ? { ...e, [key]: val } : e))
        }
        const add = () => field.onChange([...entries, EMPTY_ENTRY()])
        const remove = (idx: number) => field.onChange(entries.filter((_, i) => i !== idx))

        const inputCls = cn(
          'w-full px-2 py-1.5 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500',
          readOnly && 'bg-gray-50 cursor-not-allowed'
        )

        return (
          <div className="space-y-2">
            {/* Header row */}
            {entries.length > 0 && (
              <div className="grid grid-cols-12 gap-1.5 px-1">
                {['Component', 'Implant Name', 'Manufacturer', 'Batch / Lot No.', 'Size', 'Notes', ''].map((h, i) => (
                  <div key={i} className={cn(
                    'text-xs font-semibold text-gray-500 uppercase tracking-wide',
                    i === 0 ? 'col-span-2' : i === 1 ? 'col-span-2' : i === 2 ? 'col-span-2'
                    : i === 3 ? 'col-span-2' : i === 4 ? 'col-span-1' : i === 5 ? 'col-span-2' : 'col-span-1'
                  )}>
                    {h}
                  </div>
                ))}
              </div>
            )}

            {entries.map((e, idx) => (
              <div key={idx} className="grid grid-cols-12 gap-1.5 items-center bg-gray-50 border border-gray-200 rounded-lg p-2">
                {/* Component */}
                <div className="col-span-2">
                  <select value={e.component} disabled={readOnly}
                    onChange={ev => update(idx, 'component', ev.target.value)}
                    className={inputCls}>
                    <option value="">— Select —</option>
                    {COMPONENTS.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                {/* Implant name */}
                <div className="col-span-2">
                  <input type="text" placeholder="e.g. Corail stem" disabled={readOnly}
                    value={e.name} onChange={ev => update(idx, 'name', ev.target.value)}
                    className={inputCls} />
                </div>
                {/* Manufacturer */}
                <div className="col-span-2">
                  <input type="text" placeholder="e.g. DePuy Synthes" disabled={readOnly}
                    value={e.manufacturer} onChange={ev => update(idx, 'manufacturer', ev.target.value)}
                    className={inputCls} />
                </div>
                {/* Batch / Lot */}
                <div className="col-span-2">
                  <input type="text" placeholder="Batch / Lot no." disabled={readOnly}
                    value={e.batchLot} onChange={ev => update(idx, 'batchLot', ev.target.value)}
                    className={cn(inputCls, 'font-mono')} />
                </div>
                {/* Size */}
                <div className="col-span-1">
                  <input type="text" placeholder="Size" disabled={readOnly}
                    value={e.size} onChange={ev => update(idx, 'size', ev.target.value)}
                    className={inputCls} />
                </div>
                {/* Notes */}
                <div className="col-span-2">
                  <input type="text" placeholder="Notes…" disabled={readOnly}
                    value={e.notes} onChange={ev => update(idx, 'notes', ev.target.value)}
                    className={inputCls} />
                </div>
                {/* Remove */}
                {!readOnly && (
                  <div className="col-span-1 flex justify-center">
                    <button type="button" onClick={() => remove(idx)}
                      className="text-red-400 hover:text-red-600 text-lg leading-none" title="Remove row">×</button>
                  </div>
                )}
              </div>
            ))}

            {!readOnly && (
              <button type="button" onClick={add}
                className="flex items-center gap-1.5 text-xs font-semibold text-blue-600 hover:text-blue-800 transition-colors">
                <span className="text-base leading-none">+</span> Add Implant Component
              </button>
            )}
            {entries.length === 0 && readOnly && (
              <p className="text-xs text-gray-400 italic">No implants recorded.</p>
            )}
            {!readOnly && entries.length > 0 && (
              <p className="text-xs text-amber-600 mt-1">
                ⚠ Batch/Lot numbers are medico-legal records — verify against sticker labels.
              </p>
            )}
          </div>
        )
      }}
    />
  )
}
