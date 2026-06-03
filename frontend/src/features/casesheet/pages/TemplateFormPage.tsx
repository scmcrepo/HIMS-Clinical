import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { templateApi } from '../../../services/casesheet/casesheetApi'
import { departmentApi } from '../../../services/config/departmentApi'
import { FieldEditor } from '../components/FieldEditor'
import BackButton from '../../../components/shared/BackButton'
import { toast } from '../../../hooks/useToast'
import type { FieldRequest, FieldType, CaseSheetVisitType } from '../../../types/casesheet'

const EMPTY_FIELD = (order: number): FieldRequest => ({
  fieldKey: '', label: '', fieldType: 'TEXT', section: null,
  displayOrder: order, required: false, placeholder: null,
  helpText: null, options: null, validation: null, defaultValue: null, visible: true,
})

const QUICK_ADD: { label: string; fields: Partial<FieldRequest>[] }[] = [
  {
    label: '+ Presenting Complaint section',
    fields: [
      { fieldKey: 'sec_pc', label: 'Presenting Complaint', fieldType: 'HEADING', section: 'Presenting Complaint' },
      { fieldKey: 'chief_complaint', label: 'Chief Complaint', fieldType: 'TEXTAREA', section: 'Presenting Complaint', required: true, placeholder: 'Describe the primary complaint…' },
      { fieldKey: 'pain_score', label: 'Pain Score (VAS 0–10)', fieldType: 'NUMBER', section: 'Presenting Complaint', validation: { min: 0, max: 10 }, helpText: 'Visual Analogue Scale' },
    ]
  },
  {
    label: '+ History section',
    fields: [
      { fieldKey: 'sec_history', label: 'History', fieldType: 'HEADING', section: 'History' },
      { fieldKey: 'hopi', label: 'History of Present Illness', fieldType: 'TEXTAREA', section: 'History' },
      { fieldKey: 'drug_history', label: 'Current Medications', fieldType: 'TEXTAREA', section: 'History' },
      { fieldKey: 'allergy', label: 'Allergies', fieldType: 'TEXT', section: 'History', placeholder: 'Drug / food / metal allergies…' },
    ]
  },
  {
    label: '+ Diagnosis & Plan section',
    fields: [
      { fieldKey: 'sec_plan', label: 'Assessment & Plan', fieldType: 'HEADING', section: 'Assessment & Plan' },
      { fieldKey: 'diagnosis', label: 'Diagnosis', fieldType: 'TEXTAREA', section: 'Assessment & Plan', required: true },
      { fieldKey: 'treatment_plan', label: 'Treatment Plan', fieldType: 'TEXTAREA', section: 'Assessment & Plan' },
      { fieldKey: 'follow_up_date', label: 'Follow-up Date', fieldType: 'DATE', section: 'Assessment & Plan' },
    ]
  },
  {
    label: '+ Ortho ROM Grid',
    fields: [
      { fieldKey: 'rom', label: 'Range of Motion', fieldType: 'ROM_GRID', section: 'Clinical Examination', helpText: 'Record active range in degrees. Leave blank if not applicable.' },
    ]
  },
  {
    label: '+ Ortho Functional Scores',
    fields: [
      { fieldKey: 'functional_scores', label: 'Functional Outcome Scores', fieldType: 'FUNCTIONAL_SCORE', section: 'Functional Scores', helpText: 'Oxford Knee, Harris Hip, ODI, DASH etc.' },
    ]
  },
  {
    label: '+ Ortho Implant Log',
    fields: [
      { fieldKey: 'implant_log', label: 'Implant Log', fieldType: 'IMPLANT_LOG', section: 'Surgical Details', helpText: 'Record each component with manufacturer, batch/lot and size.' },
    ]
  },
  {
    label: '+ Pre-op Checklist',
    fields: [
      { fieldKey: 'preop_checklist', label: 'Pre-operative Checklist', fieldType: 'PREOP_CHECKLIST', section: 'Pre-operative Checklist', required: true, helpText: 'All items must be confirmed before theatre.' },
    ]
  },
]

export default function TemplateFormPage() {
  const { templateId } = useParams<{ templateId?: string }>()
  const isEdit   = !!templateId && templateId !== 'new'
  const navigate  = useNavigate()
  const qc        = useQueryClient()

  // Form state
  const [name,          setName]          = useState('')
  const [specialization,setSpecialization]= useState('')
  const [visitType,     setVisitType]     = useState<CaseSheetVisitType>('OP')
  const [description,   setDescription]  = useState('')
  const [isDefault,     setIsDefault]     = useState(false)
  const [fields,        setFields]        = useState<FieldRequest[]>([])

  // Load existing template for edit mode
  const { data: existing, isLoading: loadingExisting } = useQuery({
    queryKey: ['casesheet-template', templateId],
    queryFn:  () => templateApi.getById(templateId!),
    enabled:  isEdit,
  })

  // Load departments
  const { data: departments = [] } = useQuery({
    queryKey: ['departments'],
    queryFn: () => departmentApi.getAll(),
  })

  const clinicalDepartments = departments.filter(d => 
    d.departmentType?.toLowerCase() === 'clinical' && 
    (d.status === 1 || String(d.status).toUpperCase() === 'ACTIVE')
  )

  useEffect(() => {
    if (existing) {
      setName(existing.name)
      setSpecialization(existing.specialization)
      setVisitType(existing.visitType)
      setDescription(existing.description ?? '')
      setIsDefault(existing.defaultTemplate)
      setFields(existing.fields.map(f => ({
        fieldKey:     f.fieldKey,
        label:        f.label,
        fieldType:    f.fieldType,
        section:      f.section,
        displayOrder: f.displayOrder,
        required:     f.required,
        placeholder:  f.placeholder,
        helpText:     f.helpText,
        options:      f.options,
        validation:   f.validation,
        defaultValue: f.defaultValue,
        visible:      f.visible,
      })))
    }
  }, [existing])

  const createMut = useMutation({
    mutationFn: () => templateApi.create({ name, specialization, visitType, description, defaultTemplate: isDefault, fields }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casesheet-templates'] })
      toast({ title: 'Template created', variant: 'success' })
      navigate('/admin/casesheet-templates')
    },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const updateMut = useMutation({
    mutationFn: () => templateApi.update(templateId!, { name, specialization, visitType, description, defaultTemplate: isDefault, fields }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['casesheet-templates'] })
      qc.invalidateQueries({ queryKey: ['casesheet-template', templateId] })
      toast({ title: 'Template updated', variant: 'success' })
      navigate('/admin/casesheet-templates')
    },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const isSaving = createMut.isPending || updateMut.isPending

  const addField = () => {
    const order = fields.length > 0 ? Math.max(...fields.map(f => f.displayOrder)) + 10 : 10
    setFields(f => [...f, EMPTY_FIELD(order)])
  }

  const quickAdd = (preset: typeof QUICK_ADD[0]) => {
    const base = fields.length > 0 ? Math.max(...fields.map(f => f.displayOrder)) + 10 : 10
    const newFields = preset.fields.map((pf, i) => ({
      ...EMPTY_FIELD(base + i * 10),
      ...pf,
      displayOrder: base + i * 10,
      options:      pf.options ?? null,
      validation:   pf.validation ?? null,
      visible:      pf.visible ?? true,
    } as FieldRequest))
    setFields(f => [...f, ...newFields])
  }

  const removeField = (i: number) => setFields(f => f.filter((_, idx) => idx !== i))

  const moveField = (i: number, dir: 'up' | 'down') => {
    setFields(prev => {
      const next = [...prev]
      const swap = dir === 'up' ? i - 1 : i + 1
      if (swap < 0 || swap >= next.length) return prev
      ;[next[i], next[swap]] = [next[swap], next[i]]
      return next.map((f, idx) => ({ ...f, displayOrder: (idx + 1) * 10 }))
    })
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) { toast({ title: 'Template name is required', variant: 'destructive' }); return }
    if (!specialization) { toast({ title: 'Department is required', variant: 'destructive' }); return }
    if (fields.some(f => !f.fieldKey.trim() || !f.label.trim())) {
      toast({ title: 'All fields need a key and label', variant: 'destructive' }); return
    }
    if (isEdit) updateMut.mutate(); else createMut.mutate()
  }

  const inputCls = 'w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500'
  const labelCls = 'block text-xs font-semibold text-gray-700 mb-1'

  if (isEdit && loadingExisting) return <div className="p-6 text-sm text-gray-500">Loading template…</div>

  return (
    <form onSubmit={handleSubmit} className="space-y-6 max-w-4xl">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">
            {isEdit ? 'Edit Template' : 'New Case Sheet Template'}
          </h2>
          <p className="text-sm text-gray-500 mt-0.5">Define the form layout for a department</p>
        </div>
        <BackButton />
      </div>

      {/* Template metadata */}
      <div className="bg-white border border-gray-200 rounded-xl p-5">
        <h3 className="text-sm font-bold text-gray-800 mb-4">Template Details</h3>
        <div className="grid grid-cols-2 gap-4">
          <div className="col-span-2">
            <label className={labelCls}>Template Name <span className="text-red-500">*</span></label>
            <input value={name} onChange={e => setName(e.target.value)}
              placeholder="e.g. Orthopaedics OP Default" className={inputCls} required />
          </div>
          <div>
            <label className={labelCls}>Department <span className="text-red-500">*</span></label>
            <select value={specialization} onChange={e => setSpecialization(e.target.value)}
              className={inputCls} required>
              <option value="">Select Department</option>
              {clinicalDepartments.map(d => (
                <option key={d.id} value={d.name.toUpperCase()}>
                  {d.name}
                </option>
              ))}
            </select>
            <p className="text-xs text-gray-400 mt-0.5">Select the clinical department for this template</p>
          </div>
          <div>
            <label className={labelCls}>Visit Type <span className="text-red-500">*</span></label>
            <select value={visitType} onChange={e => setVisitType(e.target.value as CaseSheetVisitType)} className={inputCls}>
              <option value="OP">OP — Outpatient</option>
              <option value="IP">IP — Inpatient</option>
              <option value="BOTH">Both</option>
            </select>
          </div>
          <div className="col-span-2">
            <label className={labelCls}>Description</label>
            <input value={description} onChange={e => setDescription(e.target.value)}
              placeholder="Optional description…" className={inputCls} />
          </div>
          <div className="col-span-2">
            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={isDefault} onChange={e => setIsDefault(e.target.checked)}
                className="accent-blue-600 w-4 h-4" />
              <span className="text-sm font-medium text-gray-700">Set as default template for this department + visit type</span>
            </label>
            <p className="text-xs text-gray-400 mt-0.5 ml-6">Setting this will automatically demote the existing default template</p>
          </div>
        </div>
      </div>

      {/* Quick-add presets */}
      <div className="bg-blue-50 border border-blue-100 rounded-xl p-4">
        <p className="text-xs font-bold text-blue-700 mb-3 uppercase tracking-wide">Quick-add sections</p>
        <div className="flex flex-wrap gap-2">
          {QUICK_ADD.map(preset => (
            <button key={preset.label} type="button" onClick={() => quickAdd(preset)}
              className="px-3 py-1.5 text-xs font-semibold bg-white border border-blue-200 text-blue-700 rounded-lg hover:bg-blue-100 transition-colors">
              {preset.label}
            </button>
          ))}
        </div>
      </div>

      {/* Fields */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold text-gray-800">
            Fields <span className="text-gray-400 font-normal">({fields.length})</span>
          </h3>
          <button type="button" onClick={addField}
            className="px-3 py-1.5 bg-gray-100 text-gray-700 text-xs font-semibold rounded-lg hover:bg-gray-200 transition-colors">
            + Add blank field
          </button>
        </div>

        {fields.length === 0 ? (
          <div className="border border-dashed border-gray-200 rounded-xl p-6 text-center text-sm text-gray-400">
            No fields yet. Use Quick-add sections above or click "+ Add blank field".
          </div>
        ) : (
          <div className="space-y-2">
            {fields.map((field, i) => (
              <FieldEditor
                key={`${i}-${field.fieldKey}`}
                field={field}
                index={i}
                onChange={updated => setFields(f => f.map((x, idx) => idx === i ? updated : x))}
                onRemove={() => removeField(i)}
                onMoveUp={() => moveField(i, 'up')}
                onMoveDown={() => moveField(i, 'down')}
                isFirst={i === 0}
                isLast={i === fields.length - 1}
              />
            ))}
          </div>
        )}
      </div>

      {/* Save */}
      <div className="sticky bottom-0 bg-white/90 backdrop-blur-sm border-t border-gray-100 pt-3 pb-2 flex gap-3 items-center">
        <button type="submit" disabled={isSaving}
          className="px-6 py-2.5 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
          {isSaving ? 'Saving…' : isEdit ? 'Update Template' : 'Create Template'}
        </button>
        <button type="button" onClick={() => navigate('/admin/casesheet-templates')}
          className="px-4 py-2.5 text-sm text-gray-600 hover:text-gray-900 transition-colors">
          Cancel
        </button>
        <span className="text-xs text-gray-400">{fields.length} field{fields.length !== 1 ? 's' : ''} defined</span>
      </div>
    </form>
  )
}
