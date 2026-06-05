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

export default function IpWardPage() {
  const [searchParams] = useSearchParams()
  const tab = searchParams.get('tab') || 'ward'
  const qc = useQueryClient()

  const [query, setQuery] = useState('')
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().split('T')[0])
  const [selectedConsultantId, setSelectedConsultantId] = useState('')

  // Automatically refresh inpatient list when entering the page or changing tabs
  useEffect(() => {
    if (tab === 'ward') {
      qc.invalidateQueries({ queryKey: ['active-inpatients'] })
    }
  }, [qc, tab])

  // Fetch consultants list
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
  })

  // Fetch inpatient encounters with filters
  const { data, isLoading } = useQuery({
    queryKey: ['active-inpatients', query, selectedDate, selectedConsultantId],
    queryFn: () => encounterApi.getActiveInpatients(
      query || undefined,
      0,
      50,
      selectedDate || undefined,
      selectedConsultantId || undefined
    ),
    refetchInterval: 60_000,
    enabled: tab === 'ward',
  })

  const patients: EncounterSummary[] = data?.content ?? []
  const discharged = patients.filter(p => p.dischargedAt)
  const admitted = patients.filter(p => !p.dischargedAt)

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
                onChange={e => setQuery(e.target.value)}
                className="w-64 px-3 py-1.5 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              />

              {/* Date Filter */}
              <div className="w-48">
                <DatePicker
                  value={selectedDate}
                  onChange={setSelectedDate}
                  placeholder="Select Date"
                  clearable={true}
                />
              </div>

              {/* Consultant Filter */}
              <div className="w-64">
                <ConsultantSearchInput
                  consultants={consultants}
                  value={selectedConsultantId}
                  onChange={setSelectedConsultantId}
                  placeholder="Select Consultant"
                />
              </div>
            </div>
          </div>

          {/* Counts summary banner */}
          <div className="flex items-center gap-3 text-xs text-gray-500">
            <span className="w-2.5 h-2.5 rounded-full bg-blue-400 inline-block" />
            {admitted.length} Admitted
            <span className="w-2.5 h-2.5 rounded-full bg-green-400 inline-block ml-2" />
            {discharged.length} Discharged (filtered set)
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
                          className="p-1.5 inline-flex items-center justify-center text-blue-600 hover:text-blue-800 bg-blue-50 hover:bg-blue-100 border border-blue-200 rounded-lg transition-colors shadow-sm"
                          title="Open Case"
                        >
                          <User size={16} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  )
}
