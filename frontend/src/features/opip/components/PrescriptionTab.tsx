/**
 * PrescriptionTab.tsx
 * Prescription clinical tab — works for both OP (inline) and IP (modal).
 */
import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../hooks/useToast'
import { QuickAddPanel } from './QuickAddPanel'
import {
  opPrescriptionApi, ipPrescriptionApi, drugSearchApi, instructionApi, routeApi,
  frequencyApi,
  type PrescriptionLinePayload, type PrescriptionResponse,
} from '../../../services/opip/opipApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { consultantApi } from '../../../services/consultant/consultantApi'

interface Props {
  encounterId:   string
  mode:          'OP' | 'IP'
  consultantId?: string   // for quick-add panels
  readOnly?:     boolean
}

const EMPTY_LINE: PrescriptionLinePayload = {
  drugItemId: '', drugName: '', frequency: '', duration: '',
  qty: 0, instructionId: '', instructionLabel: '', routeId: '', routeLabel: '', remarks: '',
}

// ─────────────────────────────────────────────────────────────────────────────

export function PrescriptionTab({ encounterId, mode, consultantId, readOnly }: Props) {
  const qc = useQueryClient()

  // Fetch existing prescriptions
  const { data: prescriptions = [], isLoading } = useQuery({
    queryKey: ['prescriptions', encounterId],
    queryFn:  () => mode === 'OP'
      ? opPrescriptionApi.list(encounterId)
      : ipPrescriptionApi.list(encounterId),
  })

  // IP modal visibility
  const [showModal, setShowModal] = useState(false)

  const invalidate = () => qc.invalidateQueries({ queryKey: ['prescriptions', encounterId] })

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-gray-800">Prescription</h3>
        {mode === 'IP' && !readOnly && (
          <button
            onClick={() => setShowModal(true)}
            className="px-3 py-1.5 text-xs font-semibold bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
          >
            + ADD PRESCRIPTION
          </button>
        )}
      </div>

      {/* List of existing prescriptions */}
      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-6">Loading…</p>
      ) : prescriptions.length === 0 ? (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg px-4 py-3 text-xs text-yellow-800">
          No Prescription! There is no Prescription for the visit.
        </div>
      ) : (
        <div className="space-y-3">
          {prescriptions.map((rx, i) => (
            <PrescriptionCard key={rx.id ?? i} rx={rx} />
          ))}
        </div>
      )}

      {/* OP: inline form below the list */}
      {mode === 'OP' && !readOnly && (
        <InlinePrescriptionForm
          encounterId={encounterId}
          consultantId={consultantId}
          onSaved={invalidate}
        />
      )}

      {/* IP: modal */}
      {mode === 'IP' && showModal && (
        <PrescriptionModal
          encounterId={encounterId}
          consultantId={consultantId}
          onClose={() => setShowModal(false)}
          onSaved={() => { invalidate(); setShowModal(false) }}
        />
      )}
    </div>
  )
}

// ─── Prescription Card ────────────────────────────────────────────────────────

function PrescriptionCard({ rx }: { rx: PrescriptionResponse }) {
  return (
    <div className="border border-gray-200 rounded-xl bg-white overflow-hidden">
      <div className="px-4 py-2 bg-gray-50 border-b border-gray-100 flex items-center justify-between">
        <span className="text-xs text-gray-500">{formatDateTime(rx.createdAt)}</span>
        {rx.requestedByName && (
          <span className="text-xs text-blue-600 font-medium">Dr. {rx.requestedByName}</span>
        )}
      </div>
      <table className="w-full text-xs">
        <thead>
          <tr className="border-b border-gray-100">
            {['Drug', 'Freq.', 'Duration', 'Qty', 'Instruction', 'Route'].map(h => (
              <th key={h} className="px-3 py-1.5 text-left text-gray-500 font-medium">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-50">
          {rx.items.map((line, i) => (
            <tr key={line.id ?? i} className="hover:bg-gray-50">
              <td className="px-3 py-1.5 font-medium text-gray-900">{line.drugName}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.frequency}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.duration}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.qty}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.instructionLabel ?? '—'}</td>
              <td className="px-3 py-1.5 text-gray-600">{line.routeLabel ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ─── Inline Form (OP) ────────────────────────────────────────────────────────

function InlinePrescriptionForm({ encounterId, consultantId, onSaved }:
  { encounterId: string; consultantId?: string; onSaved: () => void }) {

  const [lines, setLines] = useState<PrescriptionLinePayload[]>([{ ...EMPTY_LINE }])
  const [drugQuery, setDrugQuery] = useState('')
  const [activeLine, setActiveLine] = useState(0)

  const { data: drugResults = [] } = useQuery({
    queryKey: ['drug-search', drugQuery],
    queryFn:  () => drugSearchApi.search(drugQuery),
    enabled:  drugQuery.length >= 2,
  })

  const { data: instructions = [] } = useQuery({
    queryKey: ['instructions'], queryFn: instructionApi.list,
  })
  const { data: routes = [] } = useQuery({
    queryKey: ['routes'], queryFn: routeApi.list,
  })
  const { data: frequencies = [] } = useQuery({
    queryKey: ['frequencies'], queryFn: frequencyApi.list,
  })

  const saveMut = useMutation({
    mutationFn: () => opPrescriptionApi.save(encounterId, {
      items: lines.filter(l => l.drugName),
    }),
    onSuccess: () => {
      toast({ title: 'Prescription saved', variant: 'success' })
      setLines([{ ...EMPTY_LINE }])
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const updateLine = (idx: number, patch: Partial<PrescriptionLinePayload>) =>
    setLines(ls => ls.map((l, i) => i === idx ? { ...l, ...patch } : l))

  const addLine = () => setLines(ls => [...ls, { ...EMPTY_LINE }])

  const removeLine = (idx: number) =>
    setLines(ls => ls.filter((_, i) => i !== idx))

  const handleQuickAddDrug = (drug: Partial<PrescriptionLinePayload> & { qty?: number }) => {
    setLines(ls => [...ls, {
      ...EMPTY_LINE,
      drugItemId:      drug.drugItemId ?? '',
      drugName:        drug.drugName ?? '',
      frequency:       drug.frequency ?? '',
      duration:        drug.duration ?? '',
      qty:             drug.qty ?? 1,
      instructionLabel: drug.instructionLabel,
      routeLabel:      drug.routeLabel,
    }])
  }

  // Auto-calculate qty when freq/duration change
  useEffect(() => {
    setLines(ls => ls.map(l => {
      if (!l.frequency || !l.duration) return l
      const perDay = l.frequency.split('-').reduce((a, b) => a + (parseInt(b) || 0), 0)
      const days   = parseInt(l.duration) || 0
      return perDay > 0 && days > 0 ? { ...l, qty: perDay * days } : l
    }))
  }, [lines.map(l => `${l.frequency}|${l.duration}`).join(',')])

  return (
    <div className="border-t border-gray-200 pt-4">
      <div className="flex gap-4">
        {/* Drug entry table */}
        <div className="flex-1 min-w-0 space-y-2">
          <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wide">Add Drugs</h4>

          {lines.map((line, idx) => (
            <div key={idx} className="border border-gray-200 rounded-lg p-3 space-y-2 relative">
              {lines.length > 1 && (
                <button onClick={() => removeLine(idx)}
                  className="absolute top-2 right-2 text-gray-300 hover:text-red-500 text-sm">✕</button>
              )}

              {/* Drug autocomplete */}
              <div className="relative">
                <input
                  value={line.drugName}
                  onChange={e => { updateLine(idx, { drugName: e.target.value, drugItemId: '' }); setDrugQuery(e.target.value); setActiveLine(idx) }}
                  placeholder="Search drug name (min. 2 chars)…"
                  className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                {activeLine === idx && drugQuery.length >= 2 && drugResults.length > 0 && (
                  <ul className="absolute z-20 top-full left-0 right-0 bg-white border border-gray-200 rounded-lg shadow-lg max-h-40 overflow-y-auto">
                    {drugResults.map((d: any) => (
                      <li key={d.id}>
                        <button
                          className="w-full text-left px-3 py-2 text-xs hover:bg-blue-50"
                          onClick={() => {
                            updateLine(idx, { drugItemId: d.id, drugName: d.name })
                            setDrugQuery('')
                          }}>
                          <span className="font-medium">{d.name}</span>
                          {d.genericName && <span className="text-gray-400"> · {d.genericName}</span>}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <div className="grid grid-cols-4 gap-2">
                <div>
                  <label className="text-[10px] text-gray-500">Frequency</label>
                  <input list={`freq-list-op-${idx}`} value={line.frequency}
                    onChange={e => updateLine(idx, { frequency: e.target.value })}
                    placeholder="1-0-1"
                    className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500" />
                  <datalist id={`freq-list-op-${idx}`}>
                    {frequencies.map((f: any) => <option key={f.id} value={f.code}>{f.name}</option>)}
                  </datalist>
                </div>
                <div>
                  <label className="text-[10px] text-gray-500">Duration</label>
                  <input value={line.duration}
                    onChange={e => updateLine(idx, { duration: e.target.value })}
                    placeholder="5 days"
                    className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500" />
                </div>
                <div>
                  <label className="text-[10px] text-gray-500">QTY</label>
                  <input type="number" min="1" value={line.qty || ''}
                    onChange={e => updateLine(idx, { qty: parseInt(e.target.value) || 0 })}
                    className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500" />
                </div>
                <div>
                  <label className="text-[10px] text-gray-500">Instruction</label>
                  <select value={line.instructionId ?? ''}
                    onChange={e => {
                      const ins = instructions.find(i => i.id === e.target.value)
                      updateLine(idx, { instructionId: e.target.value, instructionLabel: ins?.name })
                    }}
                    className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500">
                    <option value="">—</option>
                    {instructions.map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-[10px] text-gray-500">Route</label>
                  <select value={line.routeId ?? ''}
                    onChange={e => {
                      const r = routes.find(rt => rt.id === e.target.value)
                      updateLine(idx, { routeId: e.target.value, routeLabel: r?.name })
                    }}
                    className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500">
                    <option value="">—</option>
                    {routes.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                  </select>
                </div>
                <div>
                  <label className="text-[10px] text-gray-500">Precautions/Remarks</label>
                  <input value={line.remarks ?? ''}
                    onChange={e => updateLine(idx, { remarks: e.target.value })}
                    className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500" />
                </div>
              </div>
            </div>
          ))}

          <div className="flex items-center gap-2 pt-1">
            <button onClick={addLine}
              className="text-xs text-blue-600 hover:underline">+ Add another drug</button>
            <span className="text-gray-300">|</span>
            <button
              onClick={() => saveMut.mutate()}
              disabled={saveMut.isPending || !lines.some(l => l.drugName)}
              className="px-4 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
              {saveMut.isPending ? 'Saving…' : 'SAVE PRESCRIPTION'}
            </button>
          </div>
        </div>

        {/* Quick-add panel */}
        <QuickAddPanel
          mode="DRUG"
          consultantId={consultantId}
          encounterId={encounterId}
          onAddDrug={handleQuickAddDrug}
        />
      </div>
    </div>
  )
}

// ─── IP Modal ────────────────────────────────────────────────────────────────

function PrescriptionModal({ encounterId, consultantId, onClose, onSaved }:
  { encounterId: string; consultantId?: string; onClose: () => void; onSaved: () => void }) {

  const [lines, setLines] = useState<PrescriptionLinePayload[]>([{ ...EMPTY_LINE }])
  const [requestedById, setRequestedById] = useState(consultantId ?? '')
  const [drugQuery, setDrugQuery] = useState('')
  const [activeLine, setActiveLine] = useState(0)

  const { data: drugResults = [] } = useQuery({
    queryKey: ['drug-search', drugQuery],
    queryFn:  () => drugSearchApi.search(drugQuery),
    enabled:  drugQuery.length >= 2,
  })
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'], queryFn: consultantApi.getAll,
  })
  const { data: instructions = [] } = useQuery({
    queryKey: ['instructions'], queryFn: instructionApi.list,
  })
  const { data: routes = [] } = useQuery({
    queryKey: ['routes'], queryFn: routeApi.list,
  })
  const { data: frequencies = [] } = useQuery({
    queryKey: ['frequencies'], queryFn: frequencyApi.list,
  })

  const saveMut = useMutation({
    mutationFn: () => ipPrescriptionApi.add(encounterId, {
      items: lines.filter(l => l.drugName),
      requestedById: requestedById || undefined,
    }),
    onSuccess: () => {
      toast({ title: 'Prescription added', variant: 'success' })
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const updateLine = (idx: number, patch: Partial<PrescriptionLinePayload>) =>
    setLines(ls => ls.map((l, i) => i === idx ? { ...l, ...patch } : l))

  const addLine = () => setLines(ls => [...ls, { ...EMPTY_LINE }])

  const handleQuickAddDrug = (drug: Partial<PrescriptionLinePayload> & { qty?: number }) => {
    setLines(ls => [...ls.filter(l => l.drugName), {
      ...EMPTY_LINE, drugItemId: drug.drugItemId ?? '', drugName: drug.drugName ?? '',
      frequency: drug.frequency ?? '', duration: drug.duration ?? '',
      qty: drug.qty ?? 1, instructionLabel: drug.instructionLabel, routeLabel: drug.routeLabel,
    }])
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl my-8">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h3 className="text-base font-bold text-gray-900">Add Prescription</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>

        <div className="p-6 flex gap-6">
          {/* Drug lines */}
          <div className="flex-1 min-w-0 space-y-3">
            {/* Requested By */}
            <div className="flex items-center gap-3">
              <label className="text-xs font-medium text-gray-600 shrink-0">Requested By</label>
              <select
                value={requestedById}
                onChange={e => setRequestedById(e.target.value)}
                className="flex-1 px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                <option value="">— Select Consultant —</option>
                {consultants.map(c => (
                  <option key={c.id} value={c.id}>
                    {c.salutation ? c.salutation + ' ' : ''}{c.firstName} {c.lastName}
                  </option>
                ))}
              </select>
            </div>

            {/* Drug entries */}
            {lines.map((line, idx) => (
              <div key={idx} className="border border-gray-200 rounded-lg p-3 space-y-2 relative">
                {lines.length > 1 && (
                  <button onClick={() => setLines(ls => ls.filter((_,i) => i !== idx))}
                    className="absolute top-2 right-2 text-gray-300 hover:text-red-500 text-sm">✕</button>
                )}
                {/* Drug autocomplete */}
                <div className="relative">
                  <input
                    value={line.drugName}
                    onChange={e => { updateLine(idx, { drugName: e.target.value, drugItemId: '' }); setDrugQuery(e.target.value); setActiveLine(idx) }}
                    placeholder="Search drug name…"
                    className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  {activeLine === idx && drugQuery.length >= 2 && drugResults.length > 0 && (
                    <ul className="absolute z-20 top-full left-0 right-0 bg-white border border-gray-200 rounded-lg shadow-lg max-h-40 overflow-y-auto">
                      {drugResults.map((d: any) => (
                        <li key={d.id}>
                          <button className="w-full text-left px-3 py-2 text-xs hover:bg-blue-50"
                            onClick={() => { updateLine(idx, { drugItemId: d.id, drugName: d.name }); setDrugQuery('') }}>
                            <span className="font-medium">{d.name}</span>
                            {d.genericName && <span className="text-gray-400"> · {d.genericName}</span>}
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                <div className="grid grid-cols-4 gap-2">
                  <div>
                    <label className="text-[10px] text-gray-500">Frequency</label>
                    <input list={`freq-list-ip-${idx}`} value={line.frequency}
                      onChange={e => updateLine(idx, { frequency: e.target.value })}
                      placeholder="1-0-1"
                      className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500" />
                    <datalist id={`freq-list-ip-${idx}`}>
                      {frequencies.map((f: any) => <option key={f.id} value={f.code}>{f.name}</option>)}
                    </datalist>
                  </div>
                  <div>
                    <label className="text-[10px] text-gray-500">Duration</label>
                    <input value={line.duration}
                      onChange={e => updateLine(idx, { duration: e.target.value })}
                      placeholder="5 days"
                      className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500" />
                  </div>
                  <div>
                    <label className="text-[10px] text-gray-500">QTY</label>
                    <input type="number" min="1" value={line.qty || ''}
                      onChange={e => updateLine(idx, { qty: parseInt(e.target.value) || 0 })}
                      className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500" />
                  </div>
                  <div>
                    <label className="text-[10px] text-gray-500">Instruction</label>
                    <select value={line.instructionId ?? ''}
                      onChange={e => { const ins = instructions.find(i => i.id === e.target.value); updateLine(idx, { instructionId: e.target.value, instructionLabel: ins?.name }) }}
                      className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500">
                      <option value="">—</option>
                      {instructions.map(i => <option key={i.id} value={i.id}>{i.name}</option>)}
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="text-[10px] text-gray-500">Route</label>
                    <select value={line.routeId ?? ''}
                      onChange={e => { const r = routes.find(rt => rt.id === e.target.value); updateLine(idx, { routeId: e.target.value, routeLabel: r?.name }) }}
                      className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500">
                      <option value="">—</option>
                      {routes.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="text-[10px] text-gray-500">Precautions/Remarks</label>
                    <input value={line.remarks ?? ''}
                      onChange={e => updateLine(idx, { remarks: e.target.value })}
                      className="w-full px-2 py-1 border border-gray-300 rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500" />
                  </div>
                </div>
              </div>
            ))}

            <button onClick={addLine} className="text-xs text-blue-600 hover:underline">+ Add drug</button>
          </div>

          {/* Quick-add panel */}
          <QuickAddPanel
            mode="DRUG"
            consultantId={consultantId}
            encounterId={encounterId}
            onAddDrug={handleQuickAddDrug}
          />
        </div>

        <div className="flex justify-end gap-2 px-6 py-4 border-t border-gray-200">
          <button onClick={onClose}
            className="px-4 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
            CANCEL
          </button>
          <button
            onClick={() => saveMut.mutate()}
            disabled={saveMut.isPending || !lines.some(l => l.drugName)}
            className="px-5 py-1.5 text-sm font-semibold bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors">
            {saveMut.isPending ? 'Saving…' : 'ADD PRESCRIPTION'}
          </button>
        </div>
      </div>
    </div>
  )
}
