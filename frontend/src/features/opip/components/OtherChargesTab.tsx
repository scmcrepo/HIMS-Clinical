/**
 * OtherChargesTab.tsx
 * IP-only tab for adding miscellaneous billable charges.
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../hooks/useToast'
import { otherChargesApi, type OtherChargePayload } from '../../../services/opip/opipApi'
import { formatDateTime } from '../../../lib/dateUtils'

interface Props { encounterId: string; readOnly?: boolean }

export function OtherChargesTab({ encounterId, readOnly }: Props) {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)

  const { data: charges = [], isLoading } = useQuery({
    queryKey: ['other-charges', encounterId],
    queryFn:  () => otherChargesApi.list(encounterId),
  })

  const total = charges.reduce((sum, c) => sum + (c.amount * (c.qty ?? 1)), 0)

  const invalidate = () => qc.invalidateQueries({ queryKey: ['other-charges', encounterId] })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-gray-800">Other Charges</h3>
        {!readOnly && (
          <button
            onClick={() => setShowForm(true)}
            className="px-3 py-1.5 text-xs font-semibold bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors">
            + ADD CHARGE
          </button>
        )}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-6">Loading…</p>
      ) : charges.length === 0 ? (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg px-4 py-3 text-xs text-yellow-800">
          No charges added yet.
        </div>
      ) : (
        <>
          <div className="border border-gray-200 rounded-xl bg-white overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  {['Description', 'Qty', 'Amount', 'Total', 'Added'].map(h => (
                    <th key={h} className="px-4 py-2 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {charges.map((c, i) => (
                  <tr key={c.id ?? i} className="hover:bg-gray-50">
                    <td className="px-4 py-2">
                      <p className="font-medium text-gray-900">{c.chargeLabel}</p>
                      {c.remarks && <p className="text-xs text-gray-400">{c.remarks}</p>}
                    </td>
                    <td className="px-4 py-2 text-gray-600">{c.qty}</td>
                    <td className="px-4 py-2 text-gray-600">₹{c.amount.toFixed(2)}</td>
                    <td className="px-4 py-2 font-semibold text-gray-900">₹{(c.amount * c.qty).toFixed(2)}</td>
                    <td className="px-4 py-2 text-xs text-gray-400">{formatDateTime(c.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="text-right text-sm font-bold text-gray-900">
            Total: ₹{total.toFixed(2)}
          </div>
        </>
      )}

      {showForm && (
        <AddChargeModal
          encounterId={encounterId}
          onClose={() => setShowForm(false)}
          onSaved={() => { invalidate(); setShowForm(false) }}
        />
      )}
    </div>
  )
}

// ─── Add Charge Modal ─────────────────────────────────────────────────────────

function AddChargeModal({ encounterId, onClose, onSaved }:
  { encounterId: string; onClose: () => void; onSaved: () => void }) {

  const [form, setForm] = useState<OtherChargePayload>({
    chargeLabel: '', amount: 0, qty: 1, remarks: '',
  })

  const saveMut = useMutation({
    mutationFn: () => {
      if (!form.chargeLabel.trim()) throw new Error('Description is required')
      if (form.amount <= 0) throw new Error('Amount must be greater than 0')
      return otherChargesApi.add(encounterId, form)
    },
    onSuccess: () => {
      toast({ title: 'Charge added', variant: 'success' })
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const set = (patch: Partial<OtherChargePayload>) => setForm(f => ({ ...f, ...patch }))

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h3 className="text-base font-bold text-gray-900">Add Charge</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>

        <div className="p-6 space-y-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Description *</label>
            <input value={form.chargeLabel}
              onChange={e => set({ chargeLabel: e.target.value })}
              placeholder="e.g. Dressing supplies, Oxygen"
              className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500" />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Amount (₹) *</label>
              <input type="number" min="0" step="0.01" value={form.amount || ''}
                onChange={e => set({ amount: parseFloat(e.target.value) || 0 })}
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Quantity</label>
              <input type="number" min="1" value={form.qty ?? 1}
                onChange={e => set({ qty: parseInt(e.target.value) || 1 })}
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500" />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Remarks</label>
            <input value={form.remarks ?? ''}
              onChange={e => set({ remarks: e.target.value })}
              placeholder="Optional notes…"
              className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500" />
          </div>

          {form.amount > 0 && (form.qty ?? 1) > 1 && (
            <p className="text-xs text-gray-500">
              Total: ₹{(form.amount * (form.qty ?? 1)).toFixed(2)}
            </p>
          )}
        </div>

        <div className="flex justify-end gap-2 px-6 py-4 border-t border-gray-200">
          <button onClick={onClose}
            className="px-4 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
            CANCEL
          </button>
          <button
            onClick={() => saveMut.mutate()}
            disabled={saveMut.isPending || !form.chargeLabel.trim() || form.amount <= 0}
            className="px-5 py-1.5 text-sm font-semibold bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors">
            {saveMut.isPending ? 'Saving…' : 'ADD CHARGE'}
          </button>
        </div>
      </div>
    </div>
  )
}
