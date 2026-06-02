import { ReportCard, DateRangeType } from './ReportCard'

interface SalesReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

export function SalesReportsTab({ onViewReport }: SalesReportsTabProps) {
  const renderWarning = (message: string, rangeType: DateRangeType) => {
    const period = rangeType === 'today' ? 'today' : rangeType === 'current_month' ? 'this month' : 'last month'
    return (
      <div className="flex items-center justify-center bg-gray-50 border border-gray-200 text-gray-600 px-4 py-3 rounded-lg text-sm">
        <svg className="w-4 h-4 mr-2 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
        <span>
          {message.replace(' ! ', '. ')} <span className="font-semibold text-gray-800">{period}</span>.
        </span>
      </div>
    )
  }

  return (
    <div className="space-y-1">
      <ReportCard title="Pharmacy Sales Bills" reportName="pharmacy_sales_bills" onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) return renderWarning('No sales found. There are no sales bills', range)
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} bills.</div>
        }} />

      {/* <ReportCard title="Sales Collection Report" reportName="pharmacy_sales_collection" onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) return renderWarning('No collection records found. There are no collection entries', range)
          const totalNet = data.reduce((acc, row) => acc + (Number(row.net_amount) || 0), 0)
          return (
            <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">
              Total Collection: <span className="font-semibold text-gray-900">₹{totalNet.toFixed(2)}</span> ({data.length} users)
            </div>
          )
        }} /> */}

      {/* <div className="bg-white border border-gray-200 rounded-xl shadow-sm mb-4 mt-4">
        <table className="w-full text-sm">
          <tbody className="divide-y divide-gray-100">
            <tr>
              <td className="w-8"></td>
              <td className="py-4 font-semibold text-gray-700">Stock Ledger</td>
              <td className="py-4 pr-5 text-right w-48">
                <button
                  onClick={() => onViewReport('stock_ledger', {})}
                  className="px-4 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
                >
                  VIEW REPORT
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div> */}
    </div>
  )
}
