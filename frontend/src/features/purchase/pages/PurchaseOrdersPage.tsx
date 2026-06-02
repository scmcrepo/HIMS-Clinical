import { PrintButton } from '../../../components/shared/PrintButton'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  purchaseApi,
  type PurchaseOrderLine,
  type PurchaseOrderResponse,
} from '../../../services/purchase/purchaseApi'
import { toast } from '../../../hooks/useToast'
import { formatDate } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'

const DEMO_DEPT_ID = '00000000-0000-0000-0000-000000000001'

const STATUS_STYLES: Record<PurchaseOrderResponse['orderStatus'], string> = {
  ORDERED:             'bg-blue-50  text-blue-700  border-blue-200',
  PARTIALLY_RECEIVED:  'bg-amber-50 text-amber-700 border-amber-200',
  RECEIVED:            'bg-green-50 text-green-700 border-green-200',
}

export default function PurchaseOrdersPage() {
  const qc   = useQueryClient()
  const today = new Date().toISOString().split('T')[0]
  const [tab, setTab] = useState<'create' | 'history'>('create')

  // Form state
  const [supplierId, setSupplierId] = useState('')
  const [notes, setNotes]           = useState('')
  const [lines, setLines] = useState<PurchaseOrderLine[]>([
    { itemId: '', quantity: 1, unitRate: undefined },
  ])

  const { data: orders, isLoading } = useQuery({
    queryKey: ['purchase-orders', today],
    queryFn:  () => purchaseApi.getByDate(today),
    enabled:  tab === 'history',
    refetchInterval: 60_000,
  })

  const createMutation = useMutation({
    mutationFn: () =>
      purchaseApi.create(
        DEMO_DEPT_ID,
        supplierId || null,
        lines.filter(l => l.itemId.trim() && l.quantity > 0),
        notes || undefined
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchase-orders'] })
      toast({ title: 'Purchase order created', variant: 'success' })
      setLines([{ itemId: '', quantity: 1, unitRate: undefined }])
      setSupplierId('')
      setNotes('')
    },
    onError: (e: Error) =>
      toast({ title: 'Create failed', description: e.message, variant: 'destructive' }),
  })

  const updateLine = (i: number, field: keyof PurchaseOrderLine, value: string | number | undefined) =>
    setLines(prev => prev.map((l, idx) => idx === i ? { ...l, [field]: value } : l))

  const inputCls = "px-2.5 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 w-full"

  return (
    <div className="space-y-5 max-w-5xl">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Purchase Orders</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            Raise purchase orders to suppliers. Link to goods receipt when stock arrives.
          </p>
        </div>
        <div className="flex gap-1 bg-gray-100 p-1 rounded-lg" role="tablist">
          {(['create', 'history'] as const).map(t => (
            <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
              className={cn('px-4 py-1.5 text-sm font-medium rounded-md transition-colors',
                tab === t ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700')}>
              {t === 'create' ? 'New Order' : "Today's Orders"}
            </button>
          ))}
        </div>
      </div>

      {/* Create PO tab */}
      {tab === 'create' && (
        <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-5">
          {/* Header */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Supplier ID</label>
              <input
                value={supplierId}
                onChange={e => setSupplierId(e.target.value)}
                placeholder="Supplier UUID (optional)"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                aria-label="Supplier ID"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Notes</label>
              <input
                value={notes}
                onChange={e => setNotes(e.target.value)}
                placeholder="Optional notes"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                aria-label="Notes"
              />
            </div>
          </div>

          {/* Lines */}
          <div className="overflow-x-auto">
            <table className="w-full text-sm" aria-label="Purchase order lines">
              <thead>
                <tr className="border-b border-gray-100 text-left text-xs">
                  {['Item ID *', 'Quantity *', 'Unit Rate (₹)', ''].map(h => (
                    <th key={h} className="px-2 py-2 font-semibold text-gray-600">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {lines.map((line, i) => (
                  <tr key={i}>
                    <td className="px-2 py-2 min-w-48">
                      <input
                        value={line.itemId}
                        onChange={e => updateLine(i, 'itemId', e.target.value)}
                        placeholder="Item UUID"
                        className={inputCls}
                        aria-label={`Line ${i + 1} item ID`}
                      />
                    </td>
                    <td className="px-2 py-2 w-28">
                      <input
                        type="number" min={1} value={line.quantity}
                        onChange={e => updateLine(i, 'quantity', parseInt(e.target.value) || 1)}
                        className={inputCls}
                        aria-label={`Line ${i + 1} quantity`}
                      />
                    </td>
                    <td className="px-2 py-2 w-36">
                      <input
                        type="number" step="0.01" min={0}
                        value={line.unitRate ?? ''}
                        onChange={e => updateLine(i, 'unitRate', e.target.value ? parseFloat(e.target.value) : undefined)}
                        placeholder="Optional"
                        className={inputCls}
                        aria-label={`Line ${i + 1} unit rate`}
                      />
                    </td>
                    <td className="px-2 py-2">
                      {lines.length > 1 && (
                        <button
                          type="button"
                          onClick={() => setLines(prev => prev.filter((_, idx) => idx !== i))}
                          className="text-red-400 hover:text-red-600 font-bold text-lg leading-none"
                          aria-label={`Remove line ${i + 1}`}
                        >×</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <button
            type="button"
            onClick={() => setLines(prev => [...prev, { itemId: '', quantity: 1, unitRate: undefined }])}
            className="text-sm text-blue-600 hover:text-blue-700 font-medium"
          >
            + Add Line
          </button>

          <div className="flex items-center gap-4 pt-2 border-t border-gray-100">
            <button
              onClick={() => createMutation.mutate()}
              disabled={createMutation.isPending || !lines.some(l => l.itemId.trim())}
              className="px-6 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {createMutation.isPending ? 'Creating…' : 'Create Purchase Order'}
            </button>
            <p className="text-xs text-gray-400">
              Order date is always today. Sequence number is generated automatically.
            </p>
          </div>
        </div>
      )}

      {/* History tab */}
      {tab === 'history' && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-gray-900">
              Today's Purchase Orders — {formatDate(today)}
            </h3>
            <span className="text-xs text-gray-400">{orders?.length ?? 0} orders</span>
          </div>

          {isLoading && (
            <p className="text-sm text-gray-500 px-5 py-4" aria-live="polite">Loading…</p>
          )}

          <table className="w-full text-sm" aria-label="Purchase order history">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                <th className="px-4 py-2.5 font-semibold text-gray-600">PO Number</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Supplier</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Lines</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Status</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Date</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-center w-14">Print</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {orders?.map(o => (
                <tr key={o.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-gray-700">
                    {o.sequenceNumber ?? o.id.slice(0, 12) + '…'}
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-xs">
                    {o.supplierId ? o.supplierId.slice(0, 8) + '…' : 'Direct'}
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {o.lines.length} item{o.lines.length !== 1 ? 's' : ''}
                  </td>
                  <td className="px-4 py-3">
                    <span className={cn(
                      'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border',
                      STATUS_STYLES[o.orderStatus]
                    )}>
                      {o.orderStatus.replace('_', ' ')}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(o.orderDate)}</td>
                  <td className="px-4 py-3 text-center">
                    <PrintButton
                      templateType="PURCHASE_ORDER"
                      params={{ id: o.id }}
                      variant="icon"
                      label="Print PO"
                    />
                  </td>
                </tr>
              ))}
              {(!orders || orders.length === 0) && !isLoading && (
                <tr>
                  <td colSpan={5} className="px-4 py-10 text-center text-gray-400 text-sm">
                    No purchase orders today
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
