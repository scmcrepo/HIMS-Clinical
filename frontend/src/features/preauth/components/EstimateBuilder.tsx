import { useMemo, useState } from 'react'
import { Plus, Trash2, TriangleAlert } from 'lucide-react'

import { cn } from '../../../lib/utils'
import { formatCoPay, formatPaise, type PolicyCoverage } from '../../policy/types'
import {
  ESTIMATE_CATEGORIES,
  type EstimateCategory,
  type EstimateLine,
  categoryTotal,
  estimateTotal,
  exceedsAvailableBalance,
  lineAmount,
  patientLiability,
  roomShortfall,
} from '../types'

interface Props {
  lines: EstimateLine[]
  onChange: (lines: EstimateLine[]) => void
  /** The latest coverage check, when one exists. Drives the liability preview. */
  coverage?: PolicyCoverage | null
  expectedLosDays: number
}

/**
 * Itemised estimate builder — Screen 4.1.
 *
 * <p>The patient liability preview is the reason this screen justifies its
 * complexity. It is the figure the desk quotes before admission, and it is
 * computed here from the same rules the server uses, so the number the patient
 * is told matches the number submitted to the insurer.
 *
 * <p>Amounts are entered in rupees and held in paise. The conversion happens at
 * the input boundary, once, rather than being carried as rupees and converted at
 * submission — which is how a rounding difference gets between what was shown
 * and what was sent.
 */
export default function EstimateBuilder({
  lines,
  onChange,
  coverage,
  expectedLosDays,
}: Props) {
  const [draft, setDraft] = useState<{
    category: EstimateCategory
    description: string
    quantity: string
    unitRupees: string
  }>({ category: 'ROOM', description: '', quantity: '1', unitRupees: '' })

  const total = useMemo(() => estimateTotal(lines), [lines])

  const liability = useMemo(() => {
    const roomCharge = categoryTotal(lines, 'ROOM')
    const shortfall = roomShortfall(roomCharge, coverage?.roomRentCapPaise, expectedLosDays)
    return {
      shortfall,
      total: patientLiability(
        total,
        coverage?.coPayBasisPoints,
        coverage?.deductiblePaise,
        shortfall,
      ),
    }
  }, [lines, coverage, expectedLosDays, total])

  const overBalance = exceedsAvailableBalance(total, coverage?.balancePaise ?? null)

  function addLine() {
    const quantity = Number(draft.quantity)
    const unitAmount = Math.round(Number(draft.unitRupees) * 100)
    if (!draft.description.trim() || !(quantity > 0) || !Number.isFinite(unitAmount)) return

    onChange([
      ...lines,
      { category: draft.category, description: draft.description.trim(), quantity, unitAmount },
    ])
    setDraft({ category: draft.category, description: '', quantity: '1', unitRupees: '' })
  }

  return (
    <div className="space-y-4">
      <div className="overflow-x-auto rounded-xl border border-neutral-200">
        <table className="w-full text-sm">
          <thead className="bg-neutral-50 text-left text-xs uppercase tracking-wide text-neutral-500">
            <tr>
              <th className="px-3 py-2 font-medium">Category</th>
              <th className="px-3 py-2 font-medium">Description</th>
              <th className="px-3 py-2 text-right font-medium">Qty</th>
              <th className="px-3 py-2 text-right font-medium">Unit (₹)</th>
              <th className="px-3 py-2 text-right font-medium">Amount</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {lines.map((l, i) => (
              <tr key={`${l.description}-${i}`}>
                <td className="px-3 py-2 text-neutral-600">
                  {ESTIMATE_CATEGORIES[l.category]}
                </td>
                <td className="px-3 py-2">{l.description}</td>
                <td className="px-3 py-2 text-right tabular-nums">{l.quantity}</td>
                <td className="px-3 py-2 text-right tabular-nums">
                  {formatPaise(l.unitAmount)}
                </td>
                <td className="px-3 py-2 text-right tabular-nums">
                  {formatPaise(lineAmount(l.quantity, l.unitAmount))}
                </td>
                <td className="px-3 py-2 text-right">
                  <button
                    type="button"
                    aria-label={`Remove ${l.description}`}
                    onClick={() => onChange(lines.filter((_, idx) => idx !== i))}
                    className="text-neutral-400 hover:text-red-600"
                  >
                    <Trash2 size={15} />
                  </button>
                </td>
              </tr>
            ))}

            <tr className="bg-neutral-50/60">
              <td className="px-3 py-2">
                <select
                  aria-label="Category"
                  value={draft.category}
                  onChange={(e) =>
                    setDraft({ ...draft, category: e.target.value as EstimateCategory })
                  }
                  className="w-full rounded-md border border-neutral-200 px-2 py-1.5 text-sm"
                >
                  {Object.entries(ESTIMATE_CATEGORIES).map(([k, label]) => (
                    <option key={k} value={k}>
                      {label}
                    </option>
                  ))}
                </select>
              </td>
              <td className="px-3 py-2">
                <input
                  aria-label="Description"
                  value={draft.description}
                  onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                  placeholder="e.g. Single private room"
                  className="w-full rounded-md border border-neutral-200 px-2 py-1.5 text-sm"
                />
              </td>
              <td className="px-3 py-2">
                <input
                  aria-label="Quantity"
                  inputMode="decimal"
                  value={draft.quantity}
                  onChange={(e) => setDraft({ ...draft, quantity: e.target.value })}
                  className="w-20 rounded-md border border-neutral-200 px-2 py-1.5 text-right text-sm tabular-nums"
                />
              </td>
              <td className="px-3 py-2">
                <input
                  aria-label="Unit amount in rupees"
                  inputMode="decimal"
                  value={draft.unitRupees}
                  onChange={(e) => setDraft({ ...draft, unitRupees: e.target.value })}
                  className="w-28 rounded-md border border-neutral-200 px-2 py-1.5 text-right text-sm tabular-nums"
                />
              </td>
              <td />
              <td className="px-3 py-2 text-right">
                <button
                  type="button"
                  onClick={addLine}
                  className="inline-flex items-center gap-1 rounded-md border border-neutral-300 px-2 py-1.5 text-xs font-medium hover:bg-white"
                >
                  <Plus size={13} />
                  Add
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <dl className="grid gap-3 sm:grid-cols-3">
        <Figure label="Estimate total" value={formatPaise(total)} emphasis />
        <Figure
          label="Insurer expected to cover"
          value={formatPaise(Math.max(0, total - liability.total))}
        />
        <Figure
          label="Patient pays (estimated)"
          value={formatPaise(liability.total)}
          tone={liability.total > 0 ? 'warn' : undefined}
        />
      </dl>

      {coverage && (
        <p className="text-xs text-neutral-500">
          Based on the eligibility check of{' '}
          {new Date(coverage.checkedAt).toLocaleDateString('en-IN')}: co-payment{' '}
          {formatCoPay(coverage.coPayBasisPoints)}, deductible{' '}
          {formatPaise(coverage.deductiblePaise)}
          {liability.shortfall > 0 && (
            <>
              , plus {formatPaise(liability.shortfall)} of room charges above the policy limit
            </>
          )}
          . Final liability depends on what the insurer actually approves.
        </p>
      )}

      {!coverage && lines.length > 0 && (
        <p className="text-xs text-neutral-500">
          No eligibility check on file, so the patient's share cannot be estimated. Run a
          coverage check for a reliable figure.
        </p>
      )}

      {overBalance && (
        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
          <TriangleAlert size={16} className="mt-0.5 shrink-0" />
          The estimate exceeds the balance left on this policy (
          {formatPaise(coverage?.balancePaise)}). The insurer is likely to approve less than
          requested.
        </div>
      )}
    </div>
  )
}

function Figure({
  label,
  value,
  emphasis,
  tone,
}: {
  label: string
  value: string
  emphasis?: boolean
  tone?: 'warn'
}) {
  return (
    <div className="rounded-xl border border-neutral-200 p-4">
      <dt className="text-xs uppercase tracking-wide text-neutral-500">{label}</dt>
      <dd
        className={cn(
          'mt-1 tabular-nums',
          emphasis ? 'text-xl font-semibold' : 'text-lg',
          tone === 'warn' ? 'text-amber-700' : 'text-neutral-900',
        )}
      >
        {value}
      </dd>
    </div>
  )
}
