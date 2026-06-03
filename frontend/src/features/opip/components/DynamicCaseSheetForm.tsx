import { useEffect } from 'react'
import { useForm, Controller, FormProvider } from 'react-hook-form'
import { cn } from '../../../lib/utils'
import { RomGridField }          from './RomGridField'
import { FunctionalScoreField }  from './FunctionalScoreField'
import { ImplantLogField }       from './ImplantLogField'
import { PreopChecklistField }   from './PreopChecklistField'
import type { CaseSheetTemplateDetail, CaseSheetData, FieldResponse } from '../../../types/casesheet'

interface Props {
  template: CaseSheetTemplateDetail
  initialData?: CaseSheetData
  onSave: (data: CaseSheetData) => void
  isSaving?: boolean
  readOnly?: boolean
}

/**
 * DynamicCaseSheetForm
 *
 * Renders any case sheet template as a live clinical form.
 * Field types: TEXT | TEXTAREA | NUMBER | SELECT | MULTI_SELECT | DATE |
 *              CHECKBOX | RADIO | HEADING | ROM_GRID | FUNCTIONAL_SCORE |
 *              IMPLANT_LOG | PREOP_CHECKLIST
 *
 * Specialised renderers are used for the Ortho-specific field types above.
 * All form state is managed by react-hook-form; FormProvider is used so
 * child components (ROM grid, implant log, etc.) can call useFormContext().
 */
export function DynamicCaseSheetForm({ template, initialData, onSave, isSaving, readOnly }: Props) {
  const methods = useForm<CaseSheetData>({ defaultValues: initialData ?? {} })
  const { register, control, handleSubmit, reset } = methods

  useEffect(() => {
    if (initialData) reset(initialData)
  }, [initialData, reset])

  const inputCls = cn(
    'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-colors',
    readOnly && 'bg-gray-50 cursor-not-allowed'
  )
  const labelCls = 'block text-xs font-semibold text-gray-700 mb-1'

  // Group fields into sections
  interface SectionGroup {
    title: string | null
    fields: FieldResponse[]
    isGrid?: boolean
  }
  const sections: SectionGroup[] = []
  
  const hasHeadings = template.fields.some(f => f.fieldType === 'HEADING')
  
  if (hasHeadings) {
    let currentGroup: SectionGroup = { title: null, fields: [], isGrid: false }
    for (const f of template.fields) {
      if (f.fieldType === 'HEADING') {
        if (currentGroup.fields.length > 0 || currentGroup.title !== null) {
          sections.push(currentGroup)
        }
        currentGroup = {
          title: f.label,
          fields: [],
          isGrid: f.validation?.['grid'] === true
        }
      } else {
        currentGroup.fields.push(f)
      }
    }
    if (currentGroup.fields.length > 0 || currentGroup.title !== null) {
      sections.push(currentGroup)
    }
  } else {
    const sectionMap = new Map<string, FieldResponse[]>()
    for (const f of template.fields) {
      const key = f.section ?? '__root__'
      if (!sectionMap.has(key)) sectionMap.set(key, [])
      sectionMap.get(key)!.push(f)
    }
    Array.from(sectionMap.entries()).forEach(([title, fields]) => {
      sections.push({
        title: title === '__root__' ? null : title,
        fields,
        isGrid: false,
      })
    })
  }

  // Determine column span based on field type
  const colSpan = (ft: string) => {
    switch (ft) {
      case 'TEXTAREA':
      case 'MULTI_SELECT':
      case 'ROM_GRID':
      case 'FUNCTIONAL_SCORE':
      case 'IMPLANT_LOG':
      case 'PREOP_CHECKLIST':
      case 'HEADING':
        return 'col-span-full'
      default:
        return 'col-span-1'
    }
  }

  const renderField = (field: FieldResponse) => {
    const { fieldKey, label, fieldType, placeholder, required, options, helpText } = field
    const id = `field_${fieldKey}`

    // ── Section heading ──────────────────────────────────────────────────────
    if (fieldType === 'HEADING') {
      return (
        <div key={fieldKey} className="col-span-full pt-1">
          <h4 className="text-sm font-bold text-blue-700 border-b border-blue-100 pb-1 uppercase tracking-wide">
            {label}
          </h4>
        </div>
      )
    }

    const wrap = (children: React.ReactNode) => (
      <div key={fieldKey} className={colSpan(fieldType)}>
        {fieldType !== 'CHECKBOX' && (
          <label htmlFor={id} className={labelCls}>
            {label}
            {required && <span className="text-red-500 ml-0.5">*</span>}
          </label>
        )}
        {children}
        {helpText && <p className="text-xs text-gray-400 mt-0.5 italic">{helpText}</p>}
      </div>
    )

    switch (fieldType) {

      // ── Standard field types ───────────────────────────────────────────────
      case 'TEXTAREA':
        return wrap(
          <textarea id={id} rows={3} placeholder={placeholder ?? ''} disabled={readOnly}
            className={cn(inputCls, 'resize-none')} {...register(fieldKey)} />
        )

      case 'NUMBER':
        return wrap(
          <input id={id} type="number" placeholder={placeholder ?? ''} disabled={readOnly}
            className={inputCls} {...register(fieldKey, { valueAsNumber: true })} />
        )

      case 'DATE':
        return wrap(
          <input id={id} type="date" disabled={readOnly}
            className={inputCls} {...register(fieldKey)} />
        )

      case 'SELECT':
        return wrap(
          <select id={id} disabled={readOnly} className={inputCls} {...register(fieldKey)}>
            <option value="">— Select —</option>
            {(options ?? []).map(o => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        )

      case 'RADIO':
        return wrap(
          <div className="flex flex-wrap gap-3 mt-1">
            {(options ?? []).map(o => (
              <label key={o.value}
                className="flex items-center gap-1.5 text-sm cursor-pointer select-none">
                <input type="radio" value={o.value} disabled={readOnly}
                  className="accent-blue-600" {...register(fieldKey)} />
                {o.label}
              </label>
            ))}
          </div>
        )

      case 'CHECKBOX':
        return (
          <div key={fieldKey} className={colSpan(fieldType)}>
            <label className="flex items-center gap-2 text-sm cursor-pointer select-none mt-1">
              <input type="checkbox" disabled={readOnly}
                className="accent-blue-600 w-4 h-4" {...register(fieldKey)} />
              <span className={labelCls.replace('mb-1', '')}>{label}</span>
              {required && <span className="text-red-500">*</span>}
            </label>
            {helpText && <p className="text-xs text-gray-400 mt-0.5 italic">{helpText}</p>}
          </div>
        )

      case 'MULTI_SELECT':
        return wrap(
          <Controller name={fieldKey} control={control}
            render={({ field }) => {
              const selected: string[] = Array.isArray(field.value)
                ? (field.value as string[]) : []
              return (
                <div className="flex flex-wrap gap-2 mt-1">
                  {(options ?? []).map(o => {
                    const on = selected.includes(o.value)
                    return (
                      <label key={o.value}
                        className={cn(
                          'flex items-center gap-1.5 px-3 py-1.5 rounded-full border text-xs font-medium transition-colors',
                          on
                            ? 'bg-blue-100 border-blue-400 text-blue-800'
                            : 'bg-gray-50 border-gray-200 text-gray-700 hover:border-gray-400',
                          readOnly ? 'cursor-not-allowed' : 'cursor-pointer'
                        )}>
                        <input type="checkbox" className="hidden" disabled={readOnly}
                          checked={on}
                          onChange={() => {
                            if (readOnly) return
                            field.onChange(on
                              ? selected.filter(v => v !== o.value)
                              : [...selected, o.value])
                          }} />
                        {o.label}
                      </label>
                    )
                  })}
                </div>
              )
            }} />
        )

      // ── Specialised Ortho field types ──────────────────────────────────────
      case 'ROM_GRID':
        return wrap(<RomGridField fieldKey={fieldKey} readOnly={readOnly} />)

      case 'FUNCTIONAL_SCORE':
        return wrap(<FunctionalScoreField fieldKey={fieldKey} readOnly={readOnly} />)

      case 'IMPLANT_LOG':
        return wrap(<ImplantLogField fieldKey={fieldKey} readOnly={readOnly} />)

      case 'PREOP_CHECKLIST':
        return wrap(<PreopChecklistField fieldKey={fieldKey} readOnly={readOnly} />)

      default: // TEXT
        return wrap(
          <input id={id} type="text" placeholder={placeholder ?? ''} disabled={readOnly}
            className={inputCls} {...register(fieldKey)} />
        )
    }
  }

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(onSave)} noValidate>
        <div className="space-y-5">
          {sections.map((sec, idx) => (
            <div key={idx} className="bg-white border border-gray-200 rounded-xl p-5 shadow-sm">
              {sec.title && (
                <h3 className="text-sm font-bold text-gray-800 mb-4 uppercase tracking-wide flex items-center gap-2">
                  <span className="w-1 h-4 bg-blue-500 rounded-full inline-block" />
                  {sec.title}
                </h3>
              )}
              <div className={sec.isGrid ? "grid grid-cols-1 md:grid-cols-2 gap-4" : "grid grid-cols-1 gap-4"}>
                {sec.fields.map(renderField)}
              </div>
            </div>
          ))}
        </div>

        {!readOnly && (
          <div className="mt-5 flex items-center gap-3 sticky bottom-0 bg-white/90 backdrop-blur-sm border-t border-gray-100 pt-3 pb-2 -mx-1 px-1">
            <button type="submit" disabled={isSaving}
              className="px-6 py-2.5 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors shadow-sm">
              {isSaving ? (
                <span className="flex items-center gap-2">
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Saving…
                </span>
              ) : 'Save Case Sheet'}
            </button>
            <p className="text-xs text-gray-400">Changes are saved to this encounter's record</p>
          </div>
        )}
      </form>
    </FormProvider>
  )
}
