import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, labelCls, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow, StatusBadge } from '../MasterSharedUI';
import { taxApi, Tax, TaxCategory } from '../../../../services/masters/masterApi';

export default function TaxTab() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Tax | null>(null)

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['taxes', page, search],
    queryFn: () => taxApi.getPaginated({ start: page * 10, limit: 10, ...(search ? { value: search } : {}) })
  })

  const types = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0

  const blank = { name: '', rate: 0, hsnCode: '', status: 1, categories: [] as TaxCategory[] }
  const [form, setForm] = useState(blank)
  const [newSubTaxType, setNewSubTaxType] = useState('')
  const [newSubTaxValue, setNewSubTaxValue] = useState('')

  const mut = useMutation({
    mutationFn: () => editing ? taxApi.update(editing.id, form) : taxApi.create(form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['taxes'] }); reset(); toast({ title: editing ? 'Tax updated successfully' : 'Tax saved successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  function reset() {
    setShowForm(false)
    setEditing(null)
    setForm(blank)
    setNewSubTaxType('')
    setNewSubTaxValue('')
  }

  const startEdit = async (r: Tax) => {
    setEditing(r)
    try {
      const details = await taxApi.getDetails(r.id)
      setForm({
        name: r.name,
        rate: r.rate,
        hsnCode: r.hsnCode ?? '',
        status: r.status,
        categories: details
      })
    } catch (err) {
      console.error(err)
      setForm({
        name: r.name,
        rate: r.rate,
        hsnCode: r.hsnCode ?? '',
        status: r.status,
        categories: []
      })
    }
    setShowForm(true)
  }

  const handleAddSubTax = () => {
    const val = parseFloat(newSubTaxValue)
    if (!newSubTaxType || !val || val <= 0) return
    const updatedCategories = [...(form.categories || []), { name: newSubTaxType, rate: val }]
    const totalRate = updatedCategories.reduce((sum, c) => sum + c.rate, 0)
    setForm(f => ({
      ...f,
      categories: updatedCategories,
      rate: totalRate
    }))
    setNewSubTaxType('')
    setNewSubTaxValue('')
  }

  const handleRemoveSubTax = (index: number) => {
    const updatedCategories = (form.categories || []).filter((_, idx) => idx !== index)
    const totalRate = updatedCategories.reduce((sum, c) => sum + c.rate, 0)
    setForm(f => ({
      ...f,
      categories: updatedCategories,
      rate: totalRate
    }))
  }

  return (
    <Section
      title="Tax Rates"
      description="GST and other tax slabs applied to items and services"
      action={
        <div className="flex gap-4 items-center">
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="Add Tax" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">{editing ? 'Update Tax' : 'Create Tax'}</h3>
              <button onClick={reset} className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            </div>
            <div className="p-6 overflow-y-auto space-y-6 flex-1 bg-gray-50/50">
              <div className="bg-white p-5 rounded-xl border border-gray-150 shadow-sm flex items-center gap-4 w-1/2 mx-auto">
                <span className={cn(labelCls, "mb-0 whitespace-nowrap")}>Name</span>
                <input className={inputCls} value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
              </div>

              <div className="bg-white p-0 rounded-xl border border-gray-150 shadow-sm">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50/50 border-b border-gray-100 text-xs font-semibold text-gray-600">
                    <tr>
                      <th className="px-6 py-3 text-center w-20">S.NO.</th>
                      <th className="px-6 py-3 text-center">TYPE</th>
                      <th className="px-6 py-3 text-center">VALUE</th>
                      <th className="px-6 py-3 text-center w-24">ACTION</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {(form.categories || []).map((cat, idx) => (
                      <tr key={idx} className="hover:bg-gray-50/50 transition-colors">
                        <td className="px-6 py-3 text-center text-gray-900 font-medium">{idx + 1}</td>
                        <td className="px-6 py-3 text-center text-gray-700">{cat.name}</td>
                        <td className="px-6 py-3 text-center text-gray-700">{cat.rate}</td>
                        <td className="px-6 py-3 text-center">
                          <button type="button" onClick={() => handleRemoveSubTax(idx)} className="p-1 rounded-md text-gray-400 hover:text-gray-700 hover:bg-gray-100 transition-all duration-150">
                            <svg className="w-5 h-5 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </td>
                      </tr>
                    ))}

                    <tr className="bg-white">
                      <td className="px-6 py-3"></td>
                      <td className="px-6 py-3">
                        <select className={cn(inputCls, "w-full min-w-[150px]")}
                          value={newSubTaxType} onChange={e => setNewSubTaxType(e.target.value)}>
                          <option value="">Select Type</option>
                          <option value="CGST">CGST</option>
                          <option value="SGST">SGST</option>
                          <option value="IGST">IGST</option>
                        </select>
                      </td>
                      <td className="px-6 py-3">
                        <input type="number" min="0" max="100" step="0.1"
                          className={cn(inputCls, "w-full")}
                          value={newSubTaxValue} onChange={e => setNewSubTaxValue(e.target.value)}
                          onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), handleAddSubTax())} />
                      </td>
                      <td className="px-6 py-3 text-center">
                        <button type="button" onClick={handleAddSubTax}
                          className="inline-flex items-center justify-center w-8 h-8 border border-gray-300 bg-white text-gray-600 rounded-md hover:bg-gray-50 active:bg-gray-100 transition-colors shadow-sm">
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4" /></svg>
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
            <div className="px-6 py-4 border-t bg-white flex justify-end gap-3">
              <button
                type="button"
                onClick={reset}
                className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => mut.mutate()}
                disabled={!form.name || mut.isPending || (form.categories || []).length === 0}
                className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors"
              >
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Tax' : 'Create Tax')}
              </button>
            </div>
          </div>
        </div>
      )}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden flex flex-col mt-4">
        <Table headers={['S.NO', 'NAME', 'STATUS', 'ACTION']} className="border-0 shadow-none rounded-none border-b border-gray-200 text-center">
          {isLoading ? <LoadingRow /> : types.length === 0 ? <EmptyState label="taxes" /> :
            types.map((t: Tax, i: number) => (
              <tr key={t.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium text-gray-500 text-center">{page * 10 + i + 1}</td>
                <td className="px-4 py-3 font-medium text-gray-900 text-center">{t.name}</td>
                <td className="px-4 py-3 text-center"><StatusBadge active={t.status === 1 || (t.status as any) === 'ACTIVE'} /></td>
                <td className="px-4 py-3 text-center"><EditBtn onClick={() => startEdit(t)} /></td>
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