import { ReportCard, DateRangeType } from './ReportCard'

interface DiagnosticsReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

function DiagnosticSummaryTable({ data, type }: { data: any[], type: 'done' | 'pending' }) {
  if (!data || data.length === 0) return null
  
  const opKey = type === 'done' ? 'op_done' : 'op_pending'
  const ipKey = type === 'done' ? 'ip_done' : 'ip_pending'
  const totKey = type === 'done' ? 'total_done' : 'total_pending'
  const opLabel = type === 'done' ? 'No of Test Done for OP' : 'No of Test Pending for OP'
  const ipLabel = type === 'done' ? 'No of Test Done for IP' : 'No of Test Pending for IP'
  const totLabel = type === 'done' ? 'Total Test Done' : 'Total Test Pending'

  const totals = data.reduce((acc, row) => ({
    op: acc.op + Number(row[opKey] || 0),
    ip: acc.ip + Number(row[ipKey] || 0),
    tot: acc.tot + Number(row[totKey] || 0)
  }), { op: 0, ip: 0, tot: 0 })

  return (
    <div className="overflow-x-auto rounded-lg border border-gray-200 mt-2">
      <table className="w-full text-xs text-left text-gray-600 bg-white">
        <thead>
          <tr className="bg-gray-50 border-b border-gray-200">
            <th className="px-3 py-2 font-semibold text-gray-700">Department</th>
            <th className="px-3 py-2 font-semibold text-center text-gray-700">{opLabel}</th>
            <th className="px-3 py-2 font-semibold text-center text-gray-700">{ipLabel}</th>
            <th className="px-3 py-2 font-semibold text-center text-gray-700">{totLabel}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {data.map((row: any, idx: number) => (
            <tr key={idx} className="hover:bg-gray-50/50">
              <td className="px-3 py-2 font-medium text-gray-900">{row.department || 'UNASSIGNED'}</td>
              <td className="px-3 py-2 text-center">{row[opKey] || 0}</td>
              <td className="px-3 py-2 text-center">{row[ipKey] || 0}</td>
              <td className="px-3 py-2 text-center font-medium">{row[totKey] || 0}</td>
            </tr>
          ))}
          <tr className="bg-gray-50 font-bold text-gray-900">
            <td className="px-3 py-2.5">Total</td>
            <td className="px-3 py-2.5 text-center">{totals.op}</td>
            <td className="px-3 py-2.5 text-center">{totals.ip}</td>
            <td className="px-3 py-2.5 text-center">{totals.tot}</td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}

export function DiagnosticsReportsTab({ onViewReport }: DiagnosticsReportsTabProps) {
  const renderWarning = (message: string, rangeType: DateRangeType) => {
    const period = rangeType === 'today' ? 'today' : rangeType === 'current_month' ? 'this month' : 'last month'
    return (
      <div className="flex items-center justify-center bg-gray-50 border border-gray-200 text-gray-600 px-4 py-3 rounded-lg text-sm mt-2">
        <svg className="w-4 h-4 mr-2 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
        <span>
          {message.replace(' ! ', '. ')} <span className="font-semibold text-gray-800">{period}</span>.
        </span>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <ReportCard 
        title="Completed Tests" 
        reportName="lab_tests_done" 
        detailReportName="lab_tests_done_detail"
        onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) return renderWarning('No lab tests found. There are no lab tests done', range)
          return <DiagnosticSummaryTable data={data} type="done" />
        }} 
      />
      <ReportCard 
        title="Pending Tests" 
        reportName="lab_pending" 
        detailReportName="lab_pending_detail"
        hideFilters={false}
        onViewReport={onViewReport}
        renderSummary={(data) => {
          if (!data || data.length === 0) return <div className="text-sm text-gray-500 italic px-2 mt-2">No pending lab tests.</div>
          return <DiagnosticSummaryTable data={data} type="pending" />
        }} 
      />

    </div>
  )
}
