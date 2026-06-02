import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import type { EncounterSummary } from '../../../types/encounter'
import BedManagementPage from '../../bed/pages/BedManagementPage'

export default function IpWardPage() {
  const [searchParams] = useSearchParams()
  const tab = searchParams.get('tab') || 'ward'
  const [query, setQuery] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['active-inpatients', query],
    queryFn:  () => encounterApi.getActiveInpatients(query || undefined),
    refetchInterval: 60_000,
    enabled: tab === 'ward',
  })

  const patients: EncounterSummary[] = data?.content ?? []
  const discharged = patients.filter(p => p.dischargedAt)
  const admitted   = patients.filter(p => !p.dischargedAt)

  return (
    <div className="space-y-5">
      {tab === 'beds' ? (
        <BedManagementPage />
      ) : (
        <>
          {/* Header */}
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-bold text-gray-900">Inpatient Ward</h2>
              <p className="text-sm text-gray-500 mt-0.5">Active inpatients · auto-refreshes every 60s</p>
            </div>
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-2 text-xs text-gray-500">
                <span className="w-2.5 h-2.5 rounded-full bg-blue-400 inline-block" />
                {admitted.length} admitted
                <span className="w-2.5 h-2.5 rounded-full bg-green-400 inline-block ml-2" />
                {discharged.length} discharged today
              </div>
            </div>
          </div>
          {/* Search */}
          <input
            type="search"
            placeholder="Search patient name or number…"
            value={query}
            onChange={e => setQuery(e.target.value)}
            className="w-full max-w-sm px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />

          {isLoading ? (
            <div className="text-sm text-gray-500 py-8 text-center">Loading ward…</div>
          ) : patients.length === 0 ? (
            <div className="text-sm text-gray-400 py-12 text-center border border-dashed border-gray-200 rounded-xl">
              No active inpatients
            </div>
          ) : (
            <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-200">
                  <tr>
                    {['Patient', 'Consultant', 'Admitted', 'Bed', 'Status', 'Actions'].map(h => (
                      <th key={h} className="text-left px-4 py-3 text-xs font-semibold text-gray-600 uppercase tracking-wide">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {patients.map(enc => (
                    <tr key={enc.id} className={cn('hover:bg-gray-50 transition-colors', enc.dischargedAt && 'opacity-60')}>
                      <td className="px-4 py-3">
                        <p className="font-semibold text-gray-900">{enc.patientName}</p>
                        <p className="text-xs text-gray-400">{enc.patientNumber} · {enc.patientAge} · {enc.patientGender}</p>
                      </td>
                      <td className="px-4 py-3 text-gray-700 text-xs">{enc.providerName ?? '—'}</td>
                      <td className="px-4 py-3 text-gray-500 text-xs">{formatDateTime(enc.startedAt)}</td>
                      <td className="px-4 py-3">
                        {enc.hasBed ? (
                          <span className="inline-flex items-center gap-1 text-xs text-blue-700">
                            🛏️ Assigned
                          </span>
                        ) : (
                          <span className="text-xs text-amber-600">⚠ No bed</span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        {enc.dischargedAt ? (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border bg-green-50 text-green-700 border-green-200">
                            Discharged
                          </span>
                        ) : enc.dischargedAt === null && enc.hasBed ? (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border bg-blue-50 text-blue-700 border-blue-200">
                            Admitted
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border bg-amber-50 text-amber-700 border-amber-200">
                            Pending Bed
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <Link
                            to={`/ip-casesheet/${enc.id}`}
                            className="px-3 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                          >
                            📋 Case Sheet
                          </Link>
                          <Link
                            to={`/encounters/${enc.id}`}
                            className="px-3 py-1.5 text-xs font-semibold bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
                          >
                            Details
                          </Link>
                        </div>
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
