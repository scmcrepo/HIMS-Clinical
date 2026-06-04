/**
 * OpCaseSheetPage.tsx — full OP case sheet with:
 *  - Left column: Patient visit history timeline
 *  - Right column: Selected visit case sheet details with curved consultant header tab
 *  - 4 tabs: Clinical Notes, Prescription, Diagnostic Order, Attachments, Vitals
 */
import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { opQueueApi, recordApi, templateApi } from '../../../services/casesheet/casesheetApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { DynamicCaseSheetForm } from '../components/DynamicCaseSheetForm'
import { PrescriptionTab } from '../components/PrescriptionTab'
import { DiagnosticOrderTab } from '../components/DiagnosticOrderTab'
import { attachmentApi } from '../../../services/attachment/attachmentApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import BackButton from '../../../components/shared/BackButton'
import { toast } from '../../../hooks/useToast'
import type { EncounterStatus } from '../../../types/encounter'
import type { CaseSheetData } from '../../../types/casesheet'

const STATUS_STYLES: Record<EncounterStatus, string> = {
  CHECKED_IN: 'bg-orange-50  text-orange-700  border-orange-200',
  CONSULTATION_STARTED: 'bg-purple-50  text-purple-700  border-purple-200',
  CASESHEET_RECORDED: 'bg-amber-50   text-amber-700   border-amber-200',
  BILLING_DONE: 'bg-green-50   text-green-700   border-green-200',
}
const STATUS_LABELS: Record<EncounterStatus, string> = {
  CHECKED_IN: 'Checked In',
  CONSULTATION_STARTED: 'Vitals Entered',
  CASESHEET_RECORDED: 'Casesheet Done',
  BILLING_DONE: 'Consulted',
}

type Tab = 'vitals' | 'clinical' | 'prescription' | 'diagnostic' | 'attachments'

export default function OpCaseSheetPage() {
  const { encounterId } = useParams<{ encounterId: string }>()
  const [activeTab, setActiveTab] = useState<Tab>('vitals')
  const qc = useQueryClient()

  // 1. Fetch current encounter
  const { data: encounter, isLoading: encLoading } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn: () => encounterApi.getById(encounterId!),
    enabled: !!encounterId,
  })

  // 2. Fetch consultants
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
  })

  // 3. Fetch patient's full encounter history (filter to OP only for OP casesheet)
  const { data: encountersPage } = useQuery({
    queryKey: ['patient-encounters', encounter?.patientId],
    queryFn: () => encounterApi.getByPatient(encounter!.patientId, 0, 100),
    enabled: !!encounter?.patientId,
  })
  const patientEncounters = (encountersPage?.content ?? []).filter(e => e.encounterType === 'OUTPATIENT')

  // 4. Load case sheet data for the current encounter
  const { data: csData, isLoading: csLoading } = useQuery({
    queryKey: ['op-casesheet', encounterId],
    queryFn: () => opQueueApi.loadCasesheet(encounterId!, undefined, 'OP'),
    enabled: !!encounterId,
  })

  // 5. Fetch templates list for the select dropdown
  const { data: templates = [] } = useQuery({
    queryKey: ['case-sheet-templates', 'OP'],
    queryFn: () => templateApi.list(undefined, 'OP'),
  })

  // 6. Handle selection of template when creating a new case sheet
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('')

  const { data: selectedTemplate, isLoading: templateDetailLoading } = useQuery({
    queryKey: ['case-sheet-template-detail', selectedTemplateId],
    queryFn: () => templateApi.getById(selectedTemplateId),
    enabled: !!selectedTemplateId,
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['encounter', encounterId] })
    qc.invalidateQueries({ queryKey: ['op-casesheet', encounterId] })
    qc.invalidateQueries({ queryKey: ['patient-encounters', encounter?.patientId] })
    qc.invalidateQueries({ queryKey: ['op-queue'] })
  }

  const saveMut = useMutation({
    mutationFn: (data: CaseSheetData) => {
      const payload: { data: CaseSheetData; templateId?: string } = { data }
      const tid = csData?.template?.id || selectedTemplateId
      if (tid) {
        payload.templateId = tid
      }
      return recordApi.save(encounterId!, payload)
    },
    onSuccess: () => { invalidate(); toast({ title: 'Case sheet saved', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const markConsultedMut = useMutation({
    mutationFn: () => opQueueApi.markConsulted(encounterId!),
    onSuccess: () => { invalidate(); toast({ title: 'Encounter marked as consulted', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  if (encLoading) return <div className="p-6 text-sm text-gray-500">Loading…</div>
  if (!encounter) return <div className="p-6 text-sm text-red-600">Encounter not found</div>

  const todayStr = new Date().toISOString().split('T')[0]
  const encDateStr = new Date(encounter.startedAt).toISOString().split('T')[0]
  const isToday = todayStr === encDateStr
  const canMarkConsulted = encounter.status === 'CASESHEET_RECORDED' && isToday
  const isReadOnly = encounter.status === 'BILLING_DONE' || !isToday

  const TABS: { key: Tab; label: string }[] = [
    { key: 'vitals', label: '🩺 Vitals' },
    { key: 'clinical', label: '📋 Case Sheet' },
    { key: 'prescription', label: '💊 Prescription' },
    { key: 'diagnostic', label: '🧪 Diagnostic Order' },
    { key: 'attachments', label: '📎 Attachments' },
  ]

  // Group encounters by date string, e.g. "02 JUN"
  const sortedEncounters = [...patientEncounters].sort(
    (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
  )


  const selectedConsultant = consultants.find(c => c.id === encounter.primaryProviderId)
  const qualification = selectedConsultant?.qualification || selectedConsultant?.specialisation || 'Consultant'
  const consultantName = selectedConsultant
    ? `${selectedConsultant.salutation ? selectedConsultant.salutation + ' ' : ''}${selectedConsultant.firstName} ${selectedConsultant.lastName}`
    : 'Unknown Consultant'

  return (
    <div className="space-y-4 max-w-7xl">
      {/* Top Patient Header Banner */}
      <div className="flex items-start justify-between flex-wrap gap-3 pb-3 border-b border-gray-200">
        <div>
          <h2 className="text-xl font-bold text-gray-900 font-sans tracking-tight">OP Case Sheet</h2>
          <p className="text-lg font-bold text-blue-700 mt-0.5">{encounter.patientName}</p>
          <p className="text-xs text-gray-500 mt-1">
            {encounter.patientNumber} · Outpatient · Started: {formatDateTime(encounter.startedAt)}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap justify-end">
          <span className={cn(
            'inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border',
            STATUS_STYLES[encounter.status]
          )}>
            {STATUS_LABELS[encounter.status]}
          </span>
          {canMarkConsulted && (
            <button onClick={() => markConsultedMut.mutate()}
              disabled={markConsultedMut.isPending}
              className="px-4 py-1.5 bg-green-600 text-white text-xs font-semibold rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors shadow-sm">
              {markConsultedMut.isPending ? 'Marking…' : '✓ Mark Consulted'}
            </button>
          )}
          <BackButton />
        </div>
      </div>

      {/* Main Two-Column Layout */}
      <div className="flex flex-col lg:flex-row gap-6 items-start">
        {/* Left Column: Visit History Sidebar (Vertical Tabs) */}
        <div className="w-full lg:w-28 shrink-0 bg-white border border-gray-200 rounded-xl overflow-hidden self-stretch flex flex-col divide-y divide-gray-100 max-h-[600px] overflow-y-auto shadow-sm">
          {sortedEncounters.length === 0 ? (
            <div className="text-[10px] text-gray-400 text-center py-4 px-1">No visits</div>
          ) : (
            sortedEncounters.map(enc => {
              const isActive = enc.id === encounterId
              const encDate = new Date(enc.startedAt)
              const dayStr = encDate.getDate().toString().padStart(2, '0')
              const monthStr = encDate.toLocaleString('default', { month: 'short' }).toUpperCase()
              const timeStr = encDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
              const doc = consultants.find(c => c.id === enc.primaryProviderId)
              const docName = doc ? `${doc.salutation ? doc.salutation + ' ' : ''}${doc.firstName} ${doc.lastName}` : enc.providerName
              const deptName = doc?.specialisation || doc?.qualification || ''

              return (
                <Link
                  key={enc.id}
                  to={`/op-casesheet/${enc.id}`}
                  className={cn(
                    "flex flex-col items-center justify-center w-full min-h-[100px] py-3 px-2 text-center transition-all cursor-pointer relative",
                    isActive
                      ? "bg-blue-600 text-white font-bold"
                      : "bg-white text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                  )}
                  title={`${docName} (${deptName ? deptName + ' · ' : ''}${timeStr})`}
                >
                  <span className="text-2xl font-extrabold leading-none">{dayStr}</span>
                  <span className="text-xs uppercase font-extrabold tracking-wider mt-1">{monthStr}</span>
                  
                  <p className={cn(
                    "text-xs font-semibold mt-2.5",
                    isActive ? "text-blue-100" : "text-gray-500"
                  )}>
                    {timeStr}
                  </p>
                </Link>
              )
            })
          )}
        </div>

        {/* Right Column: Case Sheet Content Area */}
        <div className="flex-1 w-full space-y-4">
          {/* Tabs Navigation */}
          <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit flex-wrap" role="tablist">
            {TABS.map(t => (
              <button key={t.key} role="tab" aria-selected={activeTab === t.key}
                onClick={() => setActiveTab(t.key)}
                className={cn(
                  'px-3 py-1.5 text-xs font-medium rounded-md transition-colors whitespace-nowrap',
                  activeTab === t.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                )}>
                {t.label}
              </button>
            ))}
          </div>

          {/* Read-only banner */}
          {isReadOnly && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-2.5 text-xs text-amber-700 shadow-sm flex items-center gap-1.5">
              <span>⚠️</span>
              <span>
                {encounter.status === 'BILLING_DONE'
                  ? 'This encounter is closed (Consulted). All tabs are read-only.'
                  : 'This is a past day encounter. All tabs are read-only.'}
              </span>
            </div>
          )}

          {/* Curved Consultant Header Tab & Main Content Box */}
          <div className="flex flex-col shadow-sm rounded-xl overflow-hidden border border-gray-200">
            {/* Gray Curved Header Tab */}
            <div className="bg-gray-50 px-5 py-3 border-b border-gray-200 flex items-center justify-between flex-wrap gap-2">
              <div>
                <p className="text-sm font-bold text-gray-800">
                  {consultantName}
                </p>
                <p className="text-xs text-gray-500">
                  {qualification} · {formatDateTime(encounter.startedAt)}
                </p>
              </div>
              {isReadOnly && (
                <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-extrabold bg-gray-200 text-gray-700 border border-gray-300">
                  🔒 READ-ONLY PAST VISIT
                </span>
              )}
            </div>

            {/* Tab content inside */}
            <div className="bg-white p-5">
              {activeTab === 'clinical' && (
                csLoading ? (
                  <div className="text-sm text-gray-500 py-8 text-center">Loading case sheet…</div>
                ) : csData?.template ? (
                  <div className="space-y-4">
                    {/* Active template select dropdown (disabled) */}
                    <div className="flex items-center gap-4 border-b border-gray-100 pb-4 mb-4">
                      <label className="text-sm font-semibold text-gray-700 w-32 shrink-0">Case Sheet Form</label>
                      <select
                        disabled
                        value={csData.template.id}
                        className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-gray-50 text-gray-500 cursor-not-allowed max-w-md w-full"
                      >
                        <option value={csData.template.id}>{csData.template.name}</option>
                      </select>
                    </div>

                    <DynamicCaseSheetForm
                      template={csData.template}
                      initialData={csData.records[0]?.data}
                      onSave={data => saveMut.mutate(data)}
                      isSaving={saveMut.isPending}
                      readOnly={isReadOnly}
                    />
                  </div>
                ) : (
                  <div className="space-y-6">
                    {/* Template select dropdown */}
                    <div className="flex items-center gap-4 border-b border-gray-100 pb-4 mb-4">
                      <label className="text-sm font-semibold text-gray-700 w-32 shrink-0">Case Sheet Form</label>
                      <select
                        value={selectedTemplateId}
                        onChange={e => setSelectedTemplateId(e.target.value)}
                        disabled={isReadOnly}
                        className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 max-w-md w-full"
                      >
                        <option value="">Select Template</option>
                        {templates.map(t => (
                          <option key={t.id} value={t.id}>
                            {t.name}
                          </option>
                        ))}
                      </select>
                    </div>

                    {selectedTemplateId ? (
                      templateDetailLoading ? (
                        <div className="text-sm text-gray-500 py-8 text-center">Loading template details…</div>
                      ) : selectedTemplate ? (
                        <DynamicCaseSheetForm
                          template={selectedTemplate}
                          onSave={data => saveMut.mutate(data)}
                          isSaving={saveMut.isPending}
                          readOnly={isReadOnly}
                        />
                      ) : (
                        <div className="text-sm text-red-500 text-center py-8">Failed to load template.</div>
                      )
                    ) : (
                      <div className="border border-dashed border-red-200 bg-red-50/30 rounded-xl p-8 text-center text-sm text-red-700">
                        <span className="font-extrabold block text-base mb-1 text-red-800">No Medical History!</span>
                        There is no medical history for this visit. Please select a template above to create a case sheet.
                      </div>
                    )}
                  </div>
                )
              )}

              {activeTab === 'prescription' && (
                <PrescriptionTab
                  encounterId={encounterId!}
                  mode="OP"
                  consultantId={encounter.primaryProviderId}
                  readOnly={isReadOnly}
                />
              )}

              {activeTab === 'diagnostic' && (
                <DiagnosticOrderTab
                  encounterId={encounterId!}
                  mode="OP"
                  consultantId={encounter.primaryProviderId}
                  readOnly={isReadOnly}
                />
              )}

              {activeTab === 'attachments' && (
                <AttachmentsTab encounterId={encounterId!} readOnly={isReadOnly} />
              )}

              {activeTab === 'vitals' && (
                <VitalsDisplay vitalData={encounter.vitalData} />
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Vitals Display ────────────────────────────────────────────────────────────
function VitalsDisplay({ vitalData }: { vitalData: Record<string, unknown> | null }) {
  const excluded = new Set(['casesheet', 'dischargeNotes', 'vitals_history',
    'prescriptions', 'diagnostic_orders', 'progress_notes', 'nurse_notes', 'other_charges'])
  const entries = Object.entries(vitalData ?? {})
    .filter(([k]) => !excluded.has(k))

  if (entries.length === 0) {
    return <p className="text-sm text-gray-400">No vitals recorded for this visit.</p>
  }
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
      {entries.map(([key, value]) => (
        <div key={key} className="bg-blue-50 rounded-lg px-3 py-2">
          <p className="text-xs text-blue-500 capitalize">{key.replace(/([A-Z])/g, ' $1').trim()}</p>
          <p className="text-sm font-bold text-blue-900">{String(value)}</p>
        </div>
      ))}
    </div>
  )
}

// ─── Attachments Tab ───────────────────────────────────────────────────────────
function AttachmentsTab({ encounterId, readOnly }: { encounterId: string; readOnly?: boolean }) {
  const qc = useQueryClient()
  const { data: attachments = [] } = useQuery({
    queryKey: ['attachments', encounterId],
    queryFn: () => attachmentApi.getByEncounter(encounterId),
  })

  const [uploading, setUploading] = useState(false)

  const handleUpload = async (file: File) => {
    setUploading(true)
    try {
      await attachmentApi.upload(file, 'VISIT', encounterId)
      qc.invalidateQueries({ queryKey: ['attachments', encounterId] })
      toast({ title: 'File uploaded', variant: 'success' })
    } catch {
      toast({ title: 'Upload failed', variant: 'destructive' })
    } finally { setUploading(false) }
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
              <a href={attachmentApi.getDownloadUrl(a.id)} target="_blank" rel="noreferrer"
                className="text-blue-600 hover:underline shrink-0 ml-2">Download</a>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
