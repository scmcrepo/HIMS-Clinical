
import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, labelCls, Field, StatusBadge, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow } from '../MasterSharedUI';
import { payerApi, Payer } from '../../../../services/masters/masterApi';

export default function PayersTab() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Payer | null>(null)

  const [page, setPage] = useState(0)
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(searchTerm)
      setPage(0)
    }, 400)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const blank = {
    name: '',
    payerType: 'COMPANY',
    status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE'
  }
  const [form, setForm] = useState(blank)

  const { data, isLoading } = useQuery({
    queryKey: ['payers', page, debouncedSearch],
    queryFn: () => payerApi.getPaginated({ start: page * 10, limit: 10, value: debouncedSearch }),
    placeholderData: (prev) => prev
  })

  const mut = useMutation({
    mutationFn: () => {
      const generatedCode = editing?.code || form.name.replace(/[^a-zA-Z0-9]/g, '').slice(0, 10).toUpperCase()
      const payload = {
        name: form.name,
        code: generatedCode,
        payerType: form.payerType,
        contactPerson: editing?.contactPerson || '',
        contactPhone: editing?.contactPhone || '',
        email: editing?.email || '',
        address: editing?.address || '',
        status: form.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'
      }
      return editing ? payerApi.update(editing.id, payload) : payerApi.create(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payers'] })
      reset()
      toast({ title: editing ? 'Payer updated successfully' : 'Payer saved successfully', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  function reset() {
    setShowForm(false)
    setEditing(null)
    setForm(blank)
  }

  function startEdit(r: Payer) {
    setEditing(r)
    setForm({
      name: r.name,
      payerType: r.payerType || 'COMPANY',
      status: r.status === 1 || r.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'
    })
    setShowForm(true)
  }

  const totalPages = data ? Math.ceil(data.totalElements / 10) : 0
  const payersList = data?.content ?? []

  return (
    <Section
      title="Payers / TPA"
      description="Insurance companies, TPAs, and government schemes"
      action={
        <div className="flex gap-4 items-center">
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => { setSearchTerm(e.target.value); setPage(0); }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="ADD PAYER" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >

      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">

            {/* Modal Header */}
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">
                {editing ? 'Edit PayerType' : 'Add PayerType'}
              </h3>
              <button
                onClick={reset}
                className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto space-y-4 flex-1 bg-gray-50/50">
              <div className="space-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">

                <Field label="Name *">
                  <input
                    type="text"
                    className={inputCls}
                    value={form.name}
                    onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                    required
                  />
                </Field>

                <Field label="Type *">
                  <select
                    className={inputCls}
                    value={form.payerType}
                    onChange={e => setForm(f => ({ ...f, payerType: e.target.value }))}
                    required
                  >
                    <option value="COMPANY">Company</option>
                    <option value="INSURANCE">Insurance</option>
                  </select>
                </Field>

                {editing && (
                  <div className="border-t border-gray-100 pt-4 mt-2">
                    <span className={labelCls}>Status</span>
                    <div className="flex gap-2 mt-1">
                      <button
                        type="button"
                        onClick={() => setForm(f => ({ ...f, status: 'ACTIVE' }))}
                        className={cn(
                          "px-4 py-2 text-xs font-bold rounded-lg border transition-all duration-150",
                          form.status === 'ACTIVE'
                            ? "bg-neutral-600 text-white border-neutral-600 shadow-sm"
                            : "bg-white text-gray-700 border-gray-200 hover:bg-gray-50"
                        )}
                      >
                        Active
                      </button>
                      <button
                        type="button"
                        onClick={() => setForm(f => ({ ...f, status: 'INACTIVE' }))}
                        className={cn(
                          "px-4 py-2 text-xs font-bold rounded-lg border transition-all duration-150",
                          form.status === 'INACTIVE'
                            ? "bg-red-600 text-white border-red-600 shadow-sm"
                            : "bg-white text-gray-700 border-gray-200 hover:bg-gray-50"
                        )}
                      >
                        InActive
                      </button>
                    </div>
                  </div>
                )}

              </div>
            </div>

            {/* Modal Footer */}
            <div className="bg-gray-50 px-6 py-4 flex justify-end gap-3 border-t border-gray-150">
              <button
                type="button"
                onClick={reset}
                className="px-4 py-2 text-xs font-bold rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-100 active:bg-gray-200 transition-all focus:outline-none"
              >Cancel</button>
              <button
                type="button"
                onClick={() => mut.mutate()}
                disabled={mut.isPending || !form.name}
                className="px-5 py-2 text-xs font-bold rounded-lg bg-neutral-600 hover:bg-neutral-700 text-white shadow-md active:scale-[0.98] transition-all disabled:opacity-50 disabled:pointer-events-none focus:outline-none"
              >
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Payer' : 'Create')}
              </button>
            </div>

          </div>
        </div>
      )}

      <Table headers={['S.NO', 'PAYER NAME', 'TYPE', 'STATUS', 'ACTION']}>
        {isLoading ? (
          <LoadingRow />
        ) : payersList.length === 0 ? (
          <EmptyState label="payers" />
        ) : (
          payersList.map((r, idx) => (
            <tr key={r.id} className="hover:bg-gray-50/70 transition-colors">
              <td className="px-4 py-3 text-xs font-bold text-gray-400">{page * 10 + idx + 1}</td>
              <td className="px-4 py-3 font-medium text-gray-900">{r.name}</td>
              <td className="px-4 py-3 text-gray-600 capitalize text-sm">{typeof r.payerType === 'string' ? r.payerType.toLowerCase() : '—'}</td>
              <td className="px-4 py-3"><StatusBadge active={r.status === 1 || r.status === 'ACTIVE'} /></td>
              <td className="px-4 py-3 text-center"><EditBtn onClick={() => startEdit(r)} /></td>
            </tr>
          ))
        )}
      </Table>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="flex justify-between items-center mt-4 bg-white p-3 rounded-lg border border-gray-150 shadow-sm">
          <span className="text-xs text-gray-500 font-medium">
            Page {page + 1} of {totalPages} ({data?.totalElements} total items)
          </span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-xs font-bold rounded-lg border border-gray-200 hover:bg-gray-50 disabled:opacity-50 transition-all select-none"
            >
              Previous
            </button>
            <button
              onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
              disabled={page === totalPages - 1}
              className="px-3 py-1.5 text-xs font-bold rounded-lg border border-gray-200 hover:bg-gray-50 disabled:opacity-50 transition-all select-none"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </Section>
  )
}