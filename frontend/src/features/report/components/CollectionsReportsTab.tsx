import { ReportCard, DateRangeType } from './ReportCard'
import { useQuery } from '@tanstack/react-query'
import { reportApi } from '../../../services/report/reportApi'

interface CollectionsReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

function getDateParams(rangeType: DateRangeType) {
  const today = new Date()
  const y = today.getFullYear()
  const m = String(today.getMonth() + 1).padStart(2, '0')
  const d = String(today.getDate()).padStart(2, '0')

  if (rangeType === 'today') {
    const td = `${y}-${m}-${d}`
    return { from_date: td, to_date: td }
  }

  if (rangeType === 'current_month') {
    const firstDay = `${y}-${m}-01`
    const lastDay = new Date(y, today.getMonth() + 1, 0)
    const ldStr = `${y}-${m}-${String(lastDay.getDate()).padStart(2, '0')}`
    return { from_date: firstDay, to_date: ldStr }
  }

  if (rangeType === 'last_month') {
    const lastMonth = new Date(y, today.getMonth() - 1, 1)
    const lmY = lastMonth.getFullYear()
    const lmM = String(lastMonth.getMonth() + 1).padStart(2, '0')
    const firstDay = `${lmY}-${lmM}-01`
    const lastDay = new Date(lmY, lastMonth.getMonth() + 1, 0)
    const ldStr = `${lmY}-${lmM}-${String(lastDay.getDate()).padStart(2, '0')}`
    return { from_date: firstDay, to_date: ldStr }
  }

  return {}
}

const fmt = (n: number) => `₹ ${n.toLocaleString('en-IN')}`

// ── Net Collection Summary Table ──────────────────────────────────────
function NetCollectionTable({ rangeType }: { rangeType: DateRangeType }) {
  const dateParams = getDateParams(rangeType)

  const { data = [], isLoading } = useQuery({
    queryKey: ['report_summary', 'net_collection_summary', dateParams],
    queryFn: () => reportApi.executeJson('net_collection_summary', dateParams),
  })

  if (isLoading) return <div className="text-xs text-gray-400 italic">Loading summary...</div>

  if (!data || data.length === 0) {
    return (
      <div className="flex items-center justify-center bg-gray-50 border border-gray-200 text-gray-600 px-4 py-3 rounded-lg text-sm">
        <svg className="w-4 h-4 mr-2 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
        <span>No collection data found.</span>
      </div>
    )
  }

  const totals = data.reduce(
    (acc: any, r: any) => ({
      collection_cash: acc.collection_cash + Number(r.collection_cash || 0),
      cash_in_hand: acc.cash_in_hand + Number(r.cash_in_hand || 0),
      card: acc.card + Number(r.card || 0),
      upi: acc.upi + Number(r.upi || 0),
      net: acc.net + Number(r.net || 0),
    }),
    { collection_cash: 0, cash_in_hand: 0, card: 0, upi: 0, net: 0 }
  )

  return (
    <div className="overflow-x-auto rounded-lg border border-gray-200">
      <table className="w-full text-xs text-left text-gray-600 bg-white">
        <thead>
          <tr className="bg-gray-50 border-b border-gray-200">
            <th rowSpan={2} className="px-3 py-2 font-semibold text-left text-gray-700">User</th>
            <th colSpan={2} className="px-3 py-1.5 font-semibold text-center text-blue-700 border-b border-gray-200">CASH</th>
            <th rowSpan={2} className="px-3 py-2 font-semibold text-center text-orange-700">CARD</th>
            <th rowSpan={2} className="px-3 py-2 font-semibold text-center text-teal-700">UPI</th>
            <th rowSpan={2} className="px-3 py-2 font-semibold text-right text-gray-800">Net</th>
          </tr>
          <tr className="bg-gray-50 border-b border-gray-200">
            <th className="px-3 py-1.5 font-medium text-center text-gray-600">Collection</th>
            <th className="px-3 py-1.5 font-medium text-center text-gray-600">Cash In Hand</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {data.map((row: any, idx: number) => (
            <tr key={idx} className="hover:bg-gray-50/50">
              <td className="px-3 py-2 font-medium text-gray-900">{row.user}</td>
              <td className="px-3 py-2 text-right">{fmt(Number(row.collection_cash || 0))}</td>
              <td className="px-3 py-2 text-right">{fmt(Number(row.cash_in_hand || 0))}</td>
              <td className="px-3 py-2 text-right">{fmt(Number(row.card || 0))}</td>
              <td className="px-3 py-2 text-right">{fmt(Number(row.upi || 0))}</td>
              <td className="px-3 py-2 text-right font-semibold">{fmt(Number(row.net || 0))}</td>
            </tr>
          ))}
          <tr className="bg-gray-100/60 font-bold text-gray-900">
            <td className="px-3 py-2.5">Total</td>
            <td className="px-3 py-2.5 text-right">{fmt(totals.collection_cash)}</td>
            <td className="px-3 py-2.5 text-right">{fmt(totals.cash_in_hand)}</td>
            <td className="px-3 py-2.5 text-right">{fmt(totals.card)}</td>
            <td className="px-3 py-2.5 text-right">{fmt(totals.upi)}</td>
            <td className="px-3 py-2.5 text-right">{fmt(totals.net)}</td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}

// ── Mode-wise Summary Row (Cash / Card / Net) ────────────────
function ModeSummary({ data, columns }: { data: any; columns: { key: string; label: string; color?: string }[] }) {
  return (
    <div className="flex divide-x border rounded-lg bg-gray-50 text-center text-sm">
      {columns.map((col) => (
        <div key={col.key} className={`flex-1 p-2 ${col.key === 'net_amount' ? 'bg-gray-100/80' : ''}`}>
          <div className={`font-semibold mb-1 ${col.color || 'text-gray-500'}`}>{col.label}</div>
          <div className="text-lg font-bold text-gray-800">{fmt(Number(data?.[col.key] || 0))}</div>
        </div>
      ))}
    </div>
  )
}

// ── Main Component ────────────────────────────────────────────────────
export function CollectionsReportsTab({ onViewReport }: CollectionsReportsTabProps) {
  return (
    <div className="space-y-1">
      {/* ── 1. Net Collections ── */}
      <ReportCard
        title="Net Collections"
        reportName="net_collection_summary"
        detailReportName="net_collection_detail"
        onViewReport={onViewReport}
        renderSummary={(_data, range) => <NetCollectionTable rangeType={range} />}
      />

      {/* ── 2. Receipts ── */}
      <ReportCard
        title="Receipts"
        reportName="receipts_summary"
        detailReportName="receipts_detail"
        onViewReport={onViewReport}
        renderSummary={(data) => {
          const row = data?.[0] || {}
          return <ModeSummary data={row} columns={[
            { key: 'cash', label: 'Cash' },
            { key: 'card', label: 'Card' },
            { key: 'upi', label: 'UPI' },
            { key: 'net_amount', label: 'Net Amount', color: 'text-gray-600' },
          ]} />
        }}
      />

      {/* ── 3. Deposits ── */}
      <ReportCard
        title="Deposits"
        reportName="deposits_summary"
        detailReportName="deposits_detail"
        onViewReport={onViewReport}
        renderSummary={(data) => {
          const row = data?.[0] || {}
          return <ModeSummary data={row} columns={[
            { key: 'cash', label: 'Cash' },
            { key: 'card', label: 'Card' },
            { key: 'upi', label: 'UPI' },
            { key: 'net_amount', label: 'Net Amount', color: 'text-gray-600' },
          ]} />
        }}
      />

      {/* ── 4. Refunds ── */}
      <ReportCard
        title="Refunds"
        reportName="refunds_summary"
        detailReportName="refunds_detail"
        onViewReport={onViewReport}
        renderSummary={(data) => {
          const row = data?.[0] || {}
          return <ModeSummary data={row} columns={[
            { key: 'cash', label: 'Cash' },
            { key: 'card', label: 'Card' },
            { key: 'upi', label: 'UPI' },
            { key: 'net_amount', label: 'Net Amount', color: 'text-gray-600' },
          ]} />
        }}
      />

      {/* ── 5. Petty Cash (UI only — backend to be implemented) ── */}
      {/* <ReportCard
        title="Petty Cash"
        reportName="petty_cash_summary"
        onViewReport={onViewReport}
        renderSummary={() => (
          <ModeSummary
            data={{ cash: 0, net_amount: 0 }}
            columns={[
              { key: 'cash', label: 'Cash' },
              { key: 'net_amount', label: 'Net Amount', color: 'text-gray-600' },
            ]}
          />
        )}
      /> */}
    </div>
  )
}
