import { useState } from 'react'
import { cn } from '../../../lib/utils'
import type { FieldRequest, FieldType, FieldOption } from '../../../types/casesheet'

interface Props {
  field: FieldRequest
  index: number
  onChange: (updated: FieldRequest) => void
  onRemove: () => void
  onMoveUp: () => void
  onMoveDown: () => void
  isFirst: boolean
  isLast: boolean
}

const FIELD_TYPES: { value: FieldType; label: string }[] = [
  { value: 'HEADING',         label: 'Section Heading' },
  { value: 'TEXT',            label: 'Text (single line)' },
  { value: 'TEXTAREA',        label: 'Textarea (multi-line)' },
  { value: 'NUMBER',          label: 'Number' },
  { value: 'DATE',            label: 'Date' },
  { value: 'SELECT',          label: 'Dropdown (single select)' },
  { value: 'RADIO',           label: 'Radio buttons' },
  { value: 'CHECKBOX',        label: 'Checkbox (yes/no)' },
  { value: 'MULTI_SELECT',    label: 'Multi-select (pills)' },
  { value: 'ROM_GRID',        label: 'ROM Grid (Ortho)' },
  { value: 'FUNCTIONAL_SCORE',label: 'Functional Scores (Ortho)' },
  { value: 'IMPLANT_LOG',     label: 'Implant Log (Ortho)' },
  { value: 'PREOP_CHECKLIST', label: 'Pre-op Checklist (Ortho)' },
]

const needsOptions = (ft: FieldType) => ['SELECT', 'RADIO', 'MULTI_SELECT'].includes(ft)

export function FieldEditor({ field, index, onChange, onRemove, onMoveUp, onMoveDown, isFirst, isLast }: Props) {
  const [expanded, setExpanded] = useState(index === 0)
  const [optionInput, setOptionInput] = useState('')

  const upd = (key: keyof FieldRequest, val: unknown) => onChange({ ...field, [key]: val })

  const addOption = () => {
    const text = optionInput.trim()
    if (!text) return
    const value = text.toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_]/g, '')
    const opts: FieldOption[] = [...(field.options ?? []), { value, label: text }]
    onChange({ ...field, options: opts })
    setOptionInput('')
  }

  const removeOption = (i: number) => {
    const opts = (field.options ?? []).filter((_, idx) => idx !== i)
    onChange({ ...field, options: opts })
  }

  const inputCls = 'w-full px-3 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500'

  const isHeading   = field.fieldType === 'HEADING'
  const isSpecial   = ['ROM_GRID', 'FUNCTIONAL_SCORE', 'IMPLANT_LOG', 'PREOP_CHECKLIST'].includes(field.fieldType)

  return (
    <div className={cn(
      'border rounded-xl transition-all',
      isHeading ? 'border-neutral-200 bg-neutral-50/40' : 'border-gray-200 bg-white'
    )}>
      {/* Row header */}
      <div
        className="flex items-center gap-2 px-3 py-2.5 cursor-pointer select-none"
        onClick={() => setExpanded(e => !e)}
      >
        {/* Drag handle / order */}
        <div className="flex flex-col gap-0.5 shrink-0">
          <button
            type="button"
            onClick={e => { e.stopPropagation(); onMoveUp() }}
            disabled={isFirst}
            className="text-gray-300 hover:text-gray-500 disabled:opacity-20 leading-none text-xs"
          >▲</button>
          <button
            type="button"
            onClick={e => { e.stopPropagation(); onMoveDown() }}
            disabled={isLast}
            className="text-gray-300 hover:text-gray-500 disabled:opacity-20 leading-none text-xs"
          >▼</button>
        </div>

        <div className="flex-1 min-w-0">
          <span className={cn('text-xs font-semibold', isHeading ? 'text-neutral-700' : 'text-gray-800')}>
            {field.label || <span className="text-gray-400 italic">Untitled field</span>}
          </span>
          {!isHeading && (
            <span className="ml-2 text-xs text-gray-400">{field.fieldType}</span>
          )}
          {field.required && <span className="ml-1 text-red-400 text-xs">*</span>}
        </div>

        <span className="text-gray-400 text-xs">{expanded ? '▾' : '▸'}</span>

        <button
          type="button"
          onClick={e => { e.stopPropagation(); onRemove() }}
          className="text-red-300 hover:text-red-500 text-sm px-1 transition-colors"
          title="Remove field"
        >✕</button>
      </div>

      {/* Expanded editor */}
      {expanded && (
        <div className="px-4 pb-4 pt-0 grid grid-cols-2 gap-3 border-t border-gray-100">

          {/* Field key */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Field Key <span className="text-red-400">*</span></label>
            <input value={field.fieldKey} onChange={e => upd('fieldKey', e.target.value)}
              placeholder="e.g. chief_complaint" className={inputCls} />
            <p className="text-xs text-gray-400 mt-0.5">Machine key, no spaces (use underscores)</p>
          </div>

          {/* Label */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Label <span className="text-red-400">*</span></label>
            <input value={field.label} onChange={e => upd('label', e.target.value)}
              placeholder="e.g. Chief Complaint" className={inputCls} />
          </div>

          {/* Field type */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Field Type</label>
            <select value={field.fieldType} onChange={e => upd('fieldType', e.target.value as FieldType)} className={inputCls}>
              {FIELD_TYPES.map(ft => (
                <option key={ft.value} value={ft.value}>{ft.label}</option>
              ))}
            </select>
          </div>

          {/* Section */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">Section</label>
            <input value={field.section ?? ''} onChange={e => upd('section', e.target.value || null)}
              placeholder="e.g. Presenting Complaint" className={inputCls} />
          </div>

          {/* Required + Visible */}
          {!isHeading && !isSpecial && (
            <div className="col-span-2 flex gap-6">
              <label className="flex items-center gap-2 text-sm cursor-pointer">
                <input type="checkbox" checked={field.required}
                  onChange={e => upd('required', e.target.checked)}
                  className="accent-neutral-600 w-4 h-4" />
                <span className="text-xs font-medium text-gray-700">Required</span>
              </label>
              <label className="flex items-center gap-2 text-sm cursor-pointer">
                <input type="checkbox" checked={field.visible}
                  onChange={e => upd('visible', e.target.checked)}
                  className="accent-neutral-600 w-4 h-4" />
                <span className="text-xs font-medium text-gray-700">Visible</span>
              </label>
            </div>
          )}

          {/* Grid Layout Option for HEADING */}
          {isHeading && (
            <div className="col-span-2 mt-1">
              <label className="flex items-center gap-2 text-sm cursor-pointer">
                <input
                  type="checkbox"
                  checked={!!(field.validation && field.validation.grid)}
                  onChange={e => upd('validation', { ...(field.validation || {}), grid: e.target.checked })}
                  className="accent-neutral-600 w-4 h-4"
                />
                <span className="text-xs font-medium text-gray-700">Render fields in this section as side-by-side grid</span>
              </label>
              <p className="text-xs text-gray-400 mt-0.5 ml-6">If checked, fields in this section will be laid out in two columns. If unchecked, they will appear one below the other.</p>
            </div>
          )}

          {/* Placeholder + Help */}
          {!isHeading && !isSpecial && (
            <>
              <div>
                <label className="block text-xs font-semibold text-gray-600 mb-1">Placeholder</label>
                <input value={field.placeholder ?? ''} onChange={e => upd('placeholder', e.target.value || null)}
                  placeholder="e.g. Describe the complaint…" className={inputCls} />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-600 mb-1">Help text</label>
                <input value={field.helpText ?? ''} onChange={e => upd('helpText', e.target.value || null)}
                  placeholder="Shown below the field" className={inputCls} />
              </div>
            </>
          )}

          {/* NUMBER validation */}
          {field.fieldType === 'NUMBER' && (
            <div className="col-span-2 flex gap-4">
              <div className="flex-1">
                <label className="block text-xs font-semibold text-gray-600 mb-1">Min value</label>
                <input type="number" className={inputCls}
                  value={(field.validation?.min as number) ?? ''}
                  onChange={e => upd('validation', { ...field.validation, min: e.target.value ? Number(e.target.value) : undefined })} />
              </div>
              <div className="flex-1">
                <label className="block text-xs font-semibold text-gray-600 mb-1">Max value</label>
                <input type="number" className={inputCls}
                  value={(field.validation?.max as number) ?? ''}
                  onChange={e => upd('validation', { ...field.validation, max: e.target.value ? Number(e.target.value) : undefined })} />
              </div>
            </div>
          )}

          {/* Options editor */}
          {needsOptions(field.fieldType as FieldType) && (
            <div className="col-span-2">
              <label className="block text-xs font-semibold text-gray-600 mb-1">Options</label>
              <div className="space-y-1 mb-2">
                {(field.options ?? []).map((opt, i) => (
                  <div key={i} className="flex items-center gap-2">
                    <input value={opt.value} onChange={e => {
                      const opts = [...(field.options ?? [])]
                      opts[i] = { ...opts[i], value: e.target.value }
                      upd('options', opts)
                    }} placeholder="value" className="flex-1 px-2 py-1 border border-gray-200 rounded text-xs font-mono focus:outline-none focus:ring-1 focus:ring-neutral-400" />
                    <input value={opt.label} onChange={e => {
                      const opts = [...(field.options ?? [])]
                      opts[i] = { ...opts[i], label: e.target.value }
                      upd('options', opts)
                    }} placeholder="Label shown to user" className="flex-1 px-2 py-1 border border-gray-200 rounded text-xs focus:outline-none focus:ring-1 focus:ring-neutral-400" />
                    <button type="button" onClick={() => removeOption(i)}
                      className="text-red-300 hover:text-red-500 text-sm">✕</button>
                  </div>
                ))}
              </div>
              <div className="flex gap-2">
                <input
                  value={optionInput}
                  onChange={e => setOptionInput(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), addOption())}
                  placeholder="Type option label and press Enter or Add"
                  className="flex-1 px-2 py-1.5 border border-gray-300 rounded-lg text-xs focus:outline-none focus:ring-1 focus:ring-neutral-400"
                />
                <button type="button" onClick={addOption}
                  className="px-3 py-1.5 bg-neutral-50 text-neutral-700 text-xs font-semibold rounded-lg hover:bg-neutral-100 transition-colors">
                  Add
                </button>
              </div>
            </div>
          )}

          {/* Special field info */}
          {isSpecial && (
            <div className="col-span-2 bg-blue-50 border border-blue-100 rounded-lg px-3 py-2 text-xs text-blue-700">
              This is a specialised Ortho field. The frontend renderer handles its internal structure automatically — no options or validation needed.
            </div>
          )}
        </div>
      )}
    </div>
  )
}
