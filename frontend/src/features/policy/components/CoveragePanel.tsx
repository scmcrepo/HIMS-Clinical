import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Printer, ShieldCheck, ShieldQuestion, ShieldX } from 'lucide-react'

import { cn } from '../../../lib/utils'
import { policyApi } from '../../../services/policy/policyApi'
import {
  POLICY_STATUS_LABELS,
  type PolicyCoverage,
  formatCoPay,
  formatPaise,
  pedWaitingActive,
  statusTone,
  utilisationPercent,
} from '../types'

interface Props {
  patientId: string
  onPrint?: (coverage: PolicyCoverage) => void
}

const TONE_STYLES = {
  positive: 'border-emerald-200 bg-emerald-50 text-emerald-900',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
  negative: 'border-red-200 bg-red-50 text-red-900',
} as const

const TONE_ICONS = {
  positive: ShieldCheck,
  warning: ShieldQuestion,
  negative: ShieldX,
} as const

/**
 * Coverage & benefit breakdown — Screen 2.1.
 *
 * <p>Every amount renders through {@code formatPaise}, which prints an em-dash
 * for a value the payer did not state. That distinction is load-bearing here:
 * a blank room-rent cap means "no limit stated" and a zero means "nothing
 * covered", and the desk admits patients on the difference.
 */
export default function CoveragePanel({ patientId, onPrint }: Props) {
  const {
    data: coverage,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['policy', 'coverage', patientId],
    queryFn: () => policyApi.latestCoverage(patientId),
    retry: false,
  })

  if (isLoading) {
    return <div className="p-4 text-sm text-neutral-500">Loading coverage…</div>
  }

  if (isError) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        Coverage could not be loaded. This does not mean the policy is invalid — retry, or
        run a fresh eligibility check.
      </div>
    )
  }

  if (!coverage) {
    return (
      <div className="rounded-xl border border-dashed border-neutral-300 p-6 text-center">
        <p className="text-sm font-medium text-neutral-700">No eligibility check yet</p>
        <p className="mt-1 text-sm text-neutral-500">
          Run a coverage check to see the sum insured, room limits and co-payment.
        </p>
      </div>
    )
  }

  const tone = statusTone(coverage.policyStatus)
  const ToneIcon = TONE_ICONS[tone]
  const utilisation = utilisationPercent(coverage)

  return (
    <div className="space-y-4">
      <div className={cn('flex items-center gap-3 rounded-xl border p-4', TONE_STYLES[tone])}>
        <ToneIcon size={20} className="shrink-0" />
        <div className="flex-1">
          <p className="text-sm font-semibold">{POLICY_STATUS_LABELS[coverage.policyStatus]}</p>
          <p className="text-xs opacity-80">
            Checked {new Date(coverage.checkedAt).toLocaleString('en-IN')}
          </p>
        </div>
        {onPrint && (
          <button
            type="button"
            onClick={() => onPrint(coverage)}
            className="inline-flex items-center gap-1.5 rounded-lg border border-current px-3 py-1.5 text-xs font-medium hover:opacity-80"
          >
            <Printer size={14} />
            Print for patient
          </button>
        )}
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <Figure label="Sum insured" value={formatPaise(coverage.sumInsuredPaise)} />
        <Figure label="Utilised" value={formatPaise(coverage.utilisedPaise)} />
        <Figure label="Balance available" value={formatPaise(coverage.balancePaise)} emphasis />
      </div>

      {utilisation !== null && (
        <div>
          <div className="mb-1 flex justify-between text-xs text-neutral-500">
            <span>Utilised</span>
            <span>{utilisation}%</span>
          </div>
          <div className="h-2 w-full overflow-hidden rounded-full bg-neutral-100">
            <div
              className={cn('h-full', utilisation >= 90 ? 'bg-red-500' : 'bg-neutral-700')}
              style={{ width: `${utilisation}%` }}
            />
          </div>
        </div>
      )}

      <dl className="divide-y divide-neutral-100 rounded-xl border border-neutral-200">
        <Row label="Eligible room category" value={coverage.roomCategory ?? '—'} />
        <Row label="Room rent limit / day" value={formatPaise(coverage.roomRentCapPaise)} />
        <Row label="ICU limit / day" value={formatPaise(coverage.icuCapPaise)} />
        <Row label="Co-payment" value={formatCoPay(coverage.coPayBasisPoints)} />
        <Row label="Deductible" value={formatPaise(coverage.deductiblePaise)} />
        <Row
          label="PED waiting period"
          value={
            coverage.pedWaitingMonths === null
              ? '—'
              : `${coverage.pedWaitingMonths} months${
                  pedWaitingActive(coverage) ? ' — not yet served' : ''
                }`
          }
        />
      </dl>

      {pedWaitingActive(coverage) && (
        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" />
          The pre-existing disease waiting period has not been served. Claims relating to a
          pre-existing condition are likely to be rejected.
        </div>
      )}

      {coverage.exclusions.length > 0 && (
        <div className="rounded-xl border border-neutral-200 p-4">
          <p className="text-sm font-medium text-neutral-800">
            Exclusions and restrictions ({coverage.exclusions.length})
          </p>
          <ul className="mt-2 space-y-1.5">
            {coverage.exclusions.map((e, i) => (
              <li key={`${e.code ?? 'x'}-${i}`} className="flex justify-between gap-4 text-sm">
                <span className="text-neutral-700">{e.description}</span>
                {e.limitPaise !== null && (
                  <span className="shrink-0 tabular-nums text-neutral-500">
                    up to {formatPaise(e.limitPaise)}
                  </span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function Figure({
  label,
  value,
  emphasis,
}: {
  label: string
  value: string
  emphasis?: boolean
}) {
  return (
    <div className="rounded-xl border border-neutral-200 p-4">
      <dt className="text-xs uppercase tracking-wide text-neutral-500">{label}</dt>
      <dd
        className={cn(
          'mt-1 tabular-nums',
          emphasis ? 'text-xl font-semibold text-neutral-900' : 'text-lg text-neutral-800',
        )}
      >
        {value}
      </dd>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 px-4 py-2.5 text-sm">
      <dt className="text-neutral-500">{label}</dt>
      <dd className="tabular-nums text-neutral-900">{value}</dd>
    </div>
  )
}
