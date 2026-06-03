import { useState } from 'react'
import type { ReactNode } from 'react'
import { cn } from '../../../lib/utils'
import { useQuery } from '@tanstack/react-query'
import { reportApi } from '../../../services/report/reportApi'
import { consultantApi } from '../../../services/consultant/consultantApi'

export type DateRangeType = 'today' | 'current_month' | 'last_month' | 'all'

interface ReportCardProps {
  title: string
  reportName: string
  detailReportName?: string
  hideFilters?: boolean
  showConsultantFilter?: boolean
  onViewReport: (reportName: string, dateParams: Record<string, string>) => void
  renderSummary?: (data: any[], rangeType: DateRangeType) => ReactNode
  collapsible?: boolean
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

export function ReportCard({ 
  title, 
  reportName, 
  detailReportName, 
  hideFilters = false,
  showConsultantFilter = false,
  onViewReport, 
  renderSummary,
  collapsible = false 
}: ReportCardProps) {
  const [rangeType, setRangeType] = useState<DateRangeType>(hideFilters ? 'current_month' : 'today')
  const [isOpen, setIsOpen] = useState(true)
  const [consultantId, setConsultantId] = useState<string>('')
  
  const baseParams = getDateParams(rangeType)
  const dateParams = showConsultantFilter && consultantId ? { ...baseParams, consultant_id: consultantId } : baseParams

  const { data = [], isLoading } = useQuery({
    queryKey: ['report_summary', reportName, dateParams],
    queryFn: () => reportApi.executeJson(reportName, dateParams),
    enabled: !!renderSummary && isOpen,
  })

  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
    enabled: showConsultantFilter,
  })

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm mb-4 overflow-hidden">
      <div 
        className={cn(
          "px-5 py-3 border-b border-gray-100 flex flex-col md:flex-row md:items-center justify-between gap-3 select-none",
          collapsible && "cursor-pointer hover:bg-gray-50/50 transition-colors"
        )}
        onClick={collapsible ? () => setIsOpen(!isOpen) : undefined}
      >
        <div className="flex items-center gap-2.5">
          {collapsible && (
            <svg
              className={cn("w-4 h-4 transform transition-transform duration-250 text-gray-400 hover:text-gray-600", isOpen ? "rotate-90 text-blue-600" : "")}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M9 5l7 7-7 7" />
            </svg>
          )}
          <h4 className="text-sm font-semibold text-gray-700 m-0">{title}</h4>
        </div>
        
        <div className="flex items-center gap-3">
          {showConsultantFilter && isOpen && (
            <div className="relative" onClick={e => e.stopPropagation()}>
              <select
                value={consultantId}
                onChange={(e) => setConsultantId(e.target.value)}
                className="pl-3 pr-8 py-1.5 text-xs font-medium border border-gray-200 rounded-lg text-gray-600 bg-white focus:outline-none focus:border-blue-300 focus:ring-1 focus:ring-blue-300 appearance-none min-w-[160px]"
              >
                <option value="">All Consultants</option>
                {consultants.map((c: any) => (
                  <option key={c.id} value={c.id}>
                    {c.firstName} {c.lastName}
                  </option>
                ))}
              </select>
              {consultantId && (
                <button
                  type="button"
                  onClick={() => setConsultantId('')}
                  className="absolute right-6 top-1/2 -translate-y-1/2 text-gray-400 hover:text-red-500 transition-colors bg-white px-1"
                  title="Clear"
                >
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              )}
              <svg className="w-3.5 h-3.5 text-gray-400 absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          )}

          {!hideFilters && isOpen && (
            <div className="flex gap-1 bg-gray-100 p-0.5 rounded-lg" onClick={e => e.stopPropagation()}>
              {(['today', 'current_month', 'last_month'] as const).map(type => (
                <button
                  key={type}
                  type="button"
                  onClick={() => setRangeType(type)}
                  className={cn(
                    'px-3 py-1 text-xs font-medium rounded transition-colors',
                    rangeType === type
                      ? 'bg-white text-gray-900 shadow-sm'
                      : 'text-gray-500 hover:text-gray-700'
                  )}
                >
                  {type === 'today' ? 'Today' : type === 'current_month' ? 'Current Month' : 'Last Month'}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {isOpen && (
        <div className="flex items-center justify-between px-5 py-4 gap-4 animate-in fade-in duration-200">
          <div className="flex-1">
            {renderSummary ? (
               isLoading ? (
                 <div className="text-xs text-gray-400">Loading summary...</div>
               ) : (
                 renderSummary(data, rangeType)
               )
            ) : null}
          </div>
          
          <button
            type="button"
            onClick={() => onViewReport(detailReportName || reportName, dateParams)}
            className="flex-shrink-0 inline-flex items-center justify-center px-4 py-2 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
          >
            VIEW DETAIL REPORT
          </button>
        </div>
      )}
    </div>
  )
}
