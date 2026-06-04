import { useState, useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { salesReturnApi } from '../../../services/sales/salesReturnApi'
import { salesApi } from '../../../services/sales/salesApi'
import { inventoryApi } from '../../../services/inventory/inventoryApi'
import { patientApi } from '../../../services/patient/patientApi'
import { PatientSearchInput } from '../../../components/shared/PatientSearchInput'
import type { Patient } from '../../../types/patient'
import { toast } from '../../../hooks/useToast'
import DatePicker from '../../../components/shared/DatePicker'
import type { SalesReturn } from '../../../services/sales/salesReturnApi'

interface PurchasedItem {
  saleId: string
  saleNumber: string
  inventoryBatchId: string
  itemName: string
  batchNumber: string
  purchasedQty: number
  returnedQty: number
  availableQty: number
  unitRate: number
}

interface ReturnRow {
  saleId: string
  saleNumber: string
  inventoryBatchId: string
  itemName: string
  batchNumber: string
  purchasedQty: number
  returnedQty: number
  availableQty: number
  quantity: number
  unitRate: number
  subTotal: number
}

export default function SalesReturnPage() {
  const qc = useQueryClient()
  const [mode, setMode] = useState<'list' | 'add'>('list')
  const [selectedDate, setSelectedDate] = useState<string>(() => {
    const today = new Date()
    return today.toISOString().split('T')[0]
  })


  // Add view state
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null)
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [purchasedItems, setPurchasedItems] = useState<PurchasedItem[]>([])

  // Return list editor state
  const [returnRows, setReturnRows] = useState<ReturnRow[]>([])
  const [inlineQtyInputs, setInlineQtyInputs] = useState<Record<string, string>>({})
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Detail view state
  const [selectedReturn, setSelectedReturn] = useState<SalesReturn | null>(null)

  // Fetch returns list
  const { data: returns = [], isLoading } = useQuery({
    queryKey: ['salesReturns', selectedDate],
    queryFn: () => salesReturnApi.getByDate(selectedDate)
  })

  // Patient and Batch details lookup maps for the list and detail views
  const [resolvedPatients, setResolvedPatients] = useState<Record<string, Patient>>({})
  const [resolvedBatches, setResolvedBatches] = useState<Record<string, { itemName: string; batchNumber: string }>>({})

  // Resolve details in parallel
  useEffect(() => {
    if (returns.length === 0) return

    async function resolveDetails() {
      try {
        const uniquePatientIds = Array.from(new Set(returns.map(r => r.patientId).filter(Boolean))) as string[]
        const uniqueBatchIds = Array.from(new Set(returns.flatMap(r => r.lines.map(l => l.inventoryBatchId))))

        const patientMap = { ...resolvedPatients }
        const batchMap = { ...resolvedBatches }

        // Resolve Patients
        await Promise.all(
          uniquePatientIds.map(async id => {
            if (patientMap[id]) return
            try {
              const p = await patientApi.getById(id)
              patientMap[id] = p
            } catch (err) {
              // ignore
            }
          })
        )

        // Resolve Batches
        await Promise.all(
          uniqueBatchIds.map(async id => {
            if (batchMap[id]) return
            try {
              const b = await inventoryApi.getBatch(id)
              batchMap[id] = {
                itemName: b.itemName || 'Unknown Item',
                batchNumber: b.batchNumber || 'N/A'
              }
            } catch (err) {
              batchMap[id] = { itemName: 'Unknown Item', batchNumber: 'N/A' }
            }
          })
        )

        setResolvedPatients(patientMap)
        setResolvedBatches(batchMap)
      } catch (err) {
        // ignore
      }
    }

    resolveDetails()
  }, [returns])

  // Load patient purchase history
  useEffect(() => {
    if (!selectedPatient) {
      setPurchasedItems([])
      setReturnRows([])
      setInlineQtyInputs({})
      return
    }

    async function loadPatientSales() {
      setLoadingHistory(true)
      try {
        const sales = await salesApi.getByPatient(selectedPatient!.id)

        // Extract unique batch IDs and fetch details
        const uniqueBatchIds = Array.from(new Set(sales.flatMap(s => s.lines.map(l => l.inventoryBatchId))))
        const batchDetailsMap: Record<string, { itemName: string; batchNumber: string }> = {}

        await Promise.all(
          uniqueBatchIds.map(async id => {
            try {
              const b = await inventoryApi.getBatch(id)
              batchDetailsMap[id] = {
                itemName: b.itemName || 'Unknown Item',
                batchNumber: b.batchNumber || 'N/A'
              }
            } catch (err) {
              batchDetailsMap[id] = { itemName: 'Unknown Item', batchNumber: 'N/A' }
            }
          })
        )

        // Load existing returns for this patient
        const existingReturns = await salesReturnApi.getByPatient(selectedPatient!.id)
        const returnedQtyMap: Record<string, number> = {}
        existingReturns.forEach(ret => {
          ret.lines.forEach(line => {
            const key = `${ret.saleId}-${line.inventoryBatchId}`
            returnedQtyMap[key] = (returnedQtyMap[key] || 0) + line.quantity
          })
        })

        const items: PurchasedItem[] = []
        sales.forEach(sale => {
          // Calculate gross amount of sale to determine proportional discount
          const grossSaleAmount = sale.lines.reduce((sum, l) => sum + l.amount, 0)
          const discountRatio = grossSaleAmount > 0 ? (sale.totalAmount / grossSaleAmount) : 1

          // Re-map sale lines to show purchase details
          sale.lines.forEach(line => {
            const detail = batchDetailsMap[line.inventoryBatchId]
            const returnKey = `${sale.id}-${line.inventoryBatchId}`
            const returnedQty = returnedQtyMap[returnKey] || 0
            const availableQty = Math.max(0, line.quantity - returnedQty)

            // Deduct proportional discount from the unit rate
            const netUnitRate = line.unitRate * discountRatio

            items.push({
              saleId: sale.id,
              saleNumber: sale.sequenceNumber || 'SALE-DRAFT',
              inventoryBatchId: line.inventoryBatchId,
              itemName: detail?.itemName || 'Unknown Item',
              batchNumber: detail?.batchNumber || 'N/A',
              purchasedQty: line.quantity,
              returnedQty,
              availableQty,
              unitRate: netUnitRate
            })
          })
        })

        setPurchasedItems(items)
      } catch (err: any) {
        toast({ title: 'Failed to load purchase history', description: err.message, variant: 'destructive' })
      } finally {
        setLoadingHistory(false)
      }
    }

    loadPatientSales()
  }, [selectedPatient])



  // Add inline purchased item row to return rows
  const handleAddInlineRow = (item: PurchasedItem) => {
    const key = `${item.saleId}-${item.inventoryBatchId}`
    const inputQtyStr = inlineQtyInputs[key] || '1'
    const inputQty = parseInt(inputQtyStr) || 1

    if (inputQty <= 0) {
      toast({ title: 'Invalid Quantity', description: 'Quantity must be greater than zero', variant: 'destructive' })
      return
    }

    if (inputQty > item.availableQty) {
      toast({ title: 'Validation Error', description: 'Quantity is greater than Purchased quantity', variant: 'destructive' })
      return
    }

    // Check if already added
    const existing = returnRows.find(r => r.saleId === item.saleId && r.inventoryBatchId === item.inventoryBatchId)
    if (existing) {
      toast({ title: 'Already Added', description: 'This item is already added to the return list', variant: 'destructive' })
      return
    }

    const newRow: ReturnRow = {
      saleId: item.saleId,
      saleNumber: item.saleNumber,
      inventoryBatchId: item.inventoryBatchId,
      itemName: item.itemName,
      batchNumber: item.batchNumber,
      purchasedQty: item.purchasedQty,
      returnedQty: item.returnedQty,
      availableQty: item.availableQty,
      quantity: inputQty,
      unitRate: item.unitRate,
      subTotal: inputQty * item.unitRate
    }

    setReturnRows(prev => [...prev, newRow])
    setInlineQtyInputs(prev => ({ ...prev, [key]: '' }))
  }



  // Update return cart item quantity inline
  const handleUpdateRowQty = (idx: number, newQty: number) => {
    const row = returnRows[idx]
    let qty = newQty
    if (qty < 0) qty = 0
    if (qty > row.availableQty) {
      qty = row.availableQty
    }

    setReturnRows(prev => prev.map((r, i) => {
      if (i !== idx) return r
      return {
        ...r,
        quantity: qty,
        subTotal: qty * r.unitRate
      }
    }))
  }

  // Remove row
  const handleRemoveRow = (idx: number) => {
    setReturnRows(prev => prev.filter((_, i) => i !== idx))
  }

  // Submit return — group by saleId and call API once per group, show one toast total
  const handleSubmitReturn = async () => {
    if (returnRows.length === 0) {
      toast({ title: 'Validation Error', description: 'Please add at least one item to return.', variant: 'destructive' })
      return
    }

    // Group return rows by saleId
    const groupedBySale: Record<string, Array<{ inventoryBatchId: string; quantity: number }>> = {}
    returnRows.forEach(r => {
      if (!groupedBySale[r.saleId]) groupedBySale[r.saleId] = []
      groupedBySale[r.saleId].push({ inventoryBatchId: r.inventoryBatchId, quantity: r.quantity })
    })

    setIsSubmitting(true)
    try {
      for (const saleId of Object.keys(groupedBySale)) {
        await salesReturnApi.create({ saleId, lines: groupedBySale[saleId] })
      }
      toast({ title: 'Sales Return saved successfully', variant: 'success' })
      qc.invalidateQueries({ queryKey: ['salesReturns'] })
      setMode('list')
      setSelectedPatient(null)
      setReturnRows([])
      setInlineQtyInputs({})
    } catch (err: any) {
      toast({ title: 'Error saving return', description: err.message, variant: 'destructive' })
    } finally {
      setIsSubmitting(false)
    }
  }

  // Compute overall total refund amount
  const getOverallTotal = () => {
    let total = returnRows.reduce((sum, r) => sum + r.subTotal, 0)

    // Add any non-added inline inputs
    purchasedItems.forEach(item => {
      const key = `${item.saleId}-${item.inventoryBatchId}`
      const qtyStr = inlineQtyInputs[key]
      if (qtyStr) {
        const qty = parseInt(qtyStr) || 0
        const alreadyAdded = returnRows.some(r => r.saleId === item.saleId && r.inventoryBatchId === item.inventoryBatchId)
        if (qty > 0 && !alreadyAdded) {
          total += qty * item.unitRate
        }
      }
    })

    return total
  }



  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-white border border-gray-200 rounded-xl p-4 shadow-sm">
        <div>
          <h2 className="text-xl font-extrabold text-gray-900 tracking-tight flex items-center gap-2">
            Sales Return
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">Manage and track customer sales returns</p>
        </div>

        {(mode === 'list' && !selectedReturn) && (
          <div className="flex flex-wrap items-center gap-3 w-full md:w-auto">
            <div className="relative w-40">
              <DatePicker
                value={selectedDate}
                onChange={val => setSelectedDate(val || new Date().toISOString().split('T')[0])}
                size="sm"
              />
            </div>

            {/* <select
              value={selectedDeptId}
              onChange={e => setSelectedDeptId(e.target.value)}
              className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm bg-white font-medium text-gray-700 shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all cursor-pointer"
            >
              <option value={DEMO_DEPT_ID}>PHARMACY</option>
            </select> */}

            <button
              onClick={() => {
                setMode('add')
                setSelectedPatient(null)
                setReturnRows([])
                setInlineQtyInputs({})
              }}
              className="ml-auto md:ml-0 bg-gray-900 hover:bg-gray-800 text-white font-semibold text-xs px-4 py-2 rounded-lg shadow-sm hover:shadow transition-all duration-200 flex items-center gap-1.5 uppercase tracking-wider"
            >
              <span>+</span> Sales Return
            </button>
          </div>
        )}

        {(mode === 'add' || selectedReturn) && (
          <button
            onClick={() => {
              setMode('list')
              setSelectedReturn(null)
            }}
            className="flex items-center gap-1.5 text-xs font-bold text-gray-500 hover:text-gray-900 bg-gray-100 hover:bg-gray-200/80 px-3 py-2 rounded-lg transition-all uppercase tracking-wider"
          >
            <span>&lt;</span> Sales Return
          </button>
        )}
      </div>

      {selectedReturn ? (
        /* Sales Return Detail View (Matching 2nd Image) */
        <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm space-y-6 max-w-6xl mx-auto">
          {/* Header */}
          {/* <div className="flex items-center justify-between border-b border-gray-100 pb-4">
            <button 
              onClick={() => setSelectedReturn(null)}
              className="text-gray-500 hover:text-gray-800 text-sm font-bold flex items-center gap-1 transition-colors uppercase tracking-wider"
            >
              &lsaquo; SALES RETURN
            </button>
            <h2 className="text-xl font-extrabold text-gray-800 tracking-tight">Sales Return</h2>
          </div> */}

          {/* Info Row */}
          <div className="grid grid-cols-3 gap-6 bg-gray-50/50 p-4 rounded-xl border border-gray-100 text-sm">
            <div>
              <span className="font-bold text-gray-700">Department :</span> <span className="text-gray-600 font-medium ml-1">PHARMACY</span>
            </div>
            <div>
              <span className="font-bold text-gray-700">Date :</span> <span className="text-gray-600 font-medium ml-1">{new Date(selectedReturn.returnDate).toLocaleDateString('en-GB')}</span>
            </div>
            <div>
              <span className="font-bold text-gray-700">Return By :</span> <span className="text-gray-600 font-medium ml-1"></span>
            </div>
          </div>

          {/* Lines Table */}
          <div className="border border-gray-200 rounded-xl overflow-hidden shadow-sm">
            <table className="w-full text-sm text-left text-gray-500" aria-label="Return Lines Details">
              <thead className="text-xs text-gray-400 uppercase bg-gray-50/50 border-b border-gray-100">
                <tr>
                  <th className="px-4 py-3 font-bold text-center w-16">S.No.</th>
                  <th className="px-4 py-3 font-bold">Item</th>
                  <th className="px-4 py-3 font-bold">Batch No</th>
                  <th className="px-4 py-3 font-bold text-right">Price</th>
                  <th className="px-4 py-3 font-bold text-right w-24">R.Qty</th>
                  <th className="px-4 py-3 font-bold text-right w-32">Sub Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-gray-700">
                {selectedReturn.lines.map((line, idx) => {
                  const b = resolvedBatches[line.inventoryBatchId]
                  const returnAmount = line.returnAmount || 0
                  const price = line.quantity > 0 ? (returnAmount / line.quantity) : 0
                  return (
                    <tr key={idx} className="hover:bg-gray-50/20 transition-colors">
                      <td className="px-4 py-3 text-center font-bold text-gray-400 text-xs">{idx + 1}</td>
                      <td className="px-4 py-3 font-semibold text-gray-900 uppercase">{b?.itemName || 'Loading...'}</td>
                      <td className="px-4 py-3 font-mono text-xs">{b?.batchNumber || 'N/A'}</td>
                      <td className="px-4 py-3 text-right font-medium tabular-nums">{Math.round(price).toLocaleString()}</td>
                      <td className="px-4 py-3 text-right font-bold text-gray-900">{line.quantity}</td>
                      <td className="px-4 py-3 text-right font-bold text-gray-900 tabular-nums">
                        {Math.round(returnAmount).toLocaleString()}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* Total */}
          <div className="flex justify-end text-sm pr-4">
            <div className="flex gap-4">
              <span className="text-gray-500 font-bold uppercase tracking-wider">Total :</span>
              <span className="font-extrabold text-gray-900 tabular-nums">₹{Math.round(selectedReturn.totalReturnAmount).toLocaleString()}</span>
            </div>
          </div>

          {/* Customer Information */}
          {(() => {
            const patient = selectedReturn.patientId ? resolvedPatients[selectedReturn.patientId] : null
            return (
              <div className="pt-6 border-t border-gray-100 space-y-4">
                <h3 className="text-sm font-extrabold text-gray-800 tracking-wide uppercase">Customer Information</h3>
                <div className="grid grid-cols-2 gap-x-12 gap-y-4 text-sm bg-gray-50/30 p-4 rounded-xl border border-gray-100/50">
                  <div className="grid grid-cols-3">
                    <span className="font-bold text-gray-500 uppercase text-xs">Name</span>
                    <span className="col-span-2 text-gray-900 font-semibold uppercase">{patient?.fullName || 'Walk-in'}</span>
                  </div>
                  <div className="grid grid-cols-3">
                    <span className="font-bold text-gray-500 uppercase text-xs">Contact No</span>
                    <span className="col-span-2 text-gray-900 font-mono font-semibold">{patient?.contactNumber || 'N/A'}</span>
                  </div>
                  <div className="grid grid-cols-3 items-start col-span-2">
                    <span className="font-bold text-gray-500 uppercase text-xs pt-0.5">Address</span>
                    <span className="col-span-2 text-gray-700 font-medium leading-relaxed">{patient?.address || 'N/A'}</span>
                  </div>
                </div>
              </div>
            )
          })()}
        </div>
      ) : mode === 'list' ? (
        /* List View */
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
          {isLoading ? (
            <div className="flex flex-col items-center justify-center py-20 space-y-3">
              <div className="w-8 h-8 border-4 border-blue-100 border-t-blue-600 rounded-full animate-spin" />
              <p className="text-xs font-semibold text-gray-400">Loading returns list...</p>
            </div>
          ) : returns.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <div className="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center mb-4 text-2xl">📦</div>
              <h3 className="text-sm font-bold text-gray-800 mb-1">No Sales Return Found !</h3>
              <p className="text-xs text-gray-400 max-w-[280px]">
                There is no sales return on {new Date(selectedDate).toLocaleDateString('en-GB')}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left text-gray-500" aria-label="Sales Returns List">
                <thead className="text-xs text-gray-400 uppercase bg-gray-50/50 border-b border-gray-100">
                  <tr>
                    <th className="px-6 py-4 font-bold">Return No</th>
                    <th className="px-6 py-4 font-bold">Customer Name</th>
                    <th className="px-6 py-4 font-bold">Customer Ph</th>
                    <th className="px-6 py-4 font-bold">Department</th>
                    <th className="px-6 py-4 font-bold">Date</th>
                    <th className="px-6 py-4 font-bold text-right">Total Amt</th>
                    <th className="px-6 py-4 font-bold text-center w-24">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 text-gray-700">
                  {returns.map((ret) => {
                    const patient = ret.patientId ? resolvedPatients[ret.patientId] : null
                    return (
                      <tr key={ret.id} className="hover:bg-gray-50/50 transition-colors">
                        <td className="px-6 py-4 font-mono font-bold text-xs text-gray-900">{ret.sequenceNumber}</td>
                        <td className="px-6 py-4 text-xs font-bold text-gray-900 uppercase">{patient?.fullName || 'Walk-in'}</td>
                        <td className="px-6 py-4 text-xs font-mono font-medium">{patient?.contactNumber || 'N/A'}</td>
                        <td className="px-6 py-4 text-xs font-bold text-blue-600">PHARMACY</td>
                        <td className="px-6 py-4 text-xs font-medium">{new Date(ret.returnDate).toLocaleDateString('en-GB')}</td>
                        <td className="px-6 py-4 text-right font-bold text-gray-900">₹{Math.round(ret.totalReturnAmount).toLocaleString()}</td>
                        <td className="px-6 py-4 text-center">
                          <button
                            onClick={() => setSelectedReturn(ret)}
                            className="bg-gray-50 hover:bg-gray-100 text-gray-700 border border-gray-200 hover:text-gray-900 transition-all font-bold px-3 py-1.5 rounded-lg shadow-sm hover:shadow text-xs"
                            title="View Return Details"
                          >
                            &rsaquo;
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : (
        /* Create Return View */
        <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
           <div>
              <label className="block text-[10px] uppercase font-bold text-gray-400 mb-2 tracking-widest">Patient</label>
              {!selectedPatient ? (
                <PatientSearchInput
                  selectedPatient={selectedPatient}
                  onSelect={setSelectedPatient}
                  placeholder="Search with Patient Id \ Name \ Phone No"
                  className="shadow-sm"
                />
              ) : (
                <div className="flex items-center justify-between border border-blue-100 bg-blue-50/50 text-blue-700 px-4 py-2 rounded-lg">
                  <div className="flex items-center gap-2">
                    <span className="text-sm">👤</span>
                    <span className="text-xs font-bold uppercase tracking-tight">{selectedPatient.fullName}</span>
                    <span className="text-[10px] font-mono text-blue-400">#{selectedPatient.patientNumber}</span>
                  </div>
                  <button
                    onClick={() => {
                      setSelectedPatient(null)
                      setReturnRows([])
                      setInlineQtyInputs({})
                    }}
                    className="text-blue-400 hover:text-blue-700 font-bold transition-colors"
                  >
                    ×
                  </button>
                </div>
              )}
            </div>
          </div>

          {selectedPatient && (
            <div className="space-y-6 pt-4 border-t border-gray-100">
              {loadingHistory ? (
                <div className="flex flex-col items-center justify-center py-10 space-y-2">
                  <div className="w-6 h-6 border-2 border-blue-200 border-t-blue-600 rounded-full animate-spin" />
                  <p className="text-xs text-gray-400">Fetching purchased items history...</p>
                </div>
              ) : purchasedItems.length === 0 ? (
                <div className="text-center py-8 text-gray-400 text-xs">
                  No pharmacy purchase history found for this patient.
                </div>
              ) : (
                <div className="space-y-8">
                  {/* Inline Purchased Items Table */}
                  <div className="space-y-2">
                    <label className="block text-[10px] uppercase font-bold text-gray-400 tracking-widest">Purchased Items History</label>
                    <div className="border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                      <table className="w-full text-sm text-left text-gray-500" aria-label="Purchased Items List">
                        <thead className="text-xs text-gray-400 uppercase bg-gray-50/50 border-b border-gray-100">
                          <tr>
                            <th className="px-4 py-3 font-bold">Item Name</th>
                            <th className="px-4 py-3 font-bold">Sales No</th>
                            <th className="px-4 py-3 font-bold">Batch No</th>
                            <th className="px-4 py-3 font-bold text-right">P.QTY</th>
                            <th className="px-4 py-3 font-bold text-right">R.QTY</th>
                            <th className="px-4 py-3 font-bold text-right">A.QTY</th>
                            <th className="px-4 py-3 font-bold text-right w-24">R.QTY</th>
                            <th className="px-4 py-3 font-bold text-right w-32">Sub Total</th>
                            <th className="px-4 py-3 font-bold text-center w-16">Action</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 text-gray-700">
                          {purchasedItems.map((item) => {
                            const key = `${item.saleId}-${item.inventoryBatchId}`
                            const qtyStr = inlineQtyInputs[key] || ''
                            const qtyNum = parseInt(qtyStr) || 0
                            const subTotal = qtyNum * item.unitRate
                            const isAlreadyAdded = returnRows.some(r => r.saleId === item.saleId && r.inventoryBatchId === item.inventoryBatchId)

                            return (
                              <tr key={key} className={`hover:bg-gray-50/20 transition-colors ${isAlreadyAdded ? 'opacity-40' : ''}`}>
                                <td className="px-4 py-3 font-semibold text-gray-900">{item.itemName}</td>
                                <td className="px-4 py-3 font-mono text-xs">{item.saleNumber}</td>
                                <td className="px-4 py-3 font-mono text-xs">{item.batchNumber}</td>
                                <td className="px-4 py-3 text-right font-medium">{item.purchasedQty}</td>
                                <td className="px-4 py-3 text-right font-medium text-gray-400">{item.returnedQty}</td>
                                <td className="px-4 py-3 text-right font-medium text-gray-600">{item.availableQty}</td>
                                <td className="px-4 py-3 text-right">
                                  <input
                                    type="number"
                                    min="1"
                                    placeholder="Qty"
                                    value={qtyStr}
                                    disabled={isAlreadyAdded || item.availableQty <= 0}
                                    onChange={e => {
                                      const valStr = e.target.value
                                      if (valStr === '') {
                                        setInlineQtyInputs(prev => ({ ...prev, [key]: '' }))
                                        return
                                      }
                                      if (valStr.startsWith('-') || valStr.includes('-')) {
                                        setInlineQtyInputs(prev => ({ ...prev, [key]: '0' }))
                                        return
                                      }
                                      let val = parseInt(valStr)
                                      if (isNaN(val)) return
                                      if (val < 0) val = 0
                                      if (val > item.availableQty) {
                                        val = item.availableQty
                                      }
                                      setInlineQtyInputs(prev => ({
                                        ...prev,
                                        [key]: val.toString()
                                      }))
                                    }}
                                    className="w-16 px-2 py-1 border border-gray-200 rounded text-right text-xs focus:outline-none focus:ring-1 focus:ring-blue-500 disabled:bg-gray-50 disabled:cursor-not-allowed"
                                  />
                                </td>
                                <td className="px-4 py-3 text-right font-semibold text-gray-900 tabular-nums">
                                  {qtyNum > 0 ? `₹${Math.round(subTotal).toLocaleString()}` : ''}
                                </td>
                                <td className="px-4 py-3 text-center">
                                  <button
                                    onClick={() => handleAddInlineRow(item)}
                                    disabled={isAlreadyAdded || qtyNum <= 0 || item.availableQty <= 0}
                                    className="text-blue-600 hover:text-blue-800 disabled:text-gray-300 disabled:cursor-not-allowed font-bold p-1 text-base transition-colors leading-none"
                                    title={isAlreadyAdded ? 'Already added to return list' : 'Add Item to Return'}
                                  >
                                    +
                                  </button>
                                </td>
                              </tr>
                            )
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  {/* Return cart table */}
                  <div className="space-y-4 pt-4 border-t border-gray-100">
                    <label className="block text-[10px] uppercase font-bold text-gray-400 tracking-widest">Return Item Lines</label>
                    <div className="border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                      <table className="w-full text-sm text-left text-gray-500" aria-label="Return Item Lines">
                        <thead className="text-xs text-gray-400 uppercase bg-gray-50/50 border-b border-gray-100">
                          <tr>
                            <th className="px-4 py-3 font-bold text-center w-12">S.No.</th>
                            <th className="px-4 py-3 font-bold">Item</th>
                            <th className="px-4 py-3 font-bold">Sales No</th>
                            <th className="px-4 py-3 font-bold">Batch No</th>
                            <th className="px-4 py-3 font-bold text-right">P.QTY</th>
                            <th className="px-4 py-3 font-bold text-right">R.QTY</th>
                            <th className="px-4 py-3 font-bold text-right">A.QTY</th>
                            <th className="px-4 py-3 font-bold text-right w-24">R.QTY</th>
                            <th className="px-4 py-3 font-bold text-right w-32">Sub Total</th>
                            <th className="px-4 py-3 font-bold text-center w-16">Action</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 text-gray-700">
                          {returnRows.map((row, idx) => (
                            <tr key={idx} className="hover:bg-gray-50/20 transition-colors">
                              <td className="px-4 py-3 text-center font-bold text-gray-400 text-xs">{idx + 1}</td>
                              <td className="px-4 py-3 font-semibold text-gray-900">{row.itemName}</td>
                              <td className="px-4 py-3 font-mono text-xs">{row.saleNumber}</td>
                              <td className="px-4 py-3 font-mono text-xs">{row.batchNumber}</td>
                              <td className="px-4 py-3 text-right font-medium">{row.purchasedQty}</td>
                              <td className="px-4 py-3 text-right font-medium text-gray-400">{row.returnedQty}</td>
                              <td className="px-4 py-3 text-right font-medium text-gray-600">{row.availableQty}</td>
                              <td className="px-4 py-3 text-right">
                                <input
                                  type="number"
                                  min="1"
                                  max={row.availableQty}
                                  value={row.quantity}
                                  onChange={e => {
                                    const valStr = e.target.value
                                    if (valStr === '') {
                                      handleUpdateRowQty(idx, 0)
                                      return
                                    }
                                    if (valStr.startsWith('-') || valStr.includes('-')) {
                                      handleUpdateRowQty(idx, 0)
                                      return
                                    }
                                    const val = parseInt(valStr)
                                    if (isNaN(val)) return
                                    handleUpdateRowQty(idx, val)
                                  }}
                                  className="w-16 px-2 py-1 border border-gray-200 rounded text-right text-xs font-bold text-blue-600 focus:outline-none focus:ring-1 focus:ring-blue-500"
                                />
                              </td>
                              <td className="px-4 py-3 text-right font-bold text-gray-900 tabular-nums">
                                ₹{Math.round(row.subTotal).toLocaleString()}
                              </td>
                              <td className="px-4 py-3 text-center">
                                <button
                                  onClick={() => handleRemoveRow(idx)}
                                  className="text-red-500 hover:text-red-700 font-bold px-2 py-1 text-sm rounded hover:bg-red-50 transition-colors"
                                >
                                  ×
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    {/* Summary and submit action */}
                    <div className="flex flex-col items-end gap-3 pt-4 border-t border-gray-100">
                      <div className="flex items-baseline gap-2 text-sm">
                        <span className="text-gray-500 font-semibold uppercase tracking-wider text-xs">Total :</span>
                        <span className="text-lg font-bold text-gray-900 tabular-nums">₹{Math.round(getOverallTotal()).toLocaleString()}</span>
                      </div>

                      <div className="flex gap-3">
                        <button
                          onClick={() => {
                            setMode('list')
                            setSelectedPatient(null)
                            setReturnRows([])
                            setInlineQtyInputs({})
                          }}
                          className="px-4 py-2 border border-gray-200 text-gray-600 hover:bg-gray-50 font-bold text-xs uppercase tracking-wider rounded-lg transition-colors"
                        >
                          Cancel
                        </button>
                        <button
                          onClick={handleSubmitReturn}
                          disabled={isSubmitting || returnRows.length === 0}
                          className="px-6 py-2 bg-[#707070] hover:bg-[#5a5a5a] disabled:bg-gray-300  text-white font-bold text-xs uppercase tracking-wider rounded-lg transition-colors shadow-sm"
                        >
                          {isSubmitting ? 'Refunding...' : 'Refund'}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
