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
import { ClipboardList, RotateCw, Hospital, Stethoscope, Bed, Check, Pill, ChevronRight, Loader2 } from 'lucide-react'

import DatePicker from '../../../components/shared/DatePicker'
import { itemApi } from '../../../services/item/itemApi'
import { inventoryApi } from '../../../services/inventory/inventoryApi'
import { departmentApi } from '../../../services/config/departmentApi'
import { toast } from '../../../hooks/useToast'
import { Modal } from '../../../components/ui/Modal'

type TypeFilter = 'ALL' | 'OP' | 'IP'

export default function PrescriptionOrdersPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL')
  const [search, setSearch] = useState('')
  const [fromDate, setFromDate] = useState(() => new Date().toISOString().split('T')[0])
  const [toDate, setToDate] = useState(() => new Date().toISOString().split('T')[0])

  // Modal states
  const [selectedOrder, setSelectedOrder] = useState<PrescriptionOrderRow | null>(null)
  const [stockMap, setStockMap] = useState<Record<string, { availQty: number; loading: boolean }>>({})
  const [selectedItemIds, setSelectedItemIds] = useState<Record<string, boolean>>({})
  const [loadingStock, setLoadingStock] = useState(false)

  // Fetch departments to find Pharmacy Dept
  const { data: depts } = useQuery({
    queryKey: ['departments'],
    queryFn: () => departmentApi.getAll(),
  })

  useEffect(() => {
    queryClient.invalidateQueries({ queryKey: ['prescription-orders'] })
  }, [queryClient])

  const { data: orders = [], isLoading, refetch } = useQuery({
    queryKey: ['prescription-orders', typeFilter, fromDate, toDate],
    queryFn: () => prescriptionOrdersApi.getPending({
      type: typeFilter,
      fromDate: fromDate || undefined,
      toDate: toDate || undefined,
    }),
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
      // Single day: descending (latest first). Date range: ascending (oldest first).
      const isDateRange = fromDate !== toDate
      return isDateRange ? timeA - timeB : timeB - timeA
    })

  const totalItems = displayed.reduce((sum, o) => sum + (o.items?.length ?? 0), 0)

  const handleToggleSelectItem = (itemId: string) => {
    setSelectedItemIds(prev => ({
      ...prev,
      [itemId]: !prev[itemId]
    }))
  }

  const loadStockForOrder = async (order: PrescriptionOrderRow, deptId: string) => {
    setLoadingStock(true)
    const newStockMap: Record<string, { availQty: number; loading: boolean }> = {}

    const results = await Promise.all(order.items.map(async (item) => {
      let actualItemId = item.drugItemId
      if (!actualItemId && item.drugName) {
        try {
          const searchResults = await itemApi.search(item.drugName)
          if (searchResults.length > 0) {
            const searchName = item.drugName.trim().toLowerCase()
            const match = searchResults.find(r => r.name.trim().toLowerCase() === searchName) || searchResults[0]
            if (match) actualItemId = match.id
          }
        } catch (e) {
          console.error('Failed to search item name', e)
        }
      }

      let availQty = 0
      if (actualItemId) {
        try {
          const rawBatches = await inventoryApi.getAvailableBatches(actualItemId, deptId)
          const availableBatches = rawBatches.filter(b => !b.isExpired && (!b.expiryDate || new Date(b.expiryDate) > new Date()))
          availQty = availableBatches.reduce((sum, b) => sum + (b.currentQuantity ?? 0), 0)
        } catch (err) {
          console.error('Failed to load stock for', item.drugName, err)
        }
      }

      return { itemId: item.id, availQty }
    }))

    results.forEach(res => {
      newStockMap[res.itemId] = { availQty: res.availQty, loading: false }
    })

    setStockMap(newStockMap)
    setLoadingStock(false)
  }

  const handleViewOrder = (order: PrescriptionOrderRow) => {
    setSelectedOrder(order)
    
    // Initialize selectedItemIds: all initially selected
    const initialSelects: Record<string, boolean> = {}
    order.items.forEach(item => {
      initialSelects[item.id] = true
    })
    setSelectedItemIds(initialSelects)

    const pharmacyDept = depts?.find(d => d.name.toLowerCase().includes('pharmacy'))
    const deptId = pharmacyDept?.id

    if (deptId) {
      loadStockForOrder(order, deptId)
    } else {
      const emptyStockMap: Record<string, { availQty: number; loading: boolean }> = {}
      order.items.forEach(item => {
        emptyStockMap[item.id] = { availQty: 0, loading: false }
      })
      setStockMap(emptyStockMap)
      setLoadingStock(false)
    }
  }

  const handleAddSelectedToBill = () => {
    if (!selectedOrder) return

    const selectedIds = selectedOrder.items
      .filter(item => {
        return selectedItemIds[item.id]
      })
      .map(item => item.id)

    if (selectedIds.length === 0) {
      toast({ title: 'No items selected', description: 'Please select at least one item to add to the bill.', variant: 'destructive' })
      return
    }

    navigate(`/sales/sales?encounterId=${selectedOrder.encounterId}&patientId=${selectedOrder.patientId ?? ''}&prescribedAt=${encodeURIComponent(selectedOrder.prescribedAt ?? '')}&selectedItems=${selectedIds.join(',')}`)
    setSelectedOrder(null)
  }

  return (
    <div className="min-h-screen bg-gray-50/50">
      <div className="max-w-6xl mx-auto px-6 py-8 space-y-6">

        {/* Header */}
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
              <ClipboardList className="w-6 h-6 text-neutral-600" />
              <span>Prescribed Orders</span>
            </h1>
          </div>
          <button onClick={() => refetch()}
            className="px-3 py-2 text-sm font-medium text-gray-600 border border-gray-200 rounded-xl hover:bg-white transition-colors flex items-center gap-1.5">
            <RotateCw size={14} className="text-gray-500" /> Refresh
          </button>
        </div>

        {/* Filters */}
        <div className="bg-white border border-gray-200 rounded-2xl px-5 py-4 flex items-end gap-4 flex-wrap shadow-sm">
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
          <div className="w-36">
            <DatePicker
              value={fromDate}
              onChange={setFromDate}
              placeholder="From Date"
              clearable={true}
              maxDate={new Date().toISOString().split('T')[0]}
            />
          </div>
          <div className="w-36">
            <DatePicker
              value={toDate}
              onChange={setToDate}
              placeholder="To Date"
              clearable={true}
              maxDate={new Date().toISOString().split('T')[0]}
            />
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
              <div key={i} className="h-20 bg-white border border-gray-200 rounded-2xl animate-pulse" />
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
          <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden shadow-sm">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-100 text-xs font-bold text-gray-500 uppercase tracking-wider">
                  <th className="px-5 py-3">S.No</th>
                  <th className="px-5 py-3">Date</th>
                  <th className="px-5 py-3">Consultant</th>
                  <th className="px-5 py-3">Patient</th>
                  <th className="px-5 py-3">Visit</th>
                  <th className="px-5 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-sm">
                {displayed.map((order, idx) => (
                  <tr key={`${order.encounterId}-${idx}`} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-4 font-medium text-gray-900">{idx + 1}</td>
                    <td className="px-5 py-4 text-gray-600">
                      {order.prescribedAt ? formatDateTime(order.prescribedAt) : '—'}
                    </td>
                    <td className="px-5 py-4 font-medium text-gray-800" title={order.consultantFullName || order.consultantName || ''}>
                      {order.consultantName || '—'}
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex flex-col">
                        <span className="font-bold text-gray-900">{order.patientName}</span>
                        {order.patientNumber && (
                          <span className="text-xs text-gray-400 font-mono">{order.patientNumber}</span>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <span className={cn('inline-flex px-2.5 py-0.5 rounded-full text-[10px] font-bold border',
                        order.encounterType === 'INPATIENT'
                          ? 'bg-blue-50 text-blue-700 border-blue-200'
                          : 'bg-green-50 text-green-700 border-green-200')}>
                        {order.encounterType === 'INPATIENT' ? 'IP' : 'OP'}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-right">
                      {order.billed ? (
                        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-gray-100 text-gray-400 border border-gray-200 text-xs font-bold rounded-xl shadow-sm">
                          <Check size={14} className="text-gray-400 shrink-0" />
                          Billed
                        </span>
                      ) : (
                        <button
                          onClick={() => handleViewOrder(order)}
                          className="inline-flex items-center justify-center p-2 bg-neutral-600 hover:bg-neutral-700 text-white rounded-xl transition-colors shadow-sm"
                          title="View Order"
                        >
                          <ChevronRight size={16} />
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Prescription details popup / modal */}
      {selectedOrder && (
        <Modal
          isOpen={!!selectedOrder}
          onClose={() => setSelectedOrder(null)}
          size="4xl"
          title="Prescription Order"
        >
          {/* Modal Header */}
          <div className="px-5 py-3 border-b border-gray-100 flex justify-between items-center bg-gray-50/50">
            <h3 className="text-base font-bold text-gray-900 flex items-center gap-2">
              <ClipboardList className="w-5 h-5 text-neutral-600" />
              <span>Prescription Order</span>
            </h3>
          </div>

          {/* Modal Body */}
          <div className="p-4 space-y-4 overflow-y-auto flex-1 custom-scrollbar">
            {/* Metadata Details Grid */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-x-6 gap-y-2 p-3 bg-gray-50 border border-gray-150 rounded-xl text-xs">
              <div>
                <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block">Department</span>
                <span className="font-semibold text-gray-800">PHARMACY</span>
              </div>
              <div>
                <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block">Date</span>
                <span className="font-semibold text-gray-800">
                  {selectedOrder.prescribedAt ? formatDateTime(selectedOrder.prescribedAt) : '—'}
                </span>
              </div>
              <div>
                <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block">Consultant</span>
                <span className="font-semibold text-gray-800">
                  {selectedOrder.consultantName || '—'}
                </span>
              </div>
              <div className="md:col-span-3 border-t border-gray-200/60 pt-2 mt-0.5">
                <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block">Patient</span>
                <span className="font-bold text-gray-900">
                  {selectedOrder.patientName}
                </span>
                {selectedOrder.patientNumber && (
                  <span className="text-xs text-gray-500 font-mono ml-2">({selectedOrder.patientNumber})</span>
                )}
              </div>
            </div>

            {loadingStock ? (
              <div className="flex flex-col items-center justify-center py-20 border border-gray-200 rounded-2xl bg-white shadow-inner">
                <Loader2 className="w-8 h-8 animate-spin text-neutral-500 mb-2" />
                <p className="text-sm text-gray-500 font-medium">Checking inventory stock availability…</p>
              </div>
            ) : (
              /* Items Table */
              <div className="border border-gray-200 rounded-xl overflow-hidden shadow-inner bg-white">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-gray-50 border-b border-gray-100 text-xs font-bold text-gray-500 uppercase tracking-wider">
                      <th className="px-4 py-2.5">S.No</th>
                      <th className="px-4 py-2.5">Drug Name</th>
                      <th className="px-4 py-2.5">Frequency</th>
                      <th className="px-4 py-2.5">Duration</th>
                      <th className="px-4 py-2.5 text-center">Qty</th>
                      <th className="px-4 py-2.5 text-center">Avail Qty</th>
                      <th className="px-4 py-2.5 text-right">Select</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100 text-sm">
                    {selectedOrder.items.map((item, idx) => {
                      const stock = stockMap[item.id]
                      const hasStock = stock && stock.availQty > 0
                      const isChecked = !!selectedItemIds[item.id]

                      return (
                        <tr key={item.id} className="hover:bg-gray-50/50 transition-colors">
                          <td className="px-4 py-2.5 font-medium text-gray-400">{idx + 1}</td>
                          <td className="px-4 py-2.5 font-bold text-gray-900">{item.drugName}</td>
                          <td className="px-4 py-2.5 text-gray-600">{item.frequency || '—'}</td>
                          <td className="px-4 py-2.5 text-gray-600">{item.duration || '—'}</td>
                          <td className="px-4 py-2.5 text-center font-semibold text-gray-700">{item.qty}</td>
                          <td className="px-4 py-2.5 text-center font-bold">
                            {hasStock ? (
                              <span className="text-emerald-600">{stock.availQty}</span>
                            ) : (
                              <span className="text-red-500 font-normal">No stock</span>
                            )}
                          </td>
                          <td className="px-4 py-2.5 text-right">
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => handleToggleSelectItem(item.id)}
                              className="w-4 h-4 rounded border-gray-300 accent-neutral-600 focus:ring-neutral-500 cursor-pointer"
                            />
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Modal Footer */}
          <div className="px-5 py-3 border-t border-gray-100 bg-gray-50/50 flex justify-between items-center">
            <span className="text-xs text-gray-400 italic">
              * Select items to add to the pharmacy sales bill.
            </span>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setSelectedOrder(null)}
                className="px-5 py-2 text-sm font-bold text-gray-500 hover:text-gray-700 transition-colors border border-gray-200 rounded-xl bg-white shadow-sm"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleAddSelectedToBill}
                className="px-6 py-2 bg-neutral-600 text-white text-sm font-bold rounded-xl hover:bg-neutral-700 transition-colors flex items-center gap-1.5 shadow-md shadow-neutral-100"
              >
                <Pill size={14} className="text-white shrink-0" />
                Add to Bill
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
