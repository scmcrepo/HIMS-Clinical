import { useState, useRef } from 'react'
import { cn } from '../../../lib/utils'
import { UploadCloud, Trash2 } from 'lucide-react'
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
  { value: 'IMAGE_EDITOR',    label: 'Image' },
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
  const imageInputRef = useRef<HTMLInputElement>(null)

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
  const isImage     = field.fieldType === 'IMAGE_EDITOR'

  // ── Image upload handlers for template builder ──────────────────────────
  const templateImages: string[] = (isImage && field.validation?.images as string[]) || []

  const handleImageUpload = (files: FileList) => {
    const maxSize = 5 * 1024 * 1024
    const accepted = Array.from(files).filter(f => f.type.startsWith('image/') && f.size <= maxSize)
    const promises = accepted.map(file =>
      new Promise<string>(resolve => {
        const reader = new FileReader()
        reader.onload = () => {
          const img = new Image()
          img.onload = () => {
            const MAX_W = 800
            let w = img.width, h = img.height
            if (w > MAX_W) { h = Math.round((h * MAX_W) / w); w = MAX_W }
            const canvas = document.createElement('canvas')
            canvas.width = w; canvas.height = h
            canvas.getContext('2d')!.drawImage(img, 0, 0, w, h)
            resolve(canvas.toDataURL('image/jpeg', 0.8))
          }
          img.src = reader.result as string
        }
        reader.readAsDataURL(file)
      })
    )
    Promise.all(promises).then(newImgs => {
      upd('validation', { ...(field.validation || {}), images: [...templateImages, ...newImgs] })
    })
  }

  const removeTemplateImage = (idx: number) => {
    const updated = templateImages.filter((_, i) => i !== idx)
    upd('validation', { ...(field.validation || {}), images: updated })
  }

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

          {/* Image upload zone — shown when field type is Image */}
          {isImage && (
            <div className="col-span-2 space-y-4">
              <label className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-1">
                Configure Template Images
              </label>
              
              {/* Upload clickable area */}
              <div
                onClick={() => imageInputRef.current?.click()}
                onDrop={e => { e.preventDefault(); e.stopPropagation(); handleImageUpload(e.dataTransfer.files) }}
                onDragOver={e => { e.preventDefault(); e.stopPropagation() }}
                className={cn(
                  "relative border-2 border-dashed border-gray-300 rounded-xl p-8 text-center cursor-pointer",
                  "bg-slate-50/50 hover:bg-indigo-50/30 hover:border-indigo-400 transition-all duration-300 ease-in-out group shadow-sm"
                )}
              >
                <div className="flex flex-col items-center justify-center space-y-3">
                  <div className="p-3 bg-white rounded-full shadow-sm border border-gray-100 group-hover:scale-110 group-hover:border-indigo-200 transition-all duration-300">
                    <UploadCloud className="w-8 h-8 text-gray-400 group-hover:text-indigo-600 transition-colors" />
                  </div>
                  
                  <div className="space-y-1">
                    <p className="text-sm text-gray-700 font-semibold group-hover:text-indigo-700 transition-colors">
                      Drag & drop or <span className="text-indigo-600 underline">click to upload</span> template diagrams
                    </p>
                    <p className="text-xs text-gray-400">
                      These will serve as default background diagrams for doctors to annotate.
                    </p>
                    <p className="text-[10px] text-gray-400 font-medium bg-gray-100 px-2 py-0.5 rounded-full inline-block mt-1">
                      JPG, PNG, WebP • Max 5 MB each
                    </p>
                  </div>
                </div>

                <input
                  ref={imageInputRef}
                  type="file"
                  accept="image/*"
                  multiple
                  className="hidden"
                  onChange={e => { if (e.target.files) handleImageUpload(e.target.files); e.target.value = '' }}
                />
              </div>

              {/* Thumbnail gallery */}
              {templateImages.length > 0 && (
                <div className="space-y-2">
                  <span className="text-xs font-semibold text-gray-500">
                    Uploaded Images ({templateImages.length})
                  </span>
                  <div className="flex flex-wrap gap-3">
                    {templateImages.map((src, i) => (
                      <div 
                        key={i} 
                        className="relative group rounded-xl overflow-hidden border border-gray-200 shadow-sm bg-gray-50 flex items-center justify-center w-28 h-20 shrink-0"
                      >
                        <img 
                          src={src} 
                          alt={`Image ${i + 1}`} 
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" 
                        />
                        
                        {/* Hover Overlay */}
                        <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex items-center justify-center gap-1.5 backdrop-blur-[1px]">
                          <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); removeTemplateImage(i) }}
                            className="p-1.5 bg-red-600 hover:bg-red-700 text-white rounded-lg shadow transition-colors"
                            title="Delete template image"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>

                        {/* Image Counter Tag */}
                        <div className="absolute bottom-1 left-1 bg-black/60 px-1.5 py-0.5 rounded text-[9px] text-white font-medium">
                          #{i + 1}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Placeholder + Help */}
          {!isHeading && !isSpecial && !isImage && (
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
