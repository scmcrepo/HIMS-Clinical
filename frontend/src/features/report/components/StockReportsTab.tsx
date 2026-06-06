import { DateRangeType, ReportCard } from './ReportCard'

interface StockReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

export function StockReportsTab({ onViewReport }: StockReportsTabProps) {
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
      <ReportCard title="Current Stock" reportName="current_stock" hideFilters onViewReport={onViewReport}
        renderSummary={(data) => {
          if (!data || data.length === 0) return <div className="text-sm text-gray-500 italic px-2">No stock available.</div>
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} items in stock.</div>
        }} />
      <ReportCard title="Expiry Stock Report" reportName="expired_items" hideFilters defaultRangeType="today" onViewReport={onViewReport}
        renderSummary={(data) => {
          if (!data || data.length === 0) return <div className="text-sm text-gray-500 italic px-2">No expired items.</div>
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} expired items.</div>
        }} />
      <ReportCard title="Nearing Expiry Stock Report" reportName="items_expiring_month" hideFilters onViewReport={onViewReport}
        renderSummary={(data) => {
          if (!data || data.length === 0) return <div className="text-sm text-gray-500 italic px-2">No items expiring soon.</div>
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} items expiring soon.</div>
        }} />
      <ReportCard title="Non Moving Stock Report" reportName="slow_moving_items" hideFilters onViewReport={onViewReport}
        renderSummary={(data) => {
          if (!data || data.length === 0) return <div className="text-sm text-gray-500 italic px-2">No non moving stock.</div>
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} items.</div>
        }} />
      <ReportCard title="Nil Stock Report" reportName="zero_stock_items" hideFilters onViewReport={onViewReport}
        renderSummary={(data) => {
          if (!data || data.length === 0) return <div className="text-sm text-gray-500 italic px-2">No nil stock items.</div>
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} items out of stock.</div>
        }} />
      {/* <ReportCard title="Stock and Nil Stock Report" reportName="stock_and_nil_stock" onViewReport={onViewReport}
        renderSummary={() => {
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">Combined current stock and nil stock items.</div>
        }} /> */}
      <ReportCard title="Reorder Report" reportName="below_reorder_level" hideFilters onViewReport={onViewReport}
        renderSummary={(data) => {
          if (!data || data.length === 0) return <div className="text-sm text-gray-500 italic px-2">All items above reorder level.</div>
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} items need reorder.</div>
        }} />
      <ReportCard title="Scheduled Drug Report" reportName="scheduled_drug_sales" onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) return renderWarning('No scheduled drug sales. There are no sales', range)
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} records.</div>
        }} />
      <ReportCard title="Stock Correction Report" reportName="stock_adjustments" onViewReport={onViewReport}
        renderSummary={(data, range) => {
          if (!data || data.length === 0) return renderWarning('No stock adjustments. There are no adjustments', range)
          return <div className="text-sm font-medium text-gray-700 bg-gray-50 border p-3 rounded-lg">{data.length} records.</div>
        }} />
    </div>
  )
}
