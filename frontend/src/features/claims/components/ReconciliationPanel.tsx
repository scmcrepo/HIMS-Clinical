import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Check, Landmark, Loader2 } from 'lucide-react'

import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { claimPaymentApi } from '../../../services/claims/claimPaymentApi'
import { formatPaise } from '../../policy/types'
import { adviceIsSelfConsistent, expectedNet } from '../types'

/**
 * Bank reconciliation — Screen 5.3.
 *
 * <p>The accountant types the figure from the bank statement. It is never
 * prefilled from the advice, because prefilling turns the one control this
 * screen exists to provide into a rubber stamp: the whole point is comparing
 * what the insurer *said* it sent against what actually arrived.
 */
export default function ReconciliationPanel() {
  const queryClient = useQueryClient()
  const [amounts, setAmounts] = useState<Record<string, string>>({})
  const [notes, setNotes] = useState<Record<string, string>>({})

  const { data: advices = [], isLoading } = useQuery({
    queryKey: ['claims', 'pending-reconciliation'],
    queryFn: claimPaymentApi.pendingReconciliation,
  })

  const reconcileOp = useMutation({
    mutationFn: ({ id, paise, note }: { id: string; paise: number; note?: string }) =>
      claimPaymentApi.reconcile(id, paise, note),
    onSuccess: (advice) => {
      queryClient.invalidateQueries({ queryKey: ['claims'] })
      const matched = advice.bankCreditedAmount === advice.netDisbursedAmount
      toast({
        title: matched ? 'Reconciled' : 'Reconciled with a mismatch — claim marked disputed',
        variant: matched ? 'success' : 'default',
      })
    },
    onError: () => toast({ title: 'Could not reconcile', variant: 'destructive' }),
  })

  if (isLoading) {
    return <div className="p-4 text-sm text-neutral-500">Loading payment advices…</div>
  }

  if (!advices.length) {
    return (
      <div className="rounded-xl border border-dashed border-neutral-300 p-6 text-center">
        <Landmark size={20} className="mx-auto text-neutral-400" />
        <p className="mt-2 text-sm font-medium text-neutral-700">Nothing awaiting reconciliation</p>
        <p className="mt-1 text-sm text-neutral-500">
          Advices appear here when an insurer notifies a payment.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {advices.map((advice) => {
        const typed = amounts[advice.id] ?? ''
        // Rupees in the box, paise on the wire.
        const paise = typed.trim() === '' ? null : Math.round(Number(typed) * 100)
        const valid = paise !== null && Number.isFinite(paise) && paise >= 0
        const gap = valid ? advice.netDisbursedAmount - paise : null
        const consistent = adviceIsSelfConsistent(advice)

        return (
          <div key={advice.id} className="rounded-xl border border-neutral-200 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="text-sm">
                <p className="font-medium text-neutral-900">UTR {advice.utrNumber}</p>
                <p className="mt-0.5 text-neutral-500">
                  {advice.paymentDate
                    ? new Date(advice.paymentDate).toLocaleDateString('en-IN')
                    : 'No payment date given'}
                </p>
              </div>
              <dl className="flex gap-5 text-sm tabular-nums">
                <div>
                  <dt className="text-xs text-neutral-500">Gross</dt>
                  <dd>{formatPaise(advice.grossAmount)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-neutral-500">TDS</dt>
                  <dd>{formatPaise(advice.tdsAmount)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-neutral-500">Deductions</dt>
                  <dd>{formatPaise(advice.deductionAmount)}</dd>
                </div>
                <div>
                  <dt className="text-xs text-neutral-500">Insurer says sent</dt>
                  <dd className="font-semibold">{formatPaise(advice.netDisbursedAmount)}</dd>
                </div>
              </dl>
            </div>

            {!consistent && (
              <div className="mt-3 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                <AlertTriangle size={16} className="mt-0.5 shrink-0" />
                <span>
                  The insurer's own figures do not add up — gross less TDS and deductions is{' '}
                  {formatPaise(expectedNet(advice))}, but it states{' '}
                  {formatPaise(advice.netDisbursedAmount)}. Query this before reconciling.
                </span>
              </div>
            )}

            <div className="mt-3 flex flex-wrap items-end gap-3">
              <div>
                <label
                  htmlFor={`credited-${advice.id}`}
                  className="block text-xs font-medium text-neutral-700"
                >
                  Amount credited to the hospital account (₹)
                </label>
                <input
                  id={`credited-${advice.id}`}
                  inputMode="decimal"
                  value={typed}
                  onChange={(e) => setAmounts((a) => ({ ...a, [advice.id]: e.target.value }))}
                  placeholder="Read this from the bank statement"
                  className="mt-1 w-64 rounded-lg border border-neutral-200 px-3 py-2 text-sm tabular-nums focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
                />
              </div>

              <div className="flex-1 min-w-[12rem]">
                <label
                  htmlFor={`note-${advice.id}`}
                  className="block text-xs font-medium text-neutral-700"
                >
                  Note (optional)
                </label>
                <input
                  id={`note-${advice.id}`}
                  value={notes[advice.id] ?? ''}
                  onChange={(e) => setNotes((n) => ({ ...n, [advice.id]: e.target.value }))}
                  className="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
                />
              </div>

              <button
                type="button"
                disabled={!valid || reconcileOp.isPending}
                onClick={() =>
                  reconcileOp.mutate({
                    id: advice.id,
                    paise: paise!,
                    note: notes[advice.id],
                  })
                }
                className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
              >
                {reconcileOp.isPending ? (
                  <Loader2 size={15} className="animate-spin" />
                ) : (
                  <Check size={15} />
                )}
                Mark reconciled
              </button>
            </div>

            {gap !== null && gap !== 0 && (
              <p
                className={cn(
                  'mt-2 text-sm',
                  gap > 0 ? 'text-red-700' : 'text-amber-700',
                )}
              >
                {gap > 0
                  ? `Short by ${formatPaise(gap)} — the claim will be marked disputed.`
                  : `Over-credited by ${formatPaise(-gap)} — check this is the right UTR.`}
              </p>
            )}
          </div>
        )
      })}
    </div>
  )
}
