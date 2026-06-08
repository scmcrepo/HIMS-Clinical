import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, Field, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow, StatusBadge } from '../MasterSharedUI';
import { prefixApi, DocumentType, SequenceResetPolicy, SequenceGenerator } from '../../../../services/prefix/prefixApi';

export default function PrefixTab() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<SequenceGenerator | null>(null)
  
  const { data: pageData, isLoading } = useQuery({
    queryKey: ['prefix', page, search],
    queryFn: () => prefixApi.getPaginated({ start: page * 10, limit: 10, ...(search ? { value: search } : {}) })
  })
  
  const generators = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0

  const DOC_LABELS: Record<DocumentType, string> = {
    BILL: 'Bill', RECEIPT: 'Receipt', DEPOSIT: 'Deposit', REFUND: 'Refund',
    LAB_ORDER: 'Lab Order', IP_ORDER: 'IP Order', SAMPLE: 'Sample',
    PHARMACY_SALE: 'Pharmacy Sale', PAYMENT: 'Payment', PURCHASE_RECEIPT: 'GRN',
    PURCHASE_RETURN: 'Purchase Return', PURCHASE_ORDER: 'Purchase Order', PATIENT: 'Patient No.',
    REPLENISHMENT: 'Stock Indent', INVENTORY_ISSUE: 'Stock Issue', CONSUMPTION: 'Consumption', ADVANCE_REFUND: 'Advance Refund',
  }

  const blank = { documentType: 'BILL' as DocumentType, prefixString: '', resetPolicy: 'NEVER' as SequenceResetPolicy, status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE' }
  const [form, setForm] = useState(blank)

  const mut = useMutation({
    mutationFn: async () => {
      if (editing?.id) {
        const res = await prefixApi.update(editing.id, form.prefixString, form.documentType, form.resetPolicy);
        if (form.status === 'ACTIVE' && !editing.activated) {
          await prefixApi.activate(editing.id);
        } else if (form.status === 'INACTIVE' && editing.activated) {
          await prefixApi.deactivate(editing.id);
        }
        return res;
      } else {
        return prefixApi.create(form.prefixString, form.documentType, form.resetPolicy);
      }
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['prefix'] }); reset(); toast({ title: editing ? 'Prefix updated successfully' : 'Prefix saved successfully', variant: 'success' }) },
    onError: (e: any) => toast({ title: 'Error', description: e.response?.data?.message || e.message, variant: 'destructive' }),
  })

  function reset() { setShowForm(false); setEditing(null); setForm(blank) }
  function startEdit(g: SequenceGenerator) { setEditing(g); setForm({ documentType: g.documentType, prefixString: g.prefixString || '', resetPolicy: g.resetPolicy || 'NEVER', status: g.activated ? 'ACTIVE' : 'INACTIVE' }); setShowForm(true) }

  return (
    <Section
      title="Sequence Prefixes"
      description="Number prefix and reset policy per document type"
      action={
        <div className="flex gap-4 items-center">
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="New Generator" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">{editing ? 'Update Prefix' : 'Create Prefix'}</h3>
              <button onClick={reset} className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            </div>
            <div className="p-6 overflow-y-auto space-y-6 flex-1 bg-gray-50/50">
              <div className="grid grid-cols-1 gap-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                <Field label="Document Type">
                  <select className={inputCls} value={form.documentType} onChange={e => setForm(f => ({ ...f, documentType: e.target.value as DocumentType }))} disabled={!!editing}>
                    {(Object.keys(DOC_LABELS) as DocumentType[]).map(t => <option key={t} value={t}>{DOC_LABELS[t]}</option>)}
                  </select>
                </Field>
                <Field label="Prefix String *"><input className={inputCls} value={form.prefixString} onChange={e => setForm(f => ({ ...f, prefixString: e.target.value.toUpperCase() }))} /></Field>
                <Field label="Reset Policy">
                  <select className={inputCls} value={form.resetPolicy} onChange={e => setForm(f => ({ ...f, resetPolicy: e.target.value as SequenceResetPolicy }))}>
                    <option value="NEVER">Never</option><option value="FISCAL_YEAR">Fiscal Year</option><option value="CALENDAR_YEAR">Calendar Year</option>
                  </select>
                </Field>
                {editing && (
                  <div className="border-t border-gray-100 pt-4 mt-2">
                    <span className="block text-xs font-bold text-gray-700 uppercase tracking-wider mb-2">Status</span>
                    <div className="flex gap-2 mt-1">
                      <button type="button" onClick={() => setForm(f => ({ ...f, status: 'ACTIVE' }))} className={cn("px-4 py-2 text-xs font-bold rounded-lg border transition-all duration-150", form.status === 'ACTIVE' ? "bg-neutral-600 text-white border-neutral-600 shadow-sm" : "bg-white text-gray-700 border-gray-200 hover:bg-gray-50")}>Active</button>
                      <button type="button" onClick={() => setForm(f => ({ ...f, status: 'INACTIVE' }))} className={cn("px-4 py-2 text-xs font-bold rounded-lg border transition-all duration-150", form.status === 'INACTIVE' ? "bg-red-600 text-white border-red-600 shadow-sm" : "bg-white text-gray-700 border-gray-200 hover:bg-gray-50")}>Inactive</button>
                    </div>
                  </div>
                )}
              </div>
            </div>
            <div className="px-6 py-4 border-t bg-white flex justify-end gap-3">
              <button onClick={reset}
                className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors">
                Cancel
              </button>
              <button onClick={() => mut.mutate()} disabled={!form.prefixString.trim() || mut.isPending}
                className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Prefix' : 'Create Prefix')}
              </button>
            </div>
          </div>
        </div>
      )}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden flex flex-col mt-4">
        <Table headers={['S.NO', 'DOCUMENT TYPE', 'PREFIX', 'COUNTER', 'RESET POLICY', 'STATUS', 'ACTIONS']} className="border-0 shadow-none rounded-none border-b border-gray-200">
          {isLoading ? <LoadingRow /> : generators.length === 0 ? <EmptyState label="generators" /> :
            generators.map((g, i) => (
              <tr key={g.documentType + (g.id || '')} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium text-gray-500">{page * 10 + i + 1}</td>
                <td className="px-4 py-3 font-medium text-gray-900">{DOC_LABELS[g.documentType]}</td>
                <td className="px-4 py-3 font-mono text-sm">{g.prefixString ? <code className="bg-gray-100 px-1.5 py-0.5 rounded">{g.prefixString}</code> : <span className="text-red-500 text-xs">Not set</span>}</td>
                <td className="px-4 py-3 text-gray-600">{g.id ? g.currentCounter : '—'}</td>
                <td className="px-4 py-3 text-gray-500 text-xs capitalize">{g.resetPolicy?.toLowerCase().replace('_', ' ') ?? '—'}</td>
                <td className="px-4 py-3">
                  {g.id ? <StatusBadge active={g.activated} /> : <span className="text-red-500 text-xs font-medium">Missing</span>}
                </td>
                <td className="px-4 py-3 text-center">
                  <div className="flex items-center justify-center gap-3">
                    {g.id && <EditBtn onClick={() => startEdit(g)} />}
                  </div>
                </td>
              </tr>
            ))}
        </Table>
        <div className="px-6 py-4 bg-gray-50 flex items-center justify-between">
          <div className="text-xs text-gray-500">
            Page <span className="font-medium text-gray-900">{page + 1}</span> of{' '}
            <span className="font-medium text-gray-900">{totalPages || 1}</span>
            <span className="ml-2">· {pageData?.totalElements || 0} total records</span>
          </div>
          <div className="flex items-center gap-1">
            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0 || isLoading} className="p-1.5 text-gray-500 hover:text-neutral-600 hover:bg-neutral-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" /></svg>
            </button>
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              let pageNum = i
              if (totalPages > 5 && page > 2) pageNum = Math.min(page - 2 + i, totalPages - 5 + i)
              return (
                <button key={pageNum} onClick={() => setPage(pageNum)} className={cn('min-w-[32px] h-8 flex items-center justify-center rounded text-xs font-semibold transition-all', page === pageNum ? 'bg-neutral-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-100')}>
                  {pageNum + 1}
                </button>
              )
            })}
            <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1 || isLoading} className="p-1.5 text-gray-500 hover:text-neutral-600 hover:bg-neutral-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" /></svg>
            </button>
          </div>
        </div>
      </div>
    </Section>
  )
}