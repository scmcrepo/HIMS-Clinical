import { useState } from 'react'
import { ReportDetailView } from './ReportDetailView'

interface CategoryReportPageProps {
  title: string
  children: (onViewReport: (reportName: string, params: Record<string, string>) => void) => React.ReactNode
}

export function CategoryReportPage({ title, children }: CategoryReportPageProps) {
  const [reportHistory, setReportHistory] = useState<{name: string, params: Record<string, string>}[]>([])

  function handleViewReport(reportName: string, params: Record<string, string>) {
    setReportHistory([{ name: reportName, params }])
  }

  function handleDrilldown(reportName: string, params: Record<string, string>) {
    setReportHistory(prev => [...prev, { name: reportName, params }])
  }

  if (reportHistory.length > 0) {
    const currentReport = reportHistory[reportHistory.length - 1]
    const canGoBack = reportHistory.length > 1

    return (
      <div className="-mx-4 -mt-4">
        <ReportDetailView 
          key={`${currentReport.name}-${JSON.stringify(currentReport.params)}`}
          reportName={currentReport.name} 
          initialParams={currentReport.params} 
          onClose={() => setReportHistory([])} 
          onDrilldown={handleDrilldown}
          onBack={canGoBack ? () => setReportHistory(prev => prev.slice(0, -1)) : undefined}
        />
      </div>
    )
  }

  return (
    <div className="max-w-5xl mx-auto w-full pb-10">
      <h2 className="text-2xl font-bold text-gray-800 mb-6 flex items-center gap-2">
        {title}
      </h2>
      {children(handleViewReport)}
    </div>
  )
}
