/**
 * OpQueuePage.tsx — full OP Queue with:
 *  - Consultant / Status / Date filters
 *  - Vitals modal (per row)
 *  - Referral modal
 *  - Admission Request modal
 *  - Profile / Medical Record link
 *  - Waiting time display
 *  - Auto-refresh every 60s
 */
import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { opQueueApi } from '../../../services/casesheet/casesheetApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { VitalSignsModal } from '../components/VitalSignsModal'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import DatePicker from '../../../components/shared/DatePicker'
import type { EncounterSummary, EncounterStatus } from '../../../types/encounter'
import { Stethoscope, ClipboardList, Forward, Building2 } from 'lucide-react'

// ── Status config ──────────────────────────────────────────────────────────────
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

function waitingTime(startedAt: string, endedAt?: string | null): string {
  const startMs = new Date(startedAt).getTime()
  let endMs = endedAt ? new Date(endedAt).getTime() : Date.now()
  const maxEndMs = startMs + 24 * 60 * 60 * 1000
  if (endMs > maxEndMs) {
    endMs = maxEndMs
  }
  const ms = endMs - startMs
  const totalMins = Math.max(0, Math.floor(ms / 60000))
  const hrs = Math.floor(totalMins / 60)
  const mins = totalMins % 60
  if (hrs > 0) return `${hrs} hr ${mins} min`
  return `${mins} min`
}

// ── Page ───────────────────────────────────────────────────────────────────────
export default function OpQueuePage() {
  const [query, setQuery] = useState('')
  const [consultant, setConsultant] = useState('')
  const [statusFilter, setStatusFilter] = useState<EncounterStatus | ''>('')
  const [date, setDate] = useState(() => new Date().toISOString().split('T')[0])

  // Active modals
  const [vitalsEncId, setVitalsEncId] = useState<string | null>(null)
  const [referralEncId, setReferralEncId] = useState<string | null>(null)
  const [admitEncId, setAdmitEncId] = useState<string | null>(null)

  const qc = useQueryClient()

  // Automatically refresh outpatient list when entering the page
  useEffect(() => {
    qc.invalidateQueries({ queryKey: ['op-queue'] })
  }, [qc])

  const { data, isLoading } = useQuery({
    queryKey: ['op-queue', query, consultant, statusFilter, date],
    queryFn: () => encounterApi.getTodayOutpatients(query || undefined, date),
    refetchInterval: 60_000,
  })

  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'], queryFn: consultantApi.getAll,
  })

  let encounters: EncounterSummary[] = data?.content ?? []

  // Client-side filtering (consultant + status)
  if (consultant) encounters = encounters.filter(e => e.primaryProviderId === consultant)
  if (statusFilter) encounters = encounters.filter(e => e.status === statusFilter)

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Out Patients</h2>
          <p className="text-sm text-gray-500 mt-0.5">Today's outpatient queue</p>
        </div>
        <span className="text-xs text-gray-400">{encounters.length} patient{encounters.length !== 1 ? 's' : ''}</span>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        {/* Search */}
        <input
          type="search"
          placeholder="Search patient name or number…"
          value={query}
          onChange={e => setQuery(e.target.value)}
          className="w-64 px-3 py-1.5 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500"
        />

        <div className="w-48">
          <DatePicker value={date} onChange={val => setDate(val || new Date().toISOString().split('T')[0])} />
        </div>

        <div className="w-64">
          <ConsultantSearchInput
            consultants={consultants}
            value={consultant}
            onChange={setConsultant}
            placeholder="All Consultants"
          />
        </div>

        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as any)}
          className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500">
          <option value="">All Statuses</option>
          {(Object.entries(STATUS_LABELS) as [EncounterStatus, string][]).map(([k, v]) => (
            <option key={k} value={k}>{v}</option>
          ))}
        </select>

      </div>

      {/* Table */}
      {isLoading ? (
        <div className="text-sm text-gray-500 py-8 text-center">Loading queue…</div>
      ) : encounters.length === 0 ? (
        <div className="text-sm text-gray-400 py-12 text-center border border-dashed border-gray-200 rounded-xl">
          No OP visits matching filters
        </div>
      ) : (
        <div className="bg-white border border-gray-200 rounded-xl overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {['Patient No', 'Patient Name', 'Consultant', 'Waiting Time', 'Status', 'Actions'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-gray-600 uppercase tracking-wide">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {encounters.map(enc => (
                <tr key={enc.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 text-xs text-gray-500 font-mono">{enc.patientNumber ?? '—'}</td>
                  <td className="px-4 py-3">
                    <p className="font-semibold text-gray-900">{enc.patientName}</p>
                    <p className="text-xs text-gray-400">{enc.patientAge} · {enc.patientGender}</p>
                  </td>
                  <td className="px-4 py-3 text-gray-700 text-xs">{enc.providerName ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">
                    {(enc.dischargedAt || (new Date().getTime() - new Date(enc.startedAt).getTime() >= 24 * 60 * 60 * 1000)) ? (
                      <span className="text-green-600 font-medium">
                        {waitingTime(enc.startedAt, enc.dischargedAt)}
                      </span>
                    ) : (
                      waitingTime(enc.startedAt)
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className={cn(
                      'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border',
                      STATUS_STYLES[enc.status]
                    )}>
                      {STATUS_LABELS[enc.status]}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1.5">
                      {/* Vitals */}
                      <ActionBtn
                        icon={Stethoscope}
                        title={enc.status === 'BILLING_DONE' ? 'View Vitals' : 'Record Vitals'}
                        onClick={() => setVitalsEncId(enc.id)}
                        variant="blue"
                      />
                      {/* Profile / Casesheet */}
                      <Link
                        to={`/op-casesheet/${enc.id}`}
                        title="Profile / Case Sheet"
                        className="inline-flex items-center px-2 py-1.5 text-xs font-semibold bg-neutral-600 text-white rounded-lg hover:bg-neutral-700 transition-colors"
                      >
                        <ClipboardList size={14} />
                      </Link>
                      {/* Referral */}
                      <ActionBtn
                        icon={Forward}
                        title="Referral"
                        onClick={() => setReferralEncId(enc.id)}
                        variant="purple"
                        disabled={enc.status === 'BILLING_DONE'}
                      />
                      {/* Admission Request */}
                      <ActionBtn
                        icon={Building2}
                        title="Admission Request"
                        onClick={() => setAdmitEncId(enc.id)}
                        variant="amber"
                        disabled={enc.status === 'BILLING_DONE'}
                      />


                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Modals */}
      {vitalsEncId && (
        <VitalSignsModal
          encounterId={vitalsEncId}
          mode="OP"
          readOnly={encounters.find(e => e.id === vitalsEncId)?.status === 'BILLING_DONE'}
          onClose={() => setVitalsEncId(null)}
          onSaved={() => {
            setVitalsEncId(null)
            qc.invalidateQueries({ queryKey: ['op-queue'] })
          }}
        />
      )}

      {referralEncId && (
        <ReferralModal
          encounterId={referralEncId}
          patientId={encounters.find(e => e.id === referralEncId)?.patientId || ''}
          consultants={consultants}
          onClose={() => setReferralEncId(null)}
          onSaved={() => { setReferralEncId(null); qc.invalidateQueries({ queryKey: ['op-queue'] }) }}
        />
      )}

      {admitEncId && (
        <AdmissionRequestModal
          encounterId={admitEncId}
          onClose={() => setAdmitEncId(null)}
          onSaved={() => { setAdmitEncId(null); qc.invalidateQueries({ queryKey: ['op-queue'] }) }}
        />
      )}
    </div>
  )
}

// ── Action Button helper ───────────────────────────────────────────────────────
function ActionBtn({ icon: Icon, title, onClick, variant, disabled }:
  { icon: any; title: string; onClick: () => void; variant: 'blue' | 'purple' | 'amber'; disabled?: boolean }) {
  const colors = {
    blue: 'bg-neutral-50 text-neutral-700 hover:bg-neutral-100 border-neutral-200',
    purple: 'bg-neutral-50 text-neutral-700 hover:bg-neutral-100 border-neutral-200',
    amber: 'bg-neutral-600 text-white hover:bg-neutral-700 border-transparent',
  }
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={cn(
        'inline-flex items-center px-2 py-1.5 text-xs font-semibold rounded-lg border transition-colors',
        colors[variant],
        disabled && 'opacity-40 cursor-not-allowed'
      )}
    >
      <Icon size={14} className="shrink-0" />
    </button>
  )
}

// ── Referral Modal ─────────────────────────────────────────────────────────────
function ReferralModal({ encounterId, patientId, consultants, onClose, onSaved }:
  { encounterId: string; patientId: string; consultants: any[]; onClose: () => void; onSaved: () => void }) {

  const [targetConsultantId, setTargetConsultantId] = useState('')

  const saveMut = useMutation({
    mutationFn: async () => {
      if (!targetConsultantId) throw new Error('Please select a consultant')
      // 1. Create a new outpatient encounter for the referred consultant
      await encounterApi.createOutpatient({
        patientId,
        primaryProviderId: targetConsultantId,
        visitMode: 'WALK_IN',
      })
      // 2. Update share map of original encounter for referral logging
      return encounterApi.updateConsultantShare(encounterId, targetConsultantId, {
        type: 'REFERRAL', referredAt: new Date().toISOString(),
      })
    },
    onSuccess: () => {
      toast({ title: 'Referral & encounter created successfully', variant: 'success' })
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200" style={{ marginTop: 0 }} >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
          <h3 className="text-base font-bold text-gray-900">Refer Patient</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>
        <div className="p-5 space-y-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Refer to Consultant *</label>
            <ConsultantSearchInput
              consultants={consultants}
              value={targetConsultantId}
              onChange={setTargetConsultantId}
              placeholder="— Select Consultant —"
            />
          </div>
        </div>
        <div className="flex justify-end gap-2 px-5 py-4 border-t border-gray-200">
          <button onClick={onClose} className="px-4 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50">CANCEL</button>
          <button onClick={() => saveMut.mutate()} disabled={saveMut.isPending || !targetConsultantId}
            className="px-5 py-1.5 text-sm font-semibold bg-neutral-600 text-white rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
            {saveMut.isPending ? 'Saving…' : 'REFER'}
          </button>
        </div>
      </div>
    </div>
  )
}

function AdmissionRequestModal({ encounterId, onClose, onSaved }:
  { encounterId: string; onClose: () => void; onSaved: () => void }) {

  const [reason, setReason] = useState('')
  const [adviceToPatient, setAdviceToPatient] = useState('')
  const [nurseInstructions, setNurseInstructions] = useState('')
  const [admissionDate, setAdmissionDate] = useState('')

  const { data: encounter } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn: () => encounterApi.getById(encounterId),
    enabled: !!encounterId,
  })

  const saveMut = useMutation({
    mutationFn: () => {
      if (!reason.trim()) throw new Error('Reason is required')
      return opQueueApi.requestAdmission(encounterId, {
        reason, adviceToPatient, instructionsToNurses: nurseInstructions,
      })
    },
    onSuccess: () => {
      toast({ title: 'Admission request submitted', variant: 'success' })
      onSaved()
    },
    onError: (e: any) => toast({ title: 'Failed', description: e.response?.data?.message || e.message || 'Failed', variant: 'destructive' }),
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200" style={{ marginTop: 0 }} >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200">
          <div>
            <h3 className="text-base font-bold text-gray-900">Admission Request</h3>
            {encounter && (
              <p className="text-xs text-gray-500 mt-1">
                Patient: <span className="font-semibold text-gray-800">{encounter.patientName}</span> ({encounter.patientNumber})
              </p>
            )}
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>
        <div className="p-5 space-y-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Reason for Admission *</label>
            <textarea rows={3} value={reason} onChange={e => setReason(e.target.value)}
              placeholder="Clinical reason for IP admission…"
              className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-neutral-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Advice to Patient</label>
            <textarea rows={2} value={adviceToPatient} onChange={e => setAdviceToPatient(e.target.value)}
              placeholder="Pre-admission instructions for the patient…"
              className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-neutral-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Instructions to Nurses</label>
            <textarea rows={2} value={nurseInstructions} onChange={e => setNurseInstructions(e.target.value)}
              className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-neutral-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Requested Admission Date</label>
            <DatePicker value={admissionDate} onChange={val => setAdmissionDate(val || new Date().toISOString().split('T')[0])} size="sm" />
          </div>
        </div>
        <div className="flex justify-end gap-2 px-5 py-4 border-t border-gray-200">
          <button onClick={onClose} className="px-4 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50">CANCEL</button>
          <button onClick={() => saveMut.mutate()} disabled={saveMut.isPending || !reason.trim()}
            className="px-5 py-1.5 text-sm font-semibold bg-neutral-600 text-white rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
            {saveMut.isPending ? 'Saving…' : 'SUBMIT REQUEST'}
          </button>
        </div>
      </div>
    </div>
  )
}
