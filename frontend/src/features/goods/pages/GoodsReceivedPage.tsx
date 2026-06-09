import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { goodsApi, type ReceiveLine } from '../../../services/goods/goodsApi'
import { tempStockApi } from '../../../services/tempStock/tempStockApi'
import { MedicineSearchInput } from '../../../components/shared/MedicineSearchInput'
import { toast } from '../../../hooks/useToast'
import { formatDate } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import DatePicker from '../../../components/shared/DatePicker'

const DEMO_DEPT_ID = '00000000-0000-0000-0000-000000000001'

export default function GoodsReceivedPage() {
  const qc = useQueryClient()
  const today = new Date().toISOString().split('T')[0]
  const [tab, setTab] = useState<'receive' | 'history'>('receive')

  // Form state
  const [supplierId, setSupplierId]       = useState('')
  const [invoiceNumber, setInvoiceNumber] = useState('')
  const [lines, setLines] = useState<(ReceiveLine & { itemName?: string })[]>([{
    itemId: '', batchNumber: '', quantity: 1,
    purchaseRate: 0, maximumRetailPrice: 0, sellingRate: 0, expiryDate: ''
  }])
  const [adjustTempStocks, setAdjustTempStocks] = useState<Record<number, { qty: number; active: boolean }>>({})
  const [showConfirmModal, setShowConfirmModal] = useState(false)

  const { data: todayReceipts, isLoading } = useQuery({
    queryKey: ['goods-received', today],
    queryFn:  () => goodsApi.getByDate(today),
    enabled:  tab === 'history',
    refetchInterval: 30_000,
  })

  const receiveMutation = useMutation({
    mutationFn: () => {
      const payloadLines = lines
        .map((l, i) => {
          const adj = adjustTempStocks[i]
          return {
            ...l,
            tempQuantity: (adj && adj.active) ? adj.qty : undefined
          }
        })
        .filter(l => l.itemId.trim() && l.quantity > 0)

      return goodsApi.receiveGoods(
        supplierId || '',
        undefined,
        DEMO_DEPT_ID,
        invoiceNumber || undefined,
        undefined,
        undefined,
        payloadLines
      )
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['goods-received'] })
      toast({ title: 'Goods received — batches created', variant: 'success' })
      setLines([{ itemId: '', batchNumber: '', quantity: 1, purchaseRate: 0, maximumRetailPrice: 0, sellingRate: 0, expiryDate: '' }])
      setAdjustTempStocks({})
      setInvoiceNumber(''); setSupplierId('')
    },
    onError: (e: Error) => toast({ title: 'Receive failed', description: e.message, variant: 'destructive' }),
  })

  const inputCls = "px-2.5 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500 w-full"

  const updateLine = (i: number, field: keyof ReceiveLine, value: string | number) =>
    setLines(prev => prev.map((l, idx) => idx === i ? { ...l, [field]: value } : l))

  const checkTempStockForLine = async (i: number, itemId: string | undefined, batchNumber: string | undefined) => {
    if (!itemId || !itemId.trim() || !batchNumber || !batchNumber.trim()) {
      setAdjustTempStocks(prev => {
        const next = { ...prev }
        delete next[i]
        return next
      })
      return
    }
    try {
      const qty = await tempStockApi.getQuantity(itemId, batchNumber)
      if (qty > 0) {
        setAdjustTempStocks(prev => ({
          ...prev,
          [i]: { qty, active: true }
        }))
      } else {
        setAdjustTempStocks(prev => {
          const next = { ...prev }
          delete next[i]
          return next
        })
      }
    } catch (err) {
      console.error('Failed to get temp stock quantity', err)
    }
  }

  const removeLine = (i: number) => {
    setLines(prev => prev.filter((_, idx) => idx !== i))
    setAdjustTempStocks(prev => {
      const next: Record<number, { qty: number; active: boolean }> = {}
      Object.keys(prev).forEach(key => {
        const idx = parseInt(key)
        if (idx < i) {
          next[idx] = prev[idx]
        } else if (idx > i) {
          next[idx - 1] = prev[idx]
        }
      })
      return next
    })
  }

  const handleReceive = () => {
    const valid = lines.filter(l => l.itemId.trim() && l.quantity > 0)
    if (!valid.length) { toast({ title: 'Add at least one item', variant: 'destructive' }); return }
    const hasTempStocks = Object.values(adjustTempStocks).some(x => x && x.qty > 0)
    if (hasTempStocks) {
      setShowConfirmModal(true)
    } else {
      receiveMutation.mutate()
    }
  }

  return (
    <div className="space-y-5 max-w-6xl">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Goods Received</h2>
          <p className="text-sm text-gray-500 mt-0.5">Record incoming stock from suppliers. Each line creates an inventory batch.</p>
        </div>
        <div className="flex gap-1 bg-gray-100 p-1 rounded-lg" role="tablist">
          {(['receive', 'history'] as const).map(t => (
            <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
              className={cn('px-4 py-1.5 text-sm font-medium rounded-md transition-colors',
                tab === t ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700')}>
              {t === 'receive' ? 'Receive Goods' : "Today's Receipts"}
            </button>
          ))}
        </div>
      </div>

      {tab === 'receive' && (
        <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-5">
          {/* Header fields */}
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Supplier ID</label>
              <input value={supplierId} onChange={e => setSupplierId(e.target.value)}
                placeholder="UUID (optional)"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500"
                aria-label="Supplier ID" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Invoice Number</label>
              <input value={invoiceNumber} onChange={e => setInvoiceNumber(e.target.value)}
                placeholder="Supplier invoice reference"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500"
                aria-label="Invoice number" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Department</label>
              <input value="Default Department" disabled
                className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 text-gray-500" />
            </div>
          </div>

          {/* Line items table */}
          <div className="overflow-x-auto">
            <table className="w-full text-sm" aria-label="Goods receive lines">
              <thead>
                <tr className="border-b border-gray-100 text-left text-xs">
                  {['Item *', 'Batch No.', 'Qty *', 'Purchase Rate ₹ *', 'MRP ₹ *', 'Selling Rate ₹ *', 'Expiry Date', ''].map(h => (
                    <th key={h} className="px-2 py-2 font-semibold text-gray-600">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {lines.map((line, i) => (
                  <tr key={i}>
                    <td className="px-2 py-2 min-w-56">
                      <MedicineSearchInput
                        value={line.itemName || ''}
                        onSelect={async item => {
                          let autofilledBatch = ''
                          let autofilledExpiry = line.expiryDate
                          let autofilledMrp = line.maximumRetailPrice
                          let autofilledPPrice = line.purchaseRate
                          let autofilledSelling = line.sellingRate

                          try {
                            const temps = await tempStockApi.getByItem(item.id)
                            const activeTemp = temps.find(t => t.quantity > 0)
                            if (activeTemp) {
                              autofilledBatch = activeTemp.batchNumber || ''
                              autofilledExpiry = activeTemp.expiryDate || ''
                              autofilledMrp = activeTemp.mrp || 0
                              autofilledPPrice = activeTemp.purchaseRate || 0
                              autofilledSelling = activeTemp.sellingRate || 0
                            }
                          } catch (err) {
                            console.error('Failed to auto-fetch temporary stock', err)
                          }

                          setLines(prev => prev.map((l, idx) => idx === i ? { 
                            ...l, 
                            itemId: item.id, 
                            itemName: item.name,
                            batchNumber: autofilledBatch || l.batchNumber,
                            expiryDate: autofilledExpiry || l.expiryDate,
                            maximumRetailPrice: autofilledMrp || l.maximumRetailPrice,
                            purchaseRate: autofilledPPrice || l.purchaseRate,
                            sellingRate: autofilledSelling || l.sellingRate
                          } : l))

                          if (autofilledBatch) {
                            checkTempStockForLine(i, item.id, autofilledBatch)
                          }
                        }}
                        onClear={() => {
                          setLines(prev => prev.map((l, idx) => idx === i ? { ...l, itemId: '', itemName: '' } : l))
                          checkTempStockForLine(i, '', line.batchNumber)
                        }}
                        placeholder="Search item..."
                      />
                    </td>
                    <td className="px-2 py-2 min-w-28">
                      <input value={line.batchNumber ?? ''}
                        onChange={e => {
                          updateLine(i, 'batchNumber', e.target.value)
                          setAdjustTempStocks(prev => {
                            const next = { ...prev }
                            delete next[i]
                            return next
                          })
                        }}
                        onBlur={e => checkTempStockForLine(i, line.itemId, e.target.value)}
                        placeholder="Optional" className={inputCls} aria-label={`Line ${i+1} batch number`} />
                      {adjustTempStocks[i] && (
                        <div className="mt-1 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded text-[10px] text-amber-800 font-semibold max-w-[120px] text-center">
                          <span>Temp: {adjustTempStocks[i].qty}</span>
                        </div>
                      )}
                    </td>
                    <td className="px-2 py-2 w-20">
                      <input type="number" min={1} value={line.quantity}
                        onChange={e => updateLine(i, 'quantity', parseInt(e.target.value) || 1)}
                        className={inputCls} aria-label={`Line ${i+1} quantity`} />
                    </td>
                    <td className="px-2 py-2 w-32">
                      <input type="number" step="0.01" min={0} value={line.purchaseRate}
                        onChange={e => updateLine(i, 'purchaseRate', parseFloat(e.target.value) || 0)}
                        className={inputCls} aria-label={`Line ${i+1} purchase rate`} />
                    </td>
                    <td className="px-2 py-2 w-28">
                      <input type="number" step="0.01" min={0} value={line.maximumRetailPrice}
                        onChange={e => updateLine(i, 'maximumRetailPrice', parseFloat(e.target.value) || 0)}
                        className={inputCls} aria-label={`Line ${i+1} MRP`} />
                    </td>
                    <td className="px-2 py-2 w-32">
                      <input type="number" step="0.01" min={0} value={line.sellingRate}
                        onChange={e => updateLine(i, 'sellingRate', parseFloat(e.target.value) || 0)}
                        className={inputCls} aria-label={`Line ${i+1} selling rate`} />
                    </td>
                    <td className="px-2 py-2 w-36">
                      <DatePicker 
                        value={line.expiryDate ?? ''}
                        onChange={val => updateLine(i, 'expiryDate', val)}
                        size="xs"
                      />
                    </td>
                    <td className="px-2 py-2">
                      {lines.length > 1 && (
                        <button type="button" onClick={() => removeLine(i)}
                          className="text-red-400 hover:text-red-600 font-bold text-lg leading-none"
                          aria-label={`Remove line ${i+1}`}>×</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex items-center gap-4">
            <button type="button"
              onClick={() => setLines(prev => [...prev, {
                itemId: '', batchNumber: '', quantity: 1,
                purchaseRate: 0, maximumRetailPrice: 0, sellingRate: 0, expiryDate: ''
              }])}
              className="text-sm text-neutral-600 hover:text-neutral-700 font-medium">
              + Add Line
            </button>
            <span className="text-xs text-gray-400">{lines.length} line{lines.length !== 1 ? 's' : ''}</span>
          </div>

          <div className="flex items-center gap-4 pt-2 border-t border-gray-100">
            <button onClick={handleReceive} disabled={receiveMutation.isPending}
              className="px-6 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
              {receiveMutation.isPending ? 'Processing…' : 'Receive Goods'}
            </button>
            <p className="text-xs text-gray-400">An inventory batch will be created for each line item</p>
          </div>
        </div>
      )}

      {tab === 'history' && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-900">Today's Receipts — {formatDate(today)}</h3>
            <span className="text-xs text-gray-400">{todayReceipts?.length ?? 0} receipts</span>
          </div>
          {isLoading && <p className="text-sm text-gray-500 px-5 py-4" aria-live="polite">Loading…</p>}
          <table className="w-full text-sm" aria-label="Goods receipt history">
            <thead><tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
              <th className="px-4 py-2.5 font-semibold text-gray-600">Receipt #</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600">Invoice</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600">Supplier</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600">Lines</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600">Date</th>
            </tr></thead>
            <tbody className="divide-y divide-gray-100">
              {todayReceipts?.map(r => (
                <tr key={r.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-gray-700">{r.sequenceNumber ?? r.id.slice(0,12)+'…'}</td>
                  <td className="px-4 py-3 text-gray-600">{r.invoiceNumber ?? '—'}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{r.supplierId ? r.supplierId.slice(0,8)+'…' : 'Direct'}</td>
                  <td className="px-4 py-3 text-gray-600">{r.lines.length} item{r.lines.length !== 1 ? 's' : ''}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(r.receiptDate)}</td>
                </tr>
              ))}
              {(!todayReceipts || todayReceipts.length === 0) && !isLoading && (
                <tr><td colSpan={5} className="px-4 py-10 text-center text-gray-400 text-sm">No receipts today</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {showConfirmModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md p-6 border border-neutral-100 space-y-4">
            <div>
              <h3 className="text-lg font-bold text-gray-900">Reconcile Temporary Stock?</h3>
              <p className="text-sm text-gray-500 mt-1">We found temporary stock entries for items in this receipt:</p>
            </div>
            <div className="space-y-2 max-h-48 overflow-y-auto bg-gray-50 p-3 rounded-lg border border-gray-100">
              {lines.map((l, i) => {
                const adj = adjustTempStocks[i]
                if (!adj || adj.qty <= 0) return null
                return (
                  <div key={i} className="text-xs text-gray-700 flex justify-between py-1 border-b border-gray-200/50 last:border-0">
                    <span className="font-medium truncate max-w-[240px]">{l.itemName || 'Selected Item'}</span>
                    <span className="text-amber-700 font-semibold">Qty: {adj.qty} ({l.batchNumber})</span>
                  </div>
                )
              })}
            </div>
            <p className="text-xs text-gray-505">
              Do you want to automatically adjust the receipt quantities by subtracting these temporary stocks?
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowConfirmModal(false)}
                className="px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  setAdjustTempStocks(prev => {
                    const next = { ...prev }
                    Object.keys(next).forEach(k => {
                      next[parseInt(k)].active = false
                    })
                    return next
                  })
                  setShowConfirmModal(false)
                  setTimeout(() => {
                    receiveMutation.mutate()
                  }, 50)
                }}
                className="px-3 py-2 text-sm text-gray-500 hover:text-gray-700 rounded-lg transition-colors border border-gray-200"
              >
                Save Without Adjusting
              </button>
              <button
                type="button"
                onClick={() => {
                  setAdjustTempStocks(prev => {
                    const next = { ...prev }
                    Object.keys(next).forEach(k => {
                      next[parseInt(k)].active = true
                    })
                    return next
                  })
                  setShowConfirmModal(false)
                  setTimeout(() => {
                    receiveMutation.mutate()
                  }, 50)
                }}
                className="px-4 py-2 text-sm text-white bg-amber-600 hover:bg-amber-700 rounded-lg font-semibold transition-colors shadow-sm"
              >
                Adjust & Save
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
