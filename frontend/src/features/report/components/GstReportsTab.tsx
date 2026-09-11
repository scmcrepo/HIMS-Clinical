import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ReportCard } from './ReportCard'
import { gstApi } from '../../../services/gst/gstApi'

interface GstReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

const fmt = (n: number) => `₹ ${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const sum = (rows: any[], key: string) => rows.reduce((t: number, r: any) => t + (Number(r[key]) || 0), 0)

/**
 * GST reports — outward pharmacy sales and inward purchases.
 *
 * The two summaries deliberately read differently because the two sides of the
 * ledger answer different questions. Sales asks "what tax did we collect and owe",
 * so it leads with output tax. Purchases asks "what tax did we pay that we can
 * claim back", so it leads with input tax. A uniform row of counters on both would
 * hide the one figure that matters on each.
 */
export function GstReportsTab({ onViewReport }: GstReportsTabProps) {
  const Stat = ({ label, value, wide }: { label: string; value: string; wide?: boolean }) => (
    <div className="flex-1">
      <div className="text-[13px] font-semibold text-gray-500 mb-1">{label}</div>
      <div className={`text-3xl font-normal text-neutral-900 ${wide ? 'whitespace-nowrap' : ''}`}>{value}</div>
    </div>
  )

  const empty = (message: string) => (
    <div className="flex items-center bg-gray-50 border border-gray-200 text-gray-600 px-4 py-3 rounded-lg text-sm">
      <svg className="w-4 h-4 mr-2 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2"
              d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{message}</span>
    </div>
  )

  // C6 — the filing strip appears only where the integration is switched on. Report
  // viewers may not hold SETTINGS_GST, so a refused config read is a normal outcome
  // here, not an error: retry is off and the strip simply stays hidden.
  const { data: gstConfig } = useQuery({
    queryKey: ['gstConfig', 'reportsTab'],
    queryFn: gstApi.getConfig,
    retry: false,
    staleTime: 5 * 60 * 1000,
  })

  return (
    <div className="space-y-1">
      {gstConfig?.integrationEnabled && (
        <div className="flex flex-wrap items-center justify-between gap-3 bg-white border border-gray-200 rounded-xl shadow-sm px-5 py-3 mb-4">
          <div className="text-sm text-gray-700">
            <span className="font-semibold">GST filing is enabled</span> for this hospital
            {gstConfig.stateCode && <> · state <span className="font-mono">{gstConfig.stateCode}</span></>}
            <span className={`ml-2 inline-block px-2 py-0.5 rounded text-[11px] font-semibold border ${
              gstConfig.activeEnvironment === 'PRODUCTION'
                ? 'bg-amber-50 text-amber-800 border-amber-200'
                : 'bg-gray-50 text-gray-600 border-gray-200'}`}>
              {gstConfig.activeEnvironment}
            </span>
          </div>
          <Link
            to="/admin/masters?tab=gst"
            className="px-4 py-2 text-xs font-semibold text-white bg-neutral-600 rounded-lg hover:bg-neutral-700"
          >
            Generate &amp; file returns
          </Link>
        </div>
      )}

      <ReportCard
        title="Pharmacy Sales GST Detailed"
        reportName="pharmacy_sales_gst_detailed"
        onViewReport={onViewReport}
        defaultRangeType="current_month"
        renderSummary={(data = []) => {
          if (!data.length) return empty('No pharmacy sales in this period.')

          const taxable = sum(data, 'sale_excluding_tax')
          const gst = sum(data, 'gst')
          // Codes that cannot be summarised at 4, 6 or 8 digits fall outside GST's
          // HSN reporting levels. Surfacing the count here is what makes the backlog
          // visible to the people who can fix it.
          const unclassified = new Set(
            data
              .map((r: any) => String(r.hsn_code ?? '').trim())
              .filter((c: string) => c !== '' && !/^(\d{4}|\d{6}|\d{8})$/.test(c))
          ).size

          return (
            <div className="space-y-3">
              <div className="flex items-center gap-12 text-center py-2 max-w-2xl">
                <Stat label="Taxable value" value={fmt(taxable)} wide />
                <Stat label="GST collected" value={fmt(gst)} wide />
                <Stat label="Line items" value={String(data.length)} />
              </div>
              {unclassified > 0 && (
                <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                  {unclassified} HSN {unclassified === 1 ? 'code is' : 'codes are'} not 4, 6 or 8 digits and
                  will group as <span className="font-semibold">Unclassified</span> in the HSN summary.
                </div>
              )}
            </div>
          )
        }}
      />

      <ReportCard
        title="Purchase GST Details"
        reportName="purchase_gst_details"
        onViewReport={onViewReport}
        defaultRangeType="current_month"
        renderSummary={(data = []) => {
          if (!data.length) return empty('No goods received in this period.')

          const taxable = sum(data, 'purchase_value')
          const inputTax = sum(data, 'purchase_tax')
          const grns = new Set(data.map((r: any) => r.grn_no)).size

          return (
            <div className="flex items-center gap-12 text-center py-2 max-w-2xl">
              <Stat label="Taxable value" value={fmt(taxable)} wide />
              <Stat label="Input tax" value={fmt(inputTax)} wide />
              <Stat label="GRNs" value={String(grns)} />
            </div>
          )
        }}
      />

      <ReportCard
        title="OP/IP Services GST Detailed"
        reportName="service_gst_detailed"
        onViewReport={onViewReport}
        defaultRangeType="current_month"
        renderSummary={(data = []) => {
          if (!data.length) return empty('No OP or IP service lines in this period.')

          const unclassified = data.filter((r: any) => r.gst_treatment === 'UNCLASSIFIED')
          const unclassifiedValue = sum(unclassified, 'total_value')
          const totalValue = sum(data, 'total_value')
          const share = totalValue > 0 ? (unclassifiedValue / totalValue) * 100 : 0

          return (
            <div className="space-y-3">
              <div className="flex items-center gap-12 text-center py-2 max-w-2xl">
                <Stat label="Total billed" value={fmt(totalValue)} wide />
                <Stat label="GST" value={fmt(sum(data, 'gst'))} wide />
                <Stat label="Service lines" value={String(data.length)} />
              </div>
              {unclassified.length > 0 && (
                <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                  <span className="font-semibold">{fmt(unclassifiedValue)}</span> ({share.toFixed(1)}% of the total)
                  is on services nobody has classified for GST yet, so it reports with no tax and under no heading.
                  Set each service's treatment under Settings → Charge.
                </div>
              )}
            </div>
          )
        }}
      />

      <ReportCard
        title="HSN-wise Tax Summary"
        reportName="hsn_tax_summary"
        onViewReport={onViewReport}
        defaultRangeType="current_month"
        renderSummary={(data = []) => {
          if (!data.length) return empty('No supplies to summarise in this period.')

          const unclassified = data.filter((r: any) => r.hsn_code === 'Unclassified')
          const unclassifiedValue = sum(unclassified, 'total_value')
          const totalValue = sum(data, 'total_value')
          const share = totalValue > 0 ? (unclassifiedValue / totalValue) * 100 : 0
          const reportable = data.length - unclassified.length

          return (
            <div className="space-y-3">
              <div className="flex items-center gap-12 text-center py-2 max-w-2xl">
                <Stat label="Reportable HSN groups" value={String(reportable)} />
                <Stat label="Taxable value" value={fmt(sum(data, 'taxable_value'))} wide />
                <Stat label="GST" value={fmt(sum(data, 'gst'))} wide />
              </div>
              {unclassified.length > 0 && (
                <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                  <span className="font-semibold">{fmt(unclassifiedValue)}</span> ({share.toFixed(1)}% of turnover)
                  sits under <span className="font-semibold">Unclassified</span> because its HSN codes are not 4, 6
                  or 8 digits. Fix them under Settings → HSN Data Quality.
                </div>
              )}
            </div>
          )
        }}
      />

      <ReportCard
        title="Tax Liability Overview"
        reportName="gst_tax_liability"
        onViewReport={onViewReport}
        defaultRangeType="current_month"
        renderSummary={(data = []) => {
          if (!data.length) return empty('No output or input tax in this period.')

          const outputTax = sum(data, 'output_tax')
          const inputTax = sum(data, 'input_tax')
          const net = outputTax - inputTax

          return (
            <div className="space-y-3">
              <div className="flex items-center gap-12 text-center py-2 max-w-2xl">
                <Stat label="Output tax" value={fmt(outputTax)} wide />
                <Stat label="Input tax" value={fmt(inputTax)} wide />
                <div className="flex-1">
                  <div className="text-[13px] font-semibold text-gray-500 mb-1">
                    {net >= 0 ? 'Net payable' : 'Net credit'}
                  </div>
                  <div className={`text-3xl font-normal whitespace-nowrap ${net >= 0 ? 'text-neutral-900' : 'text-emerald-700'}`}>
                    {fmt(Math.abs(net))}
                  </div>
                </div>
              </div>
              <div className="text-xs text-gray-500 bg-gray-50 border border-gray-200 rounded-lg px-3 py-2">
                Indicative working figure, not a return. Output tax covers pharmacy sales plus any
                service classified <span className="font-semibold">Taxable</span> — services still
                unclassified contribute nothing, so this figure rises as they are classified.
              </div>
            </div>
          )
        }}
      />
    </div>
  )
}
