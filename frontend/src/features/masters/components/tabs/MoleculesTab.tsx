
import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, Field, FormShell, StatusBadge, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow } from '../MasterSharedUI';
import { moleculeApi, Molecule } from '../../../../services/masters/masterApi';

export default function MoleculesTab() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')

  // Debounce search input
  useEffect(() => {
    const t = setTimeout(() => {
      setQuery(search)
      setPage(0)
    }, 400)
    return () => clearTimeout(t)
  }, [search])

  const { data, isLoading } = useQuery({
    queryKey: ['molecules', 'page', page, query],
    queryFn: () => moleculeApi.getPaginated({ start: page * 20, limit: 20, ...(query ? { value: query } : {}) })
  })

  const molecules = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Molecule | null>(null)
  const blank = { name: '', status: 1 }
  const [form, setForm] = useState(blank)

  const mut = useMutation({
    mutationFn: () => editing ? moleculeApi.update(editing.id, form) : moleculeApi.create(form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['molecules'] }); reset(); toast({ title: editing ? 'Molecule updated successfully' : 'Molecule saved successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })
  function reset() { setShowForm(false); setEditing(null); setForm(blank) }
  function startEdit(r: Molecule) { setEditing(r); setForm({ name: r.name, status: r.status }); setShowForm(true) }

  return (
    <Section
      title="Molecules"
      description="Active pharmaceutical ingredients for drug items"
      action={<AddButton label="Add Molecule" onClick={() => { reset(); setShowForm(true) }} />}
    >
      {showForm && (
        <FormShell title={editing ? 'Update Molecule' : 'Create Molecule'} onCancel={reset} onSave={() => mut.mutate()} saving={mut.isPending} canSave={!!form.name}>
          <Field label="Molecule Name *"><input className={inputCls} value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} /></Field>
          {editing && (
            <Field label="Status">
              <select
                className={inputCls}
                value={form.status}
                onChange={e => setForm(f => ({ ...f, status: Number(e.target.value) }))}
              >
                <option value={1}>Active</option>
                <option value={0}>Inactive</option>
              </select>
            </Field>
          )}
        </FormShell>
      )}

      <div className="mb-4 relative max-w-md">
        <div className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" /></svg>
        </div>
        <input
          type="text"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full pl-9 pr-8 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all shadow-sm"
        />
        {search && (
          <button
            onClick={() => setSearch('')}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
          </button>
        )}
      </div>

      <Table headers={['Name', 'Status', '']}>
        {isLoading ? <LoadingRow /> : molecules.length === 0 ? <EmptyState label="molecules" /> :
          molecules.map(r => (
            <tr key={r.id} className="hover:bg-gray-50">
              <td className="px-4 py-3 font-medium text-gray-900">{r.name}</td>
              <td className="px-4 py-3"><StatusBadge active={r.status === 1 || (r.status as any) === 'ACTIVE'} /></td>
              <td className="px-4 py-3 text-right"><EditBtn onClick={() => startEdit(r)} /></td>
            </tr>
          ))}
      </Table>

      <div className="flex items-center justify-between px-6 py-4 bg-gray-50 border border-t-0 border-gray-100 rounded-b-lg text-xs font-bold text-gray-500 mt-2">
        <div className="text-xs text-gray-500 font-medium normal-case">
          Page <span className="font-semibold text-gray-900">{page + 1}</span> of{' '}
          <span className="font-semibold text-gray-900">{totalPages || 1}</span>
          {totalElements !== undefined && (
            <span className="ml-2">· {totalElements} total molecules</span>
          )}
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0 || isLoading}
            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>

          {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
            let pageNum = i
            if (totalPages > 5 && page > 2) {
              pageNum = Math.min(page - 2 + i, totalPages - 5 + i)
            }
            return (
              <button
                key={pageNum}
                onClick={() => setPage(pageNum)}
                className={cn(
                  'min-w-[28px] h-7 flex items-center justify-center rounded text-xs font-semibold transition-all',
                  page === pageNum ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-100'
                )}
              >
                {pageNum + 1}
              </button>
            )
          })}

          <button
            onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1 || isLoading}
            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>
    </Section>
  )
}