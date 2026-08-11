import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Clock, MessageSquareWarning } from 'lucide-react'

import { preAuthApi } from '../../../services/preauth/preAuthApi'
import { QueryResponseModal } from '../components/PreAuthModals'
import { pendingQueries, type PreAuthQuery } from '../types'

/**
 * Pre-authorisation status tracker — Screen 4.2.
 *
 * <p>Leads with the queries the insurer is waiting on rather than a list of all
 * pre-auths. An unanswered query is the only state on this screen where the
 * hospital is the blocker: everything else is waiting on the payer, and a
 * screen that mixes the two buries the rows anyone can act on.
 */
export default function PreAuthTrackerPage() {
  const [open, setOpen] = useState<PreAuthQuery | null>(null)

  const { data: queries = [], isLoading } = useQuery({
    queryKey: ['preauth', 'unanswered-queries'],
    queryFn: preAuthApi.unansweredQueries,
  })

  const pending = pendingQueries(queries)

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-xl font-semibold text-neutral-900">Pre-authorisations</h1>
        <p className="mt-1 text-sm text-neutral-500">
          Queries the insurer is waiting on, oldest first.
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-neutral-500">Loading queries…</p>
      ) : pending.length === 0 ? (
        <div className="rounded-xl border border-dashed border-neutral-300 p-8 text-center">
          <Clock size={20} className="mx-auto text-neutral-400" />
          <p className="mt-2 text-sm font-medium text-neutral-700">Nothing waiting on us</p>
          <p className="mt-1 text-sm text-neutral-500">
            Every insurer query has been answered. Approvals arrive on their own.
          </p>
        </div>
      ) : (
        <ul className="space-y-3">
          {pending.map((q) => {
            const ageDays = Math.floor(
              (Date.now() - new Date(q.raisedAt).getTime()) / 86_400_000,
            )
            return (
              <li
                key={q.id}
                className="flex items-start justify-between gap-4 rounded-xl border border-neutral-200 p-4"
              >
                <div className="min-w-0 text-sm">
                  <p className="flex items-center gap-2 font-medium text-neutral-900">
                    <MessageSquareWarning size={15} className="shrink-0 text-amber-600" />
                    Round {q.roundNumber}
                    {/* Age is what makes a query urgent — the patient is usually
                        already admitted while this sits unanswered. */}
                    <span
                      className={
                        ageDays >= 2 ? 'text-xs text-red-700' : 'text-xs text-neutral-500'
                      }
                    >
                      {ageDays === 0 ? 'today' : `${ageDays} day${ageDays === 1 ? '' : 's'} old`}
                    </span>
                  </p>
                  <p className="mt-1 text-neutral-600">{q.queryText}</p>
                </div>
                <button
                  type="button"
                  onClick={() => setOpen(q)}
                  className="shrink-0 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90"
                >
                  Respond
                </button>
              </li>
            )
          })}
        </ul>
      )}

      {open && <QueryResponseModal query={open} onClose={() => setOpen(null)} />}
    </div>
  )
}
