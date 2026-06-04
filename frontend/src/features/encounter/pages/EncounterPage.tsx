import { useState } from 'react'
import { useParams, Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useEncounter, useEncounterMutations } from '../../../hooks/encounter/useEncounter'
import { formatDateTime } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import BackButton from '../../../components/shared/BackButton'
import type { EncounterStatus, VisitMode } from '../../../types/encounter'
import { userApi } from '../../../services/user/userApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { encounterApi, type CreateEncounterCmd } from '../../../services/encounter/encounterApi'
import { usePatient } from '../../../hooks/patient/usePatient'
import { toast } from '../../../hooks/useToast'

const STATUS_STYLES: Record<EncounterStatus, string> = {
  CHECKED_IN: 'bg-blue-50 text-blue-700 border-blue-200',
  CONSULTATION_STARTED: 'bg-purple-50 text-purple-700 border-purple-200',
  CASESHEET_RECORDED: 'bg-amber-50 text-amber-700 border-amber-200',
  BILLING_DONE: 'bg-green-50 text-green-700 border-green-200',
}

const STATUS_LABELS: Record<EncounterStatus, string> = {
  CHECKED_IN: 'Checked In',
  CONSULTATION_STARTED: 'In Consultation',
  CASESHEET_RECORDED: 'Casesheet Done',
  BILLING_DONE: 'Billing Done',
}

const vitalsSchema = z.object({
  bloodPressureSystolic: z.coerce.number().int().min(60).max(250).optional(),
  bloodPressureDiastolic: z.coerce.number().int().min(40).max(150).optional(),
  pulseRate: z.coerce.number().int().min(30).max(250).optional(),
  temperature: z.coerce.number().min(35).max(42).optional(),
  oxygenSaturation: z.coerce.number().min(70).max(100).optional(),
  weight: z.coerce.number().min(0.5).max(300).optional(),
  height: z.coerce.number().min(30).max(250).optional(),
  respiratoryRate: z.coerce.number().int().min(8).max(60).optional(),
})
type VitalsValues = z.infer<typeof vitalsSchema>

const casesheetSchema = z.object({
  chiefComplaint: z.string().min(1, 'Required'),
  historyOfPresentIllness: z.string().optional(),
  examination: z.string().optional(),
  diagnosis: z.string().optional(),
  plan: z.string().optional(),
})
type CasesheetValues = z.infer<typeof casesheetSchema>

const newEncounterSchema = z.object({
  primaryProviderId: z.string().min(1, 'Required'),
  encounterType: z.enum(['OUTPATIENT', 'INPATIENT']),
  visitMode: z.enum(['WALK_IN', 'APPOINTMENT', 'TELE_CONSULT']).optional()
})
type NewEncounterValues = z.infer<typeof newEncounterSchema>

export default function EncounterPage() {
  const { encounterId } = useParams<{ encounterId: string }>()
  const isNew = encounterId === 'new'
  const [searchParams] = useSearchParams()
  const patientId = searchParams.get('patientId')
  const navigate = useNavigate()

  // For viewing existing encounter
  const { data: encounter, isLoading, error } = useEncounter(isNew ? undefined : encounterId)
  const mutations = useEncounterMutations(encounterId ?? '')
  const [activeTab, setActiveTab] = useState<'vitals' | 'casesheet' | 'discharge'>('vitals')
  const [dischargeNotes, setDischargeNotes] = useState('')

  const vitalsForm = useForm<VitalsValues>({ resolver: zodResolver(vitalsSchema) })
  const casesheetForm = useForm<CasesheetValues>({ resolver: zodResolver(casesheetSchema) })

  // For creating new encounter
  const { data: patient, isLoading: patientLoading } = usePatient(isNew ? (patientId ?? undefined) : undefined)
  const { data: users, isLoading: usersLoading } = useQuery({
    queryKey: ['users'],
    queryFn: userApi.getAll
  })

  const { data: consultants } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll
  })

  const doctors = users?.filter(u => u.roles.some(r => r.name.includes('DOCTOR') || r.name.includes('PHYSICIAN'))) || []
  const providerList = [
    ...(consultants?.map(c => ({
      id: c.id,
      fullName: (c.salutation ? c.salutation + ' ' : '') + c.firstName + (c.lastName && c.lastName !== '.' ? ' ' + c.lastName : '')
    })) || []),
    ...(doctors.length > 0 ? doctors : (users?.filter(u => u.status === 'ACTIVE') || []))
  ]

  const newEncounterForm = useForm<NewEncounterValues>({
    resolver: zodResolver(newEncounterSchema),
    defaultValues: { encounterType: 'OUTPATIENT', visitMode: 'WALK_IN' }
  })
  const newEncounterType = newEncounterForm.watch('encounterType')

  const createOp = useMutation({
    mutationFn: encounterApi.createOutpatient,
    onSuccess: () => {
      toast({ title: 'Encounter created', variant: 'success' })
      navigate(-1);
    },
    onError: (e: any) => {
      const msg = e.response?.data?.message || e.message || 'Failed to create encounter'
      toast({ title: 'Error', description: msg, variant: 'destructive' })
    }
  })

  const createIp = useMutation({
    mutationFn: encounterApi.createInpatient,
    onSuccess: () => {
      toast({ title: 'Encounter created', variant: 'success' })
      navigate(-1);
    },
    onError: (e: any) => {
      const msg = e.response?.data?.message || e.message || 'Failed to create encounter'
      toast({ title: 'Error', description: msg, variant: 'destructive' })
    }
  })

  const onSaveVitals = (data: VitalsValues) => {
    const vitals = Object.fromEntries(Object.entries(data).filter(([, v]) => v !== undefined && v !== null))
    mutations.recordVitals.mutate({ vitals })
  }

  const onSaveCasesheet = (data: CasesheetValues) => {
    const payload = Object.fromEntries(Object.entries(data).filter(([, v]) => v !== undefined && v !== null))
    mutations.recordCasesheet.mutate(payload as any)
  }

  const onDischarge = () => {
    mutations.discharge.mutate({ dischargeNotes })
  }

  const onSubmitNew = (data: NewEncounterValues) => {
    if (!patientId) return
    const payload: CreateEncounterCmd = {
      patientId,
      primaryProviderId: data.primaryProviderId,
    }

    if (data.encounterType === 'OUTPATIENT' && data.visitMode) {
      payload.visitMode = data.visitMode as VisitMode
    }

    if (data.encounterType === 'OUTPATIENT') {
      createOp.mutate(payload)
    } else {
      createIp.mutate(payload)
    }
  }

  const inputCls = "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
  const labelCls = "block text-xs font-medium text-gray-700 mb-1"

  if (isNew) {
    if (!patientId) return <div className="p-6 text-sm text-red-600">Patient ID is required</div>
    if (patientLoading || usersLoading) return <div className="p-6 text-sm text-gray-500">Loading…</div>
    if (!patient) return <div className="p-6 text-sm text-red-600">Patient not found</div>

    return (
      <div className="space-y-5 max-w-2xl">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold text-gray-900">New Encounter</h2>
            <p className="text-sm text-gray-500 mt-0.5">For {patient.fullName}</p>
          </div>
          <BackButton />
        </div>

        <form onSubmit={newEncounterForm.handleSubmit(onSubmitNew)} className="bg-white border border-gray-200 rounded-xl p-5 space-y-4" noValidate>
          <div>
            <label className={labelCls}>Encounter Type</label>
            <select
              {...newEncounterForm.register('encounterType')}
              className={inputCls}
            >
              <option value="OUTPATIENT">Outpatient</option>
              <option value="INPATIENT">Inpatient</option>
            </select>
          </div>

          {newEncounterType === 'OUTPATIENT' && (
            <div>
              <label className={labelCls}>Encounter Mode</label>
              <select
                {...newEncounterForm.register('visitMode')}
                className={inputCls}
              >
                <option value="WALK_IN">Walk In</option>
                <option value="APPOINTMENT">Appointment</option>
                <option value="TELE_CONSULT">Tele Consult</option>
              </select>
            </div>
          )}

          <div>
            <label className={labelCls}>Primary Consultant</label>
            <Controller
              name="primaryProviderId"
              control={newEncounterForm.control}
              render={({ field }) => (
                <ConsultantSearchInput
                  consultants={providerList.map((p: any) => ({
                    id: p.id,
                    firstName: p.fullName || p.firstName || '',
                    lastName: '',
                    status: 'ACTIVE'
                  })) as any}
                  value={field.value ?? ''}
                  onChange={field.onChange}
                />
              )}
            />
            {newEncounterForm.formState.errors.primaryProviderId && (
              <p className="text-xs text-red-600 mt-0.5">{newEncounterForm.formState.errors.primaryProviderId.message}</p>
            )}
          </div>

          <div className="pt-2">
            <button
              type="submit"
              disabled={createOp.isPending || createIp.isPending}
              className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {(createOp.isPending || createIp.isPending) ? 'Creating...' : 'Create Encounter'}
            </button>
          </div>
        </form>
      </div>
    )
  }

  if (isLoading) return <div className="text-sm text-gray-500 p-6" aria-live="polite">Loading encounter…</div>
  if (error || !encounter) return <div className="text-sm text-red-600 p-6" role="alert">Encounter not found</div>

  return (
    <div className="space-y-5 max-w-4xl">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Clinical Encounter</h2>
          <p className="text-sm font-medium text-blue-600 mt-0.5">{encounter.patientName}</p>
          <p className="text-xs text-gray-500 mt-1">
            {encounter.encounterType} · {encounter.visitMode.replace('_', ' ')} · {formatDateTime(encounter.startedAt)}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className={cn('inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border', STATUS_STYLES[encounter.status])}>
            {STATUS_LABELS[encounter.status]}
          </span>
          <BackButton />
        </div>
      </div>

      {/* Info bar */}
      <div className="grid grid-cols-3 gap-3">
        {[
          { label: 'Patient ID', value: encounter.patientNumber ?? encounter.patientId.slice(0, 8) },
          { label: 'Provider', value: providerList.find(p => p.id === encounter.primaryProviderId)?.fullName ?? encounter.primaryProviderId.slice(0, 8) + '…' },
          { label: 'Diagnosis', value: encounter.diagnosis ?? '—' },
        ].map(({ label, value }) => (
          <div key={label} className="bg-gray-50 rounded-lg px-4 py-3 border border-gray-100">
            <p className="text-xs text-gray-500">{label}</p>
            <p className="text-sm font-medium text-gray-800 mt-0.5 truncate">{value}</p>
          </div>
        ))}
      </div>

      {/* Existing vitals display */}
      {encounter.vitalData && Object.keys(encounter.vitalData).filter(k => k !== 'casesheet' && k !== 'dischargeNotes').length > 0 && (
        <div className="bg-blue-50 border border-blue-100 rounded-xl p-4">
          <p className="text-xs font-semibold text-blue-700 mb-2 uppercase tracking-wide">Recorded Vitals</p>
          <div className="grid grid-cols-4 gap-3">
            {Object.entries(encounter.vitalData).filter(([k]) => k !== 'casesheet' && k !== 'dischargeNotes').map(([key, value]) => (
              <div key={key}>
                <p className="text-xs text-blue-500">{key.replace(/([A-Z])/g, ' $1').trim()}</p>
                <p className="text-sm font-bold text-blue-900">{String(value)}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Tab navigation */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit" role="tablist">
        {(['vitals', 'casesheet', 'discharge'] as const).map(t => (
          <button key={t} role="tab" aria-selected={activeTab === t}
            onClick={() => setActiveTab(t)}
            className={cn('px-4 py-1.5 text-sm font-medium rounded-md transition-colors capitalize',
              activeTab === t ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700')}>
            {t}
          </button>
        ))}
      </div>

      {/* Vitals Tab */}
      {activeTab === 'vitals' && (
        <form onSubmit={vitalsForm.handleSubmit(onSaveVitals)} className="bg-white border border-gray-200 rounded-xl p-5 space-y-4" noValidate>
          <h3 className="text-sm font-semibold text-gray-900">Record Vitals</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {[
              { field: 'bloodPressureSystolic' as const, label: 'BP Systolic (mmHg)', placeholder: '120' },
              { field: 'bloodPressureDiastolic' as const, label: 'BP Diastolic (mmHg)', placeholder: '80' },
              { field: 'pulseRate' as const, label: 'Pulse Rate (bpm)', placeholder: '72' },
              { field: 'temperature' as const, label: 'Temperature (°C)', placeholder: '37' },
              { field: 'oxygenSaturation' as const, label: 'SpO2 (%)', placeholder: '98' },
              { field: 'weight' as const, label: 'Weight (kg)', placeholder: '70' },
              { field: 'height' as const, label: 'Height (cm)', placeholder: '170' },
              { field: 'respiratoryRate' as const, label: 'Resp. Rate (/min)', placeholder: '16' },
            ].map(({ field, label, placeholder }) => (
              <div key={field}>
                <label className={labelCls}>{label}</label>
                <input type="number" step="1" min={0} placeholder={placeholder}
                  className={inputCls} aria-label={label}
                  {...vitalsForm.register(field)} />
                {vitalsForm.formState.errors[field] && (
                  <p className="text-xs text-red-600 mt-0.5" role="alert">{vitalsForm.formState.errors[field]?.message}</p>
                )}
              </div>
            ))}
          </div>
          <button type="submit" disabled={mutations.recordVitals.isPending}
            className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
            {mutations.recordVitals.isPending ? 'Saving…' : 'Save Vitals'}
          </button>
        </form>
      )}

      {/* Casesheet Tab */}
      {activeTab === 'casesheet' && (
        <form onSubmit={casesheetForm.handleSubmit(onSaveCasesheet)} className="bg-white border border-gray-200 rounded-xl p-5 space-y-4" noValidate>
          <h3 className="text-sm font-semibold text-gray-900">Casesheet</h3>
          {[
            { field: 'chiefComplaint' as const, label: 'Chief Complaint *', rows: 2 },
            { field: 'historyOfPresentIllness' as const, label: 'History of Present Illness', rows: 3 },
            { field: 'examination' as const, label: 'Examination Findings', rows: 3 },
            { field: 'diagnosis' as const, label: 'Diagnosis', rows: 2 },
            { field: 'plan' as const, label: 'Treatment Plan / Advice', rows: 3 },
          ].map(({ field, label, rows }) => (
            <div key={field}>
              <label htmlFor={field} className={labelCls}>{label}</label>
              <textarea id={field} rows={rows}
                className={`${inputCls} resize-none`}
                aria-label={label}
                aria-invalid={!!casesheetForm.formState.errors[field]}
                {...casesheetForm.register(field)} />
              {casesheetForm.formState.errors[field] && (
                <p className="text-xs text-red-600 mt-0.5" role="alert">{casesheetForm.formState.errors[field]?.message}</p>
              )}
            </div>
          ))}
          <button type="submit" disabled={mutations.recordCasesheet.isPending}
            className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
            {mutations.recordCasesheet.isPending ? 'Saving…' : 'Save Casesheet'}
          </button>
        </form>
      )}

      {/* Discharge Tab */}
      {activeTab === 'discharge' && (
        <div className="bg-white border border-gray-200 rounded-xl p-5 space-y-4">
          <h3 className="text-sm font-semibold text-gray-900">Discharge Patient</h3>
          {encounter.dischargedAt ? (
            <div className="bg-green-50 border border-green-200 rounded-lg p-4 text-sm text-green-800">
              Patient was discharged at {formatDateTime(encounter.dischargedAt)}
            </div>
          ) : encounter.encounterType === 'INPATIENT' ? (
            <>
              <div>
                <label htmlFor="dischargeNotes" className={labelCls}>Discharge Notes</label>
                <textarea id="dischargeNotes" rows={4} value={dischargeNotes}
                  onChange={e => setDischargeNotes(e.target.value)}
                  placeholder="Discharge summary, follow-up instructions…"
                  className={`${inputCls} resize-none`} />
              </div>
              <button onClick={onDischarge} disabled={mutations.discharge.isPending}
                className="px-5 py-2 bg-amber-600 text-white text-sm font-semibold rounded-lg hover:bg-amber-700 disabled:opacity-50 transition-colors">
                {mutations.discharge.isPending ? 'Processing…' : 'Confirm Discharge'}
              </button>
            </>
          ) : (
            <p className="text-sm text-gray-500">Discharge is only applicable to inpatient encounters.</p>
          )}
        </div>
      )}

      {/* Quick links */}
      <div className="flex gap-3 text-xs text-blue-600">
        <Link to={`/billing`} className="hover:underline">→ Open Billing</Link>
        <Link to={`/diagnostics?encounterId=${encounterId}&patientId=${encounter.patientId}`} className="hover:underline">→ Diagnostics</Link>
        {encounter.encounterType === 'OUTPATIENT' && (
          <Link to={`/op-casesheet/${encounterId}`} className="hover:underline font-semibold text-blue-700">→ OP Case Sheet</Link>
        )}
        {encounter.encounterType === 'INPATIENT' && (
          <Link to={`/ip-casesheet/${encounterId}`} className="hover:underline font-semibold text-blue-700">→ IP Case Sheet</Link>
        )}
      </div>
    </div>
  )
}
