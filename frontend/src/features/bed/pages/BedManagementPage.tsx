import { useState, useRef, useEffect } from 'react'
import { useBeds, useBedSummary, useBedMutations, useBedTypes } from '../../../hooks/bed/useBed'
import { useConsultants } from '../../../hooks/consultant/useConsultant'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import { bedApi } from '../../../services/bed/bedApi'
import type { InpatientSearchResult } from '../../../services/bed/bedApi'
import type { Bed, BedStatus } from '../../../types/bed'
import { cn } from '../../../lib/utils'
import { useQuery } from '@tanstack/react-query'
import { payerApi } from '../../../services/masters/masterApi'

const STATUS_STYLES: Record<BedStatus, { card: string; dot: string; label: string }> = {
  AVAILABLE: { card: 'bg-green-50  border-green-200', dot: 'bg-green-500', label: 'Available' },
  ALLOCATED: { card: 'bg-amber-50  border-amber-200', dot: 'bg-amber-500', label: 'Occupied' },
  MAINTENANCE: { card: 'bg-gray-100  border-gray-200', dot: 'bg-gray-400', label: 'Maintenance' },
}

function BedCard({ bed, onAllocate, onTransfer, onRelease, onMaintenance, onClearMaintenance, isLoading }:
  {
    bed: Bed; onAllocate: (b: Bed) => void; onTransfer: (b: Bed) => void; onRelease: (b: Bed) => void
    onMaintenance: (b: Bed) => void; onClearMaintenance: (b: Bed) => void; isLoading: boolean
  }) {
  const s = STATUS_STYLES[bed.bedStatus]
  return (
    <div className={cn('border rounded-xl p-4 flex flex-col gap-2 min-h-28 transition-colors', s.card)}
      role="article" aria-label={`Bed ${bed.name}, ${s.label}`}>
      <div className="flex items-center justify-between">
        <span className="font-bold text-gray-900 text-sm">{bed.name}</span>
        <span className={cn('w-2.5 h-2.5 rounded-full', s.dot)} aria-hidden="true" />
      </div>
      <div className="text-[10px] text-gray-500 space-y-0.5 leading-tight">
        {/* {bed.roomCategoryName && (
          <p className="font-bold text-blue-600 uppercase tracking-tighter">{bed.roomCategoryName}</p>
        )} */}
        {bed.roomCategoryName && (
          <p className="font-medium text-gray-600">
            Bed Type:{" "}
            <span className="text-blue-600 uppercase">
              {bed.roomCategoryName}
            </span>
          </p>
        )}
        {bed.ward && <p>Ward: {bed.ward}</p>}
        {bed.floor && <p>Floor: {bed.floor}</p>}
        <p className="font-medium text-[11px] text-gray-800">{s.label}</p>
      </div>

      {bed.bedStatus === 'ALLOCATED' && bed.allocatedPatientName && (
        <div className="mt-1 p-2 bg-white/60 border border-amber-200 rounded-lg shadow-sm">
          <p className="text-[10px] font-bold text-amber-800 leading-none truncate">
            {bed.allocatedPatientName}
          </p>
          <div className="flex items-center justify-between mt-1">
            <p className="text-[9px] text-amber-600 font-mono">
              {bed.allocatedPatientNumber || 'N/A'}
            </p>
            <p className="text-[9px] font-bold text-blue-600 uppercase tracking-tighter">
              {bed.allocatedConsultantName || 'Unknown Consultant'}
            </p>
          </div>
        </div>
      )}
      <div className="flex gap-1 flex-wrap mt-auto">
        {bed.bedStatus === 'AVAILABLE' && (
          <>
            <button onClick={() => onAllocate(bed)} disabled={isLoading}
              className="text-xs px-2 py-1 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 transition-colors">
              Allocate
            </button>
            <button onClick={() => onMaintenance(bed)} disabled={isLoading}
              className="text-xs px-2 py-1 border border-gray-300 text-gray-600 rounded hover:bg-gray-50 disabled:opacity-50 transition-colors">
              Maintenance
            </button>
          </>
        )}
        {bed.bedStatus === 'ALLOCATED' && (
          <div className="flex gap-1 w-full">
            <button onClick={() => onTransfer(bed)} disabled={isLoading}
              className="flex-1 text-xs px-2 py-1 bg-indigo-600 text-white rounded hover:bg-indigo-700 disabled:opacity-50 transition-colors">
              Transfer
            </button>
            <button onClick={() => onRelease(bed)} disabled={isLoading}
              className="flex-1 text-xs px-2 py-1 bg-amber-600 text-white rounded hover:bg-amber-700 disabled:opacity-50 transition-colors">
              Release
            </button>
          </div>
        )}
        {bed.bedStatus === 'MAINTENANCE' && (
          <button onClick={() => onClearMaintenance(bed)} disabled={isLoading}
            className="text-xs px-2 py-1 bg-green-600 text-white rounded hover:bg-green-700 disabled:opacity-50 transition-colors">
            Return to Service
          </button>
        )}
      </div>
    </div>
  )
}

// ── Patient search autocomplete ───────────────────────────────────────────────
function PatientSearch({
  onSelect,
}: {
  onSelect: (result: InpatientSearchResult) => void
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<InpatientSearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState<InpatientSearchResult | null>(null)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const wrapperRef = useRef<HTMLDivElement>(null)

  // Close dropdown on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const handleChange = (val: string) => {
    setQuery(val)
    setSelected(null)
    onSelect(null as unknown as InpatientSearchResult) // clear parent selection
    if (debounceRef.current) clearTimeout(debounceRef.current)
    if (val.trim().length < 2) { setResults([]); setOpen(false); return }
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const data = await bedApi.searchInpatients(val.trim())
        setResults(data)
        setOpen(true)
      } catch { setResults([]) }
      finally { setLoading(false) }
    }, 300)
  }

  const handleSelect = (r: InpatientSearchResult) => {
    setSelected(r)
    setQuery(`${r.patientNumber} — ${r.patientName}${r.contactNumber ? ` (${r.contactNumber})` : ''}`)
    setOpen(false)
    setResults([])
    onSelect(r)
  }

  return (
    <div ref={wrapperRef} className="relative">
      <div className="relative">
        <input
          id="patient-search"
          type="text"
          value={query}
          onChange={e => handleChange(e.target.value)}
          onFocus={() => results.length > 0 && setOpen(true)}
          placeholder="Search with Patient id \ Name\ Phone No"
          autoComplete="off"
          className={cn(
            'w-full px-3 py-2 pr-8 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-colors',
            selected ? 'border-green-400 bg-green-50' : 'border-gray-300'
          )}
        />
        {loading && (
          <span className="absolute right-2 top-1/2 -translate-y-1/2">
            <svg className="w-4 h-4 animate-spin text-gray-400" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
            </svg>
          </span>
        )}
        {selected && !loading && (
          <span className="absolute right-2 top-1/2 -translate-y-1/2 text-green-500">✓</span>
        )}
      </div>

      {open && results.length > 0 && (
        <ul className="absolute z-50 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-xl max-h-52 overflow-y-auto">
          {results.map(r => (
            <li key={r.encounterId}>
              <button
                type="button"
                onMouseDown={() => handleSelect(r)}
                className="w-full text-left px-3 py-2.5 hover:bg-blue-50 transition-colors"
              >
                <span className="block text-xs font-semibold text-blue-700">
                  {r.patientNumber} {r.contactNumber && `• ${r.contactNumber}`}
                </span>
                <span className="block text-sm text-gray-800">{r.patientName}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {open && results.length === 0 && !loading && query.trim().length >= 2 && (
        <div className="absolute z-50 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-xl px-3 py-3 text-sm text-gray-400 text-center">
          No active inpatients found
        </div>
      )}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────
export default function BedManagementPage({ hideHeader = false }: { hideHeader?: boolean }) {
  const { data: beds, isLoading } = useBeds()
  const { data: bedTypes } = useBedTypes()
  const { data: consultants } = useConsultants()
  const { data: summary } = useBedSummary()
  const { data: payers = [] } = useQuery({ queryKey: ['payers'], queryFn: payerApi.getAll })
  const mutations = useBedMutations()
  const [filterStatus, setFilterStatus] = useState<BedStatus | 'ALL'>('ALL')
  const [filterRoomCategoryId, setFilterRoomCategoryId] = useState<string>('ALL')
  const [filterConsultant, setFilterConsultant] = useState<string>('ALL')
  const [allocateModal, setAllocateModal] = useState<Bed | null>(null)
  const [transferModal, setTransferModal] = useState<Bed | null>(null)
  const [selectedPatient, setSelectedPatient] = useState<InpatientSearchResult | null>(null)
  const [selectedConsultant, setSelectedConsultant] = useState<string>('')
  const [selectedBillType, setSelectedBillType] = useState<string>('')
  const [selectedPayor, setSelectedPayor] = useState<string>('')
  const [targetBedId, setTargetBedId] = useState<string>('')
  const [searchQuery, setSearchQuery] = useState('')

  const filtered = beds?.filter(b => {
    if (b.status === 'INACTIVE' || (b.status as any) === 0) return false;
    const statusMatch = filterStatus === 'ALL' || b.bedStatus === filterStatus;
    const typeMatch = filterRoomCategoryId === 'ALL' || b.roomCategoryId === filterRoomCategoryId;
    const consultantMatch = filterConsultant === 'ALL' || !filterConsultant || (
      consultants?.find(c => c.id === filterConsultant) &&
      b.allocatedConsultantName === `${consultants?.find(c => c.id === filterConsultant)?.salutation ? `${consultants?.find(c => c.id === filterConsultant)?.salutation} ` : ''}${consultants?.find(c => c.id === filterConsultant)?.firstName} ${consultants?.find(c => c.id === filterConsultant)?.lastName}`
    );

    const query = searchQuery.toLowerCase().trim();
    const searchMatch = !query ||
      b.name.toLowerCase().includes(query) ||
      (b.allocatedPatientName?.toLowerCase().includes(query)) ||
      (b.allocatedPatientNumber?.toLowerCase().includes(query)) ||
      (b.allocatedConsultantName?.toLowerCase().includes(query));

    return statusMatch && typeMatch && consultantMatch && searchMatch;
  }) ?? []

  const handleAllocate = () => {
    if (!allocateModal || !selectedPatient) return
    mutations.allocate.mutate(
      { 
        bedId: allocateModal.id, 
        encounterId: selectedPatient.encounterId,
        consultantId: selectedConsultant,
        billType: selectedBillType,
        ...(selectedPayor && selectedPayor !== 'OTHER' ? { payorId: selectedPayor } : {})
      },
      { 
        onSuccess: () => { 
          setAllocateModal(null)
          setSelectedPatient(null)
          setSelectedConsultant('')
          setSelectedBillType('')
          setSelectedPayor('')
        } 
      }
    )
  }

  const handleTransfer = () => {
    console.log('Transfer Request:', {
      fromBed: transferModal?.name,
      toBedId: targetBedId,
      encounterId: transferModal?.allocatedEncounterId
    });

    if (!transferModal?.allocatedEncounterId) {
      alert('Error: Patient encounter information missing. Cannot transfer.');
      return;
    }
    if (!targetBedId) {
      alert('Please select a target bed.');
      return;
    }

    mutations.transfer.mutate(
      { encounterId: transferModal.allocatedEncounterId, newBedId: targetBedId },
      {
        onSuccess: () => {
          setTransferModal(null);
          setTargetBedId('');
        }
      }
    )
  }

  const openModal = (bed: Bed) => {
    setAllocateModal(bed)
    setSelectedPatient(null)
    setSelectedConsultant('')
    setSelectedBillType('')
    setSelectedPayor('')
  }

  const openTransfer = (bed: Bed) => {
    setTransferModal(bed)
    setTargetBedId('')
  }

  return (
    <div className="space-y-5 max-w-6xl mx-auto">
      <div className="flex items-center justify-between flex-wrap gap-4 ">
        {!hideHeader && (
          <div className="flex items-center gap-4">
            <h2 className="text-xl font-bold text-gray-900">Bed Management</h2>
          </div>
        )}


        <div className="flex flex-1 max-w-lg items-center gap-2">
          <div className="relative flex-1">
            <svg className="absolute left-3.5 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              placeholder="Search by Patient Name / ID / Phone No"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="w-full pl-11 pr-4 py-3 text-sm bg-white border border-gray-200 rounded-xl shadow-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all placeholder:text-gray-400"
            />
          </div>
        </div>

        <div className="flex gap-2 items-center">
          <select
            value={filterRoomCategoryId}
            onChange={e => setFilterRoomCategoryId(e.target.value)}
            className="px-4 py-3 text-sm font-semibold rounded-xl border border-gray-200 text-gray-700 bg-white shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all cursor-pointer hover:border-gray-300"
          >
            <option value="ALL">All Bed Types</option>
            {bedTypes?.map(type => (
              <option key={type.id} value={type.id}>
                {type.name}
              </option>
            ))}
          </select>
          <div className="w-56">
            <ConsultantSearchInput
              consultants={(consultants ?? []).filter((c: any) => c.status !== 'INACTIVE' && c.status !== 0)}
              value={filterConsultant === 'ALL' ? '' : filterConsultant}
              onChange={val => setFilterConsultant(val || 'ALL')}
              placeholder="All Consultants"
            />
          </div>
          <select
            value={filterStatus}
            onChange={e => setFilterStatus(e.target.value as BedStatus | 'ALL')}
            className="px-4 py-3 text-sm font-semibold rounded-xl border border-gray-200 text-gray-700 bg-white shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all cursor-pointer hover:border-gray-300"
          >
            <option value="ALL">All Statuses</option>
            <option value="AVAILABLE">Available</option>
            <option value="ALLOCATED">Allocated</option>
            <option value="MAINTENANCE">Maintenance</option>
          </select>
        </div>
      </div>

      {/* Summary stats */}
      {summary && (
        <div className="grid grid-cols-4 gap-3 py-4">
          {[
            { label: 'Total', value: summary.total, color: 'text-gray-700' },
            { label: 'Available', value: summary.available, color: 'text-green-600' },
            { label: 'Occupied', value: summary.allocated, color: 'text-amber-600' },
            { label: 'Maintenance', value: summary.maintenance, color: 'text-gray-500' },
          ].map(({ label, value, color }) => (
            <div key={label} className="bg-white border border-gray-200 rounded-xl p-4 text-center">
              <p className={cn('text-2xl font-bold', color)}>{value}</p>
              <p className="text-xs text-gray-500 mt-0.5">{label}</p>
            </div>
          ))}
        </div>
      )}

      {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Loading beds…</p>}

      {/* Bed grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3" role="list">
        {filtered.map(bed => (
          <BedCard
            key={bed.id}
            bed={bed}
            onAllocate={openModal}
            onTransfer={openTransfer}
            onRelease={b => mutations.release.mutate(b.id)}
            onMaintenance={b => mutations.setMaintenance.mutate(b.id)}
            onClearMaintenance={b => mutations.clearMaintenance.mutate(b.id)}
            isLoading={mutations.allocate.isPending || mutations.release.isPending ||
              mutations.setMaintenance.isPending || mutations.clearMaintenance.isPending || mutations.transfer.isPending}
          />
        ))}
        {filtered.length === 0 && !isLoading && (
          <p className="col-span-full text-center text-gray-400 text-sm py-10">No beds found</p>
        )}
      </div>

      {/* Allocate modal */}
      {allocateModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xl p-6 w-full max-w-sm space-y-4">
            <div>
              <h3 id="allocate-title" className="font-bold text-gray-900 text-base">
                Allocate Bed — {allocateModal.name}
              </h3>
              <p className="text-xs text-gray-400 mt-0.5">
                Search the admitted patient by their number or name
              </p>
            </div>

           

            <div>
              <label htmlFor="patient-search" className="block text-sm font-medium text-gray-700 ">
                Patient *
              </label>
              <PatientSearch onSelect={r => setSelectedPatient(r)} />
            
            </div>

            {/* Selected patient info card */}
            {selectedPatient && (
              <div className="bg-blue-50 border border-blue-200 rounded-lg px-3 py-2 text-sm space-y-0.5">
                <div className="flex justify-between items-start">
                  <p className="font-semibold text-blue-800">{selectedPatient.patientNumber}</p>
                  {selectedPatient.contactNumber && (
                    <p className="text-xs text-blue-600 bg-blue-100 px-1.5 rounded">{selectedPatient.contactNumber}</p>
                  )}
                </div>
                <p className="text-blue-700">{selectedPatient.patientName}</p>
                {/* <p className="text-xs text-blue-400 font-mono">{selectedPatient.encounterId}</p> */}
              </div>
            )}

            <div className="space-y-3 mt-2">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Consultant *
                </label>
                <ConsultantSearchInput
                  consultants={consultants ?? []}
                  value={selectedConsultant}
                  onChange={setSelectedConsultant}
                  placeholder="Select Consultant"
                  size="sm"
                  className="w-full"
                />
               
              </div>
               <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Bed Type
              </label>
              <input
                type="text"
                value={bedTypes?.find(t => t.id === allocateModal.roomCategoryId)?.name || ''}
                readOnly
                disabled
                className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 text-gray-500 font-medium"
              />
            </div>

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
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                >
                  <option value="">Select Bill Type</option>
                  <option value="CASH">Cash</option>
                  <option value="CREDIT">Credit</option>
                </select>
                
              </div>

              {selectedBillType === 'CREDIT' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Payor *
                  </label>
                  <select
                    value={selectedPayor}
                    onChange={e => setSelectedPayor(e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                  >
                    <option value="">Select Payor</option>
                    {payers
                      .filter((p: any) => p.status === 1 || p.status === 'ACTIVE')
                      .map((p: any) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    <option value="OTHER">OTHER</option>
                  </select>
            
                </div>
              )}
            </div>

           
            <div className="flex gap-4 pt-3">
  <button
    onClick={() => setAllocateModal(null)}
    className="flex-1 py-3 bg-gray-100 text-gray-700 font-semibold rounded-2xl border border-gray-200 hover:bg-gray-200 shadow-sm transition-all disabled:opacity-50"
  >
    Cancel
  </button>

  <button
    onClick={handleAllocate}
    disabled={!selectedPatient || !selectedConsultant || !selectedBillType || (selectedBillType === 'CREDIT' && !selectedPayor) || mutations.allocate.isPending}
    className="flex-1 py-3 bg-blue-600 text-white font-semibold rounded-2xl hover:bg-blue-500 shadow-sm transition-all disabled:opacity-50"
  >
    {mutations.allocate.isPending ? 'Allocating…' : 'Allocate Bed'}
  </button>
</div>
          </div>
        </div>
      )}
      {/* Transfer modal */}
      {transferModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm"
          role="dialog" aria-modal="true" aria-labelledby="transfer-title">
          <div className="bg-white rounded-2xl border border-gray-200 shadow-xl p-6 w-full max-w-sm space-y-4">
            <div>
              <h3 id="transfer-title" className="font-bold text-gray-900 text-base">
                Transfer Patient — {transferModal.allocatedPatientName}
              </h3>
              <p className="text-xs text-gray-400 mt-0.5">
                Select a new bed to move this patient to
              </p>
            </div>

            <div className="bg-amber-50 p-3 rounded-lg border border-amber-100">
              <p className="text-[10px] font-bold text-amber-800 uppercase">Current Bed</p>
              <p className="text-sm font-medium text-amber-900">{transferModal.name} ({transferModal.roomCategoryName})</p>
            </div>

            <div className="space-y-1">
              <label className="block text-sm font-medium text-gray-700">Target Bed</label>
              <select
                value={targetBedId}
                onChange={e => setTargetBedId(e.target.value)}
                className="w-full px-3 py-2 border rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none bg-white"
              >
                <option value="">Select available bed...</option>
                {beds?.filter(b => b.bedStatus === 'AVAILABLE').map(b => (
                  <option key={b.id} value={b.id}>
                    {b.name} — {b.roomCategoryName} ({b.ward || 'No Ward'})
                  </option>
                ))}
              </select>
              {beds?.filter(b => b.bedStatus === 'AVAILABLE').length === 0 && (
                <p className="text-[10px] text-red-500 mt-1">No available beds for transfer</p>
              )}
            </div>

            <div className="flex gap-3 pt-2">
              <button onClick={handleTransfer}
                disabled={!targetBedId || mutations.transfer.isPending}
                className="flex-1 py-2 bg-indigo-600 text-white text-sm font-semibold rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition-colors">
                {mutations.transfer.isPending ? 'Transferring…' : 'Confirm Transfer'}
              </button>
              <button onClick={() => setTransferModal(null)}
                className="flex-1 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
