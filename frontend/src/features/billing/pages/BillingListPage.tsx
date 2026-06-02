import { useState } from 'react'
import { PrintButton } from '../../../components/shared/PrintButton'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { billingApi } from '../../../services/billing/billingApi'
import { formatDate } from '../../../lib/dateUtils'
import { BillStatusBadge } from '../../../components/shared/StatusBadge'
import { AmountDisplay } from '../../../components/shared/AmountDisplay'
import DatePicker from '../../../components/shared/DatePicker'
import { cn } from '../../../lib/utils'

interface BillingListPageProps {
  type: 'OP' | 'IP'
}

export default function BillingListPage({ type }: BillingListPageProps) {
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['bills', 'search', search, fromDate, toDate],
    queryFn: () => billingApi.searchBills(search, fromDate, toDate, 0, 1000), // Fetch up to 1000 items to filter locally
    staleTime: 0,
  })

  const handleSearchChange = (val: string) => { setSearch(val); setPage(0) }
  const handleDateChange = (type: 'from' | 'to', val: string) => {
    if (type === 'from') setFromDate(val)
    else setToDate(val)
    setPage(0)
  }

  const rawBills = data?.content ?? []
  const filteredBills = rawBills.filter(b => b.encounterType === (type === 'OP' ? 'OUTPATIENT' : 'INPATIENT'))
  
  const pageSize = 5
  const totalPages = Math.ceil(filteredBills.length / pageSize)
  const bills = filteredBills.slice(page * pageSize, (page + 1) * pageSize)

  return (
    <div className="space-y-6 w-full max-w-none px-2">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900">
            {type === 'OP' ? 'OP Billing' : 'IP Billing'}
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">
            {type === 'OP'
              ? 'View and search outpatient billing records'
              : 'View and search inpatient billing records'}
          </p>
        </div>
        {/* CHANGED: Navigate to CreateBillPage with correct type */}
        <button
          onClick={() => navigate(`/billing/create?type=${type.toLowerCase()}`)}
          className="px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 transition-all shadow-md active:scale-95 flex items-center"
        >
          <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4" />
          </svg>
          {type === 'OP' ? 'Create OP Bill' : 'Create IP Bill'}
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm flex flex-wrap items-end gap-4">
        <div className="flex-1 min-w-[240px]">
          <label className="block text-[10px] font-bold text-gray-500 uppercase tracking-wider mb-1.5 ml-1">
            Search Patient
          </label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </span>
            <input
              type="text"
              value={search}
              onChange={e => handleSearchChange(e.target.value)}
              placeholder="Name, Phone, or Number (e.g. SCMCP-123)..."
              className="w-full pl-9 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
            />
          </div>
        </div>
        <div className="w-48">
          <label className="block text-[10px] font-bold text-gray-500 uppercase tracking-wider mb-1.5 ml-1">
            From Date
          </label>
          <DatePicker
            value={fromDate}
            onChange={val => handleDateChange('from', val)}
            placeholder="Select date"
          />
        </div>
        <div className="w-48">
          <label className="block text-[10px] font-bold text-gray-500 uppercase tracking-wider mb-1.5 ml-1">
            To Date
          </label>
          <DatePicker
            value={toDate}
            onChange={val => handleDateChange('to', val)}
            placeholder="Select date"
          />
        </div>
      </div>

      {/* Bill List Table */}
      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm min-h-[350px] flex flex-col">
        <div className="flex-1 overflow-x-auto">
          {isLoading ? (
            <div className="p-12 text-center text-sm text-gray-400" role="status">
              Loading billing history...
            </div>
          ) : bills.length === 0 ? (
            <div className="p-12 text-center text-sm text-gray-400">
              No bills found.
            </div>
          ) : (
            <table className="w-full text-sm text-left border-collapse">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200 text-gray-500 text-[10px] font-bold uppercase tracking-wider">
                  <th className="px-6 py-3.5 font-bold">Bill No</th>
                  <th className="px-6 py-3.5 font-bold">Patient Name</th>
                  <th className="px-6 py-3.5 font-bold">Bill Date</th>
                  <th className="px-6 py-3.5 font-bold">Encounter Type</th>
                  <th className="px-6 py-3.5 font-bold">Status</th>
                  <th className="px-6 py-3.5 font-bold text-right">Bill Amt</th>
                  <th className="px-6 py-3.5 font-bold text-right">Paid</th>
                  <th className="px-6 py-3.5 font-bold text-right">Due</th>
                    <th className="px-6 py-3.5 text-center font-semibold text-gray-600 text-xs uppercase tracking-wider w-16">Print</th>
                  <th className="px-6 py-3.5 font-bold text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {bills.map(b => (
                  <tr key={b.id} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-6 py-4 font-medium text-gray-900">
                      {b.billNumber ? (
                        <Link to={`/billing/${b.id}`} className="text-blue-600 hover:underline font-medium">
                          {b.billNumber}
                        </Link>
                      ) : (
                        <Link to={`/billing/${b.id}`} className="text-gray-500 hover:underline font-medium italic">
                          Draft
                        </Link>
                      )}
                    </td>
                    <td className="px-6 py-4">
                      <div className="font-semibold text-gray-900">{b.patientName || 'Unknown Patient'}</div>
                      <div className="text-[11px] text-gray-400 font-medium">{b.patientNumber}</div>
                    </td>
                    <td className="px-6 py-4 text-gray-600">
                      {b.billDate ? formatDate(b.billDate) : (b.createdAt ? formatDate(b.createdAt) : '—')}
                    </td>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider bg-gray-100 text-gray-600">
                        {b.encounterType === 'OUTPATIENT' ? 'Outpatient' : 'Inpatient'}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <BillStatusBadge status={b.status} />
                    </td>
                    <td className="px-6 py-4 text-right font-medium text-gray-900">
                      <AmountDisplay amount={b.billAmount} />
                    </td>
                    <td className="px-6 py-4 text-right font-medium text-gray-900">
                      <AmountDisplay amount={b.billAmount - b.dueAmount - b.discountTotal} />
                    </td>
                    <td className="px-6 py-4 text-right">
                      {b.dueAmount > 0 ? (
                        <span className="font-bold text-red-600">
                          <AmountDisplay amount={b.dueAmount} />
                        </span>
                      ) : (
                        <span className="text-gray-500 font-medium">—</span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-center">
                      {b.status !== 'DRAFT' && (
                        <PrintButton
                          templateType={b.encounterType === 'OUTPATIENT' ? 'BILL' : 'IP_BILL_CONSOLIDATED'}
                          params={{ id: b.id }}
                          variant="icon"
                          label="Print Bill"
                        />
                      )}
                    </td>
                    <td className="px-6 py-4 text-center">
                      <Link
                        to={`/billing/${b.id}`}
                        className="inline-flex items-center justify-center px-3 py-1 bg-white border border-gray-200 text-gray-700 hover:bg-gray-50 font-semibold rounded text-xs transition-colors shadow-sm"
                      >
                        Open
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Pagination Footer */}
        <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 flex items-center justify-between">
          <div className="text-xs text-gray-500">
            Page <span className="font-medium text-gray-900">{page + 1}</span> of{' '}
            <span className="font-medium text-gray-900">{totalPages || 1}</span>
            <span className="ml-2">· {filteredBills.length} total bills</span>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0 || isLoading}
              className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
              </svg>
            </button>

            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              let pageNum = i
              if (totalPages > 5 && page > 2) {
                pageNum = Math.min(page - 2 + i, totalPages - 5 + i)
              }
              return (
                <button
                  key={pageNum}
                  onClick={() => setPage(pageNum)}
                  className={cn(
                    'min-w-[32px] h-8 flex items-center justify-center rounded text-xs font-semibold transition-all',
                    page === pageNum ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-100'
                  )}
                >
                  {pageNum + 1}
                </button>
              )
            })}

            <button
              onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || isLoading}
              className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
