import { useState, useEffect, useRef, useMemo } from 'react'
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
import { useBedTypes, useAvailableBeds, useBedMutations } from '../../../hooks/bed/useBed'
import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { payerApi } from '../../../services/masters/masterApi'
import { Modal } from '../../../components/ui/Modal'

export default function IpWardPage() {
  const { user } = useAuthStore()
  const [searchParams] = useSearchParams()
  const tab = searchParams.get('tab') || 'ward'
  const isNurseView = searchParams.get('role') === 'nurse' || (user?.roles?.includes('NURSE') && searchParams.get('role') !== 'consultant')
  const qc = useQueryClient()

  const [query, setQuery] = useState('')
  const [activeSubTab, setActiveSubTab] = useState<'active' | 'all'>('active')
  const [statusFilter, setStatusFilter] = useState('')
  const [fromDate, setFromDate] = useState(() => new Date().toISOString().split('T')[0])
  const [toDate, setToDate] = useState(() => new Date().toISOString().split('T')[0])
  const [selectedConsultantId, setSelectedConsultantId] = useState(() => user?.consultantId || '')
  const [page, setPage] = useState(0)

  // Reset page to 0 when filters change
  const handleQueryChange = (val: string) => { setQuery(val); setPage(0); }
  const handleFromDateChange = (val: string) => { setFromDate(val); setPage(0); }
  const handleToDateChange = (val: string) => { setToDate(val); setPage(0); }
  const handleConsultantChange = (val: string) => { setSelectedConsultantId(val); setPage(0); }
  const handleStatusChange = (val: string) => { setStatusFilter(val); setPage(0); }
  const handleSubTabChange = (val: 'active' | 'all') => { setActiveSubTab(val); setPage(0); }

  // Automatically refresh inpatient list when entering the page or changing tabs
  useEffect(() => {
    if (tab === 'ward') {
      qc.invalidateQueries({ queryKey: ['inpatients'] })
      qc.invalidateQueries({ queryKey: ['inpatients-admitted-count'] })
    } else if (tab === 'requests') {
      qc.invalidateQueries({ queryKey: ['pending-admission-requests'] })
    }
  }, [qc, tab])

  // Sync selectedConsultantId when user context changes (e.g. branch switch)
  useEffect(() => {
    setSelectedConsultantId(user?.consultantId || '')
  }, [user?.consultantId])

  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
  })

  const getConsultantFullNameWithDegree = (providerId: string, fallbackName: string | null | undefined) => {
    const match = consultants?.find((c: any) => c.id === providerId)
    if (match) {
      const degree = match.specialisation || match.qualification
      return `${match.salutation || ''} ${match.firstName} ${match.lastName}${degree ? ` (${degree})` : ''}`.replace(/\s+/g, ' ').trim()
    }
    return fallbackName ?? '—'
  }

  // Fetch inpatient encounters with filters (paginated: 10 per page)
  const { data, isLoading } = useQuery({
    queryKey: ['inpatients', activeSubTab, query, fromDate, toDate, selectedConsultantId, statusFilter, page],
    queryFn: () => encounterApi.getInpatients(
      query || undefined,
      activeSubTab === 'all' ? fromDate : undefined,
      activeSubTab === 'all' ? toDate : undefined,
      selectedConsultantId || undefined,
      activeSubTab === 'active',
      statusFilter || undefined,
      page,
      10,
      activeSubTab === 'all' && fromDate !== toDate ? 'ASC' : 'DESC'
    ),
    refetchInterval: 30_000,
    enabled: tab === 'ward',
  })

  // Fetch only active count if showing all to avoid 1000 items loading
  const { data: admittedCountData } = useQuery({
    queryKey: ['inpatients-admitted-count', activeSubTab, query, fromDate, toDate, selectedConsultantId, statusFilter],
    queryFn: () => encounterApi.getInpatients(
      query || undefined,
      activeSubTab === 'all' ? fromDate : undefined,
      activeSubTab === 'all' ? toDate : undefined,
      selectedConsultantId || undefined,
      true, // activeOnly = true to count admitted patients
      undefined,
      0,
      1000,
      activeSubTab === 'all' && fromDate !== toDate ? 'ASC' : 'DESC'
    ),
    refetchInterval: 30_000,
    enabled: tab === 'ward' && activeSubTab === 'all' && !statusFilter,
  })

  const patients: EncounterSummary[] = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  let admittedCount = 0
  let dischargedCount = 0

  if (activeSubTab === 'active') {
    admittedCount = data?.totalElements ?? 0
    dischargedCount = 0
  } else {
    if (statusFilter === 'ADMITTED') {
      admittedCount = data?.totalElements ?? 0
      dischargedCount = 0
    } else if (statusFilter === 'DISCHARGED') {
      admittedCount = 0
      dischargedCount = data?.totalElements ?? 0
    } else {
      admittedCount = admittedCountData?.totalElements ?? 0
      dischargedCount = Math.max(0, (data?.totalElements ?? 0) - admittedCount)
    }
  }

  return (
    <div className="space-y-5">
      {tab === 'beds' ? (
        <BedManagementPage />
      ) : tab === 'requests' ? (
        <AdmissionRequestsTab />
      ) : (
        <>
          {/* Header Bar */}
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
            <div className="flex items-center gap-6">
              <h2 className="text-xl font-bold text-gray-900">In Patients</h2>
              <div className="flex space-x-1 bg-gray-100 p-1 rounded-lg">
                <button
                  onClick={() => handleSubTabChange('active')}
                  className={cn("px-4 py-1.5 text-sm font-medium rounded-md transition-all", activeSubTab === 'active' ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700")}
                >
                  Active In Patients
                </button>
                <button
                  onClick={() => handleSubTabChange('all')}
                  className={cn("px-4 py-1.5 text-sm font-medium rounded-md transition-all", activeSubTab === 'all' ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700")}
                >
                  In Patients List
                </button>
              </div>
            </div>
            
            <div className="flex items-center gap-3 text-xs text-gray-500">
              <span className="w-2.5 h-2.5 rounded-full bg-neutral-400 inline-block" />
              {admittedCount} Admitted
              <span className="w-2.5 h-2.5 rounded-full bg-green-400 inline-block ml-2" />
              {dischargedCount} Discharged (filtered set)
            </div>
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

            {/* Consultant Filter */}
            {!user?.consultantId && (
              <div className="w-64">
                <ConsultantSearchInput
                  consultants={consultants}
                  value={selectedConsultantId}
                  onChange={handleConsultantChange}
                  placeholder="All Consultants"
                />
              </div>
            )}

            {/* Date Filters */}
            {activeSubTab === 'all' && (
              <>
                <div className="w-36">
                  <DatePicker
                    value={fromDate}
                    onChange={handleFromDateChange}
                    placeholder="From Date"
                    clearable={true}
                    maxDate={toDate || new Date().toISOString().split('T')[0]}
                  />
                </div>
                <div className="w-36">
                  <DatePicker
                    value={toDate}
                    onChange={handleToDateChange}
                    placeholder="To Date"
                    clearable={true}
                    minDate={fromDate}
                    maxDate={new Date().toISOString().split('T')[0]}
                  />
                </div>
                <select value={statusFilter} onChange={e => handleStatusChange(e.target.value)}
                  className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500">
                  <option value="">All</option>
                  <option value="ADMITTED">Admitted</option>
                  <option value="DISCHARGED">Discharged</option>
                </select>
              </>
            )}
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
                    {['DATE', 'BED NO', 'PATIENT NO', 'PATIENT', 'ADMISSION', 'DISCHARGE', 'PRIMARY CONSULTANT', 'VIEW'].map(h => (
                      <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {patients.map(enc => (
                    <tr key={enc.id} className={cn('hover:bg-gray-50/80 transition-colors', enc.dischargedAt && 'bg-gray-50/30 text-gray-500')}>
                      {/* DATE */}
                      <td className="px-4 py-3 text-xs text-gray-600">
                        {enc.startedAt ? formatDateTime(enc.startedAt) : '—'}
                      </td>

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
                      <td className="px-4 py-3 text-gray-700 text-xs font-medium" title={getConsultantFullNameWithDegree(enc.primaryProviderId, enc.providerName)}>
                        {enc.providerName ?? '—'}
                      </td>

                      {/* CASESHEET */}
                      <td className="px-4 py-3">
                        <Link
                          to={`/ip-casesheet/${enc.id}${isNurseView ? '?role=nurse' : '?role=consultant'}`}
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

function AdmissionRequestsTab() {
  const { user } = useAuthStore()
  const qc = useQueryClient()
  const [selectedRequest, setSelectedRequest] = useState<EncounterSummary | null>(null)
  const [search, setSearch] = useState('')
  const [consultantFilter, setConsultantFilter] = useState(() => user?.consultantId || '')
  const [page, setPage] = useState(0)

  // Reset page when filters change
  const handleSearchChange = (val: string) => { setSearch(val); setPage(0) }
  const handleConsultantChange = (val: string) => { setConsultantFilter(val); setPage(0) }

  // Sync consultantFilter when user context changes (e.g. branch switch)
  useEffect(() => {
    setConsultantFilter(user?.consultantId || '')
  }, [user?.consultantId])

  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
  })

  const getConsultantFullNameWithDegree = (providerId: string, fallbackName: string | null | undefined) => {
    const match = consultants?.find((c: any) => c.id === providerId)
    if (match) {
      const degree = match.specialisation || match.qualification
      return `${match.salutation || ''} ${match.firstName} ${match.lastName}${degree ? ` (${degree})` : ''}`.replace(/\s+/g, ' ').trim()
    }
    return fallbackName ?? '—'
  }

  const { data, isLoading } = useQuery({
    queryKey: ['pending-admission-requests', search, consultantFilter, page],
    queryFn: () => encounterApi.getPendingAdmissionRequests(
      search || undefined,
      consultantFilter || undefined,
      page,
      5
    ),
  })

  const requests = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  return (
    <div className="space-y-4">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900 font-sans">Pending Admission Requests</h2>
          
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Search */}
          <input
            type="search"
            placeholder="Search name, patient no, phone…"
            value={search}
            onChange={e => handleSearchChange(e.target.value)}
            className="w-64 px-3 py-1.5 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500"
          />

          {/* Consultant Filter */}
          {!user?.consultantId && (
            <div className="w-64">
              <ConsultantSearchInput
                consultants={consultants}
                value={consultantFilter}
                onChange={handleConsultantChange}
                placeholder="All Consultants"
              />
            </div>
          )}
        </div>
      </div>

      {isLoading ? (
        <div className="text-sm text-gray-500 py-8 text-center">Loading Admission Requests…</div>
      ) : requests.length === 0 ? (
        <div className="text-sm text-gray-400 py-12 text-center border border-dashed border-gray-200 rounded-xl bg-white">
          No pending admission requests found
        </div>
      ) : (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                {['PATIENT NO', 'PATIENT DETAILS', 'REQUESTED BY', 'REASON FOR ADMISSION', 'REQUESTED DATE', 'ACTION'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-xs font-bold text-gray-600 uppercase tracking-wider">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {requests.map(enc => {
                const requestData = enc.consultantShareMap?.ADMISSION_REQUEST as Record<string, any> | undefined
                const reason = requestData?.admissionReason || '—'
                const requestedDate = requestData?.requestedAdmissionDate || enc.startedAt

                return (
                  <tr key={enc.id} className="hover:bg-gray-50/80 transition-colors">
                    {/* Patient No */}
                    <td className="px-4 py-3 font-mono text-xs text-gray-500">
                      {enc.patientNumber}
                    </td>

                    {/* Patient details */}
                    <td className="px-4 py-3">
                      <p className="font-bold text-gray-900">{enc.patientName}</p>
                      <p className="text-[10px] text-gray-400 mt-0.5">{enc.patientAge} · {enc.patientGender} · {enc.patientMobileNumber || 'No phone'}</p>
                    </td>

                    {/* Requested by */}
                    <td className="px-4 py-3 text-gray-700 text-xs font-medium" title={getConsultantFullNameWithDegree(enc.primaryProviderId, enc.providerName)}>
                      {enc.providerName ?? '—'}
                    </td>

                    {/* Reason */}
                    <td className="px-4 py-3 text-gray-600 text-xs max-w-xs truncate" title={reason}>
                      {reason}
                    </td>

                    {/* Date */}
                    <td className="px-4 py-3 text-gray-500 text-xs">
                      {formatDateTime(requestedDate)}
                    </td>

                    {/* Action */}
                    <td className="px-4 py-3">
                      <button
                        onClick={() => setSelectedRequest(enc)}
                        className="px-3 py-1.5 text-xs font-bold text-white bg-neutral-800 hover:bg-neutral-900 rounded-lg shadow-sm transition-all"
                      >
                        Allocate Bed
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {/* Pagination Footer */}
          <div className="px-6 py-4 bg-gray-50 border-t border-gray-200 flex items-center justify-between">
            <div className="text-xs text-gray-500">
              Page <span className="font-medium text-gray-900">{String(page + 1)}</span> of <span className="font-medium text-gray-900">{String(totalPages || 1)}</span>
              <span className="ml-2">· {String(totalElements)} total requests</span>
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

      {selectedRequest && (
        <AllocateBedFromRequestModal
          request={selectedRequest}
          onClose={() => setSelectedRequest(null)}
          onSuccess={() => {
            setSelectedRequest(null)
            qc.invalidateQueries({ queryKey: ['pending-admission-requests'] })
            qc.invalidateQueries({ queryKey: ['inpatients'] })
            qc.invalidateQueries({ queryKey: ['inpatients-admitted-count'] })
            qc.invalidateQueries({ queryKey: ['beds'] })
          }}
        />
      )}
    </div>
  )
}

interface AllocateBedFromRequestModalProps {
  request: EncounterSummary
  onClose: () => void
  onSuccess: () => void
}

function AllocateBedFromRequestModal({ request, onClose, onSuccess }: AllocateBedFromRequestModalProps) {
  const { data: bedTypes = [] } = useBedTypes()
  const { data: consultants = [] } = useConsultants()
  const { data: payers = [] } = useQuery({ queryKey: ['payers'], queryFn: payerApi.getAll })
  
  const [selectedRoomCategoryId, setSelectedRoomCategoryId] = useState('')
  const [selectedBedId, setSelectedBedId] = useState('')
  const [selectedConsultant, setSelectedConsultant] = useState(request.primaryProviderId || '')
  const [selectedBillType, setSelectedBillType] = useState('')
  const [selectedPayor, setSelectedPayor] = useState('')

  const { data: availableBeds = [], isLoading: isLoadingBeds } = useAvailableBeds(selectedRoomCategoryId || undefined)
  const mutations = useBedMutations()

  const requestData = request.consultantShareMap?.ADMISSION_REQUEST as Record<string, any> | undefined
  const reason = requestData?.admissionReason || '—'
  const nurseInstructions = requestData?.instructionsToNurses || ''

  const handleAllocate = () => {
    if (!selectedBedId) return
    mutations.allocate.mutate({
      bedId: selectedBedId,
      encounterId: request.id,
      consultantId: selectedConsultant || undefined,
      billType: selectedBillType || undefined,
      payorId: selectedPayor || undefined
    }, {
      onSuccess: () => {
        onSuccess()
      }
    })
  }

  const isSubmitDisabled = !selectedBedId || !selectedBillType || (selectedBillType === 'CREDIT' && !selectedPayor) || mutations.allocate.isPending

  return (
    <Modal
      isOpen={true}
      onClose={onClose}
      title={`Allocate Bed — ${request.patientName}`}
      description="Select a bed type and a specific bed to admit the patient."
      size="md"
      showCloseButton={true}
    >
      <div className="p-6 space-y-4 max-h-[85vh] overflow-y-auto">
        <div>
          <h3 className="font-bold text-gray-900 text-base">
            Allocate Bed — {request.patientName}
          </h3>
          <p className="text-xs text-gray-400 mt-0.5">
            Select a bed type and a specific bed to admit the patient.
          </p>
        </div>

        {/* Patient & Request details */}
        <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-3 text-xs space-y-2">
          <div className="flex justify-between">
            <span className="font-bold text-neutral-700">Patient ID:</span>
            <span className="font-mono text-neutral-600">{request.patientNumber}</span>
          </div>
          <div className="flex justify-between">
            <span className="font-bold text-neutral-700">Gender / Age:</span>
            <span className="text-neutral-600">{request.patientGender} / {request.patientAge}</span>
          </div>
          <div>
            <span className="font-bold text-neutral-700 block mb-0.5">Admission Reason:</span>
            <span className="text-neutral-600">{reason}</span>
          </div>
          {nurseInstructions && (
            <div>
              <span className="font-bold text-neutral-700 block mb-0.5">Nurse Instructions:</span>
              <span className="text-neutral-600">{nurseInstructions}</span>
            </div>
          )}
        </div>

        {/* Inputs */}
        <div className="space-y-3">
          {/* Bed Category */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Bed Type *
            </label>
            <SearchableSelect
              options={bedTypes.map(t => ({ value: t.id, label: t.name }))}
              value={selectedRoomCategoryId}
              onChange={val => {
                setSelectedRoomCategoryId(val)
                setSelectedBedId('')
              }}
              placeholder="Select Bed Type"
            />
          </div>

          {/* Bed */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Bed *
            </label>
            <SearchableSelect
              options={availableBeds.map(b => ({ value: b.id, label: b.name }))}
              value={selectedBedId}
              onChange={setSelectedBedId}
              disabled={!selectedRoomCategoryId || isLoadingBeds}
              placeholder={
                !selectedRoomCategoryId
                  ? 'Select Bed Type first'
                  : isLoadingBeds
                  ? 'Loading beds...'
                  : availableBeds.length === 0
                  ? 'No available beds'
                  : 'Select Bed'
              }
            />
          </div>

          {/* Consultant */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Consultant *
            </label>
            <ConsultantSearchInput
              consultants={consultants}
              value={selectedConsultant}
              onChange={setSelectedConsultant}
              placeholder="Select Consultant"
              size="sm"
              className="w-full"
            />
          </div>

          {/* Bill Type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Bill Type *
            </label>
            <SearchableSelect
              options={[
                { value: 'CASH', label: 'Cash' },
                { value: 'CREDIT', label: 'Credit' }
              ]}
              value={selectedBillType}
              onChange={val => {
                setSelectedBillType(val)
                if (val !== 'CREDIT') setSelectedPayor('')
              }}
              placeholder="Select Bill Type"
            />
          </div>

          {/* Payor */}
          {selectedBillType === 'CREDIT' && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Payor *
              </label>
              <SearchableSelect
                options={[
                  ...payers
                    .filter((p: any) => p.status === 1 || p.status === 'ACTIVE')
                    .map((p: any) => ({ value: p.id, label: p.name })),
                  { value: 'OTHER', label: 'OTHER' }
                ]}
                value={selectedPayor}
                onChange={setSelectedPayor}
                placeholder="Select Payor"
              />
            </div>
          )}
        </div>

        {/* Buttons */}
        <div className="flex gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 px-4 py-2 border border-gray-200 text-gray-600 rounded-xl hover:bg-gray-50 text-sm font-semibold transition-colors"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleAllocate}
            disabled={isSubmitDisabled}
            className="flex-1 px-4 py-2 bg-neutral-800 text-white rounded-xl hover:bg-neutral-900 text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {mutations.allocate.isPending ? 'Allocating...' : 'Allocate Bed'}
          </button>
        </div>
      </div>
    </Modal>
  )
}

interface SearchableSelectProps {
  options: { value: string; label: string }[]
  value: string
  onChange: (value: string) => void
  placeholder?: string
  disabled?: boolean
  className?: string
  size?: 'sm' | 'md'
}

function SearchableSelect({
  options,
  value,
  onChange,
  placeholder = 'Select Option',
  disabled = false,
  className,
  size = 'sm'
}: SearchableSelectProps) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const selectedOption = useMemo(() => options.find(o => o.value === value), [options, value])

  useEffect(() => {
    if (!open) {
      setQuery('')
    }
  }, [open])

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const filteredOptions = useMemo(() => {
    if (!query) return options
    const q = query.toLowerCase()
    return options.filter(o => o.label.toLowerCase().includes(q))
  }, [options, query])

  const displayValue = selectedOption ? selectedOption.label : ''

  return (
    <div ref={ref} className={cn('relative w-full', className)}>
      <div className="relative group">
        <input
          type="text"
          disabled={disabled}
          value={open ? query : displayValue}
          title={displayValue}
          placeholder={open ? "Search..." : placeholder}
          className={cn(
            "w-full outline-none transition-all text-sm border focus:border-neutral-500 focus:ring-1 focus:ring-neutral-500 bg-white transition-colors",
            open && "border-neutral-500 ring-1 ring-neutral-500",
            size === 'sm'
              ? "pl-3 pr-10 py-1.5 border-gray-300 rounded-lg"
              : "pl-4 pr-12 py-2 border-gray-300 rounded-lg",
            disabled && "bg-gray-100 text-gray-500 cursor-not-allowed border-gray-200"
          )}
          onChange={e => {
            if (disabled) return
            const val = e.target.value
            setQuery(val)
            if (!val && value) {
              onChange('')
            }
            if (!open) setOpen(true)
          }}
          onFocus={() => !disabled && setOpen(true)}
          onClick={() => !disabled && setOpen(true)}
        />
        <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-0.5">
          {value && !disabled && (
            <button
              type="button"
              onMouseDown={(e) => {
                e.preventDefault()
                onChange('')
                setQuery('')
                setOpen(true)
              }}
              className="p-0.5 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100 transition-colors"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
          <div className="text-gray-400 pointer-events-none p-0.5">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>
      </div>

      {open && !disabled && (
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-md flex flex-col max-h-60 overflow-y-auto">
          {filteredOptions.length > 0 ? (
            <ul>
              {filteredOptions.map(o => (
                <li
                  key={o.value}
                  title={o.label}
                  className={cn(
                    "px-4 py-2 hover:bg-[#C25727] hover:text-white cursor-pointer transition-colors text-xs text-gray-900",
                    value === o.value ? "bg-[#C25727] text-white" : ""
                  )}
                  onMouseDown={(e) => {
                    e.preventDefault()
                    onChange(o.value)
                    setOpen(false)
                  }}
                >
                  <span className="font-medium">{o.label}</span>
                </li>
              ))}
            </ul>
          ) : (
            <div className="px-4 py-3 text-xs text-gray-500 text-center">No options found</div>
          )}
        </div>
      )}
    </div>
  )
}
