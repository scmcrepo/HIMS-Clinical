import { ReportCard } from './ReportCard'

interface PurchaseReportsTabProps {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}

const fmt = (n: number) => `₹\u00a0${n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export function PurchaseReportsTab({ onViewReport }: PurchaseReportsTabProps) {
  return (
    <div className="space-y-1">
      <ReportCard 
        title="Purchase Order" 
        reportName="purchase_orders_report" 
        onViewReport={onViewReport}
        renderSummary={(data = []) => {
          const completed = data.filter((x: any) => x.order_status === 'RECEIVED').length
          const pending = data.filter((x: any) => x.order_status !== 'RECEIVED').length
          const total = data.length

          return (
            <div className="flex items-center gap-12 text-center py-2 max-w-xl">
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">No of completed</div>
                <div className="text-3xl font-normal text-neutral-900">{completed}</div>
              </div>
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">No of pending</div>
                <div className="text-3xl font-normal text-neutral-900">{pending}</div>
              </div>
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Total</div>
                <div className="text-3xl font-normal text-neutral-900">{total}</div>
              </div>
            </div>
          )
        }} 
      />

      <ReportCard 
        title="Goods Received (GRN)" 
        reportName="goods_received_report" 
        onViewReport={onViewReport}
        renderSummary={(data = []) => {
          const totalGrns = data.length
          const totalQtyReceived = data.reduce((sum: number, x: any) => sum + (Number(x.total_qty_received) || 0), 0)
          const totalPurchaseValue = data.reduce((sum: number, x: any) => sum + (Number(x.grn_value) || 0), 0)

          return (
            <div className="flex items-center gap-12 text-center py-2 max-w-xl">
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Purchase Total</div>
                <div className="text-3xl font-normal text-neutral-900 whitespace-nowrap">{fmt(totalPurchaseValue)}</div>
              </div>
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Total Quatity</div>
                <div className="text-3xl font-normal text-neutral-900">{totalQtyReceived}</div>
              </div>
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">No of Item</div>
                <div className="text-3xl font-normal text-neutral-900">{totalGrns}</div>
              </div>
            </div>
          )
        }} 
      />

      <ReportCard 
        title="Goods Returned" 
        reportName="goods_returned_report" 
        onViewReport={onViewReport}
        renderSummary={(data = []) => {
          const totalReturns = data.length
          const totalQtyReturned = data.reduce((sum: number, x: any) => sum + (Number(x.total_qty_returned) || 0), 0)
          const totalReturnValue = data.reduce((sum: number, x: any) => sum + (Number(x.return_value) || 0), 0)

          return (
            <div className="flex items-center gap-12 text-center py-2 max-w-xl">
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Return Total</div>
                <div className="text-3xl font-normal text-neutral-900 whitespace-nowrap">{fmt(totalReturnValue)}</div>
              </div>
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">Total Quatity</div>
                <div className="text-3xl font-normal text-neutral-900">{totalQtyReturned}</div>
              </div>
              <div className="flex-1">
                <div className="text-[13px] font-semibold text-gray-500 mb-1">No of Item</div>
                <div className="text-3xl font-normal text-neutral-900">{totalReturns}</div>
              </div>
            </div>
          )
        }} 
      />
    </div>
  )
}
