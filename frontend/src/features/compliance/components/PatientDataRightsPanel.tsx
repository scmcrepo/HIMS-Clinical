import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle, Clock, FileText, Loader2, Plus, ShieldAlert, ShieldCheck, Trash2 } from 'lucide-react'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { formatDate, formatDateTime } from '../../../lib/dateUtils'
import { complianceApi } from '../../../services/compliance/complianceApi'
import type { RightsRequestState, RightsRequestType, VerificationMethod } from '../../../types/compliance'
import ErasureReceiptModal from './ErasureReceiptModal'
import { Modal } from '../../../components/ui/Modal'
import { useAuthStore } from '../../../store/authStore'

interface Props {
  patientId: string
}

const STATE_BADGES: Record<RightsRequestState, { label: string; className: string }> = {
  RECEIVED: { label: 'Awaiting Verification', className: 'bg-amber-50 text-amber-700 border-amber-200' },
  IN_PROGRESS: { label: 'Verified / Queued', className: 'bg-blue-50 text-blue-700 border-blue-200' },
  COMPLETED: { label: 'Completed', className: 'bg-green-50 text-green-700 border-green-200' },
  PARTIALLY_COMPLETED: { label: 'Partially Completed', className: 'bg-yellow-50 text-yellow-700 border-yellow-200' },
  REJECTED: { label: 'Refused', className: 'bg-red-50 text-red-700 border-red-200' },
}

export default function PatientDataRightsPanel({ patientId }: Props) {
  const queryClient = useQueryClient()
  const { hasPermission } = useAuthStore()
  const canManage = hasPermission('ERASURE_MANAGE')
  const canRequest = hasPermission('ERASURE_REQUEST')

  const [isModalOpen, setIsModalOpen] = useState(false)
  const [selectedReceiptId, setSelectedReceiptId] = useState<string | null>(null)
  const [requestType, setRequestType] = useState<RightsRequestType>('ERASURE')
  const [requestedVia, setRequestedVia] = useState('IN_PERSON')

  const { data: requests = [], isLoading } = useQuery({
    queryKey: ['rights-patient-history', patientId],
    queryFn: () => complianceApi.historyFor(patientId),
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['rights-patient-history', patientId] })
    queryClient.invalidateQueries({ queryKey: ['rights-queue'] })
  }

  const raiseMutation = useMutation({
    mutationFn: () =>
      complianceApi.raise({
        patientId,
        requestType,
        requestedVia,
        requestedByPatient: true,
      }),
    onSuccess: () => {
      toast({ title: 'Request logged successfully' })
      setIsModalOpen(false)
      invalidate()
    },
    onError: (err: any) => {
      toast({
        title: 'Failed to raise request',
        description: err?.response?.data?.message || err.message,
        variant: 'destructive',
      })
    },
  })

  const verifyMutation = useMutation({
    mutationFn: ({ id, method }: { id: string; method: VerificationMethod }) =>
      complianceApi.verify(id, method),
    onSuccess: () => {
      toast({ title: 'Requester identity verified' })
      invalidate()
    },
    onError: (err: any) => {
      toast({
        title: 'Verification failed',
        description: err?.response?.data?.message || err.message,
        variant: 'destructive',
      })
    },
  })

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 p-6 text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading data rights history…
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-gray-900">Data Principal Rights</h3>
          <p className="text-sm text-gray-500">
            DPDP Section 12 rights requests: right to erasure, correction, and processing history.
          </p>
        </div>
        {canRequest && (
          <button
            onClick={() => setIsModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold bg-neutral-900 text-white rounded-lg hover:bg-neutral-800 transition-colors shadow-sm"
          >
            <Plus size={14} /> New Rights Request
          </button>
        )}
      </div>

      {requests.length === 0 ? (
        <div className="text-center py-10 bg-gray-50 border border-gray-100 rounded-xl">
          <ShieldCheck className="mx-auto h-8 w-8 text-gray-400 mb-2" />
          <p className="text-sm font-medium text-gray-700">No data rights requests recorded</p>
          <p className="text-xs text-gray-500 mt-1">
            Requests for data erasure or correction will appear here with an auditable timeline.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden border border-gray-200 rounded-xl bg-white">
          <table className="w-full text-sm text-left">
            <thead className="bg-gray-50 text-xs font-semibold text-gray-600 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Requested At</th>
                <th className="px-4 py-3">Channel</th>
                <th className="px-4 py-3">Verification</th>
                <th className="px-4 py-3">Due By</th>
                <th className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {requests.map(req => {
                const badge = STATE_BADGES[req.state] || { label: req.state, className: 'bg-gray-100 text-gray-700' }
                return (
                  <tr key={req.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 font-medium text-gray-900 flex items-center gap-1.5">
                      {req.requestType === 'ERASURE' ? (
                        <Trash2 size={15} className="text-red-500" />
                      ) : (
                        <FileText size={15} className="text-blue-500" />
                      )}
                      {req.requestType}
                    </td>
                    <td className="px-4 py-3">
                      <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border', badge.className)}>
                        {badge.label}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-600 whitespace-nowrap">
                      {formatDateTime(req.requestedAt)}
                    </td>
                    <td className="px-4 py-3 text-gray-600 text-xs">
                      {req.requestedVia || 'IN_PERSON'}
                    </td>
                    <td className="px-4 py-3 text-xs">
                      {req.requesterVerifiedAt ? (
                        <span className="text-green-700 font-medium flex items-center gap-1">
                          <CheckCircle size={13} /> {req.verificationMethod || 'Verified'}
                        </span>
                      ) : (
                        <span className="text-amber-600 font-medium flex items-center gap-1">
                          <Clock size={13} /> Unverified
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-600 whitespace-nowrap">
                      {req.dueAt ? (
                        <span className={cn(req.overdue ? 'text-red-600 font-bold flex items-center gap-1' : '')}>
                          {req.overdue && <AlertTriangle size={13} />}
                          {formatDate(req.dueAt)}
                        </span>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {req.state === 'COMPLETED' || req.state === 'PARTIALLY_COMPLETED' ? (
                        <button
                          onClick={() => setSelectedReceiptId(req.id)}
                          className="px-2.5 py-1 text-xs font-medium bg-neutral-100 hover:bg-neutral-200 text-neutral-800 rounded-md transition-colors"
                        >
                          View Receipt
                        </button>
                      ) : req.state === 'RECEIVED' && canManage ? (
                        <button
                          onClick={() => verifyMutation.mutate({ id: req.id, method: 'IN_PERSON_ID' })}
                          disabled={verifyMutation.isPending}
                          className="px-2.5 py-1 text-xs font-medium bg-blue-50 text-blue-700 hover:bg-blue-100 rounded-md transition-colors border border-blue-200"
                        >
                          Verify ID
                        </button>
                      ) : req.state === 'REJECTED' ? (
                        <span className="text-xs text-red-600 italic" title={req.rejectionReason}>
                          {req.rejectionReason || 'Refused'}
                        </span>
                      ) : (
                        <span className="text-xs text-gray-400">—</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Raise Request Modal */}
      {isModalOpen && (
        <Modal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          title="Log Data Principal Rights Request"
          description="Exercise statutory rights under the DPDP Act for this patient."
          size="lg"
        >
          <div className="flex flex-col max-h-[85vh]">
            {/* Modal Header */}
            <div className="px-6 pt-6 pb-4 border-b border-gray-100 pr-12 shrink-0">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-center text-blue-600 shrink-0">
                  <ShieldAlert className="w-4 h-4" />
                </div>
                <div>
                  <h2 className="text-lg font-bold text-gray-900 leading-tight">Log Data Principal Rights Request</h2>
                  <p className="text-xs text-gray-500 mt-0.5">
                    Exercise statutory rights under the DPDP Act 2023 for this patient.
                  </p>
                </div>
              </div>
            </div>

            {/* Modal Body */}
            <div className="p-6 space-y-4 text-sm text-gray-700 overflow-y-auto flex-1">
              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Request Type</label>
                <select
                  value={requestType}
                  onChange={e => setRequestType(e.target.value as RightsRequestType)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-neutral-900 bg-white"
                >
                  <option value="ERASURE">Erasure (Right to be Forgotten)</option>
                  <option value="CORRECTION">Correction of Data</option>
                </select>
                <p className="text-xs text-gray-500 mt-1">
                  {requestType === 'ERASURE'
                    ? 'Erases non-clinical PHI and anonymises historical records subject to legal retention.'
                    : 'Requires identity verification before updating patient records.'}
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">Intake Channel</label>
                <select
                  value={requestedVia}
                  onChange={e => setRequestedVia(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-neutral-900 bg-white"
                >
                  <option value="IN_PERSON">In Person at Front Desk</option>
                  <option value="WRITTEN_REQUEST">Written Request / Letter</option>
                  <option value="EMAIL">Official Grievance Email</option>
                  <option value="PHONE">Phone / Reception</option>
                </select>
              </div>

              <div className="p-3.5 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 leading-relaxed">
                <p className="font-semibold mb-1">DPDP Statutory Notice:</p>
                Once logged, the request triggers a 90-day statutory resolution clock. Requester identity must be verified before executing irreversible deletion or changes.
              </div>
            </div>

            {/* Modal Footer */}
            <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end gap-2.5 shrink-0">
              <button
                type="button"
                onClick={() => setIsModalOpen(false)}
                className="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 hover:bg-gray-100 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => raiseMutation.mutate()}
                disabled={raiseMutation.isPending}
                className="px-4 py-2 bg-neutral-900 text-white rounded-lg text-sm font-medium hover:bg-neutral-800 disabled:opacity-50 transition-colors shadow-2xs cursor-pointer flex items-center gap-1.5"
              >
                {raiseMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {raiseMutation.isPending ? 'Submitting…' : 'Submit Request'}
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Erasure Receipt Modal */}
      {selectedReceiptId && (
        <ErasureReceiptModal
          requestId={selectedReceiptId}
          onClose={() => setSelectedReceiptId(null)}
        />
      )}
    </div>
  )
}
