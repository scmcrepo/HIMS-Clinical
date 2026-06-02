import { ReportCard, DateRangeType } from './ReportCard'

interface InsuranceReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

export function InsuranceReportsTab({ onViewReport }: InsuranceReportsTabProps) {
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
      <ReportCard title="Insurance Claim Summary" reportName="insurance_summary" onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) return renderWarning('No insurance claims found. There are no claims', range)
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} claims.</div>
        }} />
    </div>
  )
}
