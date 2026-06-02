import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { prefixApi, type DocumentType, type SequenceResetPolicy } from '../../../services/prefix/prefixApi'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'

const DOC_TYPE_LABELS: Record<DocumentType, string> = {
  BILL: 'Bill', RECEIPT: 'Receipt', DEPOSIT: 'Deposit', REFUND: 'Refund',
  LAB_ORDER: 'Lab Order', IP_ORDER: 'IP Order (Radiology)', SAMPLE: 'Sample Collection',
  PHARMACY_SALE: 'Pharmacy Sale', PAYMENT: 'Payment', PURCHASE_RECEIPT: 'GRN',
  PURCHASE_RETURN: 'Purchase Return', PURCHASE_ORDER: 'Purchase Order', PATIENT: 'Patient Number',
  REPLENISHMENT: 'Stock Indent', INVENTORY_ISSUE: 'Stock Issue',
  CONSUMPTION: 'Stock Consumption', ADVANCE_REFUND: 'Advance Refund',
}

export default function PrefixConfigPage() {
  const qc = useQueryClient()
  const { data: generators, isLoading } = useQuery({
    queryKey: ['prefix', 'summary'],
    queryFn: () => prefixApi.getSummary(),
  })

  const [showForm, setShowForm] = useState(false)
  const [selectedType, setSelectedType] = useState<DocumentType>('BILL')
  const [prefix, setPrefix] = useState('')
  const [resetPolicy, setResetPolicy] = useState<SequenceResetPolicy>('NEVER')

  const invalidate = () => qc.invalidateQueries({ queryKey: ['prefix'] })

  const createMutation = useMutation({
    mutationFn: () => prefixApi.create(prefix, selectedType, resetPolicy),
    onSuccess: () => { invalidate(); setShowForm(false); setPrefix(''); toast({ title: 'Sequence generator created', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Create failed', description: e.message, variant: 'destructive' }),
  })

  const activateMutation = useMutation({
    mutationFn: (id: string) => prefixApi.activate(id),
    onSuccess: () => { invalidate(); toast({ title: 'Generator activated', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const deactivateMutation = useMutation({
    mutationFn: (id: string) => prefixApi.deactivate(id),
    onSuccess: () => { invalidate(); toast({ title: 'Generator deactivated', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  return (
    <div className="space-y-5 max-w-4xl">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Sequence Generators</h2>
          <p className="text-sm text-gray-500 mt-0.5">Configure prefix and numbering for all document types. All types must be configured and activated before the system can generate bills, patient numbers, etc.</p>
        </div>
        <button onClick={() => setShowForm(v => !v)}
          className="px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 transition-colors">
          + New Generator
        </button>
      </div>

      {/* Create form */}
      {showForm && (
        <div className="bg-blue-50 border border-blue-200 rounded-xl p-5 space-y-4" role="region" aria-label="Create sequence generator">
          <h3 className="text-sm font-semibold text-blue-900">New Sequence Generator</h3>
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Document Type</label>
              <select value={selectedType} onChange={e => setSelectedType(e.target.value as DocumentType)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                {(Object.keys(DOC_TYPE_LABELS) as DocumentType[]).map(t => (
                  <option key={t} value={t}>{DOC_TYPE_LABELS[t]}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Prefix String</label>
              <input value={prefix} onChange={e => setPrefix(e.target.value.toUpperCase())}
                placeholder="e.g. OP- or BILL-"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                aria-label="Prefix string" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Reset Policy</label>
              <select value={resetPolicy} onChange={e => setResetPolicy(e.target.value as SequenceResetPolicy)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="NEVER">Never reset</option>
                <option value="FISCAL_YEAR">Reset each fiscal year (Apr 1)</option>
                <option value="CALENDAR_YEAR">Reset each calendar year (Jan 1)</option>
              </select>
            </div>
          </div>
          {prefix && (
            <p className="text-xs text-blue-600 font-medium">
              Preview: <code className="bg-blue-100 px-1.5 py-0.5 rounded">{prefix}00001</code>, <code className="bg-blue-100 px-1.5 py-0.5 rounded">{prefix}00002</code>, …
            </p>
          )}
          <div className="flex gap-3">
            <button onClick={() => createMutation.mutate()} disabled={!prefix.trim() || createMutation.isPending}
              className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
              {createMutation.isPending ? 'Creating…' : 'Create Generator'}
            </button>
            <button onClick={() => setShowForm(false)}
              className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Generator table */}
      {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Loading…</p>}

      {generators && (
        <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
          <table className="w-full text-sm" aria-label="Sequence generators">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                <th className="px-4 py-3 font-semibold text-gray-600">Document Type</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Prefix</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Counter</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Reset Policy</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Status</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {generators.map(gen => (
                <tr key={gen.documentType} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-medium text-gray-900">{DOC_TYPE_LABELS[gen.documentType]}</td>
                  <td className="px-4 py-3 font-mono text-sm text-gray-700">
                    {gen.prefixString
                      ? <code className="bg-gray-100 px-1.5 py-0.5 rounded">{gen.prefixString}</code>
                      : <span className="text-red-500 text-xs font-medium">⚠ Not configured</span>}
                  </td>
                  <td className="px-4 py-3 text-gray-600">{gen.id ? gen.currentCounter.toLocaleString() : '—'}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs capitalize">
                    {gen.resetPolicy ? gen.resetPolicy.toLowerCase().replace('_', ' ') : '—'}
                  </td>
                  <td className="px-4 py-3">
                    {gen.id ? (
                      <span className={cn('inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border',
                        gen.activated
                          ? 'bg-green-50 text-green-700 border-green-200'
                          : 'bg-gray-100 text-gray-600 border-gray-200')}>
                        {gen.activated ? 'Active' : 'Inactive'}
                      </span>
                    ) : (
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border bg-red-50 text-red-700 border-red-200">
                        Missing
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end">
                      {gen.id && (
                        gen.activated ? (
                          <button onClick={() => deactivateMutation.mutate(gen.id!)}
                            disabled={deactivateMutation.isPending}
                            className="px-3 py-1 bg-amber-50 text-amber-700 border border-amber-200 rounded text-xs font-semibold hover:bg-amber-100 disabled:opacity-40 transition-colors">
                            Deactivate
                          </button>
                        ) : (
                          <button onClick={() => activateMutation.mutate(gen.id!)}
                            disabled={activateMutation.isPending}
                            className="px-3 py-1 bg-green-50 text-green-700 border border-green-200 rounded text-xs font-semibold hover:bg-green-100 disabled:opacity-40 transition-colors">
                            Activate
                          </button>
                        )
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="px-4 py-3 border-t border-gray-100 text-xs text-gray-400">
            {generators.filter(g => !g.id).length > 0
              ? `⚠ ${generators.filter(g => !g.id).length} document type(s) not yet configured — billing will fail for these types`
              : '✓ All document types configured'}
          </div>
        </div>
      )}
    </div>
  )
}
