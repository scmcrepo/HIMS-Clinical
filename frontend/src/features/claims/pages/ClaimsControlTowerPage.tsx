import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Landmark, ListFilter } from 'lucide-react'

import api from '../../../lib/axios'
import { cn } from '../../../lib/utils'
import type { ApiResponse } from '../../../types/api'
import { formatPaise } from '../../policy/types'
import ReconciliationPanel from '../components/ReconciliationPanel'
import {
  FINANCIAL_STATE_LABELS,
  FINANCIAL_STATES,
  type ClaimRow,
  type FinancialState,
  computeMetrics,
  lifecycleIndex,
  needsAttention,
} from '../types'

type Tab = 'ALL' | 'ATTENTION' | 'RECONCILE'

/**
 * NHCX claims & payment control tower — Screen 5.2, with Screen 5.3 as a tab.
 *
 * <p>The metric cards are computed on the client from the same array that
 * renders the table. A separate aggregate endpoint would let the headline
 * totals disagree with the rows printed beneath them, and when they disagree
 * nobody can tell which is wrong.
 */
export default function ClaimsControlTowerPage() {
  const [tab, setTab] = useState<Tab>('ALL')
  const [stateFilter, setStateFilter] = useState<FinancialState | 'ALL'>('ALL')

  const { data: claims = [], isLoading } = useQuery({
    queryKey: ['claims', 'control-tower'],
    queryFn: () =>
      api
        .get<ApiResponse<ClaimRow[]>>('/insurance/claims')
        .then(r => r.data.data ?? []),
  })

  const metrics = useMemo(() => computeMetrics(claims), [claims])
  const attention = useMemo(() => needsAttention(claims), [claims])

  const visible = useMemo(() => {
    const base = tab === 'ATTENTION' ? attention : claims
    return stateFilter === 'ALL'
      ? base
      : base.filter((c) => c.financialState === stateFilter)
  }, [tab, attention, claims, stateFilter])

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-xl font-semibold text-neutral-900">Claims & payments</h1>
        <p className="mt-1 text-sm text-neutral-500">
          What was claimed, what the insurers allowed, and what has actually reached the bank.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <Metric label="Claimed" value={formatPaise(metrics.totalClaimed)} />
        <Metric label="Approved" value={formatPaise(metrics.totalApproved)} />
        <Metric
          label="Received in bank"
          value={formatPaise(metrics.totalReceivedInBank)}
          tone="positive"
        />
        <Metric label="Awaiting disbursal" value={formatPaise(metrics.totalPendingDisbursal)} />
        <Metric
          label="Disallowed"
          value={formatPaise(metrics.totalDisallowed)}
          tone={metrics.totalDisallowed > 0 ? 'negative' : undefined}
        />
      </div>

      <div className="flex flex-wrap items-center gap-2 border-b border-neutral-200">
        <TabButton active={tab === 'ALL'} onClick={() => setTab('ALL')}>
          All claims ({claims.length})
        </TabButton>
        <TabButton active={tab === 'ATTENTION'} onClick={() => setTab('ATTENTION')}>
          <AlertTriangle size={14} className="mr-1 inline" />
          Needs attention ({attention.length})
        </TabButton>
        <TabButton active={tab === 'RECONCILE'} onClick={() => setTab('RECONCILE')}>
          <Landmark size={14} className="mr-1 inline" />
          Bank reconciliation
        </TabButton>
      </div>

      {tab === 'RECONCILE' ? (
        <ReconciliationPanel />
      ) : (
        <>
          <div className="flex items-center gap-2">
            <ListFilter size={15} className="text-neutral-400" />
            <select
              aria-label="Filter by status"
              value={stateFilter}
              onChange={(e) => setStateFilter(e.target.value as FinancialState | 'ALL')}
              className="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
            >
              <option value="ALL">All statuses</option>
              {FINANCIAL_STATES.map((s) => (
                <option key={s} value={s}>
                  {FINANCIAL_STATE_LABELS[s]}
                </option>
              ))}
            </select>
          </div>

          {isLoading ? (
            <p className="text-sm text-neutral-500">Loading claims…</p>
          ) : visible.length === 0 ? (
            <div className="rounded-xl border border-dashed border-neutral-300 p-8 text-center text-sm text-neutral-500">
              No claims match this filter.
            </div>
          ) : (
            <div className="overflow-x-auto rounded-xl border border-neutral-200">
              <table className="w-full text-sm">
                <thead className="bg-neutral-50 text-left text-xs uppercase tracking-wide text-neutral-500">
                  <tr>
                    <th className="px-4 py-2.5 font-medium">Payer</th>
                    <th className="px-4 py-2.5 font-medium">Status</th>
                    <th className="px-4 py-2.5 text-right font-medium">Claimed</th>
                    <th className="px-4 py-2.5 text-right font-medium">Approved</th>
                    <th className="px-4 py-2.5 text-right font-medium">Disallowed</th>
                    <th className="px-4 py-2.5 font-medium">UTR</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-100">
                  {visible.map((c) => (
                    <tr key={c.id} className="hover:bg-neutral-50">
                      <td className="px-4 py-2.5">{c.payerCode}</td>
                      <td className="px-4 py-2.5">
                        <StatusPill state={c.financialState} />
                      </td>
                      <td className="px-4 py-2.5 text-right tabular-nums">
                        {formatPaise(c.claimedAmount)}
                      </td>
                      <td className="px-4 py-2.5 text-right tabular-nums">
                        {formatPaise(c.approvedAmount)}
                      </td>
                      <td
                        className={cn(
                          'px-4 py-2.5 text-right tabular-nums',
                          (c.disallowedAmount ?? 0) > 0 && 'text-red-700',
                        )}
                      >
                        {formatPaise(c.disallowedAmount)}
                      </td>
                      <td className="px-4 py-2.5 text-neutral-500">
                        {c.advices.map((a) => a.utrNumber).join(', ') || '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  )
}

function Metric({
  label,
  value,
  tone,
}: {
  label: string
  value: string
  tone?: 'positive' | 'negative'
}) {
  return (
    <div className="rounded-xl border border-neutral-200 p-4">
      <p className="text-xs uppercase tracking-wide text-neutral-500">{label}</p>
      <p
        className={cn(
          'mt-1 text-lg font-semibold tabular-nums',
          tone === 'positive' && 'text-emerald-700',
          tone === 'negative' && 'text-red-700',
          !tone && 'text-neutral-900',
        )}
      >
        {value}
      </p>
    </div>
  )
}

function StatusPill({ state }: { state: FinancialState }) {
  // A dispute is off the happy path, not a later stage of it.
  const off = lifecycleIndex(state) < 0
  const settled = state === 'AMOUNT_RECEIVED_IN_BANK'
  return (
    <span
      className={cn(
        'inline-block rounded-full px-2 py-0.5 text-xs font-medium',
        off && 'bg-red-50 text-red-700',
        settled && 'bg-emerald-50 text-emerald-700',
        !off && !settled && 'bg-neutral-100 text-neutral-700',
      )}
    >
      {FINANCIAL_STATE_LABELS[state]}
    </span>
  )
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        '-mb-px border-b-2 px-3 py-2 text-sm font-medium',
        active
          ? 'border-neutral-900 text-neutral-900'
          : 'border-transparent text-neutral-500 hover:text-neutral-700',
      )}
    >
      {children}
    </button>
  )
}
