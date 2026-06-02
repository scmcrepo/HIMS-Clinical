import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { DateRangeType } from './ReportCard'
import { reportApi } from '../../../services/report/reportApi'
import { cn } from '../../../lib/utils'

interface RevenueReportsTabProps {
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

function formatCurrency(val: number | undefined | null) {
  if (val == null || isNaN(Number(val))) return '0'
  return Math.round(Number(val)).toLocaleString('en-IN')
}

function DateFilterButtons({
  rangeType,
  onChange,
}: {
  rangeType: DateRangeType
  onChange: (r: DateRangeType) => void
}) {
  return (
    <div className="flex gap-1 bg-gray-100 p-0.5 rounded-lg">
      {(['today', 'current_month', 'last_month'] as const).map(type => (
        <button
          key={type}
          onClick={() => onChange(type)}
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
  )
}

function periodLabel(rangeType: DateRangeType) {
  return rangeType === 'today' ? 'today' : rangeType === 'current_month' ? 'this month' : 'last month'
}

// ─── Net Revenue Summary Card ──────────────────────────────────────────────────

function NetRevenueCard({
  onViewReport,
}: {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}) {
  const [rangeType, setRangeType] = useState<DateRangeType>('today')
  const dateParams = getDateParams(rangeType)

  const { data: raisedData = [], isLoading: raisedLoading } = useQuery({
    queryKey: ['report_summary', 'bill_raised_summary', dateParams],
    queryFn: () => reportApi.executeJson('bill_raised_summary', dateParams),
    staleTime: 0,
    refetchOnMount: 'always',
  })

  const { data: cancelledData = [], isLoading: cancelledLoading } = useQuery({
    queryKey: ['report_summary', 'bill_cancelled_summary', dateParams],
    queryFn: () => reportApi.executeJson('bill_cancelled_summary', dateParams),
    staleTime: 0,
    refetchOnMount: 'always',
  })

  const isLoading = raisedLoading || cancelledLoading

  const raised   = (raisedData as any[])[0] ?? {}
  const cancelled = (cancelledData as any[])[0] ?? {}

  const opBills   = Number(raised.OP_Bill_AMOUNT ?? 0)
  const ipCash    = Number(raised.IP_CASH_Bill_AMOUNT ?? 0)
  const ipCredit  = Number(raised.IP_CREDIT_Bill_AMOUNT ?? 0)
  const ipBills   = ipCash + ipCredit
  const opCan     = Number(cancelled.OP_CAN_AMOUNT ?? 0)
  const ipCashCan = Number(cancelled.IP_CASH_CAN_AMOUNT ?? 0)
  const ipCredCan = Number(cancelled.IP_CREDIT_CAN_AMOUNT ?? 0)
  const cancelledAmt = opCan + ipCashCan + ipCredCan
  const netRevenue   = opBills + ipBills - cancelledAmt

  const statItems = [
    { label: 'OP Bills',        value: opBills },
    { label: 'IP Bills',        value: ipBills },
    { label: 'Cancelled Bills', value: cancelledAmt },
    { label: 'Net Revenue',     value: netRevenue, highlighted: true },
  ]

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm mb-4 overflow-hidden">
      <div className="px-5 py-3 border-b border-gray-100 flex items-center justify-between">
        <h4 className="text-sm font-semibold text-gray-700 m-0">Net Revenue</h4>
        <DateFilterButtons rangeType={rangeType} onChange={setRangeType} />
      </div>

      <div className="flex items-center gap-4 px-5 py-4">
        <div className="flex-1">
          {isLoading ? (
            <div className="text-xs text-gray-400">Loading summary...</div>
          ) : (
            <div className="flex items-center py-2 w-full max-w-4xl">
              {statItems.map((item, idx) => (
                <div key={item.label} className="flex items-center flex-1">
                  <div className="flex-1 text-center">
                    <div className="text-[13px] font-semibold text-gray-500 mb-1">{item.label}</div>
                    <div className="text-3xl font-normal text-blue-900">
                      ₹ {formatCurrency(item.value)}
                    </div>
                  </div>
                  {idx < statItems.length - 1 && <div className="h-10 w-px bg-gray-200 shrink-0" />}
                </div>
              ))}
            </div>
          )}
        </div>
        <button
          onClick={() => onViewReport('net_revenue_report', dateParams)}
          className="flex-shrink-0 inline-flex items-center justify-center px-4 py-2 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
        >
          VIEW DETAIL REPORT
        </button>
      </div>
    </div>
  )
}

// ─── Consultant Revenue Summary Card ──────────────────────────────────────────

function ConsultantRevenueCard({
  onViewReport,
}: {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}) {
  const [rangeType, setRangeType] = useState<DateRangeType>('today')
  const dateParams = getDateParams(rangeType)

  const { data = [], isLoading } = useQuery({
    queryKey: ['report_summary', 'consultant_revenue_opip', dateParams],
    queryFn: () => reportApi.executeJson('consultant_revenue_opip', dateParams),
    staleTime: 0,
    refetchOnMount: 'always',
  })

  const rows = (data as any[]).map((row: any) => ({
    name: row.consultant_name || 'Unknown',
    op_bills: Number(row.op_bills ?? 0),
    ip_bills: Number(row.ip_bills ?? 0),
    total: Number(row.total ?? 0),
  }))

  const totalOp = rows.reduce((s, r) => s + r.op_bills, 0)
  const totalIp = rows.reduce((s, r) => s + r.ip_bills, 0)
  const grandTotal = rows.reduce((s, r) => s + r.total, 0)
  const noData = !isLoading && rows.length === 0

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm mb-4 overflow-hidden">
      <div className="px-5 py-3 border-b border-gray-100 flex items-center justify-between">
        <h4 className="text-sm font-semibold text-gray-700 m-0">Consultant Wise Revenue</h4>
        <DateFilterButtons rangeType={rangeType} onChange={setRangeType} />
      </div>

      <div className="flex items-center gap-4 px-5 py-4">
        <div className="flex-1 min-w-0">
          {isLoading ? (
            <div className="text-xs text-gray-400">Loading summary...</div>
          ) : noData ? (
            <div className="flex items-center bg-gray-50 border border-gray-200 text-gray-600 px-4 py-3 rounded-lg text-sm">
              <svg className="w-4 h-4 mr-2 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>No Amount Collected for <span className="font-semibold text-gray-800">{periodLabel(rangeType)}</span>.</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs border-collapse">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-200 text-gray-700">
                    <th className="text-left py-2 px-3 font-semibold rounded-tl">Consultant</th>
                    <th className="text-right py-2 px-3 font-semibold">OP Bills</th>
                    <th className="text-right py-2 px-3 font-semibold">IP Bills</th>
                    <th className="text-right py-2 px-3 font-semibold rounded-tr">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {rows.map(row => (
                    <tr key={row.name} className="hover:bg-gray-50 transition-colors">
                      <td className="py-2 px-3 text-gray-700 font-medium">{row.name}</td>
                      <td className="py-2 px-3 text-right text-gray-600">₹ {formatCurrency(row.op_bills)}</td>
                      <td className="py-2 px-3 text-right text-gray-600">₹ {formatCurrency(row.ip_bills)}</td>
                      <td className="py-2 px-3 text-right text-gray-800 font-semibold">₹ {formatCurrency(row.total)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="bg-gray-50 font-bold text-gray-900 border-t border-gray-200">
                    <td className="py-2 px-3">Total</td>
                    <td className="py-2 px-3 text-right">₹ {formatCurrency(totalOp)}</td>
                    <td className="py-2 px-3 text-right">₹ {formatCurrency(totalIp)}</td>
                    <td className="py-2 px-3 text-right">₹ {formatCurrency(grandTotal)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </div>
        <button
          onClick={() => onViewReport('consultant_revenue_opip', dateParams)}
          className="flex-shrink-0 inline-flex items-center justify-center px-4 py-2 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
        >
          VIEW DETAIL REPORT
        </button>
      </div>
    </div>
  )
}

// ─── Department Revenue Summary Card ──────────────────────────────────────────

function DepartmentRevenueCard({
  onViewReport,
}: {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}) {
  const [rangeType, setRangeType] = useState<DateRangeType>('today')
  const dateParams = getDateParams(rangeType)

  const { data = [], isLoading } = useQuery({
    queryKey: ['report_summary', 'department_revenue_opip', dateParams],
    queryFn: () => reportApi.executeJson('department_revenue_opip', dateParams),
    staleTime: 0,
    refetchOnMount: 'always',
  })

  const rows = (data as any[]).map((row: any) => ({
    name: row.department || 'Unknown',
    op_bills: Number(row.op_bills ?? 0),
    ip_bills: Number(row.ip_bills ?? 0),
    total: Number(row.total ?? 0),
  }))

  const totalOp = rows.reduce((s, r) => s + r.op_bills, 0)
  const totalIp = rows.reduce((s, r) => s + r.ip_bills, 0)
  const grandTotal = rows.reduce((s, r) => s + r.total, 0)
  const noData = !isLoading && rows.length === 0

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm mb-4 overflow-hidden">
      <div className="px-5 py-3 border-b border-gray-100 flex items-center justify-between">
        <h4 className="text-sm font-semibold text-gray-700 m-0">Department Wise Revenue</h4>
        <DateFilterButtons rangeType={rangeType} onChange={setRangeType} />
      </div>

      <div className="flex items-center gap-4 px-5 py-4">
        <div className="flex-1 min-w-0">
          {isLoading ? (
            <div className="text-xs text-gray-400">Loading summary...</div>
          ) : noData ? (
            <div className="flex items-center bg-gray-50 border border-gray-200 text-gray-600 px-4 py-3 rounded-lg text-sm">
              <svg className="w-4 h-4 mr-2 text-gray-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>No Amount Collected for <span className="font-semibold text-gray-800">{periodLabel(rangeType)}</span>.</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs border-collapse">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-200 text-gray-700">
                    <th className="text-left py-2 px-3 font-semibold rounded-tl">Department</th>
                    <th className="text-right py-2 px-3 font-semibold">OP Bills</th>
                    <th className="text-right py-2 px-3 font-semibold">IP Bills</th>
                    <th className="text-right py-2 px-3 font-semibold rounded-tr">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {rows.map(row => (
                    <tr key={row.name} className="hover:bg-gray-50 transition-colors">
                      <td className="py-2 px-3 text-gray-700 font-medium">{row.name}</td>
                      <td className="py-2 px-3 text-right text-gray-600">₹ {formatCurrency(row.op_bills)}</td>
                      <td className="py-2 px-3 text-right text-gray-600">₹ {formatCurrency(row.ip_bills)}</td>
                      <td className="py-2 px-3 text-right text-gray-800 font-semibold">₹ {formatCurrency(row.total)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="bg-gray-50 font-bold text-gray-900 border-t border-gray-200">
                    <td className="py-2 px-3">Total</td>
                    <td className="py-2 px-3 text-right">₹ {formatCurrency(totalOp)}</td>
                    <td className="py-2 px-3 text-right">₹ {formatCurrency(totalIp)}</td>
                    <td className="py-2 px-3 text-right">₹ {formatCurrency(grandTotal)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </div>
        <button
          onClick={() => onViewReport('department_revenue_opip', dateParams)}
          className="flex-shrink-0 inline-flex items-center justify-center px-4 py-2 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
        >
          VIEW DETAIL REPORT
        </button>
      </div>
    </div>
  )
}

// ─── Room Revenue Summary Card ─────────────────────────────────────────────────

function RoomRevenueCard({
  onViewReport,
}: {
  onViewReport: (reportName: string, params: Record<string, string>) => void
}) {
  const today = new Date()
  const y = today.getFullYear()
  const m = String(today.getMonth() + 1).padStart(2, '0')
  const d = String(today.getDate()).padStart(2, '0')
  const td = `${y}-${m}-${d}`

  return (
    <div className="bg-white border border-gray-200 rounded-xl shadow-sm mb-4">
      <table className="w-full text-sm">
        <tbody className="divide-y divide-gray-100">
          <tr>
            <td className="w-8"></td>
            <td className="py-4 font-semibold text-gray-700">Room Wise Revenue</td>
            <td className="py-4 pr-5 text-right w-48">
              <button
                onClick={() => onViewReport('room_revenue', { from_date: td, to_date: td })}
                className="px-4 py-1.5 bg-blue-600 text-white text-xs font-semibold rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
              >
                VIEW DETAIL REPORT
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}


// ─── Main Tab ──────────────────────────────────────────────────────────────────

export function RevenueReportsTab({ onViewReport }: RevenueReportsTabProps) {
  return (
    <div className="space-y-1">
      <NetRevenueCard onViewReport={onViewReport} />
      <ConsultantRevenueCard onViewReport={onViewReport} />
      <DepartmentRevenueCard onViewReport={onViewReport} />
      <RoomRevenueCard onViewReport={onViewReport} />
    </div>
  )
}
