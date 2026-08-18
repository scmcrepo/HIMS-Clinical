/**
 * Aggregation logic for the insurance report summary cards (WO-021 / IR-002).
 *
 * Extracted from the JSX so it can be tested. These are the numbers a finance
 * lead reads at a glance and does not re-derive — a silently wrong total here is
 * worse than no summary at all, because it looks authoritative.
 *
 * Report rows arrive from raw JDBC via the report engine, so numeric columns can
 * come back as strings or nulls depending on the driver and the aggregate. Every
 * function below coerces rather than assuming, which is why `sumBy` exists
 * instead of a plain `reduce`.
 *
 * Amounts here are already in RUPEES — the report SQL divides by 100 — unlike
 * the desk, which works in paise.
 */

export type ReportRow = Record<string, unknown>

/** The six ageing brackets, in order. Must match the SQL CASE in InsuranceReportDataService. */
export const AGEING_BRACKETS = [
  'Less than 31 days',
  '31 to 60 days',
  '61 to 90 days',
  '91 to 120 days',
  '121 to 150 days',
  'More than 150 days',
] as const

export type AgeingBracket = (typeof AGEING_BRACKETS)[number]

/** Coerce a report cell to a number. Nulls, blanks and junk become 0, never NaN. */
export function num(value: unknown): number {
  if (value === null || value === undefined || value === '') return 0
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n : 0
}

/** Sum a column across rows, tolerating string and null cells. */
export function sumBy(rows: readonly ReportRow[], key: string): number {
  return rows.reduce((acc, r) => acc + num(r[key]), 0)
}

export interface DecisionSummary {
  approved: number
  rejected: number
  pending: number
  approvedAmount: number
}

/**
 * Approved / rejected / awaiting, for both status reports.
 *
 * "In process" is counted from the label the SQL emits rather than inferred from
 * a missing decision, so the frontend and the report agree on what pending means.
 */
export function summariseDecisions(rows: readonly ReportRow[]): DecisionSummary {
  const approved = rows.filter(r => r['status'] === 'Approved')
  return {
    approved: approved.length,
    rejected: rows.filter(r => r['status'] === 'Rejected').length,
    pending: rows.filter(r => r['status'] === 'In process').length,
    approvedAmount: sumBy(approved, 'approved_amount'),
  }
}

export interface DisallowanceSummary {
  claims: number
  billed: number
  disallowed: number
  received: number
  /** Percentage of billed value the payers refused, to one decimal. */
  disallowedPct: number
}

export function summariseDisallowance(rows: readonly ReportRow[]): DisallowanceSummary {
  const billed = sumBy(rows, 'billed_amount')
  const disallowed = sumBy(rows, 'disallowed_amount')
  return {
    claims: sumBy(rows, 'claims'),
    billed,
    disallowed,
    received: sumBy(rows, 'received_amount'),
    // Guarded: a period with deductions but no billed value would otherwise
    // divide by zero and render "Infinity%" on a finance dashboard.
    disallowedPct: billed > 0 ? Math.round((disallowed / billed) * 1000) / 10 : 0,
  }
}

/** The charge with the largest total deduction — the one worth arguing about. */
export function largestDisallowedCharge(
  rows: readonly ReportRow[],
): { charge: string; amount: number } | null {
  const byCharge = new Map<string, number>()
  for (const r of rows) {
    const key = typeof r['charge'] === 'string' && r['charge'] ? r['charge'] : 'Other'
    byCharge.set(key, (byCharge.get(key) ?? 0) + num(r['disallowed_amount']))
  }
  const sorted = [...byCharge.entries()].sort((a, b) => b[1] - a[1])
  const top = sorted[0]
  return top ? { charge: top[0], amount: top[1] } : null
}

export interface AgeingSummary {
  total: number
  /** Every bracket, in order, always present — a zero bracket is information. */
  buckets: Array<{ bracket: AgeingBracket; amount: number; share: number }>
  /** 91 days and older: the balance that usually needs escalation. */
  over90: number
}

export function summariseAgeing(rows: readonly ReportRow[]): AgeingSummary {
  const byBracket = new Map<string, number>()
  for (const r of rows) {
    const b = typeof r['ageing_bracket'] === 'string' ? r['ageing_bracket'] : 'Unknown'
    byBracket.set(b, (byBracket.get(b) ?? 0) + num(r['outstanding']))
  }
  const total = sumBy(rows, 'outstanding')

  // Every bracket is emitted whether or not it has rows. Dropping empty ones
  // would make the ladder shorter in a good month and longer in a bad one,
  // which destroys the month-on-month comparison the report exists for.
  const buckets = AGEING_BRACKETS.map(bracket => {
    const amount = byBracket.get(bracket) ?? 0
    return { bracket, amount, share: total > 0 ? (amount / total) * 100 : 0 }
  })

  return {
    total,
    buckets,
    over90: buckets.slice(3).reduce((a, b) => a + b.amount, 0),
  }
}

export interface DispatchSummary {
  dispatched: number
  /** Null when no claim in the set has a sanction date to measure from. */
  avgDaysToDispatch: number | null
  /** Couriered with no consignment number — undeliverable proof, a real exposure. */
  courieredWithoutPod: number
}

export function summariseDispatch(rows: readonly ReportRow[]): DispatchSummary {
  const withDays = rows.filter(r => r['days_to_dispatch'] !== null && r['days_to_dispatch'] !== undefined)
  return {
    dispatched: rows.length,
    avgDaysToDispatch: withDays.length
      ? Math.round(sumBy(withDays, 'days_to_dispatch') / withDays.length)
      : null,
    courieredWithoutPod: rows.filter(
      r => r['mode'] === 'COURIER' && (!r['pod_no'] || r['pod_no'] === '-'),
    ).length,
  }
}

/** How much more is being asked for than was originally sanctioned. */
export function enhancementUplift(rows: readonly ReportRow[]): number {
  return sumBy(rows, 'requested_amount') - sumBy(rows, 'original_limit')
}

/** Rupee display for report summaries. Whole rupees — decimals add noise at this level. */
export function formatRupees(n: number): string {
  return `₹ ${Math.round(n).toLocaleString('en-IN')}`
}
