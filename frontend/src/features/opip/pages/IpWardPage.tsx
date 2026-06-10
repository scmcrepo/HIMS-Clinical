import { useState, useEffect } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import type { EncounterSummary } from '../../../types/encounter'
import BedManagementPage from '../../bed/pages/BedManagementPage'
import DatePicker from '../../../components/shared/DatePicker'
import { User } from 'lucide-react'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import { useAuthStore } from '../../../store/authStore'

export default function IpWardPage() {
  const { user } = useAuthStore()
  const [searchParams] = useSearchParams()
  const tab = searchParams.get('tab') || 'ward'
  const qc = useQueryClient()

  const [query, setQuery] = useState('')
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().split('T')[0])
  const [selectedConsultantId, setSelectedConsultantId] = useState(() => user?.consultantId || '')
  const [page, setPage] = useState(0)

  // Reset page to 0 when filters change
  const handleQueryChange = (val: string) => { setQuery(val); setPage(0); }
  const handleDateChange = (val: string) => { setSelectedDate(val); setPage(0); }
  const handleConsultantChange = (val: string) => { setSelectedConsultantId(val); setPage(0); }

  // Automatically refresh inpatient list when entering the page or changing tabs
  useEffect(() => {
    if (tab === 'ward') {
      qc.invalidateQueries({ queryKey: ['active-inpatients'] })
      qc.invalidateQueries({ queryKey: ['active-inpatients-all'] })
    }
  }, [qc, tab])

  // Fetch consultants list
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
  })

  // Fetch inpatient encounters with filters (paginated: 10 per page)
  const { data, isLoading } = useQuery({
    queryKey: ['active-inpatients', query, selectedDate, selectedConsultantId, page],
    queryFn: () => encounterApi.getActiveInpatients(
      query || undefined,
      page,
      10,
      selectedDate || undefined,
      selectedConsultantId || undefined
    ),
    refetchInterval: 60_000,
    enabled: tab === 'ward',
  })

  // Fetch all matching inpatient encounters (large size) to calculate accurate header counts
  const { data: allData } = useQuery({
    queryKey: ['active-inpatients-all', query, selectedDate, selectedConsultantId],
    queryFn: () => encounterApi.getActiveInpatients(
      query || undefined,
      0,
      1000,
      selectedDate || undefined,
      selectedConsultantId || undefined
    ),
    refetchInterval: 60_000,
    enabled: tab === 'ward',
  })

  const patients: EncounterSummary[] = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  const allPatients: EncounterSummary[] = allData?.content ?? []
  const dischargedCount = allPatients.filter(p => p.dischargedAt).length
  const admittedCount = allPatients.filter(p => !p.dischargedAt).length

  return (
    <div className="space-y-5">
      {tab === 'beds' ? (
        <BedManagementPage />
      ) : (
        <>
          {/* Header Bar */}
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
            <div>
              <h2 className="text-xl font-bold text-gray-900">In Patients</h2>
              <p className="text-sm text-gray-500 mt-0.5">Active & historical inpatient records</p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              {/* Search */}
              <input
                type="search"
                placeholder="Search patient name or number…"
                value={query}
                onChange={e => handleQueryChange(e.target.value)}
                className="w-64 px-3 py-1.5 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500"
              />

              {/* Date Filter */}
              <div className="w-48">
                <DatePicker
                  value={selectedDate}
                  onChange={handleDateChange}
                  placeholder="Select Date"
                  clearable={true}
                />
              </div>

              {/* Consultant Filter */}
              <div className="w-64">
                <ConsultantSearchInput
                  consultants={consultants}
                  value={selectedConsultantId}
                  onChange={handleConsultantChange}
                  placeholder="Select Consultant"
                />
              </div>
            </div>
          </div>

          {/* Counts summary banner */}
          <div className="flex items-center gap-3 text-xs text-gray-500">
            <span className="w-2.5 h-2.5 rounded-full bg-neutral-400 inline-block" />
            {admittedCount} Admitted
            <span className="w-2.5 h-2.5 rounded-full bg-green-400 inline-block ml-2" />
            {dischargedCount} Discharged (filtered set)
          </div>

          {isLoading ? (
            <div className="text-sm text-gray-500 py-8 text-center">Loading In Patients…</div>
          ) : patients.length === 0 ? (
            <div className="text-sm text-gray-400 py-12 text-center border border-dashed border-gray-200 rounded-xl bg-white">
              No matching inpatient records found
            </div>
          ) : (
            <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    {['BED NO', 'PATIENT NO', 'PATIENT', 'ADMISSION', 'DISCHARGE', 'PRIMARY CONSULTANT', 'VIEW'].map(h => (
                      <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {patients.map(enc => (
                    <tr key={enc.id} className={cn('hover:bg-gray-50/80 transition-colors', enc.dischargedAt && 'bg-gray-50/30 text-gray-500')}>
                      {/* BED NO */}
                      <td className="px-4 py-3 font-semibold text-gray-700">
                        {enc.bedName ?? '—'}
                      </td>

                      {/* PATIENT NO */}
                      <td className="px-4 py-3 font-mono text-xs text-gray-500">
                        {enc.patientNumber}
                      </td>

                      {/* PATIENT */}
                      <td className="px-4 py-3">
                        <p className="font-bold text-gray-900">{enc.patientName}</p>
                        <p className="text-[10px] text-gray-400 mt-0.5">{enc.patientAge} · {enc.patientGender}</p>
                      </td>

                      {/* ADMISSION */}
                      <td className="px-4 py-3 text-gray-600 text-xs">
                        {formatDateTime(enc.startedAt)}
                      </td>

                      {/* DISCHARGE */}
                      <td className="px-4 py-3 text-gray-600 text-xs">
                        {enc.dischargedAt ? formatDateTime(enc.dischargedAt) : '—'}
                      </td>

                      {/* PRIMARY CONSULTANT */}
                      <td className="px-4 py-3 text-gray-700 text-xs font-medium">
                        {enc.providerName ?? '—'}
                      </td>

                      {/* CASESHEET */}
                      <td className="px-4 py-3">
                        <Link
                          to={`/ip-casesheet/${enc.id}`}
                          className="p-1.5 inline-flex items-center justify-center text-neutral-600 hover:text-neutral-800 bg-neutral-50 hover:bg-neutral-100 border border-neutral-200 rounded-lg transition-colors shadow-sm"
                          title="Open Case"
                        >
                          <User size={16} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {/* Pagination Footer */}
              <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 flex items-center justify-between">
                <div className="text-xs text-gray-500">
                  Page <span className="font-medium text-gray-900">{String(page + 1)}</span> of <span className="font-medium text-gray-900">{String(totalPages || 1)}</span>
                  {data?.totalElements !== undefined && <span className="ml-2">· {String(data.totalElements)} total encounters</span>}
                </div>
                <div className="flex items-center gap-1">
                  <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0 || isLoading}
                    className="p-1.5 text-gray-500 hover:text-neutral-600 hover:bg-neutral-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" /></svg>
                  </button>
                  {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                    let pageNum = i
                    if (totalPages > 5 && page > 2) pageNum = Math.min(page - 2 + i, totalPages - 5 + i)
                    return (
                      <button key={pageNum} onClick={() => setPage(pageNum)}
                        className={cn("min-w-[32px] h-8 flex items-center justify-center rounded text-xs font-semibold transition-all",
                          page === pageNum ? "bg-neutral-600 text-white shadow-sm" : "text-gray-600 hover:bg-gray-100")}>
                        {String(pageNum + 1)}
                      </button>
                    )
                  })}
                  <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1 || isLoading}
                    className="p-1.5 text-gray-500 hover:text-neutral-600 hover:bg-neutral-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" /></svg>
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
