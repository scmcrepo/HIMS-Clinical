/**
 * DiagnosticOrderTab.tsx
 * Diagnostic Order clinical tab — works for both OP (inline) and IP (modal).
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../hooks/useToast'
import { QuickAddPanel } from './QuickAddPanel'
import {
  opDiagnosticApi, ipDiagnosticApi, diagTestSearchApi,
  type DiagnosticOrderLinePayload, type DiagnosticOrderResponse,
} from '../../../services/opip/opipApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { consultantApi } from '../../../services/consultant/consultantApi'

interface Props {
  encounterId:   string
  mode:          'OP' | 'IP'
  consultantId?: string
  readOnly?:     boolean
}

// ─────────────────────────────────────────────────────────────────────────────

export function DiagnosticOrderTab({ encounterId, mode, consultantId, readOnly }: Props) {
  const qc = useQueryClient()
  const [showModal, setShowModal] = useState(false)

  const { data: orders = [], isLoading } = useQuery({
    queryKey: ['diagnostic-orders', encounterId],
    queryFn:  () => mode === 'OP'
      ? opDiagnosticApi.list(encounterId)
      : ipDiagnosticApi.list(encounterId),
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['diagnostic-orders', encounterId] })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-gray-800">Diagnostic Orders</h3>
        {mode === 'IP' && !readOnly && (
          <button
            onClick={() => setShowModal(true)}
            className="px-3 py-1.5 text-xs font-semibold bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors">
            + ADD DIAGNOSTIC ORDER
          </button>
        )}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-6">Loading…</p>
      ) : orders.length === 0 ? (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg px-4 py-3 text-xs text-yellow-800">
          No Diagnostics Order! There is no Diagnostics order for the visit.
        </div>
      ) : (
        <div className="space-y-3">
          {orders.map((order, i) => (
            <DiagnosticOrderCard key={order.id ?? i} order={order} />
          ))}
        </div>
      )}

      {/* OP: inline entry */}
      {mode === 'OP' && !readOnly && (
        <InlineDiagnosticForm
          encounterId={encounterId}
          consultantId={consultantId}
          onSaved={invalidate}
        />
      )}

      {mode === 'IP' && showModal && (
        <DiagnosticOrderModal
          encounterId={encounterId}
          consultantId={consultantId}
          onClose={() => setShowModal(false)}
          onSaved={() => { invalidate(); setShowModal(false) }}
        />
      )}
    </div>
  )
}

// ─── Card ─────────────────────────────────────────────────────────────────────

const STATUS_COLOR: Record<string, string> = {
  ORDERED:   'bg-amber-50 text-amber-700',
  COLLECTED: 'bg-blue-50 text-blue-700',
  RESULTED:  'bg-green-50 text-green-700',
}

function DiagnosticOrderCard({ order }: { order: DiagnosticOrderResponse }) {
  return (
    <div className="border border-gray-200 rounded-xl bg-white overflow-hidden">
      <div className="px-4 py-2 bg-gray-50 border-b border-gray-100 flex items-center justify-between">
        <span className="text-xs text-gray-500">{formatDateTime(order.orderedAt)}</span>
        {order.requestedByName && (
          <span className="text-xs text-blue-600 font-medium">Dr. {order.requestedByName}</span>
        )}
      </div>
      <ul className="divide-y divide-gray-50">
        {order.items.map((line, i) => (
          <li key={line.id ?? i} className="px-4 py-2 flex items-center justify-between text-xs">
            <div>
              <span className="font-medium text-gray-900">{line.testName}</span>
              {line.category && (
                <span className="ml-2 text-gray-400">({line.category})</span>
              )}
            </div>
            <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${STATUS_COLOR[line.status] ?? 'bg-gray-100 text-gray-600'}`}>
              {line.status}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

// ─── OP Inline Form ───────────────────────────────────────────────────────────

function InlineDiagnosticForm({ encounterId, consultantId, onSaved }:
  { encounterId: string; consultantId?: string; onSaved: () => void }) {

  const [tests, setTests] = useState<DiagnosticOrderLinePayload[]>([])
  const [query, setQuery] = useState('')
  const [currentTest, setCurrentTest] = useState<DiagnosticOrderLinePayload | null>(null)

  const { data: results = [] } = useQuery({
    queryKey: ['test-search', query],
    queryFn:  () => diagTestSearchApi.search(query),
    enabled:  query.length >= 2,
  })

  const saveMut = useMutation({
    mutationFn: () => opDiagnosticApi.save(encounterId, { items: tests }),
    onSuccess: () => {
      toast({ title: 'Diagnostic order saved', variant: 'success' })
      setTests([])
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const addTest = (test: DiagnosticOrderLinePayload) => {
    if (!tests.find(t => t.testName === test.testName)) {
      setTests(ts => [...ts, test])
    }
    setQuery('')
    setCurrentTest(null)
  }

  const removeTest = (idx: number) => setTests(ts => ts.filter((_, i) => i !== idx))

  return (
    <div className="border-t border-gray-200 pt-4">
      <div className="flex gap-4">
        <div className="flex-1 min-w-0 space-y-3">
          <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wide">Add Tests</h4>

          {/* Test search */}
          <div className="flex gap-2 relative">
            <div className="relative flex-1">
              <input
                value={query}
                onChange={e => setQuery(e.target.value)}
                placeholder="Search test name (min. 2 chars)…"
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {query.length >= 2 && results.length > 0 && (
                <ul className="absolute z-20 top-full left-0 right-0 bg-white border border-gray-200 rounded-lg shadow-lg max-h-40 overflow-y-auto">
                  {results.map(t => (
                    <li key={t.id}>
                      <button className="w-full text-left px-3 py-2 text-xs hover:bg-blue-50"
                        onClick={() => addTest({ diagnosticTestId: t.id, testName: t.name, category: t.category })}>
                        <span className="font-medium">{t.name}</span>
                        {t.category && <span className="text-gray-400 ml-2">({t.category})</span>}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {/* Added tests list */}
          {tests.length > 0 && (
            <ul className="space-y-1">
              {tests.map((t, i) => (
                <li key={i} className="flex items-center justify-between px-3 py-1.5 bg-blue-50 rounded-lg text-xs">
                  <span className="font-medium text-blue-800">{t.testName}</span>
                  <button onClick={() => removeTest(i)} className="text-blue-400 hover:text-red-500">✕</button>
                </li>
              ))}
            </ul>
          )}

          {tests.length > 0 && (
            <button
              onClick={() => saveMut.mutate()}
              disabled={saveMut.isPending}
              className="px-4 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
              {saveMut.isPending ? 'Saving…' : 'SAVE ORDER'}
            </button>
          )}
        </div>

        <QuickAddPanel
          mode="TEST"
          consultantId={consultantId}
          onAddTest={test => addTest(test)}
        />
      </div>
    </div>
  )
}

// ─── IP Modal ────────────────────────────────────────────────────────────────

function DiagnosticOrderModal({ encounterId, consultantId, onClose, onSaved }:
  { encounterId: string; consultantId?: string; onClose: () => void; onSaved: () => void }) {

  const [tests, setTests] = useState<DiagnosticOrderLinePayload[]>([])
  const [query, setQuery] = useState('')
  const [requestedById, setRequestedById] = useState(consultantId ?? '')

  const { data: results = [] } = useQuery({
    queryKey: ['test-search', query],
    queryFn:  () => diagTestSearchApi.search(query),
    enabled:  query.length >= 2,
  })
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'], queryFn: consultantApi.getAll,
  })

  const saveMut = useMutation({
    mutationFn: () => ipDiagnosticApi.add(encounterId, {
      items: tests, requestedById: requestedById || undefined,
    }),
    onSuccess: () => {
      toast({ title: 'Diagnostic order added', variant: 'success' })
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const addTest = (test: DiagnosticOrderLinePayload) => {
    if (!tests.find(t => t.testName === test.testName)) setTests(ts => [...ts, test])
    setQuery('')
  }

  return (
    <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl my-8">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h3 className="text-base font-bold text-gray-900">Add Diagnostic Order</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>

        <div className="p-6 flex gap-6">
          <div className="flex-1 min-w-0 space-y-4">
            {/* Requested By */}
            <div className="flex items-center gap-3">
              <label className="text-xs font-medium text-gray-600 shrink-0">Requested By</label>
              <select value={requestedById} onChange={e => setRequestedById(e.target.value)}
                className="flex-1 px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="">— Select Consultant —</option>
                {consultants.map(c => (
                  <option key={c.id} value={c.id}>
                    {c.salutation ? c.salutation + ' ' : ''}{c.firstName} {c.lastName}
                  </option>
                ))}
              </select>
            </div>

            {/* Test search */}
            <div className="relative">
              <input value={query} onChange={e => setQuery(e.target.value)}
                placeholder="Search test name (min. 2 chars)…"
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              {query.length >= 2 && results.length > 0 && (
                <ul className="absolute z-20 top-full left-0 right-0 bg-white border border-gray-200 rounded-lg shadow-lg max-h-40 overflow-y-auto">
                  {results.map(t => (
                    <li key={t.id}>
                      <button className="w-full text-left px-3 py-2 text-xs hover:bg-blue-50"
                        onClick={() => addTest({ diagnosticTestId: t.id, testName: t.name, category: t.category })}>
                        <span className="font-medium">{t.name}</span>
                        {t.category && <span className="text-gray-400 ml-2">({t.category})</span>}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {tests.length > 0 && (
              <ul className="space-y-1">
                {tests.map((t, i) => (
                  <li key={i} className="flex items-center justify-between px-3 py-1.5 bg-blue-50 rounded-lg text-xs">
                    <span className="font-medium text-blue-800">{t.testName}</span>
                    <button onClick={() => setTests(ts => ts.filter((_, j) => j !== i))} className="text-blue-400 hover:text-red-500">✕</button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <QuickAddPanel
            mode="TEST"
            consultantId={consultantId}
            onAddTest={addTest}
          />
        </div>

        <div className="flex justify-end gap-2 px-6 py-4 border-t border-gray-200">
          <button onClick={onClose}
            className="px-4 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
            CANCEL
          </button>
          <button onClick={() => saveMut.mutate()}
            disabled={saveMut.isPending || tests.length === 0}
            className="px-5 py-1.5 text-sm font-semibold bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors">
            {saveMut.isPending ? 'Saving…' : 'ADD DIAGNOSTIC'}
          </button>
        </div>
      </div>
    </div>
  )
}
