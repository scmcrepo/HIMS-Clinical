/**
 * PrescriptionOrdersPage.tsx
 * Pharmacy screen showing pending prescription orders from today's OP visits
 * and all active IP admissions. Allows pharmacist to:
 *  - View all pending orders in one place
 *  - Filter by OP / IP / All, and search by patient
 *  - Click "Dispense" to navigate to pharmacy sales pre-filled with patient
 */
import { useState, useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { prescriptionOrdersApi, type PrescriptionOrderRow } from '../../../services/opip/opipApi'
import { cn } from '../../../lib/utils'
import { formatDateTime } from '../../../lib/dateUtils'
import { ClipboardList, RotateCw, Hospital, Stethoscope, Bed, User, Clock, Check, Pill } from 'lucide-react'

import DatePicker from '../../../components/shared/DatePicker'

type TypeFilter = 'ALL' | 'OP' | 'IP'

export default function PrescriptionOrdersPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL')
  const [search, setSearch] = useState('')
  const [filterDate, setFilterDate] = useState(() => new Date().toISOString().split('T')[0])

  useEffect(() => {
    queryClient.invalidateQueries({ queryKey: ['prescription-orders'] })
  }, [queryClient])

  const { data: orders = [], isLoading, refetch } = useQuery({
    queryKey: ['prescription-orders', typeFilter, filterDate],
    queryFn: () => prescriptionOrdersApi.getPending({ type: typeFilter, date: filterDate }),
    staleTime: 0,
    refetchInterval: 60_000,
  })

  const displayed = orders
    .filter(o => {
      if (!search.trim()) return true
      const q = search.toLowerCase()
      return (
        o.patientName?.toLowerCase().includes(q) ||
        o.patientNumber?.toLowerCase().includes(q) ||
        o.consultantName?.toLowerCase().includes(q) ||
        o.items.some(i => i.drugName?.toLowerCase().includes(q))
      )
    })
    .sort((a, b) => {
      const timeA = a.prescribedAt ? new Date(a.prescribedAt).getTime() : 0
      const timeB = b.prescribedAt ? new Date(b.prescribedAt).getTime() : 0
      return timeB - timeA
    })

  const totalItems = displayed.reduce((sum, o) => sum + (o.items?.length ?? 0), 0)

  function handleDispense(order: PrescriptionOrderRow) {
    navigate(`/sales/sales?encounterId=${order.encounterId}&patientId=${order.patientId ?? ''}&prescribedAt=${encodeURIComponent(order.prescribedAt ?? '')}`)
  }

  return (
    <div className="min-h-screen bg-gray-50/50">
      <div className="max-w-6xl mx-auto px-6 py-8 space-y-6">

        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
              <ClipboardList className="w-6 h-6 text-neutral-600" />
              <span>Prescription Orders</span>
            </h1>
            <p className="text-sm text-gray-500 mt-1">
              Pending prescriptions from today's OP visits and active IP admissions.
              Click <strong>Add To Bill</strong> to open a pharmacy sale for the patient.
            </p>
          </div>
          <button onClick={() => refetch()}
            className="px-3 py-2 text-sm font-medium text-gray-600 border border-gray-200 rounded-xl hover:bg-white transition-colors flex items-center gap-1.5">
            <RotateCw size={14} className="text-gray-500" /> Refresh
          </button>
        </div>

        {/* Filters */}
        <div className="bg-white border border-gray-200 rounded-2xl px-5 py-4 flex items-center gap-4 flex-wrap shadow-sm">
          <div className="flex gap-1">
            {(['ALL', 'OP', 'IP'] as TypeFilter[]).map(t => (
              <button key={t} onClick={() => setTypeFilter(t)}
                className={cn('px-4 py-2 text-xs font-bold rounded-lg border transition-colors flex items-center gap-1.5',
                  typeFilter === t
                    ? 'bg-neutral-600 text-white border-neutral-600'
                    : 'bg-white text-gray-600 border-gray-200 hover:border-neutral-300')}>
                {t === 'ALL' ? (
                  <>
                    <Hospital size={14} />
                    <span>All</span>
                  </>
                ) : t === 'OP' ? (
                  <>
                    <Stethoscope size={14} />
                    <span>Outpatient</span>
                  </>
                ) : (
                  <>
                    <Bed size={14} />
                    <span>Inpatient</span>
                  </>
                )}
              </button>
            ))}
          </div>
          <div className="w-40">
            <DatePicker value={filterDate} onChange={setFilterDate} clearable={false} />
          </div>
          <input type="search" value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Search patient, drug…"
            className="w-56 px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all" />
          <div className="ml-auto flex items-center gap-4 text-xs text-gray-400">
            <span>{displayed.length} order{displayed.length !== 1 ? 's' : ''}</span>
            <span>·</span>
            <span>{totalItems} drug line{totalItems !== 1 ? 's' : ''}</span>
          </div>
        </div>

        {/* Orders list */}
        {isLoading ? (
          <div className="space-y-3">
            {[1, 2, 3, 4].map(i => (
              <div key={i} className="h-32 bg-white border border-gray-200 rounded-2xl animate-pulse" />
            ))}
          </div>
        ) : displayed.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 bg-white border border-dashed border-gray-200 rounded-2xl text-center">
            <ClipboardList className="w-12 h-12 text-gray-300 mx-auto mb-4" />
            <p className="text-base font-semibold text-gray-600">No pending prescriptions</p>
            <p className="text-sm text-gray-400 mt-1 max-w-xs">
              Prescriptions written in today's OP consultations and active IP admissions appear here.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {displayed.map((order, idx) => (
              <div key={`${order.encounterId}-${idx}`}
                className="bg-white border border-gray-200 rounded-2xl overflow-hidden hover:shadow-md transition-shadow">
                <div className="px-5 py-3.5 flex items-center gap-4 border-b border-gray-100 bg-gray-50/50">
                  <div className={cn('w-8 h-8 rounded-lg flex items-center justify-center shrink-0',
                    order.encounterType === 'INPATIENT' ? 'bg-neutral-100 text-neutral-500' : 'bg-green-100 text-green-700')}>
                    {order.encounterType === 'INPATIENT' ? <Bed size={16} /> : <Stethoscope size={16} />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-sm text-gray-900">
                        {order.patientName ?? 'Patient'}
                      </span>
                      {order.patientNumber && (
                        <span className="text-xs text-gray-400 font-mono">{order.patientNumber}</span>
                      )}
                      <span className={cn('inline-flex px-2 py-0.5 rounded-full text-[10px] font-bold border',
                        order.encounterType === 'INPATIENT'
                          ? 'bg-blue-50 text-blue-700 border-blue-200'
                          : 'bg-green-50 text-green-700 border-green-200')}>
                        {order.encounterType === 'INPATIENT' ? 'IP' : 'OP'}
                      </span>
                    </div>
                    <div className="flex items-center gap-3 mt-0.5 text-xs text-gray-400">
                      {order.consultantName && (
                        <span className="flex items-center gap-1">
                          <User size={12} className="text-gray-400 shrink-0" />
                          <span>{order.consultantName}</span>
                        </span>
                      )}
                      {order.prescribedAt && (
                        <span className="flex items-center gap-1">
                          <Clock size={12} className="text-gray-400 shrink-0" />
                          <span>{formatDateTime(order.prescribedAt)}</span>
                        </span>
                      )}
                    </div>
                  </div>
                  {order.billed ? (
                    <button disabled
                      className="shrink-0 px-4 py-2 bg-gray-100 text-gray-400 border border-gray-200 text-xs font-bold rounded-xl cursor-default flex items-center gap-1.5 shadow-sm">
                      <Check size={14} className="text-gray-400 shrink-0" />
                      Billed
                    </button>
                  ) : (
                    <button onClick={() => handleDispense(order)}
                      className="shrink-0 px-4 py-2 bg-emerald-600 text-white text-xs font-bold rounded-xl hover:bg-emerald-700 transition-colors flex items-center gap-1.5 shadow-sm">
                      <Pill size={14} className="text-white shrink-0" />
                      Add To Bill
                    </button>
                  )}
                </div>

                <div className="px-5 py-3">
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                    {(order.items ?? []).map((item, lineIdx) => (
                      <div key={lineIdx}
                        className="flex items-start gap-2.5 bg-gray-50 border border-gray-100 rounded-xl px-3 py-2.5">
                        <Pill size={14} className="text-neutral-400 mt-1 shrink-0" />
                        <div className="min-w-0">
                          <p className="text-xs font-bold text-gray-900 truncate">{item.drugName ?? 'Drug'}</p>
                          <div className="flex flex-wrap gap-1 mt-1">
                            {item.frequency && <span className="px-1.5 py-0.5 bg-white border border-gray-200 rounded text-[10px] text-gray-600">{item.frequency}</span>}
                            {item.duration && <span className="px-1.5 py-0.5 bg-white border border-gray-200 rounded text-[10px] text-gray-600">{item.duration}</span>}
                            {item.qty > 0 && <span className="px-1.5 py-0.5 bg-blue-50 border border-blue-200 rounded text-[10px] text-blue-700 font-bold">Qty: {item.qty}</span>}
                            {item.instructionLabel && <span className="px-1.5 py-0.5 bg-amber-50 border border-amber-200 rounded text-[10px] text-amber-700">{item.instructionLabel}</span>}
                          </div>
                          {item.remarks && <p className="text-[10px] text-gray-400 mt-1 italic">{item.remarks}</p>}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
