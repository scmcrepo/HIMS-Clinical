import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import type { EncounterStatus } from '../../../types/encounter'
import DatePicker from '../../../components/shared/DatePicker'

const today = () => new Date().toISOString().slice(0, 10)

const STATUS_STYLES: Record<EncounterStatus, string> = {
  CHECKED_IN: 'bg-blue-50 text-blue-700 border-blue-200',
  CONSULTATION_STARTED: 'bg-purple-50 text-purple-700 border-purple-200',
  CASESHEET_RECORDED: 'bg-amber-50 text-amber-700 border-amber-200',
  BILLING_DONE: 'bg-green-50 text-green-700 border-green-200',
}

const STATUS_LABELS: Record<EncounterStatus, string> = {
  CHECKED_IN: 'Checked In',
  CONSULTATION_STARTED: 'In Consultation',
  CASESHEET_RECORDED: 'Casesheet Done',
  BILLING_DONE: 'Billing Done',
}

export default function EncounterListPage() {
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState<'ALL' | 'IP' | 'OP'>('ALL')
  const [searchInput, setSearchInput] = useState('')
  const [searchDate, setSearchDate] = useState<string>(today())
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['encounters', activeTab, searchInput, searchDate, page],
    queryFn: () => {
      if (activeTab === 'IP') return encounterApi.getActiveInpatients(searchInput, page, 5)
      if (activeTab === 'OP') return encounterApi.getTodayOutpatients(searchInput, undefined, page, 5)
      return encounterApi.getAll(searchInput, searchDate, page, 5)
    },
    refetchInterval: 10000,
  })

  const totalPages = data?.totalPages ?? 0

  return (
    <div className="space-y-8 max-w-6xl mx-auto">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-900">Recent Encounters</h2>
        {/* CHANGED: Navigate to CreateEncounterPage instead of opening a modal */}
        <button
          onClick={() => navigate('/encounters/create')}
          className="px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
        >
          + Create Encounter
        </button>
      </div>

      <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div className="flex flex-col md:flex-row items-center gap-4 flex-1">
          <div className="relative flex-1 max-w-md w-full">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <svg className="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
            </div>
            <input
              type="text"
              placeholder="Search by Patient ID, Name or Number..."
              value={searchInput}
              onChange={(e) => { setSearchInput(e.target.value); setPage(0) }}
              className="block w-full pl-10 pr-3 py-2 border border-gray-200 rounded-xl leading-5 bg-gray-50 placeholder-gray-500 focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500 sm:text-sm transition-all"
            />
          </div>
          {activeTab === 'ALL' && (
            <div className="w-full md:w-48 z-10">
              <DatePicker
                value={searchDate}
                onChange={(val) => { setSearchDate(val); setPage(0) }}
                placeholder="Filter by Date"
              />
            </div>
          )}
        </div>
        <div className="flex bg-gray-100 p-1 rounded-lg border border-gray-200 shrink-0">
          <button onClick={() => { setActiveTab('ALL'); setPage(0) }}
            className={cn("px-4 py-1.5 rounded-md text-xs font-bold transition-all", activeTab === 'ALL' ? "bg-white text-gray-800 shadow-sm" : "text-gray-500 hover:text-gray-700")}>
            All Encounters
          </button>
          <button onClick={() => { setActiveTab('IP'); setPage(0) }}
            className={cn("px-4 py-1.5 rounded-md text-xs font-bold transition-all", activeTab === 'IP' ? "bg-white text-gray-800 shadow-sm" : "text-gray-500 hover:text-gray-700")}>
            Active In Patients
          </button>
          <button onClick={() => { setActiveTab('OP'); setPage(0) }}
            className={cn("px-4 py-1.5 rounded-md text-xs font-bold transition-all", activeTab === 'OP' ? "bg-white text-gray-800 shadow-sm" : "text-gray-500 hover:text-gray-700")}>
            Today's Out Patients
          </button>
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
        <table className="w-full text-sm" role="table">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
              <th className="px-4 py-3 font-semibold text-gray-600 w-12">S.No</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Patient</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Contact</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Consultant</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Date & Time</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Type</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              <tr><td colSpan={8} className="px-4 py-10 text-center text-gray-500">Loading encounters...</td></tr>
            ) : data?.content.map((e, index) => (
              <tr key={e.id} className="hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3 text-gray-500 font-medium">{(page * 5) + index + 1}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-col">
                    <span className="text-gray-900 font-medium">{String(e.patientName ?? 'Unknown')}</span>
                    <div className="flex flex-col gap-0.5 mt-0.5">
                      {e.patientNumber && <span className="text-[10px] font-mono text-gray-400">{e.patientNumber}</span>}
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3 text-gray-600 font-medium">{e.patientMobileNumber || '—'}</td>
                <td className="px-4 py-3 text-gray-600 font-medium">{String(e.providerName ?? '—')}</td>
                <td className="px-4 py-3 text-gray-600 font-medium whitespace-nowrap">{formatDateTime(e.startedAt)}</td>
                <td className="px-4 py-3">
                  <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border',
                    e.encounterType === 'OUTPATIENT' ? 'bg-blue-50 text-blue-700 border-blue-200' : 'bg-amber-50 text-amber-700 border-amber-200')}>
                    {e.encounterType === 'OUTPATIENT' ? 'OP' : 'IP'}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className={cn('inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border', STATUS_STYLES[e.status])}>
                    {STATUS_LABELS[e.status]}
                  </span>
                </td>
              </tr>
            ))}
            {!isLoading && data?.content.length === 0 && (
              <tr><td colSpan={8} className="px-4 py-10 text-center text-gray-400 text-sm">No encounters found</td></tr>
            )}
          </tbody>
        </table>

        <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 flex items-center justify-between">
          <div className="text-xs text-gray-500">
            Page <span className="font-medium text-gray-900">{String(page + 1)}</span> of <span className="font-medium text-gray-900">{String(totalPages || 1)}</span>
            {data?.totalElements !== undefined && <span className="ml-2">· {String(data.totalElements)} total encounters</span>}
          </div>
          <div className="flex items-center gap-1">
            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0 || isLoading}
              className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" /></svg>
            </button>
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              let pageNum = i
              if (totalPages > 5 && page > 2) pageNum = Math.min(page - 2 + i, totalPages - 5 + i)
              return (
                <button key={pageNum} onClick={() => setPage(pageNum)}
                  className={cn("min-w-[32px] h-8 flex items-center justify-center rounded text-xs font-semibold transition-all",
                    page === pageNum ? "bg-blue-600 text-white shadow-sm" : "text-gray-600 hover:bg-gray-100")}>
                  {String(pageNum + 1)}
                </button>
              )
            })}
            <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1 || isLoading}
              className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" /></svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
