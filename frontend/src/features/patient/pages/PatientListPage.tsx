import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { patientApi } from '../../../services/patient/patientApi'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { cn } from '../../../lib/utils'

type SearchMode = 'GENERAL' | 'ACTIVE_IP' | 'TODAY_OP'

export default function PatientListPage() {
  const [searchInput, setSearchInput] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [mode, setMode] = useState<SearchMode>('GENERAL')

  const { data: patientData, isLoading: isPatientLoading } = useQuery({
    queryKey: ['patients', 'search', query, page],
    queryFn: () => patientApi.search(query, page),
    enabled: mode === 'GENERAL' && query.length >= 2,
  })

  const { data: encounterData, isLoading: isEncounterLoading } = useQuery({
    queryKey: ['encounters', mode, query, page],
    queryFn: () => {
      if (mode === 'ACTIVE_IP') return encounterApi.getActiveInpatients(query, page)
      if (mode === 'TODAY_OP') return encounterApi.getTodayOutpatients(query, page)
      return null
    },
    enabled: mode !== 'GENERAL',
  })

  useEffect(() => {
    // Only debounce if we're in GENERAL mode or if there's actual input
    // In ACTIVE_IP/TODAY_OP modes, query is usually empty or managed differently
    const handler = setTimeout(() => {
      setQuery(searchInput)
      setPage(0)
    }, 400) // 400ms delay is usually a sweet spot for real-time search

    return () => clearTimeout(handler)
  }, [searchInput])

  const handleQuickSearch = (newMode: SearchMode) => {
    setMode(newMode)
    setQuery('')
    setSearchInput('')
    setPage(0)
  }

  const isLoading = isPatientLoading || isEncounterLoading
  const data = mode === 'GENERAL' ? patientData : encounterData

  return (
    <div className="space-y-6 max-w-6xl mx-auto p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-xl font-bold text-gray-900">Patient Search</h1>
        <Link to="/patients/register"
          className="px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 transition-all shadow-sm">
          + Register Patient
        </Link>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6 space-y-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          <div className="flex items-center gap-4 flex-1 max-w-2xl">
            <label htmlFor="patientDetail" className="text-sm font-bold text-gray-700 whitespace-nowrap min-w-[100px]">
              Patient Detail
            </label>
            <div className="flex-1 relative">
              <div className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
              </div>
              <input
                id="patientDetail"
                type="text"
                placeholder="Search by Name, Phone, or Patient ID..."
                value={searchInput}
                onChange={e => {
                  setSearchInput(e.target.value)
                  if (mode !== 'GENERAL') setMode('GENERAL')
                }}
                className="w-full pl-11 pr-12 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all shadow-sm"
              />
              {searchInput && (
                <button
                  onClick={() => setSearchInput('')}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
                  aria-label="Clear search"
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                </button>
              )}
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex gap-2">
              <button
                onClick={() => handleQuickSearch('ACTIVE_IP')}
                className={cn(
                  "px-4 py-2 border rounded-lg text-[11px] font-bold transition-all shadow-sm",
                  mode === 'ACTIVE_IP' ? "bg-blue-600 border-blue-600 text-white" : "bg-white border-gray-200 text-gray-600 hover:bg-gray-50"
                )}
              >
                Active In-Patients
              </button>
              <button
                onClick={() => handleQuickSearch('TODAY_OP')}
                className={cn(
                  "px-4 py-2 border rounded-lg text-[11px] font-bold transition-all shadow-sm",
                  mode === 'TODAY_OP' ? "bg-blue-600 border-blue-600 text-white" : "bg-white border-gray-200 text-gray-600 hover:bg-gray-50"
                )}
              >
                Today's Out-Patients
              </button>
            </div>
          </div>
        </div>
      </div>

      {isLoading && (
        <div className="flex justify-center py-12">
          <div className="w-8 h-8 border-4 border-blue-100 border-t-blue-500 rounded-full animate-spin" />
        </div>
      )}

      {!isLoading && data && (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden animate-in fade-in slide-in-from-top-4 duration-500">
          <table className="w-full text-sm text-left">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-gray-600">
                <th className="px-6 py-4 font-bold text-xs uppercase tracking-wider w-12">S.No</th>
                <th className="px-6 py-4 font-bold text-xs uppercase tracking-wider">Patient ID</th>
                <th className="px-6 py-4 font-bold text-xs uppercase tracking-wider">Name</th>
                <th className="px-6 py-4 font-bold text-xs uppercase tracking-wider">Gender</th>
                <th className="px-6 py-4 font-bold text-xs uppercase tracking-wider">Age</th>
                <th className="px-6 py-4 font-bold text-xs uppercase tracking-wider">Mobile No</th>
                <th className="px-6 py-4 font-bold text-xs uppercase tracking-wider text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.content.map((item: any, index: number) => {
                const isPatient = mode === 'GENERAL'
                const p = isPatient ? item : {
                  id: item.patientId,
                  patientNumber: item.patientNumber,
                  fullName: item.patientName,
                  encounterId: item.id
                }

                return (
                  <tr key={item.id} className="hover:bg-gray-50 transition-colors group">
                    <td className="px-6 py-4 text-gray-500 font-medium text-sm">{(page * (data.size || 10)) + index + 1}</td>
                    <td className="px-6 py-4 font-mono text-xs text-blue-600 font-semibold">{p.patientNumber}</td>
                    <td className="px-6 py-4">
                      <div className="font-bold text-gray-900">{p.fullName}</div>
                    </td>

                    <td className="px-6 py-4 text-gray-600 capitalize">
                      {isPatient 
                        ? (item.gender ? item.gender.toLowerCase() : '—')
                        : (item.patientGender ? item.patientGender.toLowerCase() : '—')
                      }
                    </td>
                    <td className="px-6 py-4 text-gray-600">
                      {isPatient ? (item.age || '—') : (item.patientAge || '—')}
                    </td>
                    <td className="px-6 py-4 text-gray-600">
                      {isPatient ? (item.contactNumber ?? '—') : (item.patientMobileNumber ?? '—')}
                    </td>

                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <Link to={`/patients/${p.id}`} className="px-3 py-1.5 bg-white border border-blue-200 rounded-lg text-[10px] font-bold text-blue-600 hover:bg-blue-50 hover:border-blue-300 transition-all shadow-sm">
                          PROFILE
                        </Link>
                        {isPatient && (
                          <Link to={`/patients/${p.id}/edit`} className="px-3 py-1.5 bg-white border border-amber-200 rounded-lg text-[10px] font-bold text-amber-600 hover:bg-amber-50 hover:border-amber-300 transition-all shadow-sm">
                            EDIT
                          </Link>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
              {data.content.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-gray-400">
                    {mode === 'GENERAL'
                      ? (query.length >= 2 ? 'No patients found matching your search' : 'Type at least 2 characters and click SEARCH')
                      : `No ${mode === 'ACTIVE_IP' ? 'active in-patients' : "today's out-patients"} found`}
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          <div className="flex items-center justify-between px-6 py-4 border-t border-gray-100 bg-gray-50/50 text-xs font-bold text-gray-500">
            <span>SHOWING {data.content.length} OF {data.totalElements} RESULTS</span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1 border border-gray-200 rounded bg-white disabled:opacity-40 hover:bg-gray-50 transition-all"
              >
                PREV
              </button>
              <span className="px-2">PAGE {page + 1} OF {Math.max(1, data.totalPages)}</span>
              <button
                onClick={() => setPage(p => p + 1)}
                disabled={page >= data.totalPages - 1}
                className="px-3 py-1 border border-gray-200 rounded bg-white disabled:opacity-40 hover:bg-gray-50 transition-all"
              >
                NEXT
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

