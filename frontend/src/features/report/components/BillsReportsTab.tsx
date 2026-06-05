import { ReportCard, DateRangeType } from './ReportCard'
import { useQuery } from '@tanstack/react-query'
import { reportApi } from '../../../services/report/reportApi'

interface BillsReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

function getDateParams(rangeType: DateRangeType): Record<string, string> {
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

interface IPOutstandingSummaryTableProps {
  rangeType: DateRangeType
}

export function IPOutstandingSummaryTable({ rangeType }: IPOutstandingSummaryTableProps) {
  const dateParams = getDateParams(rangeType)

  const { data = [], isLoading } = useQuery({
    queryKey: ['report_summary', 'ip_outstanding_bills_summary', dateParams],
    queryFn: () => reportApi.executeJson('ip_outstanding_bills_summary', dateParams),
    staleTime: 0,
    refetchOnMount: 'always',
  })

  if (isLoading) {
    return <div className="text-xs text-gray-400 mt-2 italic">Loading IP outstanding summary...</div>
  }

  if (!data || data.length === 0) {
    return null
  }

  const netTotal = data.reduce((sum: number, row: any) => sum + Number(row.net_amount || 0), 0)
  const paidTotal = data.reduce((sum: number, row: any) => sum + Number(row.paid_amount || 0), 0)
  const balanceTotal = data.reduce((sum: number, row: any) => sum + Number(row.balanced_amount || 0), 0)

  return (
    <div className="mt-4 border-t pt-3">
      <h5 className="text-xs font-bold text-gray-700 mb-2">IP Outstanding Summary</h5>
      <div className="overflow-x-auto rounded-lg border border-gray-200">
        <table className="w-full text-xs text-left text-gray-600 bg-white">
          <thead className="text-xs text-gray-700 uppercase bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="px-4 py-2.5 font-semibold text-left">Payor</th>
              <th className="px-4 py-2.5 font-semibold text-right">Bill Amount</th>
              <th className="px-4 py-2.5 font-semibold text-right">Paid Amount</th>
              <th className="px-4 py-2.5 font-semibold text-right">Balance Amount</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-150">
            {data.map((summary: any, idx: number) => (
              <tr key={idx} className="hover:bg-gray-50/50">
                <td className="px-4 py-2 font-medium text-gray-900">{summary.payor}</td>
                <td className="px-4 py-2 text-right">₹{Number(summary.net_amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                <td className="px-4 py-2 text-right">₹{Number(summary.paid_amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                <td className="px-4 py-2 text-right">₹{Number(summary.balanced_amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
              </tr>
            ))}
            <tr className="bg-gray-100/50 font-bold text-gray-900">
              <td className="px-4 py-2.5">Total</td>
              <td className="px-4 py-2.5 text-right">₹{netTotal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
              <td className="px-4 py-2.5 text-right">₹{paidTotal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
              <td className="px-4 py-2.5 text-right">₹{balanceTotal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}

export function BillsReportsTab({ onViewReport }: BillsReportsTabProps) {
  const renderWarning = (message: string, rangeType: DateRangeType) => {
    const period = rangeType === 'today' ? 'today' : rangeType === 'current_month' ? 'this month' : 'last month'
    return (
      <div className="flex items-center justify-center bg-gray-50 border border-gray-250 text-gray-500 px-4 py-3 rounded-lg text-sm">
        <svg className="w-4 h-4 mr-2 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
        <span>
          {message.replace(' ! ', '. ')} <span className="font-semibold text-gray-800">{period}</span>.
        </span>
      </div>
    )
  }

  return (
    <div className="space-y-1">
      <ReportCard
        title="Bill Raised"
        reportName="bill_raised_summary"
        detailReportName="bills_raised_daywise"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          const row = data?.[0] || {}
          const op = Number(row.OP_Bill_AMOUNT || 0)
          const ipCash = Number(row.IP_CASH_Bill_AMOUNT || 0)
          const ipCredit = Number(row.IP_CREDIT_Bill_AMOUNT || 0)
          const net = op + ipCash + ipCredit

          if (net === 0) {
            return renderWarning('No bill raised ! There is no bills raised', range)
          }

          return (
            <div className="flex divide-x border rounded-lg bg-gray-50 text-center text-sm">
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">OP Bills</div>
                <div className="text-lg font-bold text-gray-800">₹{op.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">IP Cash Bills</div>
                <div className="text-lg font-bold text-gray-800">₹{ipCash.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">IP Credit Bills</div>
                <div className="text-lg font-bold text-gray-800">₹{ipCredit.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2 bg-gray-100/80">
                <div className="text-gray-600 font-semibold mb-1">Net Amount</div>
                <div className="text-lg font-bold text-gray-950">₹{net.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
            </div>
          )
        }}
      />

      <ReportCard
        title="Cancelled Bills"
        reportName="bill_cancelled_summary"
        detailReportName="bills_cancelled_daywise"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          const row = data?.[0] || {}
          const op = Number(row.OP_CAN_AMOUNT || 0)
          const ipCash = Number(row.IP_CASH_CAN_AMOUNT || 0)
          const ipCredit = Number(row.IP_CREDIT_CAN_AMOUNT || 0)
          const net = op + ipCash + ipCredit

          if (net === 0) {
            return renderWarning('No bill cancelled ! There is no bills cancelled', range)
          }

          return (
            <div className="flex divide-x border rounded-lg bg-gray-50 text-center text-sm">
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">OP Bills</div>
                <div className="text-lg font-bold text-gray-800">₹{op.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">IP Cash Bills</div>
                <div className="text-lg font-bold text-gray-800">₹{ipCash.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">IP Credit Bills</div>
                <div className="text-lg font-bold text-gray-800">₹{ipCredit.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2 bg-gray-100/80">
                <div className="text-gray-600 font-semibold mb-1">Net Amount</div>
                <div className="text-lg font-bold text-gray-955">₹{net.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
            </div>
          )
        }}
      />

      <ReportCard
        title="Discounts"
        reportName="discount_summary"
        detailReportName="discount_report"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          const row = data?.[0] || {}
          const op = Number(row.OP_DIS_AMOUNT || 0)
          const ipCash = Number(row.IP_CASH_DIS_AMOUNT || 0)
          const ipCredit = Number(row.IP_CREDIT_DIS_AMOUNT || 0)
          const net = op + ipCash + ipCredit

          if (net === 0) {
            return renderWarning('No discount given ! There is no discount given', range)
          }

          return (
            <div className="flex divide-x border rounded-lg bg-gray-50 text-center text-sm">
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">OP Bills</div>
                <div className="text-lg font-bold text-gray-800">₹{op.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">IP Cash Bills</div>
                <div className="text-lg font-bold text-gray-800">₹{ipCash.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">IP Credit Bills</div>
                <div className="text-lg font-bold text-gray-800">₹{ipCredit.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2 bg-gray-100/80">
                <div className="text-gray-600 font-semibold mb-1">Net Amount</div>
                <div className="text-lg font-bold text-gray-955">₹{net.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
            </div>
          )
        }}
      />

      <ReportCard
        title="IP OverDue Bills"
        reportName="overdue_bills_summary"
        detailReportName="bills_overdue"
        hideFilters
        onViewReport={onViewReport}
        renderSummary={(data) => {
          const row = data?.[0] || {}
          const billed = Number(row.bill_amount || 0)
          const deposits = Number(row.paid_amount || 0)
          const due = Number(row.due_amount || 0)

          if (billed === 0 && deposits === 0 && due === 0) {
            return <div className="text-sm text-gray-500 italic px-2">No overdue bills found.</div>
          }

          return (
            <div className="flex divide-x border rounded-lg bg-gray-50 text-center text-sm">
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">Billed</div>
                <div className="text-lg font-bold text-gray-800">₹{billed.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2">
                <div className="text-gray-500 font-semibold mb-1">Deposits</div>
                <div className="text-lg font-bold text-gray-800">₹{deposits.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
              <div className="flex-1 p-2 bg-gray-100/80">
                <div className="text-gray-600 font-semibold mb-1">OverDue</div>
                <div className="text-lg font-bold text-gray-800">₹{due.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
              </div>
            </div>
          )
        }}
      />

      <ReportCard
        title="Outstanding Bills"
        reportName="outstanding_bills_summary"
        detailReportName="unsettled_bills"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          const row = data?.[0] || {}
          const op = Number(row.OP_OUT_AMOUNT || 0)
          const ipCash = Number(row.IP_CASH_OUT_AMOUNT || 0)
          const ipCredit = Number(row.IP_CREDIT_OUT_AMOUNT || 0)
          const net = op + ipCash + ipCredit

          const hasData = net > 0

          return (
            <div className="flex flex-col w-full">
              {!hasData ? (
                renderWarning('No outstanding bills ! There is no outstanding bills', range)
              ) : (
                <>
                  <div className="flex divide-x border rounded-lg bg-gray-50 text-center text-sm">
                    <div className="flex-1 p-2">
                      <div className="text-gray-500 font-semibold mb-1">OP Bills</div>
                      <div className="text-lg font-bold text-gray-800">₹{op.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                    </div>
                    <div className="flex-1 p-2">
                      <div className="text-gray-500 font-semibold mb-1">IP Cash Bills</div>
                      <div className="text-lg font-bold text-gray-800">₹{ipCash.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                    </div>
                    <div className="flex-1 p-2">
                      <div className="text-gray-500 font-semibold mb-1">IP Credit Bills</div>
                      <div className="text-lg font-bold text-gray-800">₹{ipCredit.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                    </div>
                    <div className="flex-1 p-2 bg-gray-100/80">
                      <div className="text-gray-600 font-semibold mb-1">Net Amount</div>
                      <div className="text-lg font-bold text-gray-955">₹{net.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                    </div>
                  </div>
                  <IPOutstandingSummaryTable rangeType={range} />
                </>
              )}
            </div>
          )
        }}
      />

    </div>
  )
}
