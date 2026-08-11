import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2, MessageSquareWarning, TrendingUp } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { toast } from '../../../hooks/useToast'
import { preAuthApi } from '../../../services/preauth/preAuthApi'
import { formatPaise } from '../../policy/types'
import { enhancementDelta, validateEnhancement, type PreAuthQuery } from '../types'

/**
 * Answer an insurer query — Screen 4.3.
 *
 * <p>Shows the full question rather than a summary. Insurer queries are often
 * specific ("send the OT notes for 12 March"), and a truncated version sends the
 * desk back to the payer portal to read what was actually asked.
 */
export function QueryResponseModal({
  query,
  onClose,
}: {
  query: PreAuthQuery
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const [attachmentIds, setAttachmentIds] = useState('')

  const respondOp = useMutation({
    mutationFn: () => preAuthApi.respondToQuery(query.id, text.trim(), attachmentIds || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['preauth'] })
      toast({ title: 'Response sent to the insurer', variant: 'success' })
      onClose()
    },
    onError: () => toast({ title: 'Could not send the response', variant: 'destructive' }),
  })

  return (
    <Modal isOpen onClose={onClose} title="Respond to insurer query" size="lg">
      <div className="px-6 pt-6">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-neutral-900">
          <MessageSquareWarning size={18} className="text-amber-600" />
          Insurer query — round {query.roundNumber}
        </h2>
        <p className="mt-1 text-sm text-neutral-500">
          Raised {new Date(query.raisedAt).toLocaleString('en-IN')}
        </p>
      </div>

      <div className="space-y-4 px-6 py-5">
        <blockquote className="rounded-lg border-l-4 border-amber-300 bg-amber-50 p-3 text-sm text-neutral-800">
          {query.queryText}
        </blockquote>

        <div>
          <label htmlFor="query-response" className="block text-sm font-medium text-neutral-700">
            Your response
          </label>
          <textarea
            id="query-response"
            rows={5}
            value={text}
            onChange={(e) => setText(e.target.value)}
            className="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
          />
        </div>

        <div>
          <label htmlFor="query-attachments" className="block text-sm font-medium text-neutral-700">
            Attachment IDs (optional)
          </label>
          <input
            id="query-attachments"
            value={attachmentIds}
            onChange={(e) => setAttachmentIds(e.target.value)}
            placeholder="Comma-separated ids from the documents already uploaded"
            className="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
          />
          <p className="mt-1.5 text-xs text-neutral-500">
            Documents are referenced, not re-uploaded — the insurer receives the same file the
            case sheet holds.
          </p>
        </div>
      </div>

      <div className="flex justify-end gap-2 border-t border-neutral-100 px-6 py-4">
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg px-4 py-2 text-sm font-medium text-neutral-600 hover:bg-neutral-50"
        >
          Cancel
        </button>
        <button
          type="button"
          disabled={!text.trim() || respondOp.isPending}
          onClick={() => respondOp.mutate()}
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
        >
          {respondOp.isPending && <Loader2 size={15} className="animate-spin" />}
          Send response
        </button>
      </div>
    </Modal>
  )
}

/**
 * Request an enhancement — Screen 4.4.
 *
 * <p>Shows the delta, not just the revised total. The insurer is being asked for
 * the additional amount, and a screen that only shows the new total invites the
 * desk to think of it as a fresh request — which is how an already-approved
 * amount gets counted twice.
 */
export function EnhancementModal({
  transactionId,
  previousApproved,
  onClose,
}: {
  transactionId: string
  previousApproved: number
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [revisedRupees, setRevisedRupees] = useState('')
  const [justification, setJustification] = useState('')

  const revisedPaise =
    revisedRupees.trim() === '' ? 0 : Math.round(Number(revisedRupees) * 100)
  const check = validateEnhancement(previousApproved, revisedPaise, justification)
  const delta = enhancementDelta(previousApproved, revisedPaise)

  const enhanceOp = useMutation({
    mutationFn: () => preAuthApi.requestEnhancement(transactionId, revisedPaise, justification.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['preauth'] })
      toast({ title: 'Enhancement requested', variant: 'success' })
      onClose()
    },
    onError: () => toast({ title: 'Could not request the enhancement', variant: 'destructive' }),
  })

  return (
    <Modal isOpen onClose={onClose} title="Request enhancement" size="lg">
      <div className="px-6 pt-6">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-neutral-900">
          <TrendingUp size={18} />
          Request enhancement
        </h2>
        <p className="mt-1 text-sm text-neutral-500">
          Ask the insurer to approve more than the amount already sanctioned.
        </p>
      </div>

      <div className="space-y-4 px-6 py-5">
        <dl className="grid grid-cols-3 gap-3 text-sm">
          <div className="rounded-lg border border-neutral-200 p-3">
            <dt className="text-xs text-neutral-500">Already approved</dt>
            <dd className="mt-0.5 tabular-nums">{formatPaise(previousApproved)}</dd>
          </div>
          <div className="rounded-lg border border-neutral-200 p-3">
            <dt className="text-xs text-neutral-500">Revised estimate</dt>
            <dd className="mt-0.5 tabular-nums">
              {revisedPaise > 0 ? formatPaise(revisedPaise) : '—'}
            </dd>
          </div>
          <div className="rounded-lg border border-neutral-900 p-3">
            <dt className="text-xs text-neutral-500">Additional requested</dt>
            <dd className="mt-0.5 font-semibold tabular-nums">
              {delta > 0 ? formatPaise(delta) : '—'}
            </dd>
          </div>
        </dl>

        <div>
          <label htmlFor="revised-estimate" className="block text-sm font-medium text-neutral-700">
            Revised total estimate (₹)
          </label>
          <input
            id="revised-estimate"
            inputMode="decimal"
            value={revisedRupees}
            onChange={(e) => setRevisedRupees(e.target.value)}
            className="mt-1 w-56 rounded-lg border border-neutral-200 px-3 py-2 text-sm tabular-nums focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
          />
          <p className="mt-1.5 text-xs text-neutral-500">
            Enter the new total, not the difference. The additional amount is calculated above.
          </p>
        </div>

        <div>
          <label htmlFor="justification" className="block text-sm font-medium text-neutral-700">
            Clinical justification
          </label>
          <textarea
            id="justification"
            rows={4}
            value={justification}
            onChange={(e) => setJustification(e.target.value)}
            placeholder="What changed since the original approval?"
            className="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
          />
          <p className="mt-1.5 text-xs text-neutral-500">
            Insurers reject unexplained enhancements almost by default.
          </p>
        </div>

        {revisedRupees.trim() !== '' && !check.valid && (
          <ul role="alert" className="space-y-1 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            {check.errors.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
        )}
      </div>

      <div className="flex justify-end gap-2 border-t border-neutral-100 px-6 py-4">
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg px-4 py-2 text-sm font-medium text-neutral-600 hover:bg-neutral-50"
        >
          Cancel
        </button>
        <button
          type="button"
          disabled={!check.valid || enhanceOp.isPending}
          onClick={() => enhanceOp.mutate()}
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
        >
          {enhanceOp.isPending && <Loader2 size={15} className="animate-spin" />}
          Request enhancement
        </button>
      </div>
    </Modal>
  )
}
