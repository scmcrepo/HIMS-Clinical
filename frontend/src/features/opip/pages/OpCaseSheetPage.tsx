/**
 * OpCaseSheetPage.tsx — full OP case sheet with all 4 tabs:
 *  1. Clinical Notes (DynamicCaseSheetForm with template)
 *  2. Prescription (inline)
 *  3. Diagnostic Order (inline)
 *  4. Attachments
 * Plus: Vitals display, Mark Consulted action
 */
import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { opQueueApi, recordApi } from '../../../services/casesheet/casesheetApi'
import { DynamicCaseSheetForm } from '../components/DynamicCaseSheetForm'
import { PrescriptionTab }     from '../components/PrescriptionTab'
import { DiagnosticOrderTab }  from '../components/DiagnosticOrderTab'
import { attachmentApi }       from '../../../services/attachment/attachmentApi'
import { formatDateTime }      from '../../../lib/dateUtils'
import { cn }                  from '../../../lib/utils'
import BackButton              from '../../../components/shared/BackButton'
import { toast }               from '../../../hooks/useToast'
import type { EncounterStatus } from '../../../types/encounter'
import type { CaseSheetData }   from '../../../types/casesheet'

const STATUS_STYLES: Record<EncounterStatus, string> = {
  CHECKED_IN:           'bg-orange-50  text-orange-700  border-orange-200',
  CONSULTATION_STARTED: 'bg-purple-50  text-purple-700  border-purple-200',
  CASESHEET_RECORDED:   'bg-amber-50   text-amber-700   border-amber-200',
  BILLING_DONE:         'bg-green-50   text-green-700   border-green-200',
}
const STATUS_LABELS: Record<EncounterStatus, string> = {
  CHECKED_IN:           'Checked In',
  CONSULTATION_STARTED: 'Vitals Entered',
  CASESHEET_RECORDED:   'Casesheet Done',
  BILLING_DONE:         'Consulted',
}

type Tab = 'clinical' | 'prescription' | 'diagnostic' | 'attachments' | 'vitals'

export default function OpCaseSheetPage() {
  const { encounterId } = useParams<{ encounterId: string }>()
  const [activeTab, setActiveTab] = useState<Tab>('clinical')
  const qc = useQueryClient()

  const { data: encounter, isLoading: encLoading } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn:  () => encounterApi.getById(encounterId!),
    enabled:  !!encounterId,
  })

  const { data: csData, isLoading: csLoading } = useQuery({
    queryKey: ['op-casesheet', encounterId],
    queryFn:  () => opQueueApi.loadCasesheet(encounterId!, 'ORTHOPAEDICS', 'OP'),
    enabled:  !!encounterId,
  })

  const { data: attachments = [] } = useQuery({
    queryKey: ['attachments', encounterId],
    queryFn:  () => attachmentApi.getByEncounter(encounterId!),
    enabled:  !!encounterId,
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['encounter', encounterId] })
    qc.invalidateQueries({ queryKey: ['op-casesheet', encounterId] })
    qc.invalidateQueries({ queryKey: ['op-queue'] })
  }

  const saveMut = useMutation({
    mutationFn: (data: CaseSheetData) =>
      recordApi.save(encounterId!, { templateId: csData?.template?.id, data }),
    onSuccess: () => { invalidate(); toast({ title: 'Case sheet saved', variant: 'success' }) },
    onError:   (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const markConsultedMut = useMutation({
    mutationFn: () => opQueueApi.markConsulted(encounterId!),
    onSuccess:  () => { invalidate(); toast({ title: 'Encounter marked as consulted', variant: 'success' }) },
    onError:    (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  if (encLoading) return <div className="p-6 text-sm text-gray-500">Loading…</div>
  if (!encounter) return <div className="p-6 text-sm text-red-600">Encounter not found</div>

  const canMarkConsulted = encounter.status === 'CASESHEET_RECORDED'
  const isReadOnly       = encounter.status === 'BILLING_DONE'

  const TABS: { key: Tab; label: string }[] = [
    { key: 'clinical',     label: '📋 Clinical Notes' },
    { key: 'prescription', label: '💊 Prescription' },
    { key: 'diagnostic',   label: '🧪 Diagnostic Order' },
    { key: 'attachments',  label: '📎 Attachments' },
    { key: 'vitals',       label: '🩺 Vitals' },
  ]

  return (
    <div className="space-y-4 max-w-6xl">
      {/* Header */}
      <div className="flex items-start justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-xl font-bold text-gray-900">OP Case Sheet</h2>
          <p className="text-base font-semibold text-blue-700 mt-0.5">{encounter.patientName}</p>
          <p className="text-xs text-gray-500 mt-1">
            {encounter.patientNumber} · Outpatient · {formatDateTime(encounter.startedAt)}
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
              className="px-4 py-1.5 bg-green-600 text-white text-xs font-semibold rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors">
              {markConsultedMut.isPending ? 'Marking…' : '✓ Mark Consulted'}
            </button>
          )}
          <BackButton />
        </div>
      </div>

      {/* Tabs */}
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
        <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-2 text-xs text-amber-700">
          This encounter is closed (Consulted). All tabs are read-only.
        </div>
      )}

      {/* Tab content */}
      <div className="bg-white border border-gray-200 rounded-xl p-5">
        {activeTab === 'clinical' && (
          csLoading ? (
            <div className="text-sm text-gray-500 py-8 text-center">Loading case sheet…</div>
          ) : csData?.template ? (
            <DynamicCaseSheetForm
              template={csData.template}
              initialData={csData.records[0]?.data}
              onSave={data => saveMut.mutate(data)}
              isSaving={saveMut.isPending}
              readOnly={isReadOnly}
            />
          ) : (
            <div className="border border-dashed border-gray-200 rounded-xl p-8 text-center text-sm text-gray-400">
              No OP template found.
              <br />Contact admin to configure a template for this specialization.
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

      {/* Quick links */}
      <div className="flex gap-4 text-xs text-blue-600 pt-1 border-t border-gray-100 flex-wrap">
        <Link to={`/billing/create?encounterId=${encounterId}&patientId=${encounter.patientId}`}
          className="hover:underline">→ Create Bill</Link>
        <Link to={`/diagnostics?encounterId=${encounterId}&patientId=${encounter.patientId}`}
          className="hover:underline">→ Diagnostics / Imaging</Link>
        <Link to="/op-queue" className="hover:underline">← Back to OP Queue</Link>
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
    queryFn:  () => attachmentApi.getByEncounter(encounterId),
  })

  const uploadRef = useState<HTMLInputElement | null>(null)
  const [uploading, setUploading] = useState(false)

  const handleUpload = async (file: File) => {
    setUploading(true)
    try {
      await attachmentApi.upload(file, 'CLINICAL', encounterId)
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
