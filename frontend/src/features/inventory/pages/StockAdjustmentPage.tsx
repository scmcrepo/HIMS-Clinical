import { useState, useEffect, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { stockAdjustmentApi } from '../../../services/inventory/stockAdjustmentApi'
import { departmentApi } from '../../../services/config/departmentApi'
import { inventoryApi } from '../../../services/inventory/inventoryApi'
import { MedicineSearchInput } from '../../../components/shared/MedicineSearchInput'
import type { InventoryItem, InventoryBatch } from '../../../types/inventory'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'
import { 
  FileSpreadsheet, Calendar, 
  Layers, HelpCircle, Eye, X, ArrowUpRight, ArrowDownRight,
  ChevronDown, Search
} from 'lucide-react'

interface FormLine {
  tempId: string
  itemId: string
  itemName: string
  inventoryBatchId: string
  batches: InventoryBatch[]
  currentQty: number
  adjustmentQty: number | ''
  adjustmentType: 'ADD' | 'SUBTRACT' | ''
  reason: string
}

export default function StockAdjustmentPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<'history' | 'new'>('history')
  const [selectedAdjustmentId, setSelectedAdjustmentId] = useState<string | null>(null)
  const [openBatchPickerIndex, setOpenBatchPickerIndex] = useState<number | null>(null)
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const size = 10

  // 1. Fetch departments
  const { data: depts } = useQuery({
    queryKey: ['departments'],
    queryFn: () => departmentApi.getAll(),
  })

  // 2. Fetch stock adjustments history
  const { data: pageData, isLoading: isLoadingHistory } = useQuery({
    queryKey: ['stockAdjustments', search, page],
    queryFn: () => stockAdjustmentApi.getAll(search, page, size),
    enabled: tab === 'history',
  })

  const adjustments = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0
  const totalElements = pageData?.totalElements ?? 0

  // 3. Fetch details of a selected adjustment
  const { data: selectedAdjustment } = useQuery({
    queryKey: ['stockAdjustment', selectedAdjustmentId],
    queryFn: () => stockAdjustmentApi.getById(selectedAdjustmentId!),
    enabled: !!selectedAdjustmentId,
  })

  // Form states
  const [selectedDeptId, setSelectedDeptId] = useState<string>('')
  const [notes, setNotes] = useState<string>('')
  const [lines, setLines] = useState<FormLine[]>([
    {
      tempId: Math.random().toString(),
      itemId: '',
      itemName: '',
      inventoryBatchId: '',
      batches: [],
      currentQty: 0,
      adjustmentQty: '',
      adjustmentType: '',
      reason: ''
    }
  ])

  // Form validity check (active items must have item, batch, adjustmentType, and valid qty)
  const isFormValid = useMemo(() => {
    const filledLines = lines.filter(l => l.itemId)
    if (filledLines.length === 0) return false
    return filledLines.every(l => 
      l.inventoryBatchId && 
      l.adjustmentType && 
      l.adjustmentQty !== '' && 
      Number(l.adjustmentQty) > 0 &&
      (l.adjustmentType !== 'SUBTRACT' || Number(l.adjustmentQty) <= l.currentQty)
    )
  }, [lines])

  // Try to pre-select Pharmacy department
  useEffect(() => {
    if (depts && depts.length > 0 && !selectedDeptId) {
      const pharmacy = depts.find(d => d.name.toLowerCase().includes('pharmacy'))
      if (pharmacy) {
        setSelectedDeptId(pharmacy.id)
      } else {
        setSelectedDeptId(depts[0].id)
      }
    }
  }, [depts, selectedDeptId])

  // Mutation for creating adjustment
  const createMutation = useMutation({
    mutationFn: (payload: any) => stockAdjustmentApi.create(payload),
    onSuccess: () => {
      setSearch('')
      setPage(0)
      qc.invalidateQueries({ queryKey: ['stockAdjustments'] })
      qc.invalidateQueries({ queryKey: ['inventory'] })
      toast({ title: 'Stock adjustment completed successfully', variant: 'success' })
      resetForm()
      setTab('history')
    },
    onError: (err: any) => {
      toast({ title: 'Failed to save stock adjustment', description: err.message, variant: 'destructive' })
    }
  })

  const resetForm = () => {
    setNotes('')
    setLines([
      {
        tempId: Math.random().toString(),
        itemId: '',
        itemName: '',
        inventoryBatchId: '',
        batches: [],
        currentQty: 0,
        adjustmentQty: '',
        adjustmentType: '',
        reason: ''
      }
    ])
  }

  const handleMedicineSelect = async (item: InventoryItem, index: number) => {
    try {
      if (!selectedDeptId) {
        toast({ title: 'Validation Error', description: 'Please select a department first.', variant: 'destructive' })
        return
      }

      // Fetch active batches in department
      const allBatches = await inventoryApi.getAvailableBatches(item.id, selectedDeptId)
      if (allBatches.length === 0) {
        toast({
          title: 'No stock available',
          description: `There are no active stock batches for "${item.name}" in this department. To adjust stock, please receive a batch first.`,
          variant: 'destructive'
        })
        return
      }

      const defaultBatch = allBatches[0]
      setLines(prev => prev.map((l, i) => {
        if (i !== index) return l
        return {
          ...l,
          itemId: item.id,
          itemName: item.name,
          batches: allBatches,
          inventoryBatchId: defaultBatch.id,
          currentQty: defaultBatch.currentQuantity,
          adjustmentQty: 1,
          adjustmentType: ''
        }
      }))
    } catch (err) {
      toast({ title: 'Error fetching batches', variant: 'destructive' })
    }
  }

  const handleBatchSelect = (batchId: string, index: number) => {
    setLines(prev => prev.map((l, i) => {
      if (i !== index) return l
      const matched = l.batches.find(b => b.id === batchId)
      return {
        ...l,
        inventoryBatchId: batchId,
        currentQty: matched ? matched.currentQuantity : 0
      }
    }))
  }

  const handleQtyChange = (valStr: string, index: number) => {
    setLines(prev => prev.map((l, i) => {
      if (i !== index) return l
      if (valStr === '') return { ...l, adjustmentQty: '' }
      const val = parseInt(valStr)
      if (isNaN(val) || val < 1) return { ...l, adjustmentQty: 1 }
      return { ...l, adjustmentQty: val }
    }))
  }

  const handleLineFieldChange = (key: keyof FormLine, value: any, index: number) => {
    setLines(prev => prev.map((l, i) => {
      if (i !== index) return l
      return { ...l, [key]: value }
    }))
  }

  const addLine = (index: number) => {
    const currentLine = lines[index]
    if (!currentLine.itemId || !currentLine.inventoryBatchId || currentLine.adjustmentQty === '') {
      toast({ 
        title: 'Validation Error', 
        description: 'Please select a medicine, a batch, and enter a quantity before adding another item.', 
        variant: 'destructive' 
      })
      return
    }

    setLines(prev => [
      ...prev,
      {
        tempId: Math.random().toString(),
        itemId: '',
        itemName: '',
        inventoryBatchId: '',
        batches: [],
        currentQty: 0,
        adjustmentQty: '',
        adjustmentType: '',
        reason: ''
      }
    ])
  }

  const removeLine = (index: number) => {
    if (lines.length > 1) {
      setLines(prev => prev.filter((_, i) => i !== index))
    } else {
      resetForm()
    }
  }

  const handleSubmit = () => {
    if (!selectedDeptId) {
      toast({ title: 'Validation Error', description: 'Please select a department.', variant: 'destructive' })
      return
    }

    const validLines = lines.filter(l => l.itemId && l.inventoryBatchId)
    if (validLines.length === 0) {
      toast({ title: 'Validation Error', description: 'Please add at least one line item with a selected batch.', variant: 'destructive' })
      return
    }

    const errors: string[] = []
    validLines.forEach((l, idx) => {
      const rowNo = idx + 1
      if (!l.adjustmentType) {
        errors.push(`Row ${rowNo}: Please select an adjustment type (Add or Subtract).`)
      } else if (l.adjustmentQty === '' || l.adjustmentQty <= 0) {
        errors.push(`Row ${rowNo}: Adjustment quantity must be greater than zero.`)
      } else if (l.adjustmentType === 'SUBTRACT' && l.adjustmentQty > l.currentQty) {
        errors.push(`Row ${rowNo}: Subtraction quantity (${l.adjustmentQty}) cannot exceed current quantity (${l.currentQty}).`)
      }
    })

    if (errors.length > 0) {
      toast({ title: 'Validation Error', description: errors[0], variant: 'destructive' })
      return
    }

    createMutation.mutate({
      departmentId: selectedDeptId,
      notes: notes.trim() || undefined,
      lines: validLines.map(l => ({
        inventoryBatchId: l.inventoryBatchId,
        adjustmentQty: Number(l.adjustmentQty),
        adjustmentType: l.adjustmentType,
        reason: l.reason.trim() || undefined
      }))
    })
  }

  const inputCls = "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500 focus:border-neutral-500 transition-all bg-white"

  return (
    <div className="space-y-6 max-w-6xl mx-auto">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-gray-150 pb-5">
        <div>
          <h2 className="text-2xl font-extrabold text-gray-900 tracking-tight flex items-center gap-2">
            <FileSpreadsheet className="w-6 h-6 text-neutral-600" />
            Stock Adjustment
          </h2>
          <p className="text-sm text-gray-500 mt-1">
            Create and manage stock adjustments to reconcile inventory counts.
          </p>
        </div>

        {/* Tab Controls */}
        <div className="flex bg-gray-100 p-1 rounded-xl">
          <button
            onClick={() => { setTab('history'); setSelectedAdjustmentId(null) }}
            className={cn(
              "px-4 py-2 text-sm font-semibold rounded-lg transition-all",
              tab === 'history' ? "bg-white text-neutral-800 shadow-sm" : "text-gray-500 hover:text-gray-900"
            )}
          >
            History List
          </button>
          <button
            onClick={() => setTab('new')}
            className={cn(
              "px-4 py-2 text-sm font-semibold rounded-lg transition-all",
              tab === 'new' ? "bg-white text-neutral-800 shadow-sm" : "text-gray-500 hover:text-gray-900"
            )}
          >
            Create Adjustment
          </button>
        </div>
      </div>

      {/* ── History List View ── */}
      {tab === 'history' && (
        <div className="space-y-4 animate-in fade-in duration-200">
          {/* Search Box */}
          <div className="bg-white border border-gray-200 rounded-2xl p-4 shadow-sm flex items-center gap-3">
            <div className="relative flex-1 max-w-md">
              <input
                type="text"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setPage(0)
                }}
                placeholder="Search by Correction No. (e.g. ADJ-0001)..."
                className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500 focus:border-neutral-500 transition-all bg-white"
              />
              <Search className="w-4 h-4 text-gray-400 absolute left-3 top-3" />
            </div>
          </div>

          <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden flex flex-col">
            {isLoadingHistory ? (
              <div className="flex flex-col items-center justify-center p-12 space-y-3">
                <div className="w-8 h-8 border-3 border-gray-200 border-t-gray-700 rounded-full animate-spin" />
                <p className="text-sm text-gray-500 font-medium">Loading history...</p>
              </div>
            ) : !adjustments || adjustments.length === 0 ? (
              <div className="flex flex-col items-center justify-center p-16 text-center">
                <div className="w-16 h-16 bg-gray-50 rounded-2xl flex items-center justify-center border border-gray-100 mb-4">
                  <HelpCircle className="w-8 h-8 text-gray-400" />
                </div>
                <h3 className="text-base font-bold text-gray-900">No adjustments found</h3>
                <p className="text-sm text-gray-400 mt-1 max-w-sm">
                  {search ? 'No adjustment records match your search criteria.' : 'No stock adjustment records have been logged yet. Click "Create Adjustment" to log your first correction.'}
                </p>
              </div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm text-left">
                    <thead className="bg-gray-50 border-b border-gray-100 text-xs text-gray-400 uppercase font-bold tracking-wider">
                      <tr>
                        <th className="px-6 py-4 w-16 text-left">S.No.</th>
                        <th className="px-6 py-4">Correction No.</th>
                        <th className="px-6 py-4">Date</th>
                        <th className="px-6 py-4">Department</th>
                        <th className="px-6 py-4">Remarks</th>
                        <th className="px-6 py-4 text-center">Items</th>
                        <th className="px-6 py-4 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {adjustments.map((adj, idx) => (
                        <tr key={adj.id} className="hover:bg-neutral-50/50 transition-colors">
                          <td className="px-6 py-4 font-semibold text-gray-500 tabular-nums">
                            {(page * size) + idx + 1}
                          </td>
                          <td className="px-6 py-4 font-mono font-bold text-gray-900">{adj.sequenceNumber || 'N/A'}</td>
                          <td className="px-6 py-4 text-gray-600">
                            {new Date(adj.adjustmentDate).toLocaleDateString('en-IN', {
                              day: '2-digit', month: '2-digit', year: 'numeric'
                            })}
                          </td>
                          <td className="px-6 py-4 text-gray-700 font-semibold">{adj.departmentName}</td>
                          <td className="px-6 py-4 text-gray-500 max-w-[200px] truncate" title={adj.notes || ''}>
                            {adj.notes || '—'}
                          </td>
                          <td className="px-6 py-4 text-center font-bold text-gray-800">{adj.lines?.length || 0}</td>
                          <td className="px-6 py-4 text-right">
                            <button
                              onClick={() => setSelectedAdjustmentId(adj.id)}
                              className="inline-flex items-center gap-1.5 text-xs font-bold text-neutral-600 hover:text-neutral-900 bg-neutral-50 hover:bg-neutral-100 px-3 py-1.5 rounded-lg border border-gray-200 transition-all"
                            >
                              <Eye className="w-3.5 h-3.5" />
                              View
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Pagination Controls */}
                {totalPages > 0 && (
                  <div className="px-6 py-4 border-t border-gray-100 flex items-center justify-between bg-gray-50/50 text-xs">
                    <div className="text-gray-500">
                      Showing <span className="font-semibold text-gray-800">{(page * size) + 1}</span> to{' '}
                      <span className="font-semibold text-gray-800">
                        {Math.min((page + 1) * size, totalElements)}
                      </span>{' '}
                      of <span className="font-semibold text-gray-800">{totalElements}</span> adjustments
                    </div>
                    <div className="flex items-center gap-1.5">
                      <button
                        onClick={() => setPage(p => Math.max(0, p - 1))}
                        disabled={page === 0}
                        className="px-3 py-1.5 border border-gray-200 bg-white hover:bg-neutral-50 rounded-lg font-semibold text-gray-600 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        Previous
                      </button>
                      {Array.from({ length: totalPages }).map((_, idx) => (
                        <button
                          key={idx}
                          onClick={() => setPage(idx)}
                          className={cn(
                            "w-8 h-8 rounded-lg font-semibold flex items-center justify-center transition-all",
                            page === idx
                              ? "bg-neutral-900 text-white shadow-sm"
                              : "border border-gray-200 bg-white hover:bg-neutral-50 text-gray-600"
                          )}
                        >
                          {idx + 1}
                        </button>
                      ))}
                      <button
                        onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                        disabled={page >= totalPages - 1}
                        className="px-3 py-1.5 border border-gray-200 bg-white hover:bg-neutral-50 rounded-lg font-semibold text-gray-600 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        Next
                      </button>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}

      {/* ── Create New Adjustment View ── */}
      {tab === 'new' && (
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm space-y-6 animate-in fade-in duration-200">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            {/* Adjustment Date (Read-only representation) */}
            <div>
              <label className="block text-[10px] uppercase font-bold text-gray-400 mb-2 tracking-widest">
                Adjustment Date
              </label>
              <div className="flex items-center gap-2 bg-neutral-50 px-3 py-2 border border-gray-200 rounded-lg text-sm text-gray-500 cursor-not-allowed">
                <Calendar className="w-4 h-4 text-gray-400" />
                {new Date().toLocaleDateString('en-IN', {
                  day: '2-digit', month: '2-digit', year: 'numeric'
                })}
              </div>
            </div>

            {/* Notes area */}
            <div className="md:col-span-3">
              <label className="block text-[10px] uppercase font-bold text-gray-400 mb-2 tracking-widest">
                Notes / Correction Reason (General)
              </label>
              <input
                type="text"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder="Enter audit remarks or general reason for adjustment..."
                className={inputCls}
              />
            </div>
          </div>

          {/* Lines Table */}
          <div className="border-t border-gray-100 pt-6">
            <h3 className="text-sm font-bold text-gray-900 mb-4 flex items-center gap-2">
              <Layers className="w-4.5 h-4.5 text-gray-400" />
              Adjusted Items List
            </h3>

            <div className="overflow-x-auto min-h-[400px] pb-12">
              <table className="w-full text-sm text-left">
                <thead>
                  <tr className="border-b border-gray-150 text-[10px] uppercase font-extrabold text-gray-400 tracking-wider">
                    <th className="pb-3 pr-4 text-left min-w-[280px]">Item & Batch Select</th>
                    <th className="pb-3 pr-4 text-right w-28">Current Stock</th>
                    <th className="pb-3 pr-4 text-center w-36">Adjustment Type</th>
                    <th className="pb-3 pr-4 text-right w-28">Qty</th>
                    <th className="pb-3 pr-4 text-right w-32">New Balance</th>
                    <th className="pb-3 pr-4 text-left w-52">Line Reason</th>
                    <th className="pb-3 text-center w-24">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {lines.map((line, i) => {
                    const selectedBatch = line.batches.find((b) => b.id === line.inventoryBatchId)
                    return (
                      <tr key={line.tempId} className="align-top">
                        {/* Search Medicine and Batch Dropdown */}
                        <td className="py-3 pr-4 min-w-[280px] relative">
                          <MedicineSearchInput
                            value={line.itemName}
                            onSelect={(item) => handleMedicineSelect(item, i)}
                            placeholder="Search medicine by name..."
                            className="w-full"
                          />
                          {line.batches.length > 0 && (
                            <div className="mt-2 animate-in fade-in duration-200">
                              <button
                                type="button"
                                onClick={() => setOpenBatchPickerIndex(openBatchPickerIndex === i ? null : i)}
                                className="w-full flex items-center justify-between px-3 py-1.5 bg-neutral-50 hover:bg-neutral-100 border border-neutral-200 rounded-lg text-xs text-left text-neutral-800 transition-all font-semibold"
                              >
                                <span>
                                  {selectedBatch 
                                    ? `Batch: ${selectedBatch.batchNumber || 'No Batch #'} (Qty: ${selectedBatch.currentQuantity})` 
                                    : 'Select Batch...'}
                                </span>
                                <ChevronDown className="w-3.5 h-3.5 text-neutral-500" />
                              </button>

                              {openBatchPickerIndex === i && (
                                <>
                                  {/* Click-away backdrop overlay */}
                                  <div 
                                    className="fixed inset-0 z-20 cursor-default"
                                    onClick={(e) => {
                                      e.stopPropagation()
                                      setOpenBatchPickerIndex(null)
                                    }}
                                  />
                                  <div className="absolute left-0 top-full mt-1 w-[380px] bg-white border border-gray-200 rounded-xl shadow-xl p-3 z-30 space-y-2 max-h-60 overflow-y-auto">
                                    <div className="text-[10px] font-extrabold uppercase text-gray-400 tracking-wider pb-1.5 border-b border-gray-100">
                                      Available Batches
                                    </div>
                                    <div className="divide-y divide-gray-100">
                                      {line.batches.map((b) => (
                                        <div key={b.id} className="py-2 flex items-center justify-between gap-3 hover:bg-neutral-50/50 px-1.5 rounded-lg transition-colors">
                                          <div>
                                            <div className="text-xs font-bold text-gray-950">
                                              {b.batchNumber || 'No Batch #'}
                                            </div>
                                            <div className="text-[10px] text-gray-500 font-medium">
                                              Exp: {b.expiryDate ? b.expiryDate : 'N/A'} • Available: {b.currentQuantity}
                                            </div>
                                          </div>
                                          <button
                                            type="button"
                                            onClick={() => {
                                              handleBatchSelect(b.id, i)
                                              setOpenBatchPickerIndex(null)
                                            }}
                                            className="px-3 py-1 bg-neutral-800 hover:bg-neutral-900 text-white rounded-lg text-[10px] font-extrabold transition-all"
                                          >
                                            Add
                                          </button>
                                        </div>
                                      ))}
                                    </div>
                                  </div>
                                </>
                              )}
                            </div>
                          )}
                        </td>

                      {/* Current Stock */}
                      <td className="py-5 pr-4 text-right text-gray-600 font-medium tabular-nums">
                        {line.itemId ? line.currentQty : '—'}
                      </td>

                      {/* Type: Add or Subtract */}
                      <td className="py-3 pr-4">
                        <select
                          value={line.adjustmentType}
                          disabled={!line.itemId}
                          onChange={(e) => handleLineFieldChange('adjustmentType', e.target.value, i)}
                          className={cn(
                            inputCls, 
                            "font-semibold text-center border-gray-200"
                          )}
                        >
                          <option value="" disabled hidden>Adjust Mode</option>
                          <option value="ADD">Add</option>
                          <option value="SUBTRACT">Subtract</option>
                        </select>
                      </td>

                      {/* Quantity */}
                      <td className="py-3 pr-4 w-28">
                        <input
                          type="number"
                          min={1}
                          disabled={!line.itemId}
                          value={line.adjustmentQty}
                          onChange={(e) => handleQtyChange(e.target.value, i)}
                          placeholder="Qty"
                          className={cn(inputCls, "text-right no-spinner")}
                        />
                      </td>

                      {/* New Balance Predicted */}
                      <td className="py-5 pr-4 text-right font-bold tabular-nums text-neutral-900">
                        {line.itemId && line.adjustmentQty !== '' && line.adjustmentType ? (
                          line.adjustmentType === 'ADD' ? (
                            <span className="flex items-center justify-end gap-1">
                              {line.currentQty + Number(line.adjustmentQty)}
                              <ArrowUpRight className="w-3.5 h-3.5 text-neutral-400" />
                            </span>
                          ) : (
                            <span className="flex items-center justify-end gap-1">
                              {line.currentQty - Number(line.adjustmentQty)}
                              <ArrowDownRight className="w-3.5 h-3.5 text-neutral-400" />
                            </span>
                          )
                        ) : '—'}
                      </td>

                      {/* Reason */}
                      <td className="py-3 pr-4">
                        <input
                          type="text"
                          disabled={!line.itemId}
                          value={line.reason}
                          onChange={(e) => handleLineFieldChange('reason', e.target.value, i)}
                          placeholder="e.g. Expired, Damage, Spill"
                          className={inputCls}
                        />
                      </td>

                      {/* Actions: Add / Remove */}
                      <td className="py-4 text-center">
                        {i === lines.length - 1 ? (
                          <button
                            type="button"
                            disabled={!line.itemId || !line.adjustmentType || line.adjustmentQty === '' || Number(line.adjustmentQty) <= 0}
                            onClick={() => addLine(i)}
                            className={cn(
                              "w-7 h-7 rounded-full border flex items-center justify-center transition-all text-lg font-extrabold mx-auto shadow-sm",
                              (line.itemId && line.adjustmentType && line.adjustmentQty !== '' && Number(line.adjustmentQty) > 0)
                                ? "border-neutral-350 bg-white hover:bg-neutral-900 hover:text-white text-neutral-900 cursor-pointer"
                                : "border-gray-200 bg-gray-50 text-gray-300 cursor-not-allowed opacity-50"
                            )}
                            title="Add item row"
                          >
                            +
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => removeLine(i)}
                            className="w-7 h-7 flex items-center justify-center text-neutral-900 hover:text-red-650 transition-colors text-base font-extrabold mx-auto"
                            title="Remove item row"
                          >
                            ✖
                          </button>
                        )}
                      </td>
                    </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Form Actions */}
          <div className="border-t border-gray-100 pt-6 flex justify-end gap-3">
            <button
              type="button"
              onClick={resetForm}
              className="px-4 py-2 border border-gray-200 rounded-xl text-sm font-semibold text-gray-500 hover:bg-gray-50 hover:text-gray-700 transition-colors"
            >
              Reset Form
            </button>
            <button
              type="button"
              onClick={handleSubmit}
              disabled={createMutation.isPending || !isFormValid}
              className={cn(
                "inline-flex items-center gap-1.5 px-6 py-2 rounded-xl text-sm font-bold shadow-sm transition-all",
                isFormValid && !createMutation.isPending
                  ? "bg-neutral-800 text-white hover:bg-neutral-900 cursor-pointer"
                  : "bg-gray-150 text-gray-400 border border-gray-200 cursor-not-allowed opacity-50"
              )}
            >
              {createMutation.isPending ? 'Saving Adjustment...' : 'Complete & Save'}
            </button>
          </div>
        </div>
      )}

      {/* ── Slide-out side drawer for detailed adjustment view ── */}
      {selectedAdjustment && (
        <div className="fixed inset-0 z-50 overflow-hidden" role="dialog" aria-modal="true">
          <div className="absolute inset-0 overflow-hidden">
            {/* Backdrop */}
            <div
              onClick={() => setSelectedAdjustmentId(null)}
              className="absolute inset-0 bg-neutral-900/40 backdrop-blur-xs transition-opacity duration-300 animate-in fade-in"
            />

            <div className="pointer-events-none fixed inset-y-0 right-0 flex max-w-full pl-10">
              <div className="pointer-events-auto w-screen max-w-2xl transform transition-transform duration-300 ease-in-out bg-white shadow-2xl flex flex-col h-full animate-in slide-in-from-right duration-350">
                {/* Drawer Header */}
                <div className="px-6 py-5 border-b border-gray-100 flex items-center justify-between">
                  <div>
                    <span className="text-[10px] uppercase font-bold text-gray-400 tracking-wider">Adjustment Detail</span>
                    <h3 className="text-lg font-extrabold text-gray-900 font-mono mt-0.5">
                      {selectedAdjustment.sequenceNumber}
                    </h3>
                  </div>
                  <button
                    onClick={() => setSelectedAdjustmentId(null)}
                    className="w-8 h-8 rounded-full bg-neutral-50 hover:bg-neutral-100 flex items-center justify-center text-gray-400 hover:text-gray-600 transition-all"
                  >
                    <X className="w-5 h-5" />
                  </button>
                </div>

                {/* Drawer Body */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                  {/* General Info Card */}
                  <div className="bg-neutral-50 border border-neutral-100 rounded-2xl p-5 space-y-4">
                    <div>
                      <p className="text-[10px] uppercase font-bold text-gray-400 tracking-wider">Adjustment Date</p>
                      <p className="text-sm font-semibold text-gray-800 mt-1 flex items-center gap-1.5">
                        <Calendar className="w-4 h-4 text-gray-400" />
                        {new Date(selectedAdjustment.adjustmentDate).toLocaleDateString('en-IN', {
                          day: '2-digit', month: '2-digit', year: 'numeric'
                        })}
                      </p>
                    </div>

                    {selectedAdjustment.notes && (
                      <div className="border-t border-neutral-150 pt-4">
                        <p className="text-[10px] uppercase font-bold text-gray-400 tracking-wider">General Remarks</p>
                        <p className="text-sm text-gray-600 mt-1 italic whitespace-pre-wrap">
                          "{selectedAdjustment.notes}"
                        </p>
                      </div>
                    )}
                  </div>

                  {/* Lines List */}
                  <div>
                    <h4 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3">Adjusted Lines</h4>
                    <div className="border border-gray-200 rounded-2xl overflow-hidden bg-white shadow-xs">
                      <table className="w-full text-sm text-left">
                        <thead className="bg-gray-50 border-b border-gray-100 text-[10px] uppercase font-bold text-gray-400 tracking-wider">
                          <tr>
                            <th className="px-4 py-3">Item Name</th>
                            <th className="px-4 py-3">Batch Number</th>
                            <th className="px-4 py-3 text-center">Type</th>
                            <th className="px-4 py-3 text-right">Qty</th>
                            <th className="px-4 py-3">Line Reason</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-150">
                          {selectedAdjustment.lines?.map((line) => (
                            <tr key={line.id} className="hover:bg-neutral-50/50 transition-colors">
                              <td className="px-4 py-3.5 font-bold text-gray-900">{line.itemName}</td>
                              <td className="px-4 py-3.5 font-mono text-xs text-gray-600">{line.batchNumber}</td>
                              <td className="px-4 py-3.5 text-center font-extrabold text-xs text-neutral-900 uppercase">
                                {line.adjustmentType === 'ADD' ? 'Add' : 'Subtract'}
                              </td>
                              <td className="px-4 py-3.5 text-right font-bold text-gray-800 tabular-nums">
                                {line.adjustmentQty}
                              </td>
                              <td className="px-4 py-3.5 text-gray-500 italic text-xs">
                                {line.reason || '—'}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>

                {/* Drawer Footer */}
                <div className="px-6 py-4 border-t border-gray-100 bg-gray-50 flex justify-end">
                  <button
                    onClick={() => setSelectedAdjustmentId(null)}
                    className="px-5 py-2 bg-neutral-800 hover:bg-neutral-900 text-white rounded-xl text-sm font-bold transition-all shadow-sm"
                  >
                    Close Detail View
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
