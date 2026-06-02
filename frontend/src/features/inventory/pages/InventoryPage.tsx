import { useState } from 'react'
import { useForm, useFieldArray, Controller } from 'react-hook-form'
import DatePicker from '../../../components/shared/DatePicker'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useInventoryMutations } from '../../../hooks/inventory/useInventory'
import type { InventoryBatch } from '../../../types/inventory'
import { cn } from '../../../lib/utils'

const receiveSchema = z.object({
  departmentId: z.string().uuid('Valid UUID required'),
  lines: z.array(z.object({
    itemId:              z.string().uuid('Valid UUID required'),
    batchNumber:         z.string().optional(),
    quantity:            z.coerce.number().int().positive('Must be > 0'),
    purchaseRate:        z.coerce.number().positive('Must be > 0'),
    maximumRetailPrice:  z.coerce.number().positive('Must be > 0'),
    sellingRate:         z.coerce.number().positive('Must be > 0'),
    expiryDate:          z.string().optional(),
  })).min(1, 'At least one item required'),
})
type ReceiveFormValues = z.infer<typeof receiveSchema>

const inputCls = "w-full px-2.5 py-1.5 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"

export default function InventoryPage() {
  const [tab, setTab]     = useState<'receive' | 'adjust'>('receive')
  const [receivedBatches, setReceivedBatches] = useState<InventoryBatch[]>([])
  const mutations = useInventoryMutations()

  const { register, control, handleSubmit, reset, formState: { errors } } =
    useForm<ReceiveFormValues>({
      resolver: zodResolver(receiveSchema),
      defaultValues: { departmentId: '', lines: [{ itemId: '', quantity: 1, purchaseRate: 0, maximumRetailPrice: 0, sellingRate: 0 }] },
    })

  const { fields, append, remove } = useFieldArray({ control, name: 'lines' })

  const onReceive = async (data: ReceiveFormValues) => {
    const result = await mutations.receiveGoods.mutateAsync({
      departmentId: data.departmentId,
      lines: data.lines.map(l => {
        const line: any = {
          itemId: l.itemId,
          quantity: l.quantity,
          purchaseRate: l.purchaseRate,
          maximumRetailPrice: l.maximumRetailPrice,
          sellingRate: l.sellingRate,
        }
        if (l.batchNumber) line.batchNumber = l.batchNumber
        if (l.expiryDate) line.expiryDate = l.expiryDate
        return line
      }),
    })
    setReceivedBatches(result)
    reset()
  }

  return (
    <div className="space-y-5 max-w-5xl">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-900">Inventory</h2>
        <div className="flex gap-1 bg-gray-100 p-1 rounded-lg" role="tablist">
          {(['receive', 'adjust'] as const).map(t => (
            <button key={t} role="tab" aria-selected={tab === t}
              onClick={() => setTab(t)}
              className={cn('px-4 py-1.5 text-sm font-medium rounded-md transition-colors',
                tab === t ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700')}>
              {t === 'receive' ? 'Receive Goods' : 'Adjust Stock'}
            </button>
          ))}
        </div>
      </div>

      {/* Receive Goods Tab */}
      {tab === 'receive' && (
        <div className="space-y-4">
          <form onSubmit={handleSubmit(onReceive)} noValidate className="bg-white border border-gray-200 rounded-xl p-5 space-y-4">
            <div className="max-w-sm">
              <label htmlFor="departmentId" className="block text-sm font-medium text-gray-700 mb-1">Department ID</label>
              <input id="departmentId" {...register('departmentId')}
                placeholder="UUID of receiving department"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                aria-invalid={!!errors.departmentId} />
              {errors.departmentId && <p className="text-xs text-red-600 mt-1" role="alert">{errors.departmentId.message}</p>}
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-xs" aria-label="Goods receive lines">
                <thead>
                  <tr className="border-b border-gray-100 text-left">
                    {['Item ID', 'Batch No.', 'Qty', 'Purchase Rate (₹)', 'MRP (₹)', 'Selling Rate (₹)', 'Expiry', ''].map(h => (
                      <th key={h} className="px-2 py-2 font-semibold text-gray-600">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {fields.map((field, i) => (
                    <tr key={field.id}>
                      <td className="px-2 py-2 min-w-40">
                        <input {...register(`lines.${i}.itemId`)} placeholder="Item UUID" className={inputCls} aria-label="Item ID" />
                        {errors.lines?.[i]?.itemId && <p className="text-red-500 text-xs">{errors.lines[i]?.itemId?.message}</p>}
                      </td>
                      <td className="px-2 py-2 min-w-24">
                        <input {...register(`lines.${i}.batchNumber`)} placeholder="Optional" className={inputCls} aria-label="Batch number" />
                      </td>
                      <td className="px-2 py-2 w-20">
                        <input type="number" min={1} {...register(`lines.${i}.quantity`)} className={inputCls} aria-label="Quantity" />
                        {errors.lines?.[i]?.quantity && <p className="text-red-500 text-xs">{errors.lines[i]?.quantity?.message}</p>}
                      </td>
                      <td className="px-2 py-2 w-28">
                        <input type="number" step="0.01" min={0.01} {...register(`lines.${i}.purchaseRate`)} className={inputCls} aria-label="Purchase rate" />
                      </td>
                      <td className="px-2 py-2 w-28">
                        <input type="number" step="0.01" min={0.01} {...register(`lines.${i}.maximumRetailPrice`)} className={inputCls} aria-label="MRP" />
                      </td>
                      <td className="px-2 py-2 w-28">
                        <input type="number" step="0.01" min={0.01} {...register(`lines.${i}.sellingRate`)} className={inputCls} aria-label="Selling rate" />
                      </td>
                      <td className="px-2 py-2 w-32">
                        <Controller
                          name={`lines.${i}.expiryDate`}
                          control={control}
                          render={({ field }) => (
                            <DatePicker 
                              value={field.value ?? null}
                              onChange={field.onChange}
                              size="xs"
                            />
                          )}
                        />
                      </td>
                      <td className="px-2 py-2">
                        {fields.length > 1 && (
                          <button type="button" onClick={() => remove(i)}
                            className="text-red-400 hover:text-red-600 font-bold px-1" aria-label="Remove line">×</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex items-center gap-3">
              <button type="button"
                onClick={() => append({ itemId: '', quantity: 1, purchaseRate: 0, maximumRetailPrice: 0, sellingRate: 0 })}
                className="text-sm text-blue-600 hover:text-blue-700 font-medium">
                + Add Line
              </button>
              <button type="submit" disabled={mutations.receiveGoods.isPending}
                className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                {mutations.receiveGoods.isPending ? 'Receiving…' : 'Receive Goods'}
              </button>
            </div>
          </form>

          {/* Receipt result */}
          {receivedBatches.length > 0 && (
            <div className="bg-green-50 border border-green-200 rounded-xl p-4" role="status">
              <p className="text-sm font-semibold text-green-800 mb-2">
                ✓ {receivedBatches.length} batch(es) received successfully
              </p>
              <table className="w-full text-xs" aria-label="Received batches">
                <thead>
                  <tr className="text-left border-b border-green-200">
                    <th className="py-1 pr-4 text-green-700">Item ID</th>
                    <th className="py-1 pr-4 text-green-700">Batch</th>
                    <th className="py-1 pr-4 text-green-700">Qty</th>
                    <th className="py-1 pr-4 text-green-700">Selling Rate</th>
                    <th className="py-1 text-green-700">Expiry</th>
                  </tr>
                </thead>
                <tbody>
                  {receivedBatches.map(b => (
                    <tr key={b.id} className="border-b border-green-100 last:border-0">
                      <td className="py-1 pr-4 font-mono text-green-800">{b.itemId.slice(0, 8)}…</td>
                      <td className="py-1 pr-4 text-green-700">{b.batchNumber ?? '—'}</td>
                      <td className="py-1 pr-4 text-green-800 font-semibold">{b.currentQuantity}</td>
                      <td className="py-1 pr-4 text-green-700">₹{b.sellingRate}</td>
                      <td className="py-1 text-green-700">{b.expiryDate ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Adjust Stock Tab */}
      {tab === 'adjust' && (
        <AdjustStockPanel mutations={mutations} />
      )}
    </div>
  )
}

function AdjustStockPanel({ mutations }: { mutations: ReturnType<typeof useInventoryMutations> }) {
  const [batchId, setBatchId]     = useState('')
  const [qty, setQty]             = useState('')
  const [reason, setReason]       = useState('')

  const handleAdjust = () => {
    const adjustmentQty = parseInt(qty, 10)
    if (!batchId || isNaN(adjustmentQty) || adjustmentQty === 0) return
    const payload: { batchId: string; qty: number; reason?: string } = { batchId, qty: adjustmentQty }
    if (reason) payload.reason = reason
    mutations.adjustStock.mutate(payload, {
      onSuccess: () => { setBatchId(''); setQty(''); setReason('') }
    })
  }

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-5 max-w-md space-y-4">
      <h3 className="text-sm font-semibold text-gray-900">Manual Stock Adjustment</h3>
      <p className="text-xs text-gray-500">
        Enter a positive number to add stock, negative to remove.
      </p>
      <div>
        <label htmlFor="adj-batch" className="block text-sm font-medium text-gray-700 mb-1">Batch ID</label>
        <input id="adj-batch" value={batchId} onChange={e => setBatchId(e.target.value)}
          placeholder="InventoryBatch UUID"
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <div>
        <label htmlFor="adj-qty" className="block text-sm font-medium text-gray-700 mb-1">Adjustment Quantity</label>
        <input id="adj-qty" type="number" value={qty} onChange={e => setQty(e.target.value)}
          placeholder="e.g. 10 or -5"
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <div>
        <label htmlFor="adj-reason" className="block text-sm font-medium text-gray-700 mb-1">Reason</label>
        <input id="adj-reason" value={reason} onChange={e => setReason(e.target.value)}
          placeholder="Optional adjustment reason"
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <button onClick={handleAdjust}
        disabled={!batchId || !qty || qty === '0' || mutations.adjustStock.isPending}
        className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
        {mutations.adjustStock.isPending ? 'Adjusting…' : 'Apply Adjustment'}
      </button>
    </div>
  )
}
