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
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { encounterApi }       from '../../../services/encounter/encounterApi'
import { ipCasesheetApi, recordApi } from '../../../services/casesheet/casesheetApi'
import { ipVitalsApi }        from '../../../services/opip/opipApi'
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
import BackButton             from '../../../components/shared/BackButton'
import { toast }              from '../../../hooks/useToast'
import type { CaseSheetData } from '../../../types/casesheet'

type Tab =
  | 'diag' | 'prescrp' | 'otherChrg' | 'attach'
  | 'dischargeSummary' | 'otNotes' | 'vitalSign'
  | 'progressNotes' | 'nurseNotes' | 'ipBill'

const TABS: { key: Tab; label: string }[] = [
  { key: 'vitalSign',       label: '🩺 Vitals' },
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
  const [activeTab, setActiveTab] = useState<Tab>('vitalSign')
  const [dischargeNotes, setDischargeNotes] = useState('')
  const qc = useQueryClient()

  const { data: encounter, isLoading: encLoading } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn:  () => encounterApi.getById(encounterId!),
    enabled:  !!encounterId,
  })

  // IP casesheet (OT Notes template)
  const { data: csData, isLoading: csLoading } = useQuery({
    queryKey: ['ip-casesheet', encounterId],
    queryFn:  () => ipCasesheetApi.loadCasesheet(encounterId!, undefined),
    enabled:  !!encounterId,
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['encounter', encounterId] })
    qc.invalidateQueries({ queryKey: ['ip-casesheet', encounterId] })
  }

  const saveMut = useMutation({
    mutationFn: (data: CaseSheetData) =>
      recordApi.save(encounterId!, { templateId: csData?.template?.id, data }),
    onSuccess: () => { invalidate(); toast({ title: 'OT Notes saved', variant: 'success' }) },
    onError:   (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const dischargeMut = useMutation({
    mutationFn: () => encounterApi.discharge(encounterId!, { dischargeNotes }),
    onSuccess:  () => { invalidate(); toast({ title: 'Patient discharged', variant: 'success' }) },
    onError:    (e: Error) => toast({ title: 'Discharge failed', description: e.message, variant: 'destructive' }),
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
      {/* Header */}
      <div className="flex items-start justify-between flex-wrap gap-3">
        <div>
          <div className="flex items-center gap-3">
            {encounter.hasBed && (
              <span className="px-2.5 py-0.5 text-sm font-bold bg-red-600 text-white rounded">
                Bed
              </span>
            )}
            <h2 className="text-xl font-bold text-gray-900">IP Case Sheet</h2>
          </div>
          <p className="text-base font-semibold text-blue-700 mt-0.5">{encounter.patientName}</p>
          <p className="text-xs text-gray-500 mt-1">
            {encounter.patientNumber} · Inpatient · Admitted {formatDateTime(encounter.startedAt)}
          </p>
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
          <BackButton />
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
            dischargeMut={dischargeMut}
            isDischarged={isDischarged}
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
function DischargeSummaryTab({ encounter, dischargeNotes, setDischargeNotes, checklistOk, dischargeMut, isDischarged }:
  any) {
  return (
    <div className="space-y-4">
      <h3 className="text-sm font-bold text-gray-900">Discharge Summary</h3>

      {isDischarged ? (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4">
          <p className="text-sm font-semibold text-green-800">
            ✓ Patient was discharged on {formatDateTime(encounter.dischargedAt)}
          </p>
          {encounter.vitalData?.dischargeNotes && (
            <p className="text-xs text-green-700 mt-2 whitespace-pre-wrap">
              {String(encounter.vitalData.dischargeNotes)}
            </p>
          )}
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 opacity-60">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Bed No</label>
              <input readOnly value={encounter.hasBed ? 'Assigned' : 'N/A'}
                className="w-full px-2.5 py-1.5 border border-gray-200 rounded-lg text-sm bg-gray-50 text-gray-600" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Discharge Date</label>
              <input type="date" defaultValue={new Date().toISOString().split('T')[0]}
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>

          {!checklistOk && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 text-xs text-amber-700">
              ⚠ Pre-operative checklist is incomplete. Verify in OT Notes tab before discharge.
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1">
              Discharge Notes / Summary
            </label>
            <textarea rows={6} value={dischargeNotes}
              onChange={e => setDischargeNotes(e.target.value)}
              placeholder="Summarise treatment, discharge medications, follow-up instructions, red flags…"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>

          <button
            onClick={() => dischargeMut.mutate()}
            disabled={dischargeMut.isPending}
            className="px-5 py-2 bg-amber-600 text-white text-sm font-semibold rounded-lg hover:bg-amber-700 disabled:opacity-50 transition-colors">
            {dischargeMut.isPending ? 'Processing…' : '🏠 Confirm Discharge'}
          </button>
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
              <a href={attachmentApi.getDownloadUrl(a.id)} target="_blank" rel="noreferrer"
                className="text-blue-600 hover:underline shrink-0 ml-2">Download</a>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
