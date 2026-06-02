import { Controller, useFormContext } from 'react-hook-form'
import { cn } from '../../../lib/utils'
import type { RomRow } from '../../../types/casesheet'

interface Props {
  fieldKey: string
  readOnly?: boolean
}

const JOINTS = [
  { id: 'cervical',  label: 'Cervical Spine' },
  { id: 'lumbar',    label: 'Lumbar Spine' },
  { id: 'shoulder_r',label: 'Shoulder (R)' },
  { id: 'shoulder_l',label: 'Shoulder (L)' },
  { id: 'elbow_r',   label: 'Elbow (R)' },
  { id: 'elbow_l',   label: 'Elbow (L)' },
  { id: 'wrist_r',   label: 'Wrist (R)' },
  { id: 'wrist_l',   label: 'Wrist (L)' },
  { id: 'hip_r',     label: 'Hip (R)' },
  { id: 'hip_l',     label: 'Hip (L)' },
  { id: 'knee_r',    label: 'Knee (R)' },
  { id: 'knee_l',    label: 'Knee (L)' },
  { id: 'ankle_r',   label: 'Ankle (R)' },
  { id: 'ankle_l',   label: 'Ankle (L)' },
]

const COLS: { key: keyof RomRow; label: string; width: string }[] = [
  { key: 'active_flexion',   label: 'Flex°',   width: 'w-16' },
  { key: 'active_extension', label: 'Ext°',    width: 'w-16' },
  { key: 'active_abduction', label: 'Abd°',    width: 'w-16' },
  { key: 'active_adduction', label: 'Add°',    width: 'w-16' },
  { key: 'active_ir',        label: 'IR°',     width: 'w-14' },
  { key: 'active_er',        label: 'ER°',     width: 'w-14' },
  { key: 'notes',            label: 'Notes',   width: 'w-40' },
]

const EMPTY_ROW = (): RomRow => ({
  joint: '', active_flexion: '', active_extension: '',
  active_abduction: '', active_adduction: '',
  active_ir: '', active_er: '', notes: '',
})

/**
 * ROM_GRID field renderer.
 * Displays a compact table with one row per joint.
 * Only joints with at least one non-empty cell are included in saved data.
 * Stored as: { [fieldKey]: RomRow[] }
 */
export function RomGridField({ fieldKey, readOnly }: Props) {
  const { control } = useFormContext()

  return (
    <Controller name={fieldKey} control={control}
      defaultValue={[]}
      render={({ field }) => {
        // Ensure every joint has a row object
        const rows: RomRow[] = JOINTS.map(j => {
          const existing = (field.value as RomRow[] ?? []).find(r => r.joint === j.id)
          return existing ?? { ...EMPTY_ROW(), joint: j.id }
        })

        const update = (jointId: string, col: keyof RomRow, val: string) => {
          const next = rows.map(r =>
            r.joint === jointId ? { ...r, [col]: val } : r
          )
          // Only keep rows that have at least one value
          field.onChange(next.filter(r =>
            COLS.some(c => c.key !== 'joint' && (r[c.key] ?? '').toString().trim() !== '')
          ))
        }

        const cellCls = cn(
          'border border-gray-200 px-1 py-1 text-xs text-center focus:outline-none focus:ring-1 focus:ring-blue-400 rounded',
          readOnly ? 'bg-gray-50 cursor-not-allowed' : 'bg-white'
        )

        return (
          <div className="overflow-x-auto">
            <table className="min-w-full text-xs border-collapse">
              <thead>
                <tr className="bg-blue-50">
                  <th className="text-left px-2 py-1.5 text-xs font-semibold text-gray-600 border border-gray-200 w-32">Joint</th>
                  {COLS.map(c => (
                    <th key={c.key} className={cn('px-1 py-1.5 text-xs font-semibold text-gray-600 border border-gray-200', c.width)}>
                      {c.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {JOINTS.map(j => {
                  const row = rows.find(r => r.joint === j.id) ?? { ...EMPTY_ROW(), joint: j.id }
                  const hasData = COLS.some(c => c.key !== 'joint' && (row[c.key] ?? '').toString().trim() !== '')
                  return (
                    <tr key={j.id} className={cn(hasData ? 'bg-blue-50/40' : 'hover:bg-gray-50')}>
                      <td className="px-2 py-1 border border-gray-200 text-xs font-medium text-gray-700 whitespace-nowrap">
                        {j.label}
                      </td>
                      {COLS.map(c => (
                        <td key={c.key} className="border border-gray-200 p-0.5">
                          {c.key === 'notes' ? (
                            <input type="text" disabled={readOnly} value={row.notes ?? ''}
                              onChange={e => update(j.id, 'notes', e.target.value)}
                              className={cn(cellCls, 'w-full text-left px-2')} />
                          ) : (
                            <input type="number" min={0} max={360} disabled={readOnly}
                              value={(row[c.key] ?? '') as string}
                              onChange={e => update(j.id, c.key, e.target.value)}
                              className={cn(cellCls, 'w-full')} />
                          )}
                        </td>
                      ))}
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <p className="text-xs text-gray-400 mt-1">
              Enter active range of motion in degrees. Leave blank if joint not examined.
            </p>
          </div>
        )
      }}
    />
  )
}
