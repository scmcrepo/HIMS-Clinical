import type { ReactNode } from 'react'
import { ReportCard, type DateRangeType } from './ReportCard'
import {
  enhancementUplift,
  formatRupees as rupees,
  largestDisallowedCharge,
  summariseAgeing,
  summariseDecisions,
  summariseDisallowance,
  summariseDispatch,
  sumBy,
} from '../../insurance/insuranceReports'

interface InsuranceReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

/**
 * The ten insurance MIS reports (WO-021 / IR-002).
 *
 * Replaces the single generic "Insurance Claim Summary" card, which pointed at
 * the billing module and answered none of the questions an insurance desk
 * actually asks.
 *
 * Each card carries a summary that answers its own question at a glance, so the
 * desk can triage without opening ten full reports. The summaries are
 * deliberately different shapes — a count is the right answer for "what did we
 * raise", a rupee total for "what did they refuse to pay", and an ageing ladder
 * for "how old is our money" — rather than one uniform row of counters.
 *
 * The two point-in-time reports (outstanding, ageing) take an as-on date. The
 * shared ReportCard only emits from_date/to_date, so the server falls back to
 * the END of the range, which is what "as on this month" means.
 */
export function InsuranceReportsTab({ onViewReport }: InsuranceReportsTabProps) {
  const period = (rangeType: DateRangeType) =>
    rangeType === 'today' ? 'today' : rangeType === 'current_month' ? 'this month' : 'last month'

  /** Empty state. An empty report is usually good news, so it should not read as an error. */
  const empty = (message: string, rangeType: DateRangeType) => (
    <div className="flex items-center justify-center bg-gray-50 border border-gray-200 text-gray-600 px-4 py-3 rounded-lg text-sm">
      <svg
        className="w-4 h-4 mr-2 text-gray-400 shrink-0"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="2"
          d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
        />
      </svg>
      <span>
        {message} <span className="font-semibold text-gray-800">{period(rangeType)}</span>.
      </span>
    </div>
  )

  /** One figure with a label under it. */
  const Stat = ({
    label,
    value,
    tone = 'default',
  }: {
    label: string
    value: string
    tone?: 'default' | 'good' | 'bad' | 'warn'
  }) => {
    const tones = {
      default: 'text-gray-900',
      good: 'text-green-700',
      bad: 'text-red-700',
      warn: 'text-amber-700',
    }
    return (
      <div>
        <div className={`text-lg font-semibold ${tones[tone]}`}>{value}</div>
        <div className="text-[11px] text-gray-500 mt-0.5">{label}</div>
      </div>
    )
  }

  const StatRow = ({ children }: { children: ReactNode }) => (
    <div className="flex flex-wrap gap-x-10 gap-y-3 bg-gray-50 border border-gray-200 p-4 rounded-lg">
      {children}
    </div>
  )

  /** Approved / in process / rejected — the shape both status reports share. */
  const decisionSummary = (rows: any[], noun: string) => {
    const s = summariseDecisions(rows)
    return (
      <StatRow>
        <Stat label={`${noun} approved`} value={String(s.approved)} tone="good" />
        <Stat label="Amount sanctioned" value={rupees(s.approvedAmount)} tone="good" />
        {/* Awaiting a reply is the actionable bucket — these are the ones
            somebody has to chase today. */}
        <Stat
          label="Awaiting TPA reply"
          value={String(s.pending)}
          tone={s.pending > 0 ? 'warn' : 'default'}
        />
        <Stat label="Rejected" value={String(s.rejected)} tone={s.rejected > 0 ? 'bad' : 'default'} />
      </StatRow>
    )
  }

  return (
    <div className="space-y-1">
      {/* ── Pre-authorisation ───────────────────────────────────────────── */}

      <ReportCard
        title="Pre-Authorisation Raised"
        reportName="preauth_raised"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No pre-authorisation requests were raised', range)
          return (
            <StatRow>
              <Stat label="Requests raised" value={String(data.length)} />
              <Stat label="Amount requested" value={rupees(sumBy(data, 'requested_amount'))} />
              <Stat
                label="Sent by fax"
                value={String(data.filter((r: any) => r.sent_via === 'Fax').length)}
              />
            </StatRow>
          )
        }}
      />

      <ReportCard
        title="Pre-Authorisation Status"
        reportName="preauth_status"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No pre-authorisations to report on', range)
          return decisionSummary(data, 'Requests')
        }}
      />

      {/* ── Enhancement ─────────────────────────────────────────────────── */}

      <ReportCard
        title="Enhancement Raised"
        reportName="enhancement_raised"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No enhancements were raised', range)
          const uplift = enhancementUplift(data)
          return (
            <StatRow>
              <Stat label="Enhancements raised" value={String(data.length)} />
              <Stat label="Revised total requested" value={rupees(sumBy(data, 'requested_amount'))} />
              {/* The uplift is the number a finance lead actually wants: how
                  much more we are asking for than was first sanctioned. */}
              <Stat label="Additional sought" value={rupees(uplift)} tone="warn" />
            </StatRow>
          )
        }}
      />

      <ReportCard
        title="Enhancement Status"
        reportName="enhancement_status"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No enhancements to report on', range)
          return decisionSummary(data, 'Enhancements')
        }}
      />

      {/* ── Dispatch ────────────────────────────────────────────────────── */}

      <ReportCard
        title="Claim Dispatch"
        reportName="claim_dispatch"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No claim dockets were dispatched', range)
          const s = summariseDispatch(data)
          return (
            <StatRow>
              <Stat label="Dockets dispatched" value={String(s.dispatched)} />
              {s.avgDaysToDispatch !== null && (
                <Stat
                  label="Avg days sanction to dispatch"
                  value={String(s.avgDaysToDispatch)}
                  tone={s.avgDaysToDispatch > 7 ? 'warn' : 'default'}
                />
              )}
              {/* A docket sent without a consignment number cannot be proved
                  delivered — a real exposure, not a data-quality nit. */}
              {s.courieredWithoutPod > 0 && (
                <Stat label="Couriered without POD" value={String(s.courieredWithoutPod)} tone="bad" />
              )}
            </StatRow>
          )
        }}
      />

      {/* ── Disallowance ────────────────────────────────────────────────── */}

      <ReportCard
        title="Disallowance Summary"
        reportName="disallowance_summary"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No settled claims to analyse', range)
          const s = summariseDisallowance(data)
          return (
            <StatRow>
              <Stat label="Claims" value={String(s.claims)} />
              <Stat label="Billed" value={rupees(s.billed)} />
              <Stat label="Disallowed" value={rupees(s.disallowed)} tone="bad" />
              <Stat
                label="Disallowed %"
                value={`${s.disallowedPct}%`}
                tone={s.disallowedPct > 10 ? 'bad' : 'default'}
              />
              <Stat label="Received" value={rupees(s.received)} tone="good" />
            </StatRow>
          )
        }}
      />

      <ReportCard
        title="Disallowance Detail"
        reportName="disallowance_detail"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No charges were disallowed', range)
          // Naming the worst offender turns the report from a record into a
          // negotiating position with the TPA.
          const worst = largestDisallowedCharge(data)
          return (
            <StatRow>
              <Stat label="Lines disallowed" value={String(data.length)} />
              <Stat
                label="Total disallowed"
                value={rupees(sumBy(data, 'disallowed_amount'))}
                tone="bad"
              />
              {worst && <Stat label={`Largest: ${worst.charge}`} value={rupees(worst.amount)} />}
            </StatRow>
          )
        }}
      />

      {/* ── Worklists ───────────────────────────────────────────────────── */}

      <ReportCard
        title="Document Pending Status"
        reportName="document_pending_status"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No claims are held up on documents', range)
          const stale = data.filter((r: any) => Number(r.days_pending ?? 0) > 15)
          return (
            <StatRow>
              <Stat label="Claims held up" value={String(data.length)} tone="warn" />
              <Stat
                label="Pending over 15 days"
                value={String(stale.length)}
                tone={stale.length > 0 ? 'bad' : 'default'}
              />
            </StatRow>
          )
        }}
      />

      <ReportCard
        title="IP Outstanding Credit Bills"
        reportName="ip_outstanding_credit_bills"
        onViewReport={onViewReport}
        defaultRangeType="current_month"
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No outstanding credit bills', range)
          return (
            <StatRow>
              <Stat label="Bills outstanding" value={String(data.length)} />
              <Stat
                label="Amount outstanding"
                value={rupees(sumBy(data, 'outstanding'))}
                tone="warn"
              />
              <Stat label="Received so far" value={rupees(sumBy(data, 'received'))} tone="good" />
            </StatRow>
          )
        }}
      />

      <ReportCard
        title="Ageing Analysis"
        reportName="insurance_ageing_analysis"
        onViewReport={onViewReport}
        defaultRangeType="current_month"
        renderSummary={(data, range) => {
          if (!data?.length) return empty('No receivables to age', range)
          // The ladder, in bracket order rather than by size — an ageing report
          // read out of order tells you nothing about the trend.
          const s = summariseAgeing(data)
          return (
            <div className="space-y-3">
              <StatRow>
                <Stat label="Total receivable" value={rupees(s.total)} />
                <Stat
                  label="Over 90 days"
                  value={rupees(s.over90)}
                  tone={s.over90 > 0 ? 'bad' : 'default'}
                />
              </StatRow>
              <div className="border border-gray-200 rounded-lg overflow-hidden">
                <table className="w-full text-sm" aria-label="Receivables by age">
                  <tbody className="divide-y divide-gray-100">
                    {s.buckets.map(b => (
                      <tr key={b.bracket}>
                        <td className="px-3 py-2 text-gray-700 w-44">{b.bracket}</td>
                        <td className="px-3 py-2">
                          <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                            <div
                              className="h-full bg-neutral-400 rounded-full"
                              style={{ width: `${b.share}%` }}
                            />
                          </div>
                        </td>
                        <td className="px-3 py-2 text-right text-gray-800 w-32 tabular-nums">
                          {rupees(b.amount)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )
        }}
      />
    </div>
  )
}
