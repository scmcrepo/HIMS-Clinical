import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Loader2, Plus, Search, ShieldCheck, Trash2 } from 'lucide-react'

import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { complianceApi } from '../../../services/compliance/complianceApi'
import { patientApi } from '../../../services/patient/patientApi'
import type { Patient } from '../../../types/patient'
import type {
  RightsRequest,
  RightsRequestState,
  RightsRequestType,
  VerificationMethod,
} from '../../../types/compliance'
import ErasureReceiptModal from './ErasureReceiptModal'
import { Modal } from '../../../components/ui/Modal'

const STATES: { value: RightsRequestState; label: string }[] = [
  { value: 'RECEIVED', label: 'Awaiting verification' },
  { value: 'IN_PROGRESS', label: 'Verified, not yet run' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'PARTIALLY_COMPLETED', label: 'Partially completed' },
  { value: 'REJECTED', label: 'Refused' },
]

const VERIFICATION_METHODS: { value: VerificationMethod; label: string }[] = [
  { value: 'IN_PERSON_ID', label: 'Photo ID, in person' },
  { value: 'PORTAL_OTP', label: 'Portal OTP' },
  { value: 'ABHA_VERIFIED', label: 'ABHA verified' },
  { value: 'REGISTERED_POST', label: 'Registered post' },
  { value: 'STAFF_OVERRIDE', label: 'Staff override (audited)' },
]

/**
 * The queue of erasure and correction requests — WO-024.
 *
 * <p>Verification and execution are separate buttons, mirroring the separate
 * server permissions. Merging them into one "process" action would mean a single
 * misclick both asserted the requester's identity and irreversibly cleared their
 * record.
 *
 * <p>Note what this screen does not show: patient names. A queue of erasure
 * requests is a list of people who asked to be forgotten, and rendering their
 * names into an admin table would be its own small irony. Operators work from
 * the patient id and open the record separately, where that access is audited.
 */
export default function RightsQueuePage() {
  const queryClient = useQueryClient()
  const [state, setState] = useState<RightsRequestState>('RECEIVED')
  const [openReceipt, setOpenReceipt] = useState<string | null>(null)
  const [confirming, setConfirming] = useState<string | null>(null)

  // Raise Request Modal State
  const [showRaiseModal, setShowRaiseModal] = useState(false)
  const [patientSearch, setPatientSearch] = useState('')
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [requestType, setRequestType] = useState<RightsRequestType>('ERASURE')
  const [requestedVia, setRequestedVia] = useState('IN_PERSON')

  const { data: searchResults = [], isLoading: isSearching } = useQuery({
    queryKey: ['patient-search-rights', patientSearch],
    queryFn: () => patientApi.search(patientSearch, 0, 5).then(res => res.content),
    enabled: patientSearch.trim().length >= 2 && !selectedPatient,
  })

  const { data: requests = [], isLoading } = useQuery({
    queryKey: ['rights-queue', state],
    queryFn: () => complianceApi.queue(state),
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['rights-queue'] })
    queryClient.invalidateQueries({ queryKey: ['rights-patient-history'] })
  }

  const raiseMutation = useMutation({
    mutationFn: () => {
      if (!selectedPatient) throw new Error('Please select a patient')
      return complianceApi.raise({
        patientId: selectedPatient.id,
        requestType,
        requestedVia,
        requestedByPatient: true,
      })
    },
    onSuccess: () => {
      toast({ title: 'Rights request created successfully' })
      setShowRaiseModal(false)
      setSelectedPatient(null)
      setPatientSearch('')
      invalidate()
    },
    onError: (err: any) => {
      toast({
        title: 'Could not create request',
        description: err?.response?.data?.message || err.message,
        variant: 'destructive',
      })
    },
  })

  const verify = useMutation({
    mutationFn: ({ id, method }: { id: string; method: VerificationMethod }) =>
      complianceApi.verify(id, method),
    onSuccess: () => {
      toast({ title: 'Requester verified' })
      invalidate()
    },
    onError: (e: Error) => toast({ title: 'Could not verify', description: e.message }),
  })

  const execute = useMutation({
    mutationFn: (id: string) => complianceApi.execute(id),
    onSuccess: receipt => {
      setConfirming(null)
      setOpenReceipt(receipt.request.id)
      toast({
        title: 'Erasure processed',
        description: `${receipt.erased} store(s) erased, ${receipt.anonymised} anonymised, ${receipt.retained} retained.`,
      })
      invalidate()
    },
    onError: (e: Error) => {
      setConfirming(null)
      toast({ title: 'Erasure failed', description: e.message })
    },
  })

  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      complianceApi.reject(id, reason),
    onSuccess: () => {
      toast({ title: 'Request refused' })
      invalidate()
    },
  })

  const handleReject = (request: RightsRequest) => {
    // Mandatory server-side too; asked here so the operator is not bounced by a
    // validation error after they have already committed to refusing.
    const reason = window.prompt(
      'Why is this request being refused? The patient will be shown this.',
    )
    if (reason?.trim()) reject.mutate({ id: request.id, reason: reason.trim() })
  }

  return (
    <div className="space-y-4 p-6">
      <header className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">
            Data principal rights
          </h1>
          <p className="text-sm text-slate-600">
            Erasure and correction requests under the DPDP Act. Verify the requester
            before running anything — erasure cannot be undone.
          </p>
        </div>
        <button
          onClick={() => setShowRaiseModal(true)}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold bg-blue-600 hover:bg-blue-700 text-white rounded-lg shadow-sm transition-colors cursor-pointer self-start sm:self-auto"
        >
          <Plus size={15} /> Log New Request
        </button>
      </header>

      <div className="flex flex-wrap gap-2">
        {STATES.map(s => (
          <button
            key={s.value}
            onClick={() => setState(s.value)}
            className={cn(
              'rounded-full px-3 py-1 text-sm',
              state === s.value
                ? 'bg-blue-600 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200',
            )}
          >
            {s.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 p-8 text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      ) : requests.length === 0 ? (
        <p className="rounded-md border border-slate-200 bg-slate-50 p-8 text-center text-sm text-slate-500">
          Nothing in this state.
        </p>
      ) : (
        <div className="overflow-hidden rounded-md border border-slate-200">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-2">Patient</th>
                <th className="px-4 py-2">Type</th>
                <th className="px-4 py-2">Raised</th>
                <th className="px-4 py-2">Due</th>
                <th className="px-4 py-2">Verified</th>
                <th className="px-4 py-2 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {requests.map(r => (
                <tr key={r.id} className={cn(r.overdue && 'bg-red-50')}>
                  <td className="px-4 py-3 font-mono text-xs text-slate-700">
                    {r.patientId.slice(0, 8)}…
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={cn(
                        'rounded px-2 py-0.5 text-xs font-medium',
                        r.requestType === 'ERASURE'
                          ? 'bg-red-100 text-red-800'
                          : 'bg-blue-100 text-blue-800',
                      )}
                    >
                      {r.requestType}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {new Date(r.requestedAt).toLocaleDateString()}
                    {r.requestedByPatient && (
                      <span className="ml-1 text-xs text-slate-400">(by patient)</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {r.dueAt ? (
                      <span
                        className={cn(
                          r.overdue ? 'font-medium text-red-700' : 'text-slate-600',
                        )}
                      >
                        {r.overdue && (
                          <AlertTriangle className="mr-1 inline h-3.5 w-3.5" />
                        )}
                        {new Date(r.dueAt).toLocaleDateString()}
                      </span>
                    ) : (
                      <span className="text-slate-400">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {r.requesterVerifiedAt ? (
                      <span className="inline-flex items-center gap-1 text-xs text-emerald-700">
                        <ShieldCheck className="h-3.5 w-3.5" />
                        {r.verificationMethod}
                      </span>
                    ) : (
                      <span className="text-xs text-amber-600">Not verified</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      {!r.requesterVerifiedAt && r.state === 'RECEIVED' && (
                        <select
                          className="rounded border border-slate-300 px-2 py-1 text-xs"
                          defaultValue=""
                          onChange={e => {
                            if (!e.target.value) return
                            verify.mutate({
                              id: r.id,
                              method: e.target.value as VerificationMethod,
                            })
                          }}
                        >
                          <option value="">Verify as…</option>
                          {VERIFICATION_METHODS.map(m => (
                            <option key={m.value} value={m.value}>
                              {m.label}
                            </option>
                          ))}
                        </select>
                      )}

                      {r.requesterVerifiedAt &&
                        r.requestType === 'ERASURE' &&
                        r.state === 'IN_PROGRESS' && (
                          <button
                            onClick={() => setConfirming(r.id)}
                            className="inline-flex items-center gap-1 rounded bg-red-600 px-2 py-1 text-xs font-medium text-white hover:bg-red-700"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                            Run erasure
                          </button>
                        )}

                      {['COMPLETED', 'PARTIALLY_COMPLETED'].includes(r.state) && (
                        <button
                          onClick={() => setOpenReceipt(r.id)}
                          className="rounded border border-slate-300 px-2 py-1 text-xs hover:bg-slate-50"
                        >
                          Receipt
                        </button>
                      )}

                      {['RECEIVED', 'IN_PROGRESS'].includes(r.state) && (
                        <button
                          onClick={() => handleReject(r)}
                          className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50"
                        >
                          Refuse
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Erasure is irreversible for the DELETE targets, so it gets its own
          confirmation rather than firing straight from the table. */}
      {confirming && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
            <h2 className="mb-2 text-lg font-semibold text-slate-900">
              Run erasure?
            </h2>
            <p className="mb-4 text-sm text-slate-600">
              This clears the patient's data across every registered store. Deleted
              records cannot be recovered. Clinical records under statutory
              retention will be kept, and the receipt will say which.
            </p>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setConfirming(null)}
                className="rounded-md border border-slate-300 px-4 py-2 text-sm"
              >
                Cancel
              </button>
              <button
                onClick={() => execute.mutate(confirming)}
                disabled={execute.isPending}
                className="inline-flex items-center gap-2 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-60"
              >
                {execute.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                Erase permanently
              </button>
            </div>
          </div>
        </div>
      )}

      {openReceipt && (
        <ErasureReceiptModal
          requestId={openReceipt}
          onClose={() => setOpenReceipt(null)}
        />
      )}

      {/* Raise Request Modal */}
      {showRaiseModal && (
        <Modal
          isOpen={showRaiseModal}
          onClose={() => {
            setShowRaiseModal(false)
            setSelectedPatient(null)
            setPatientSearch('')
          }}
          title="Log Data Principal Rights Request"
          description="Search patient and record a new DPDP statutory request."
          size="lg"
        >
          <div className="flex flex-col max-h-[85vh]">
            {/* Modal Header */}
            <div className="px-6 pt-6 pb-4 border-b border-gray-100 pr-12 shrink-0">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-center text-blue-600 shrink-0">
                  <ShieldCheck className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-lg font-bold text-gray-900 leading-tight">Log Data Principal Rights Request</h2>
                  <p className="text-xs text-gray-500 mt-0.5">
                    Search patient and record a new DPDP statutory request.
                  </p>
                </div>
              </div>
            </div>

            {/* Modal Body */}
            <div className="p-6 space-y-4 text-sm text-gray-700 overflow-y-auto flex-1">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Select Patient <span className="text-red-500">*</span>
                </label>
                {selectedPatient ? (
                  <div className="flex items-center justify-between p-3.5 bg-blue-50/70 border border-blue-200 rounded-xl">
                    <div>
                      <p className="font-semibold text-blue-900">{selectedPatient.fullName}</p>
                      <p className="text-xs text-blue-700 mt-0.5">
                        MRN: {selectedPatient.patientNumber} · {selectedPatient.gender} · {selectedPatient.contactNumber || 'No phone'}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedPatient(null)
                        setPatientSearch('')
                      }}
                      className="text-xs text-blue-600 hover:text-blue-800 underline font-medium cursor-pointer"
                    >
                      Change
                    </button>
                  </div>
                ) : (
                  <div className="relative">
                    <div className="relative">
                      <Search size={15} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
                      <input
                        type="text"
                        value={patientSearch}
                        onChange={e => setPatientSearch(e.target.value)}
                        placeholder="Search patient by name, phone or MRN..."
                        className="w-full pl-10 pr-3.5 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-1 focus:ring-blue-600 bg-white"
                      />
                    </div>
                    {isSearching && (
                      <p className="text-xs text-gray-500 mt-1 flex items-center gap-1">
                        <Loader2 size={12} className="animate-spin" /> Searching…
                      </p>
                    )}
                    {searchResults.length > 0 && !selectedPatient && (
                      <div className="absolute left-0 right-0 top-full mt-1 bg-white border border-gray-200 rounded-xl shadow-lg z-50 max-h-48 overflow-y-auto divide-y divide-gray-100">
                        {searchResults.map(p => (
                          <button
                            key={p.id}
                            type="button"
                            onClick={() => {
                              setSelectedPatient(p)
                              setPatientSearch('')
                            }}
                            className="w-full text-left px-4 py-2.5 text-xs hover:bg-blue-50 transition-colors flex items-center justify-between cursor-pointer"
                          >
                            <div>
                              <span className="font-semibold text-gray-900">{p.fullName}</span>
                              <span className="text-gray-500 ml-2">({p.patientNumber})</span>
                            </div>
                            <span className="text-gray-400 font-mono">{p.contactNumber || ''}</span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Request Type</label>
                <select
                  value={requestType}
                  onChange={e => setRequestType(e.target.value as RightsRequestType)}
                  className="w-full border border-gray-300 rounded-xl px-3.5 py-2.5 text-sm focus:outline-none focus:ring-1 focus:ring-blue-600 bg-white"
                >
                  <option value="ERASURE">Erasure (Right to be Forgotten)</option>
                  <option value="CORRECTION">Correction of Data</option>
                </select>
                <p className="text-xs text-gray-500 mt-1">
                  {requestType === 'ERASURE'
                    ? 'Erases non-clinical PHI across stores and anonymises retained historical records.'
                    : 'Allows correction of inaccurate personal demographic data.'}
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Intake Channel</label>
                <select
                  value={requestedVia}
                  onChange={e => setRequestedVia(e.target.value)}
                  className="w-full border border-gray-300 rounded-xl px-3.5 py-2.5 text-sm focus:outline-none focus:ring-1 focus:ring-blue-600 bg-white"
                >
                  <option value="IN_PERSON">In Person at Front Desk</option>
                  <option value="WRITTEN_REQUEST">Written Request / Letter</option>
                  <option value="EMAIL">Official Grievance Email</option>
                  <option value="PHONE">Phone / Reception</option>
                </select>
              </div>

              <div className="p-3.5 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 leading-relaxed">
                <p className="font-semibold mb-1">DPDP Statutory Notice:</p>
                Once logged, the request triggers a 90-day statutory resolution clock. Requester identity must be verified before executing irreversible deletion.
              </div>
            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end gap-2.5 shrink-0">
              <button
                type="button"
                onClick={() => {
                  setShowRaiseModal(false)
                  setSelectedPatient(null)
                  setPatientSearch('')
                }}
                className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-100 text-sm font-medium transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => raiseMutation.mutate()}
                disabled={!selectedPatient || raiseMutation.isPending}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 text-sm font-medium cursor-pointer shadow-2xs transition-colors flex items-center gap-1.5"
              >
                {raiseMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {raiseMutation.isPending ? 'Submitting…' : 'Submit Request'}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
