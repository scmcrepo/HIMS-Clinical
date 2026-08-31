import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Loader2, ShieldCheck, Trash2 } from 'lucide-react'

import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { complianceApi } from '../../../services/compliance/complianceApi'
import type {
  RightsRequest,
  RightsRequestState,
  VerificationMethod,
} from '../../../types/compliance'
import ErasureReceiptModal from './ErasureReceiptModal'

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

  const { data: requests = [], isLoading } = useQuery({
    queryKey: ['rights-queue', state],
    queryFn: () => complianceApi.queue(state),
  })

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['rights-queue'] })

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
      <header>
        <h1 className="text-xl font-semibold text-slate-900">
          Data principal rights
        </h1>
        <p className="text-sm text-slate-600">
          Erasure and correction requests under the DPDP Act. Verify the requester
          before running anything — erasure cannot be undone.
        </p>
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
    </div>
  )
}
