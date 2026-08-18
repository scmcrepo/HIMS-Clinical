import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  insuranceApi,
  type CreateInsuranceCmd,
  type InsurancePreAuthType,
} from '../../../services/insurance/insuranceApi'
import { PatientSearchInput } from '../../../components/shared/PatientSearchInput'
import DatePicker from '../../../components/shared/DatePicker'
import type { Patient } from '../../../types/patient'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import {
  STAGE_LABELS,
  WORKFLOW_STAGES,
  formatPaise,
  type WorkflowStage,
} from '../insuranceDesk'
import { InsuranceDeskModal } from '../components/InsuranceDeskModal'
import { inputCls, labelCls } from '../components/formPrimitives'

/**
 * The insurance desk worklist (WO-020 / ID-007).
 *
 * Replaces the old flat "pending pre-authorisations" table, which showed one
 * step of a seven-step process and offered Settle/Reject buttons that skipped
 * the middle five.
 *
 * Defaults to the last 30 days rather than today: a desk screen that opens
 * empty every morning reads as broken, and the claims that need chasing are the
 * older ones anyway.
 */

const STAGE_STYLES: Partial<Record<WorkflowStage, string>> = {
  PREAUTHORISATION: 'bg-blue-50 text-blue-700 border-blue-200',
  PREAUTHORISATION_APPROVAL: 'bg-purple-50 text-purple-700 border-purple-200',
  PREAUTHORISATION_REJECTED: 'bg-red-50 text-red-700 border-red-200',
  ENHANCEMENT_REQUEST: 'bg-amber-50 text-amber-700 border-amber-200',
  ENHANCEMENT_APPROVAL: 'bg-purple-50 text-purple-700 border-purple-200',
  ENHANCEMENT_REJECTED: 'bg-orange-50 text-orange-700 border-orange-200',
  CHECK_LIST_ENTRY: 'bg-cyan-50 text-cyan-700 border-cyan-200',
  DISPATCH_ENTRY: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  DISALLOWANCE_ENTRY: 'bg-green-50 text-green-700 border-green-200',
}

const isoDaysAgo = (n: number) =>
  new Date(Date.now() - n * 86_400_000).toISOString().slice(0, 10)

export default function InsurancePage() {
  const qc = useQueryClient()

  const [fromDate, setFromDate] = useState(isoDaysAgo(30))
  const [toDate, setToDate] = useState(isoDaysAgo(0))
  const [stage, setStage] = useState<WorkflowStage | ''>('')
  const [openClaimId, setOpenClaimId] = useState<string | null>(null)

  const [showForm, setShowForm] = useState(false)
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [form, setForm] = useState({
    insurerName: '',
    tpaName: '',
    policyNumber: '',
    memberId: '',
    preAuthType: '' as InsurancePreAuthType | '',
  })

  const { data: claims, isLoading } = useQuery({
    queryKey: ['insurance', 'search', fromDate, toDate, stage],
    queryFn: () =>
      insuranceApi.search({
        searchFromDate: fromDate,
        searchToDate: toDate,
        ...(stage ? { stage } : {}),
      }),
  })

  const createMutation = useMutation({
    mutationFn: (cmd: CreateInsuranceCmd) => insuranceApi.create(cmd),
    onSuccess: created => {
      qc.invalidateQueries({ queryKey: ['insurance'] })
      setShowForm(false)
      setSelectedPatient(null)
      setForm({ insurerName: '', tpaName: '', policyNumber: '', memberId: '', preAuthType: '' })
      toast({ title: 'Claim created', variant: 'success' })
      // Straight into the desk — creating a record is never the goal, working
      // the claim is.
      setOpenClaimId(created.id)
    },
    onError: (e: Error) =>
      toast({ title: 'Could not create claim', description: e.message, variant: 'destructive' }),
  })

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Insurance desk</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            Pre-authorisation through to settlement, for TPA and corporate credit claims.
          </p>
        </div>
        <button
          onClick={() => setShowForm(v => !v)}
          className="px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors"
        >
          + New claim
        </button>
      </div>

      {showForm && (
        <div
          className="bg-neutral-50 border border-neutral-200 rounded-xl p-5 space-y-4"
          role="region"
          aria-label="Create claim"
        >
          <h3 className="text-sm font-semibold text-neutral-900">New claim</h3>

          <div>
            <label className={labelCls}>Patient</label>
            <PatientSearchInput
              selectedPatient={selectedPatient}
              onSelect={setSelectedPatient}
              placeholder="Search patient…"
              className="max-w-sm"
            />
          </div>

          <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
            <div>
              <label className={labelCls}>
                Insurer <span className="text-red-500">*</span>
              </label>
              <input
                value={form.insurerName}
                onChange={e => setForm(f => ({ ...f, insurerName: e.target.value }))}
                placeholder="e.g. Star Health"
                className={inputCls}
                aria-label="Insurer"
              />
            </div>
            <div>
              <label className={labelCls}>TPA</label>
              <input
                value={form.tpaName}
                onChange={e => setForm(f => ({ ...f, tpaName: e.target.value }))}
                placeholder="e.g. Medi Assist"
                className={inputCls}
                aria-label="TPA"
              />
            </div>
            <div>
              <label className={labelCls}>Policy number</label>
              <input
                value={form.policyNumber}
                onChange={e => setForm(f => ({ ...f, policyNumber: e.target.value }))}
                className={inputCls}
                aria-label="Policy number"
              />
            </div>
            <div>
              <label className={labelCls}>Member / card id</label>
              <input
                value={form.memberId}
                onChange={e => setForm(f => ({ ...f, memberId: e.target.value }))}
                className={inputCls}
                aria-label="Member id"
              />
            </div>
            <div>
              <label className={labelCls}>Pre-auth type</label>
              <select
                value={form.preAuthType}
                onChange={e =>
                  setForm(f => ({ ...f, preAuthType: e.target.value as InsurancePreAuthType | '' }))
                }
                className={inputCls}
                aria-label="Pre-auth type"
              >
                <option value="">Select…</option>
                <option value="PLANNED">Planned admission</option>
                <option value="EMERGENCY">Emergency</option>
                <option value="DAY_CARE">Day care</option>
                <option value="OPD">OPD</option>
                <option value="MATERNITY">Maternity</option>
              </select>
            </div>
          </div>

          <div className="flex gap-3">
            <button
              onClick={() => {
                const cmd: CreateInsuranceCmd = { insurerName: form.insurerName }
                if (selectedPatient?.id) cmd.patientId = selectedPatient.id
                if (form.tpaName) cmd.tpaName = form.tpaName
                if (form.policyNumber) cmd.policyNumber = form.policyNumber
                if (form.memberId) cmd.memberId = form.memberId
                if (form.preAuthType) cmd.preAuthType = form.preAuthType
                createMutation.mutate(cmd)
              }}
              disabled={!form.insurerName.trim() || createMutation.isPending}
              className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors"
            >
              {createMutation.isPending ? 'Creating…' : 'Create claim'}
            </button>
            <button
              onClick={() => setShowForm(false)}
              className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label className={labelCls}>From</label>
          <DatePicker value={fromDate} onChange={setFromDate} size="sm" />
        </div>
        <div>
          <label className={labelCls}>To</label>
          <DatePicker value={toDate} onChange={setToDate} size="sm" />
        </div>
        <div className="min-w-48">
          <label className={labelCls}>Stage</label>
          <select
            value={stage}
            onChange={e => setStage(e.target.value as WorkflowStage | '')}
            className={inputCls}
            aria-label="Filter by stage"
          >
            <option value="">All stages</option>
            {WORKFLOW_STAGES.map(s => (
              <option key={s} value={s}>
                {STAGE_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Worklist */}
      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-900">Claims</h3>
          <span className="text-xs text-gray-400">{claims?.length ?? 0} shown</span>
        </div>

        {isLoading && (
          <p className="px-5 py-10 text-center text-sm text-gray-400" aria-live="polite">
            Loading claims…
          </p>
        )}

        {claims && (
          <table className="w-full text-sm" aria-label="Insurance claims">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                <th className="px-4 py-2.5 font-semibold text-gray-600">Insurer / TPA</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Claim no.</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Stage</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Bill</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-right">Sanctioned</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-right">Received</th>
                <th className="px-4 py-2.5" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {claims.map(c => (
                <tr key={c.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3">
                    <span className="block font-medium text-gray-900">{c.insurerName}</span>
                    {c.tpaName && (
                      <span className="block text-xs text-gray-400">{c.tpaName}</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-gray-600 font-mono text-xs">{c.claimNo ?? '—'}</td>
                  <td className="px-4 py-3">
                    {c.currentStage ? (
                      <span
                        className={cn(
                          'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border',
                          STAGE_STYLES[c.currentStage] ??
                            'bg-gray-50 text-gray-600 border-gray-200',
                        )}
                      >
                        {STAGE_LABELS[c.currentStage]}
                      </span>
                    ) : (
                      // A record created before the desk flow existed. Saying
                      // "not started" would be wrong; it may well be settled.
                      <span className="text-xs text-gray-400">Not started</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {c.billLinked ? (
                      <span className="text-xs text-gray-500">Linked</span>
                    ) : (
                      <span className="text-xs text-amber-600">Not linked</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-800">
                    {formatPaise(c.effectiveApprovedLimit)}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-800">
                    {c.totalReceived > 0 ? formatPaise(c.totalReceived) : '—'}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => setOpenClaimId(c.id)}
                      className="text-xs text-neutral-600 hover:text-neutral-900 font-medium"
                    >
                      Open claim →
                    </button>
                  </td>
                </tr>
              ))}
              {claims.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-12 text-center text-gray-400 text-sm">
                    No claims in this date range. Widen the dates, or start one with “New claim”.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {openClaimId && (
        <InsuranceDeskModal insuranceId={openClaimId} onClose={() => setOpenClaimId(null)} />
      )}
    </div>
  )
}
