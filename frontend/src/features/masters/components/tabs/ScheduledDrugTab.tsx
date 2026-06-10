import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, Field, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow, StatusBadge } from '../MasterSharedUI';
import { scheduledDrugApi, type ScheduledDrugItem } from '../../../../services/masters/masterApi';

/**
 * ScheduledDrugTab — manage scheduled drug types.
 * Examples: "H", "H1"
 */
export default function ScheduledDrugTab() {
  const qc = useQueryClient()
  const [page, setPage]       = useState(0)
  const [search, setSearch]   = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<ScheduledDrugItem | null>(null)

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['scheduledDrugs', page, search],
    queryFn: () => scheduledDrugApi.getPaginated({ start: page * 10, limit: 10, ...(search ? { value: search } : {}) })
  })

  const items: ScheduledDrugItem[]  = pageData?.content ?? []
  const totalPages: number      = pageData?.totalPages ?? 0

  const blank = { name: '', status: 'ACTIVE' as any }
  const [form, setForm] = useState(blank)

  const mut = useMutation({
    mutationFn: () => editing
      ? scheduledDrugApi.update({ ...form, id: editing.id })
      : scheduledDrugApi.create(form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['scheduledDrugs'] })
      qc.invalidateQueries({ queryKey: ['scheduledDrugTypes'] })
      reset()
      toast({ title: editing ? 'Scheduled drug updated' : 'Scheduled drug created', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  function reset() { setShowForm(false); setEditing(null); setForm(blank) }
  function startEdit(r: ScheduledDrugItem) {
    setEditing(r)
    setForm({ name: r.name, status: r.status ?? 'ACTIVE' })
    setShowForm(true)
  }

  return (
    <Section
      title="Scheduled Drug Master"
      description="Define drug schedules — dynamically flows into Item forms and Reports"
      action={
        <div className="flex gap-3 items-center">
          <input type="search" value={search} onChange={e => { setSearch(e.target.value); setPage(0) }}
            placeholder="Search scheduled drug…"
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all w-52" />
          <AddButton label="Add Scheduled Drug" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl border border-gray-150 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white rounded-t-2xl">
              <h3 className="text-lg font-bold tracking-tight">{editing ? 'Update Scheduled Drug' : 'Add Scheduled Drug'}</h3>
              <button onClick={reset} className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            </div>
            <div className="p-6 overflow-visible space-y-4 flex-1 bg-gray-50/50 overflow-y-auto">
              <div className="space-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                <Field label="Scheduled Drug Name *">
                  <input className={inputCls} value={form.name}
                    placeholder='e.g. "H", "H1", "X", "G"'
                    onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
                  <p className="text-xs text-gray-400 mt-1">Drug schedule name to display in dropdowns</p>
                </Field>
                <Field label="Status">
                  <select className={inputCls} value={form.status ?? 'ACTIVE'}
                    onChange={e => setForm(f => ({ ...f, status: e.target.value }))}>
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                  </select>
                </Field>
              </div>
            </div>
            <div className="bg-gray-50 px-6 py-4 flex justify-end gap-3 border-t border-gray-150 rounded-b-2xl">
              <button type="button" onClick={reset} className="px-4 py-2 text-xs font-bold rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-100 active:bg-gray-200 transition-all focus:outline-none">Cancel</button>
              <button type="button" onClick={() => mut.mutate()} disabled={!form.name || mut.isPending}
                className="px-5 py-2 text-xs font-bold rounded-lg bg-neutral-600 hover:bg-neutral-700 text-white shadow-md active:scale-[0.98] transition-all disabled:opacity-50 disabled:pointer-events-none focus:outline-none">
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update' : 'Create')}
              </button>
            </div>
          </div>
        </div>
      )}

      <Table headers={['Scheduled Drug Name', 'Status', 'Actions']}>
        {isLoading ? (
          <LoadingRow />
        ) : items.length === 0 ? (
          <tr><td colSpan={3}><EmptyState label="No scheduled drugs found. Click Add to define one." /></td></tr>
        ) : (
          items.map(sd => (
            <tr key={sd.id} className="hover:bg-gray-50 transition-colors">
              <td className="px-4 py-3 text-sm font-semibold text-gray-900">{sd.name}</td>
              <td className="px-4 py-3"><StatusBadge active={(sd.status as any) === 'ACTIVE' || (sd.status as any) === 1} /></td>
              <td className="px-4 py-3">
                <div className="flex items-center gap-2">
                  <EditBtn onClick={() => startEdit(sd)} />
                </div>
              </td>
            </tr>
          ))
        )}
      </Table>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 pt-4">
          {Array.from({ length: totalPages }, (_, i) => (
            <button key={i} onClick={() => setPage(i)}
              className={cn('w-8 h-8 rounded-lg text-sm font-medium transition-colors',
                page === i ? 'bg-neutral-600 text-white' : 'border border-gray-200 text-gray-600 hover:bg-gray-50')}>
              {i + 1}
            </button>
          ))}
        </div>
      )}
    </Section>
  );
}
