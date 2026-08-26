import { useState, useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
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
import { payerApi } from '../../../services/masters/masterApi'
import { billingApi } from '../../../services/billing/billingApi'
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
  const location = useLocation()
  const navState = location.state as { openClaimId?: string } | null
  const pageNavigate = useNavigate()

  const [fromDate, setFromDate] = useState(isoDaysAgo(30))
  const [toDate, setToDate] = useState(isoDaysAgo(0))
  const [stage, setStage] = useState<WorkflowStage | ''>('')
  const [page, setPage] = useState(0)
  const [openClaimId, setOpenClaimId] = useState<string | null>(navState?.openClaimId ?? null)

  // Reset page when filters change
  useEffect(() => {
    setPage(0)
  }, [fromDate, toDate, stage])

  // Clear navigation state after consuming it so a page refresh doesn't re-open the modal
  useEffect(() => {
    if (navState?.openClaimId) {
      pageNavigate(location.pathname, { replace: true, state: {} })
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const [showForm, setShowForm] = useState(false)
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [form, setForm] = useState({
    insurerName: '',
    tpaName: '',
    policyNumber: '',
    memberId: '',
    preAuthType: '' as InsurancePreAuthType | '',
  })

  const { data: searchResponse, isLoading } = useQuery({
    queryKey: ['insurance', 'search', fromDate, toDate, stage, page],
    queryFn: () =>
      insuranceApi.search({
        searchFromDate: fromDate,
        searchToDate: toDate,
        ...(stage ? { stage } : {}),
        page,
        size: 5,
      }),
  })
  
  const claims = searchResponse?.content

  const { data: payers = [] } = useQuery({
    queryKey: ['payers'],
    queryFn: payerApi.getAll,
  })

  const { data: patientInsuranceRecords } = useQuery({
    queryKey: ['insurance', 'patient', selectedPatient?.id],
    queryFn: () => insuranceApi.getByPatient(selectedPatient!.id),
    enabled: !!selectedPatient?.id,
  })

  // Fetch the patient's bills to find the payorId from their credit bill
  const { data: patientBills } = useQuery({
    queryKey: ['patient-bills', selectedPatient?.id],
    queryFn: () => billingApi.getBillsByPatient(selectedPatient!.id),
    enabled: !!selectedPatient?.id,
  })

  // Find the latest inpatient credit bill and fetch its full details (which includes payorId)
  const latestCreditBill = patientBills?.find(
    b => b.billType === 'CREDIT' && b.encounterType === 'INPATIENT',
  )
  const { data: creditBillDetail } = useQuery({
    queryKey: ['bill-detail', latestCreditBill?.id],
    queryFn: () => billingApi.getBillById(latestCreditBill!.id),
    enabled: !!latestCreditBill?.id,
    retry: false,
  })

  // Auto-populate insurer when a patient is selected
  useEffect(() => {
    if (!selectedPatient) return

    // Priority 1: Check the patient's credit bill for payorId
    if (creditBillDetail?.payorId) {
      const matchingPayer = payers.find(
        (p: any) => p.id === creditBillDetail.payorId && (p.status === 1 || p.status === 'ACTIVE')
      )
      if (matchingPayer) {
        setForm(f => ({ ...f, insurerName: matchingPayer.id }))
        return
      }
    }

    // Priority 2: Fall back to previous insurance records
    if (patientInsuranceRecords && patientInsuranceRecords.length > 0) {
      const latest = patientInsuranceRecords[0]
      const matchingPayer = payers.find(
        (p: any) => (p.status === 1 || p.status === 'ACTIVE') &&
          p.name.toLowerCase() === latest.insurerName?.toLowerCase()
      )
      setForm(f => ({
        ...f,
        insurerName: matchingPayer ? matchingPayer.id : latest.insurerName || '',
      }))
    }
  }, [selectedPatient, creditBillDetail, patientInsuranceRecords, payers])

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
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="new-claim-title"
        >
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xl w-full max-w-5xl flex flex-col overflow-visible">
            {/* Modal Header */}
            <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
              <div>
                <h3 id="new-claim-title" className="font-semibold text-lg text-gray-900">
                  New claim
                </h3>
                <p className="text-xs text-gray-500 mt-0.5">
                  Create a new pre-authorisation / insurance claim record.
                </p>
              </div>
              <button
                onClick={() => setShowForm(false)}
                className="text-gray-400 hover:text-gray-600 text-sm p-1.5 rounded-lg hover:bg-gray-100 transition-colors"
                aria-label="Close modal"
              >
                ✕
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 space-y-4 max-h-[75vh] overflow-y-visible">
              <div>
                <label className={labelCls}>Patient</label>
                <PatientSearchInput
                  selectedPatient={selectedPatient}
                  onSelect={setSelectedPatient}
                  placeholder="Search patient…"
                  className="w-full"
                />
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 pt-1">
                <div>
                  <label className={labelCls}>
                    Insurer <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={form.insurerName}
                    onChange={e => setForm(f => ({ ...f, insurerName: e.target.value }))}
                    className={inputCls}
                    aria-label="Insurer"
                  >
                    <option value="">Select Insurer</option>
                    {payers
                      .filter((p: any) => p.status === 1 || p.status === 'ACTIVE')
                      .map((p: any) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    <option value="OTHER">OTHER</option>
                  </select>
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
            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 border-t border-gray-100 flex justify-end gap-3 bg-gray-50 rounded-b-2xl">
              <button
                onClick={() => setShowForm(false)}
                className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  const cmd: CreateInsuranceCmd = {
                    insurerName: (() => {
                      const payer = payers.find((p: any) => p.id === form.insurerName)
                      return payer ? payer.name : form.insurerName
                    })(),
                  }
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
            </div>
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
          <span className="text-xs text-gray-400">{searchResponse?.totalElements ?? 0} total</span>
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
                <th className="px-4 py-2.5 font-semibold text-gray-600">S.NO</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">PATIENT NO</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">PATIENT NAME</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">INSURER</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">TPA</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-right">APPROVED AMOUNT</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-right">BILL AMOUNT</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">STATUS</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-right">ACTION</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {claims.map((c, index) => (
                <tr key={c.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 text-gray-500 text-xs">{index + 1}</td>
                  <td className="px-4 py-3 text-gray-800 font-mono text-xs">
                    {c.patientNo || c.claimNo || '—'}
                  </td>
                  <td className="px-4 py-3 font-medium text-gray-900">
                    {c.patientName || '—'}
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {c.insurerName || '—'}
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {c.tpaName || '—'}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-800">
                    {c.effectiveApprovedLimit != null ? formatPaise(c.effectiveApprovedLimit) : '0'}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-800">
                    {c.billAmount != null ? formatPaise(c.billAmount) : (c.billLinked ? '0' : '—')}
                  </td>
                  <td className="px-4 py-3">
                    {c.currentStage ? (
                      <div className="space-y-1">
                        <span
                          className={cn(
                            'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border',
                            STAGE_STYLES[c.currentStage] ??
                              'bg-gray-50 text-gray-600 border-gray-200',
                          )}
                        >
                          {STAGE_LABELS[c.currentStage]}
                        </span>
                        {c.podNo && (
                          <div className="text-[11px] text-indigo-700 font-medium font-mono">
                            POD: {c.podNo}
                          </div>
                        )}
                      </div>
                    ) : (
                      // A record created before the desk flow existed. Saying
                      // "not started" would be wrong; it may well be settled.
                      <span className="text-xs text-gray-400">Not started</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => setOpenClaimId(c.id)}
                      className="inline-flex items-center justify-center p-1.5 border border-gray-200 rounded-md text-gray-500 hover:text-gray-900 hover:bg-gray-100 transition-colors"
                      title="Open claim"
                      aria-label={`Open claim for ${c.patientName || c.insurerName}`}
                    >
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                      </svg>
                    </button>
                  </td>
                </tr>
              ))}
              {claims.length === 0 && (
                <tr>
                  <td colSpan={9} className="px-4 py-12 text-center text-gray-400 text-sm">
                    No claims in this date range. Widen the dates, or start one with “New claim”.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}

        {searchResponse && (
          <div className="flex items-center justify-between px-6 py-4 border-t border-gray-100 bg-gray-50/50 text-xs font-bold text-gray-500">
            <span>SHOWING {claims?.length ?? 0} OF {searchResponse.totalElements} RESULTS</span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1 border border-gray-200 rounded bg-white disabled:opacity-40 hover:bg-gray-50 transition-all"
              >
                PREV
              </button>
              <span className="px-2">PAGE {page + 1} OF {Math.max(1, searchResponse.totalPages)}</span>
              <button
                onClick={() => setPage(p => p + 1)}
                disabled={page >= searchResponse.totalPages - 1}
                className="px-3 py-1 border border-gray-200 rounded bg-white disabled:opacity-40 hover:bg-gray-50 transition-all"
              >
                NEXT
              </button>
            </div>
          </div>
        )}
      </div>

      {openClaimId && (
        <InsuranceDeskModal insuranceId={openClaimId} onClose={() => setOpenClaimId(null)} />
      )}
    </div>
  )
}
