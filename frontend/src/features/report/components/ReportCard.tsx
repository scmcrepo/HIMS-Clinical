import { useState } from 'react'
import type { ReactNode } from 'react'
import { cn } from '../../../lib/utils'
import { useQuery } from '@tanstack/react-query'
import { reportApi } from '../../../services/report/reportApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'

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
  buttonClassName?: string
  defaultRangeType?: DateRangeType
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
  collapsible = false,
  buttonClassName,
  defaultRangeType
}: ReportCardProps) {
  const [rangeType, setRangeType] = useState<DateRangeType>(defaultRangeType || (hideFilters ? 'current_month' : 'today'))
  const [isOpen, setIsOpen] = useState(true)
  const [consultantId, setConsultantId] = useState<string>('')
  
  const baseParams = getDateParams(rangeType)
  const dateParams = showConsultantFilter && consultantId ? { ...baseParams, consultant_id: consultantId } : baseParams

  const { data = [], isLoading } = useQuery({
    queryKey: ['report_summary', reportName, dateParams],
    queryFn: () => reportApi.executeJson(reportName, dateParams),
    enabled: !!renderSummary && isOpen,
    staleTime: 0,
    refetchOnMount: 'always',
  })

  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
    enabled: showConsultantFilter,
  })

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm mb-4">
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
              className={cn("w-4 h-4 transform transition-transform duration-250 text-gray-400 hover:text-gray-600", isOpen ? "rotate-90 text-neutral-600" : "")}
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
            <div className="min-w-[200px]" onClick={e => e.stopPropagation()}>
              <ConsultantSearchInput
                consultants={consultants}
                value={consultantId}
                onChange={setConsultantId}
                placeholder="All Consultants"
                size="sm"
              />
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
            className={cn(
              "flex-shrink-0 inline-flex items-center justify-center px-4 py-2 text-white text-xs font-semibold transition-colors shadow-sm",
              buttonClassName || "bg-neutral-600 hover:bg-neutral-700 rounded-lg"
            )}
          >
            VIEW DETAIL REPORT
          </button>
        </div>
      )}
    </div>
  )
}
