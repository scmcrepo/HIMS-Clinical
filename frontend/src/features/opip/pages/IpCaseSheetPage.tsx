/**
 * IpCaseSheetPage.tsx — full IP case sheet with all 9 tabs:
 *  1. Diagnostic Order (modal)
 *  2. Prescription (modal)
 *  3. Other Charges
 *  4. Attachments
 *  5. Discharge Summary
 *  6. OT Notes (casesheet template based)
 *  7. Vital Signs (multiple, history + report)
 *  8. Progress Notes (modal)
 *  9. Nurse Notes (modal)
 */
import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { encounterApi }       from '../../../services/encounter/encounterApi'
import { ipCasesheetApi, recordApi, templateApi } from '../../../services/casesheet/casesheetApi'
import { ipVitalsApi }        from '../../../services/opip/opipApi'
import { usePatient }         from '../../../hooks/patient/usePatient'
import { consultantApi }      from '../../../services/consultant/consultantApi'
import { DynamicCaseSheetForm } from '../components/DynamicCaseSheetForm'
import { PrescriptionTab }    from '../components/PrescriptionTab'
import { DiagnosticOrderTab } from '../components/DiagnosticOrderTab'
import { ClinicalNoteTab }    from '../components/ClinicalNoteTab'
import { OtherChargesTab }    from '../components/OtherChargesTab'
import { IpBillPanel }        from '../components/IpBillPanel'
import { VitalSignsModal }    from '../components/VitalSignsModal'
import { attachmentApi }      from '../../../services/attachment/attachmentApi'
import { formatDateTime }     from '../../../lib/dateUtils'
import { cn }                 from '../../../lib/utils'
import DatePicker             from '../../../components/shared/DatePicker'
import BackButton             from '../../../components/shared/BackButton'
import { toast }              from '../../../hooks/useToast'
import type { CaseSheetData } from '../../../types/casesheet'

type Tab =
  | 'diag' | 'prescrp' | 'otherChrg' | 'attach'
  | 'dischargeSummary' | 'otNotes' | 'vitalSign'
  | 'progressNotes' | 'nurseNotes' | 'ipBill'

const TABS: { key: Tab; label: string }[] = [
  // { key: 'vitalSign',       label: '🩺 Vitals' },
  { key: 'prescrp',         label: '💊 Prescription' },
  { key: 'diag',            label: '🧪 Diagnostic Order' },
  // { key: 'prescrp',         label: '💊 Prescription' },
  // { key: 'otherChrg',       label: '💰 Other Charges' },
  { key: 'attach',          label: '📎 Attachments' },
  { key: 'dischargeSummary',label: '📄 Discharge Summary' },
  // { key: 'otNotes',         label: '🏥 OT Notes' },
  // { key: 'vitalSign',       label: '📊 Vital Signs' },
  // { key: 'progressNotes',   label: '📝 Progress Notes' },
  // { key: 'nurseNotes',      label: '🩺 Nurse Notes' },
  // { key: 'ipBill',          label: '🧾 Bill' },
]

export default function IpCaseSheetPage() {
  const { encounterId } = useParams<{ encounterId: string }>()
  const [activeTab, setActiveTab] = useState<Tab>('prescrp')
  const [dischargeNotes, setDischargeNotes] = useState('')
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('')
  const qc = useQueryClient()

  const { data: encounter, isLoading: encLoading } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn:  () => encounterApi.getById(encounterId!),
    enabled:  !!encounterId,
  })

  // Automatically refresh encounter and patient-encounter details when entering the page
  useEffect(() => {
    if (encounterId) {
      qc.invalidateQueries({ queryKey: ['encounter', encounterId] })
      qc.invalidateQueries({ queryKey: ['ip-casesheet', encounterId] })
    }
  }, [qc, encounterId])

  useEffect(() => {
    if (encounter?.patientId) {
      qc.invalidateQueries({ queryKey: ['patient-encounters', encounter.patientId] })
    }
  }, [qc, encounter?.patientId])

  const { data: patient } = usePatient(encounter?.patientId)

  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
  })

  const { data: encountersPage } = useQuery({
    queryKey: ['patient-encounters', encounter?.patientId],
    queryFn: () => encounterApi.getByPatient(encounter!.patientId, 0, 100),
    enabled: !!encounter?.patientId,
  })

  const selectedConsultant = consultants.find(c => c.id === encounter?.primaryProviderId)
  const qualification = selectedConsultant?.qualification || selectedConsultant?.specialisation || ''
  const consultantName = selectedConsultant
    ? `${selectedConsultant.salutation ? selectedConsultant.salutation + ' ' : ''}${selectedConsultant.firstName} ${selectedConsultant.lastName}`
    : '—'

  const currentEncounterSummary = encountersPage?.content?.find(e => e.id === encounterId)
  const bedName = currentEncounterSummary?.bedName || '—'

  // IP casesheet (OT Notes template)
  const { data: csData, isLoading: csLoading } = useQuery({
    queryKey: ['ip-casesheet', encounterId],
    queryFn:  () => ipCasesheetApi.loadCasesheet(encounterId!, undefined),
    enabled:  !!encounterId,
  })

  // Fetch IP templates list
  const { data: ipTemplates = [] } = useQuery({
    queryKey: ['ip-templates-all'],
    queryFn:  () => templateApi.list(undefined, 'IP', 'ACTIVE'),
  })

  const dischargeTemplates = ipTemplates.filter(t =>
    t.specialization?.toUpperCase() === 'GENERAL' ||
    t.name?.toUpperCase().includes('DISCHARGE')
  )

  // Fetch saved records for encounter
  const { data: records = [], refetch: refetchRecords } = useQuery({
    queryKey: ['casesheet-records', encounterId],
    queryFn:  () => recordApi.getByEncounter(encounterId!),
    enabled:  !!encounterId,
  })

  const existingDischargeRecord = records.find(r =>
    dischargeTemplates.some(dt => dt.id === r.template?.id)
  )

  useEffect(() => {
    if (existingDischargeRecord?.template?.id) {
      setSelectedTemplateId(existingDischargeRecord.template.id)
    } else if (dischargeTemplates.length > 0 && !selectedTemplateId) {
      const defaultTmpl = dischargeTemplates.find(t => t.name === 'DISCHARGE') || dischargeTemplates[0]
      setSelectedTemplateId(defaultTmpl.id)
    }
  }, [existingDischargeRecord, dischargeTemplates, selectedTemplateId])

  // Fetch selected template details
  const { data: templateDetail, isLoading: templateLoading } = useQuery({
    queryKey: ['casesheet-template-detail', selectedTemplateId],
    queryFn:  () => templateApi.getById(selectedTemplateId),
    enabled:  !!selectedTemplateId,
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['encounter', encounterId] })
    qc.invalidateQueries({ queryKey: ['ip-casesheet', encounterId] })
    qc.invalidateQueries({ queryKey: ['casesheet-records', encounterId] })
  }

  const saveMut = useMutation({
    mutationFn: (data: CaseSheetData) =>
      recordApi.save(encounterId!, { templateId: csData?.template?.id, data }),
    onSuccess: () => { invalidate(); refetchRecords(); toast({ title: 'OT Notes saved', variant: 'success' }) },
    onError:   (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const saveDischargeRecordMut = useMutation({
    mutationFn: (formData: CaseSheetData) =>
      recordApi.save(encounterId!, { templateId: selectedTemplateId, data: formData }),
    onSuccess: (savedRecord) => {
      invalidate()
      refetchRecords()
      if (templateDetail?.fields) {
        const notes = templateDetail.fields
          .map(f => {
            const val = savedRecord.data?.[f.fieldKey]
            if (val === undefined || val === null || val === '') return null
            if (Array.isArray(val)) return `${f.label}: ${val.join(', ')}`
            return `${f.label}: ${val}`
          })
          .filter(Boolean)
          .join('\n\n')
        setDischargeNotes(notes)
      }
      toast({ title: 'Discharge details saved', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  if (encLoading) return <div className="p-6 text-sm text-gray-500">Loading…</div>
  if (!encounter) return <div className="p-6 text-sm text-red-600">Encounter not found</div>

  const isDischarged = !!encounter.dischargedAt
  const isReadOnly   = isDischarged

  const savedData   = csData?.records[0]?.data
  const checklistOk = savedData?.preop_checklist
    ? Object.values(savedData.preop_checklist as Record<string, boolean>).every(Boolean)
    : false

  return (
    <div className="space-y-4 max-w-6xl">
      {/* Patient Info Banner */}
      <div className="flex items-start justify-between flex-wrap gap-3 pb-4 border-b border-gray-200">
        <div>
          {/* Line 1: SCMC-5 : Mr Mariadoss Y (Male / 61 Y ) */}
          <h2 className="text-xl font-bold text-gray-900 tracking-tight">
            <span className="text-blue-600 mr-3">{encounter.patientNumber}</span>{patient?.salutation ? patient.salutation + ' ' : ''}{encounter.patientName}{' '}
            <span className="text-gray-600 font-semibold">
              ({patient?.gender ? (patient.gender === 'MALE' ? 'Male' : patient.gender === 'FEMALE' ? 'Female' : 'Other') : '—'} / {patient?.age || '—'} )
            </span>
          </h2>
          
          {/* Line 2: Bed No : A103        Primary Consultant : Dr A Srinivasula Reddy MBBS        Admission Date : 02/06/2026 03:59 PM */}
          <div className="flex flex-wrap items-center gap-x-6 gap-y-2 mt-2 text-xs font-semibold text-gray-500">
            <div className="flex items-center gap-1.5">
              <span className="text-gray-400">Bed No :</span>
              <span className="text-gray-900 font-bold">{bedName}</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-gray-400">Primary Consultant :</span>
              <span className="text-gray-900 font-bold">
                {consultantName} {qualification && <span className="text-gray-500 font-medium text-[10px] bg-gray-100 px-1.5 py-0.5 rounded ml-1">{qualification}</span>}
              </span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-gray-400">Admission Date :</span>
              <span className="text-gray-900 font-bold">{formatDateTime(encounter.startedAt)}</span>
            </div>
          </div>
        </div>
        
        <div className="flex items-center gap-2">
          {isDischarged ? (
            <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border bg-green-50 text-green-700 border-green-200">
              ✓ Discharged {formatDateTime(encounter.dischargedAt!)}
            </span>
          ) : (
            <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border bg-blue-50 text-blue-700 border-blue-200">
              Admitted
            </span>
          )}
          <BackButton to="/ip-ward" />
        </div>
      </div>

      {/* Pre-op checklist warning */}
      {!isDischarged && !checklistOk && csData?.records[0] && (
        <div className="bg-amber-50 border border-amber-300 rounded-lg px-4 py-2.5 text-xs text-amber-800 flex items-center gap-2">
          <span>⚠</span>
          Pre-operative checklist is incomplete. Complete it in OT Notes before sending to theatre.
        </div>
      )}

      {/* Tab bar — vertical left sidebar style adapted to responsive horizontal */}
      <div className="flex gap-1 bg-gray-100 p-1 rounded-xl w-full flex-wrap" role="tablist">
        {TABS.map(t => (
          <button key={t.key} role="tab" aria-selected={activeTab === t.key}
            onClick={() => setActiveTab(t.key)}
            className={cn(
              'px-3 py-1.5 text-xs font-medium rounded-lg transition-colors whitespace-nowrap',
              activeTab === t.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
            )}>
            {t.label}
          </button>
        ))}
      </div>

      {isReadOnly && (
        <div className="bg-green-50 border border-green-200 rounded-lg px-4 py-2 text-xs text-green-700">
          Patient has been discharged. All tabs are read-only.
        </div>
      )}

      {/* Tab content */}
      <div className="bg-white border border-gray-200 rounded-xl p-5 min-h-64">
        {activeTab === 'diag' && (
          <DiagnosticOrderTab
            encounterId={encounterId!}
            mode="IP"
            consultantId={encounter.primaryProviderId}
            readOnly={isReadOnly}
          />
        )}

        {activeTab === 'prescrp' && (
          <PrescriptionTab
            encounterId={encounterId!}
            mode="IP"
            consultantId={encounter.primaryProviderId}
            readOnly={isReadOnly}
          />
        )}

        {activeTab === 'otherChrg' && (
          <OtherChargesTab encounterId={encounterId!} readOnly={isReadOnly} />
        )}

        {activeTab === 'attach' && (
          <IpAttachmentsTab encounterId={encounterId!} readOnly={isReadOnly} />
        )}

        {activeTab === 'dischargeSummary' && (
          <DischargeSummaryTab
            encounter={encounter}
            dischargeNotes={dischargeNotes}
            setDischargeNotes={setDischargeNotes}
            checklistOk={checklistOk}
            isDischarged={isDischarged}
            selectedTemplateId={selectedTemplateId}
            templateDetail={templateDetail}
            templateLoading={templateLoading}
            existingRecord={existingDischargeRecord}
            saveRecordMut={saveDischargeRecordMut}
          />
        )}

        {activeTab === 'otNotes' && (
          csLoading ? (
            <p className="text-sm text-gray-500 py-8 text-center">Loading OT template…</p>
          ) : csData?.template ? (
            <DynamicCaseSheetForm
              template={csData.template}
              initialData={csData.records[0]?.data}
              onSave={data => saveMut.mutate(data)}
              isSaving={saveMut.isPending}
              readOnly={isReadOnly}
              saveButtonText={csData?.records?.[0] ? 'Update OT Notes' : 'Save OT Notes'}
            />
          ) : (
            <div className="border border-dashed border-gray-200 rounded-xl p-8 text-center text-sm text-gray-400">
              No IP OT Notes template found. Contact admin to configure one.
            </div>
          )
        )}

        {activeTab === 'vitalSign' && (
          <IpVitalsTab encounterId={encounterId!} readOnly={isReadOnly} />
        )}

        {activeTab === 'progressNotes' && (
          <ClinicalNoteTab
            encounterId={encounterId!}
            noteType="PROGRESS"
            readOnly={isReadOnly}
          />
        )}

        {activeTab === 'nurseNotes' && (
          <ClinicalNoteTab
            encounterId={encounterId!}
            noteType="NURSE"
            readOnly={isReadOnly}
          />
        )}

        {activeTab === 'ipBill' && (
          <IpBillPanel
            encounterId={encounterId!}
            readOnly={isReadOnly}
          />
        )}
      </div>
    </div>
  )
}

// ─── IP Vitals Tab ────────────────────────────────────────────────────────────
function IpVitalsTab({ encounterId, readOnly }: { encounterId: string; readOnly?: boolean }) {
  const qc = useQueryClient()
  const [subTab, setSubTab] = useState<'history' | 'report'>('history')
  const [showForm, setShowForm] = useState(false)

  const { data: vitals = [], isLoading } = useQuery({
    queryKey: ['ip-vitals', encounterId],
    queryFn:  () => ipVitalsApi.list(encounterId),
  })

  const VITAL_KEYS = ['weight','height','bpSystolic','bpDiastolic','pulseRate','respiratoryRate','temperature','spo2']

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex gap-1 bg-gray-100 p-0.5 rounded-lg">
          {(['history', 'report'] as const).map(s => (
            <button key={s} onClick={() => setSubTab(s)}
              className={cn(
                'px-3 py-1 text-xs font-medium rounded transition-colors capitalize',
                subTab === s ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'
              )}>
              {s === 'history' ? 'History' : 'Report'}
            </button>
          ))}
        </div>
        {!readOnly && (
          <button onClick={() => setShowForm(true)}
            className="px-3 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
            + ADD VITALSIGNS
          </button>
        )}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-6">Loading…</p>
      ) : vitals.length === 0 ? (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg px-4 py-3 text-xs text-yellow-800">
          No VitalSigns! There is no history of VitalSign
        </div>
      ) : subTab === 'history' ? (
        <div className="overflow-x-auto">
          <table className="w-full text-xs border border-gray-200 rounded-xl overflow-hidden">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-3 py-2 text-left text-gray-500 font-medium">Date/Time</th>
                {VITAL_KEYS.map(k => (
                  <th key={k} className="px-3 py-2 text-left text-gray-500 font-medium capitalize">
                    {k.replace(/([A-Z])/g, ' $1')}
                  </th>
                ))}
                <th className="px-3 py-2 text-left text-gray-500 font-medium">Remark</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {[...vitals].reverse().map((v, i) => (
                <tr key={i} className="hover:bg-gray-50">
                  <td className="px-3 py-2 text-gray-500 whitespace-nowrap">
                    {formatDateTime(v.recordedAt as string)}
                  </td>
                  {VITAL_KEYS.map(k => (
                    <td key={k} className="px-3 py-2 text-gray-700 font-medium">
                      {v[k as keyof typeof v] ?? '—'}
                    </td>
                  ))}
                  <td className="px-3 py-2 text-gray-500">{(v.remark as string) ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        // Report: simple visual trend
        <div className="space-y-3">
          {VITAL_KEYS.filter(k => vitals.some(v => v[k as keyof typeof v])).map(k => {
            const vals = vitals.map(v => parseFloat(v[k as keyof typeof v] as string) || 0).filter(Boolean)
            const max  = Math.max(...vals)
            return (
              <div key={k} className="space-y-1">
                <p className="text-xs font-medium text-gray-600 capitalize">
                  {k.replace(/([A-Z])/g, ' $1')}
                </p>
                <div className="flex items-end gap-1 h-12">
                  {vals.map((val, i) => (
                    <div key={i} title={String(val)}
                      style={{ height: `${max > 0 ? (val / max) * 100 : 20}%` }}
                      className="flex-1 bg-blue-400 rounded-t-sm min-h-[4px] transition-all"
                    />
                  ))}
                </div>
                <div className="flex justify-between text-[10px] text-gray-400">
                  <span>min: {Math.min(...vals)}</span>
                  <span>max: {max}</span>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showForm && (
        <VitalSignsModal
          encounterId={encounterId}
          mode="IP"
          onClose={() => setShowForm(false)}
          onSaved={() => {
            setShowForm(false)
            qc.invalidateQueries({ queryKey: ['ip-vitals', encounterId] })
          }}
        />
      )}
    </div>
  )
}

// ─── Discharge Summary Tab ────────────────────────────────────────────────────
function DischargeSummaryTab({
  encounter,
  dischargeNotes,
  setDischargeNotes,
  checklistOk,
  isDischarged,
  selectedTemplateId,
  templateDetail,
  templateLoading,
  existingRecord,
  saveRecordMut,
}: any) {
  return (
    <div className="space-y-4">
      <h3 className="text-sm font-bold text-gray-900">Discharge Summary</h3>

      {isDischarged ? (
        <div className="space-y-4">
          <div className="bg-green-50 border border-green-200 rounded-lg p-4">
            <p className="text-sm font-semibold text-green-800">
              ✓ Patient was discharged on {formatDateTime(encounter.dischargedAt)}
            </p>
            {encounter.vitalData?.dischargeNotes && !existingRecord && (
              <p className="text-xs text-green-700 mt-2 whitespace-pre-wrap">
                {String(encounter.vitalData.dischargeNotes)}
              </p>
            )}
          </div>

          {existingRecord && templateDetail && (
            <div className="bg-white border border-gray-200 rounded-xl p-5 shadow-sm space-y-3">
              <h4 className="text-sm font-bold text-gray-800 border-b border-gray-100 pb-2">
                Discharge Details (Template Form)
              </h4>
              <DynamicCaseSheetForm
                template={templateDetail}
                initialData={existingRecord.data}
                onSave={() => {}}
                readOnly={true}
              />
            </div>
          )}

          {!selectedTemplateId && encounter.vitalData?.dischargeNotes && (
            <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
              <p className="text-xs font-semibold text-gray-700 mb-1">Discharge Notes Summary (Text Output)</p>
              <p className="text-xs text-gray-600 whitespace-pre-wrap font-mono">
                {String(encounter.vitalData.dischargeNotes)}
              </p>
            </div>
          )}
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 opacity-60">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Bed No</label>
              <input readOnly value={encounter.bedName || (encounter.hasBed ? 'Assigned' : 'N/A')}
                className="w-full px-2.5 py-1.5 border border-gray-200 rounded-lg text-sm bg-gray-50 text-gray-600" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Discharge Date</label>
              <DatePicker value={new Date().toISOString().split('T')[0]} onChange={() => {}} size="sm" />
            </div>
          </div>

          {!checklistOk && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 text-xs text-amber-700">
              ⚠ Pre-operative checklist is incomplete. Verify in OT Notes tab before discharge.
            </div>
          )}

          {/* Template rendering if resolved */}
          {selectedTemplateId ? (
            <div className="bg-gray-50 border border-gray-200 rounded-xl p-4 space-y-3">
              {templateLoading ? (
                <p className="text-xs text-gray-500 italic">Loading template fields...</p>
              ) : templateDetail ? (
                <div className="space-y-3">
                  <p className="text-xs font-bold text-blue-700 uppercase tracking-wide">
                    Discharge Details Form
                  </p>
                  <DynamicCaseSheetForm
                    template={templateDetail}
                    initialData={existingRecord?.data}
                    onSave={data => saveRecordMut.mutate(data)}
                    isSaving={saveRecordMut.isPending}
                    saveButtonText={existingRecord ? 'Update Discharge Template' : 'Save Discharge Template'}
                    helperText="Changes are saved to this encounter's discharge summary data"
                  />
                </div>
              ) : (
                <p className="text-xs text-red-500">Failed to load template fields.</p>
              )}
            </div>
          ) : null}

          {!selectedTemplateId && (
            <div>
              <label className="block text-xs font-semibold text-gray-700 mb-1">
                Discharge Notes / Summary (Final Text Output)
              </label>
              <textarea rows={6} value={dischargeNotes}
                onChange={e => setDischargeNotes(e.target.value)}
                placeholder="Summarise treatment, discharge medications, follow-up instructions, red flags…"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-500" />
              <p className="text-xs text-gray-400 mt-0.5">
                This field will be automatically populated when you save the template form above, but you can also edit it directly before discharging.
              </p>
            </div>
          )}

        </>
      )}
    </div>
  )
}

// ─── Attachments Tab (IP) ─────────────────────────────────────────────────────
function IpAttachmentsTab({ encounterId, readOnly }: { encounterId: string; readOnly?: boolean }) {
  const qc = useQueryClient()
  const { data: attachments = [] } = useQuery({
    queryKey: ['attachments', encounterId],
    queryFn:  () => attachmentApi.getByEncounter(encounterId),
  })
  const [uploading, setUploading] = useState(false)

  const handleUpload = async (file: File) => {
    setUploading(true)
    try {
      await attachmentApi.upload(file, 'VISIT', encounterId)
      qc.invalidateQueries({ queryKey: ['attachments', encounterId] })
      toast({ title: 'File uploaded', variant: 'success' })
    } catch { toast({ title: 'Upload failed', variant: 'destructive' }) }
    finally   { setUploading(false) }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-gray-800">Attachments</h3>
        {!readOnly && (
          <label className={cn(
            'px-3 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-lg cursor-pointer hover:bg-blue-700 transition-colors',
            uploading && 'opacity-50 pointer-events-none'
          )}>
            {uploading ? 'Uploading…' : '+ Upload File'}
            <input type="file" className="hidden" onChange={e => e.target.files?.[0] && handleUpload(e.target.files[0])} />
          </label>
        )}
      </div>

      {attachments.length === 0 ? (
        <p className="text-sm text-gray-400">No attachments yet.</p>
      ) : (
        <ul className="space-y-2">
          {attachments.map(a => (
            <li key={a.id} className="flex items-center justify-between px-3 py-2 border border-gray-200 rounded-lg text-xs">
              <span className="font-medium text-gray-800 truncate">{a.fileName}</span>
              <a href={attachmentApi.getDownloadUrl(a.id)} download={a.fileName}
                className="text-blue-600 hover:underline shrink-0 ml-2">Download</a>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
