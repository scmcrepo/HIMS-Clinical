import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PrintButton } from '../../../components/shared/PrintButton'
import { useNavigate } from 'react-router-dom'
import { salesApi } from '../../../services/sales/salesApi'
import { departmentApi } from '../../../services/config/departmentApi'
import { formatDate } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'

export default function SalesHistoryPage() {
  const navigate = useNavigate()
  const [date, setDate] = useState(new Date().toISOString().split('T')[0])
  const [departmentId, setDepartmentId] = useState<string>('')
  const [searchQuery, setSearchQuery] = useState('')

  const { data: depts } = useQuery({
    queryKey: ['departments'],
    queryFn: () => departmentApi.getAll(),
  })

  const { data: sales, isLoading } = useQuery({
    queryKey: ['sales', 'history', date],
    queryFn: () => salesApi.getByDate(date),
  })

  // Client-side filtering
  const filteredSales = sales?.filter(s => {
    if (departmentId && s.departmentId !== departmentId) return false
    if (searchQuery) {
      const q = searchQuery.toLowerCase()
      const saleNoMatch = s.sequenceNumber?.toLowerCase().includes(q)
      const patientMatch = s.patientName?.toLowerCase().includes(q)
      if (!saleNoMatch && !patientMatch) return false
    }
    // Only show finalized sales in "Sales History" matching the screenshot expectations
    if (s.status === 'DRAFT') return false
    return true
  })

  const inputCls = "px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 bg-white"

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight">Sales History</h2>
      </div>

      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden min-h-[500px] flex flex-col">
        {/* Filters */}
        <div className="p-4 border-b border-gray-100 flex flex-wrap gap-4 items-center bg-gray-50/50">
        <div className="flex items-center">
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className={`${inputCls} w-40`}
          />
        </div>
        <div className="flex items-center">
          <select
            value={departmentId}
            onChange={(e) => setDepartmentId(e.target.value)}
            className={`${inputCls} w-48`}
          >
            <option value="">All Departments</option>
            {depts?.map(d => (
              <option key={d.id} value={d.id}>{d.name}</option>
            ))}
          </select>
        </div>
        <div className="flex items-center ml-auto">
          <input
            type="text"
            placeholder="Search Sales No"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className={`${inputCls} w-64`}
          />
        </div>
      </div>

      {/* Data Table */}
      <div className="flex-1 overflow-auto">
        {isLoading ? (
          <div className="p-8 text-center text-gray-400">Loading...</div>
        ) : (
          <table className="w-full text-sm text-left">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-gray-500 text-xs uppercase tracking-wider">
                <th className="px-4 py-3 font-semibold">S.No</th>
                <th className="px-4 py-3 font-semibold">Sales No</th>
                <th className="px-4 py-3 font-semibold">Department</th>
                <th className="px-4 py-3 font-semibold">Date</th>
                <th className="px-4 py-3 font-semibold">Customer Name</th>
                <th className="px-4 py-3 font-semibold">Status</th>
                {/* <th className="px-4 py-3 font-semibold">Sale By</th> */}
                <th className="px-4 py-3 font-semibold text-right">Total Amt</th>
                <th className="px-4 py-3 font-semibold text-center">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filteredSales?.map((s, idx) => {
                const deptName = depts?.find(d => d.id === s.departmentId)?.name || 'PHARMACY'
                return (
                  <tr key={s.id} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-4 py-3 text-gray-500">{idx + 1}</td>
                    <td className="px-4 py-3 font-medium text-gray-900">{s.sequenceNumber}</td>
                    <td className="px-4 py-3 text-gray-600">{deptName}</td>
                    <td className="px-4 py-3 text-gray-600">{formatDate(s.saleDate)}</td>
                    <td className="px-4 py-3 text-gray-900">{s.patientName || s.customerName || 'Walk-in'}</td>
                    <td className="px-4 py-3">
                      <span className={cn('inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold',
                        s.status === 'SETTLED' ? 'bg-green-600 text-white' :
                        s.status === 'WITH_DUE' ? 'bg-red-500 text-white' :
                        s.status === 'BILLED' ? 'bg-green-600 text-white' :
                        'bg-gray-100 text-gray-700'
                      )}>
                        {s.status === 'SETTLED' ? `Settled on ${formatDate(s.saleDate)}` : 
                         s.status === 'WITH_DUE' ? `WithDue Amount of ${Math.round(Number(s.dueAmount) / 100)}` :
                         s.status === 'BILLED' ? (s.patientNumber?.startsWith('SCMCB') ? `Added to Bill - ${s.patientNumber}` : `Billed to - ${s.patientNumber || 'Ward'}`) :
                         s.status}
                      </span>
                    </td>
                    {/* <td className="px-4 py-3 text-gray-600">-</td> */}
                    <td className="px-4 py-3 text-right font-medium text-gray-900">
                      {Math.round(Number(s.totalAmount) / 100).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <div className="inline-flex items-center gap-1 bg-white border border-gray-200 rounded p-0.5">
                        <PrintButton
                          templateType="SALES"
                          params={{ id: s.id }}
                          variant="icon"
                          label="Print Receipt"
                        />
                        <button 
                          onClick={() => navigate(`/sales/salesHistory/view/${s.id}`)}
                          className="p-1 hover:bg-gray-100 rounded text-gray-600 transition-colors" 
                          title="View Details"
                        >
                          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                          </svg>
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
              {(!filteredSales || filteredSales.length === 0) && (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-gray-400 text-sm">
                    No sales found for the selected criteria.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  </div>
  )
}
