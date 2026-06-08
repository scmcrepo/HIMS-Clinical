import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, Field, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow, StatusBadge } from '../MasterSharedUI';
import { accountUnitApi, AccountUnit } from '../../../../services/masters/masterApi';

export default function AccountUnitTab() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<AccountUnit | null>(null)
  
  const { data: pageData, isLoading } = useQuery({
    queryKey: ['accountUnits', page, search],
    queryFn: () => accountUnitApi.getPaginated({ start: page * 10, limit: 10, ...(search ? { value: search } : {}) })
  })
  
  const accountUnits = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0

  const blank = { name: '', code: '', type: 'INCOME', status: 1 }
  const [form, setForm] = useState<Omit<AccountUnit, 'id'>>(blank)

  const mut = useMutation({
    mutationFn: () => editing ? accountUnitApi.update(editing.id, form) : accountUnitApi.create(form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accountUnits'] }); reset(); toast({ title: editing ? 'Account Unit updated successfully' : 'Account Unit saved successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  function reset() { setShowForm(false); setEditing(null); setForm(blank) }
  function startEdit(r: AccountUnit) { 
    setEditing(r); 
    setForm({ name: r.name, code: r.code || '', type: r.type || 'INCOME', status: r.status }); 
    setShowForm(true) 
  }

  return (
    <Section
      title="Account Units"
      description="GL account categories for financial reporting"
      action={
        <div className="flex gap-4 items-center">
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="Add Account Unit" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">{editing ? 'Update Account Unit' : 'Create Account Unit'}</h3>
              <button onClick={reset} className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            </div>
            <div className="p-6 overflow-y-auto space-y-6 flex-1 bg-gray-50/50">
              <div className="bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                <Field label="Unit Name">
                  <input className={inputCls} value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
                </Field>
              </div>
            </div>
            <div className="px-6 py-4 border-t bg-white flex justify-end gap-3">
              <button onClick={reset}
                className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors">Cancel</button>
              <button onClick={() => mut.mutate()} disabled={!form.name || mut.isPending}
                className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors">
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Account Unit' : 'Create Account Unit')}
              </button>
            </div>
          </div>
        </div>
      )}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden flex flex-col mt-4">
        <Table headers={['S.NO', 'Unit Name', 'STATUS', 'ACTION']} className="border-0 shadow-none rounded-none border-b border-gray-200">
          {isLoading ? <LoadingRow /> : accountUnits.length === 0 ? <EmptyState label="account units" /> :
            accountUnits.map((r: any, i: number) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium text-gray-500">{page * 10 + i + 1}</td>
                <td className="px-4 py-3 font-medium text-gray-900">{r.name}</td>
                <td className="px-4 py-3"><StatusBadge active={r.status === 1 || (r.status as any) === 'ACTIVE'} /></td>
                <td className="px-4 py-3"><EditBtn onClick={() => startEdit(r)} /></td>
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