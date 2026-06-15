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
import { useBedTypes, useAvailableBeds, useBedMutations } from '../../../hooks/bed/useBed'
import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { payerApi } from '../../../services/masters/masterApi'

export default function IpWardPage() {
  const { user } = useAuthStore()
  const [searchParams] = useSearchParams()
  const tab = searchParams.get('tab') || 'ward'
  const qc = useQueryClient()

  const [query, setQuery] = useState('')
  const [selectedDate, setSelectedDate] = useState('')
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
    } else if (tab === 'requests') {
      qc.invalidateQueries({ queryKey: ['pending-admission-requests'] })
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
      ) : tab === 'requests' ? (
        <AdmissionRequestsTab />
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
                  maxDate={new Date().toISOString().split('T')[0]}
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

function AdmissionRequestsTab() {
  const qc = useQueryClient()
  const [selectedRequest, setSelectedRequest] = useState<EncounterSummary | null>(null)
  const [search, setSearch] = useState('')
  const [consultantFilter, setConsultantFilter] = useState('')
  const [page, setPage] = useState(0)

  // Reset page when filters change
  const handleSearchChange = (val: string) => { setSearch(val); setPage(0) }
  const handleConsultantChange = (val: string) => { setConsultantFilter(val); setPage(0) }

  // Fetch consultants
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
  })

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
          <p className="text-sm text-gray-500 mt-0.5">List of patients with pending IP admission requests from outpatient consultations</p>
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
          <div className="w-64">
            <ConsultantSearchInput
              consultants={consultants}
              value={consultantFilter}
              onChange={handleConsultantChange}
              placeholder="All Consultants"
            />
          </div>
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
                    <td className="px-4 py-3 text-gray-700 text-xs font-medium">
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
            qc.invalidateQueries({ queryKey: ['active-inpatients'] })
            qc.invalidateQueries({ queryKey: ['active-inpatients-all'] })
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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-white rounded-2xl border border-gray-200 shadow-xl p-6 w-full max-w-sm space-y-4 max-h-[95vh] overflow-y-auto">
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
            <select
              value={selectedRoomCategoryId}
              onChange={e => {
                setSelectedRoomCategoryId(e.target.value)
                setSelectedBedId('')
              }}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500"
            >
              <option value="">Select Bed Type</option>
              {bedTypes.map(t => (
                <option key={t.id} value={t.id}>{t.name}</option>
              ))}
            </select>
          </div>

          {/* Bed */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Bed *
            </label>
            <select
              value={selectedBedId}
              onChange={e => setSelectedBedId(e.target.value)}
              disabled={!selectedRoomCategoryId || isLoadingBeds}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 disabled:opacity-50 disabled:bg-gray-50"
            >
              <option value="">
                {!selectedRoomCategoryId
                  ? 'Select Bed Type first'
                  : isLoadingBeds
                  ? 'Loading beds...'
                  : availableBeds.length === 0
                  ? 'No available beds'
                  : 'Select Bed'}
              </option>
              {availableBeds.map(b => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
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
            <select
              value={selectedBillType}
              onChange={e => {
                setSelectedBillType(e.target.value)
                if (e.target.value !== 'CREDIT') setSelectedPayor('')
              }}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500"
            >
              <option value="">Select Bill Type</option>
              <option value="CASH">Cash</option>
              <option value="CREDIT">Credit</option>
            </select>
          </div>

          {/* Payor */}
          {selectedBillType === 'CREDIT' && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Payor *
              </label>
              <select
                value={selectedPayor}
                onChange={e => setSelectedPayor(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500"
              >
                <option value="">Select Payor</option>
                {payers
                  .filter((p: any) => p.status === 1 || p.status === 'ACTIVE')
                  .map((p: any) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                <option value="OTHER">OTHER</option>
              </select>
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
    </div>
  )
}
