
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, Field, StatusBadge, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow } from '../MasterSharedUI';
import { categoryMasterApi, Category, CategoryType, ChargeCategoryType, DiagnosticType, EntityStatus } from '../../../../services/masters/masterApi';

export default function CategoryTab() {
  const qc = useQueryClient()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const { data: pageData, isLoading } = useQuery({ 
    queryKey: ['masterCategories', page, search], 
    queryFn: () => categoryMasterApi.getPaginated({ start: page * 10, limit: 10, ...(search ? { value: search } : {}) }) 
  })
  const cats = pageData?.content ?? []
  const totalPages = pageData?.totalPages ?? 0

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Category | null>(null)

  const blank: Omit<Category, 'id'> = {
    name: '',
    type: 'PATIENT',
    paramValue: '',
    chargeCategoryType: undefined,
    diagnosticType: undefined,
    status: 'ACTIVE',
  }
  const [form, setForm] = useState<Omit<Category, 'id'>>(blank)

  const CATEGORY_TYPES: CategoryType[] = ['PATIENT', 'ITEM_CATEGORY', 'CHARGE', 'EQUIPMENT', 'INSTRUMENT']
  const CHARGE_CATEGORY_TYPES: ChargeCategoryType[] = ['DIAGNOSTICS', 'CONSULTATION', 'ROOM_CHARGE', 'OTHERS', 'PACKAGES', 'SURGERY']
  const DIAGNOSTIC_TYPES: DiagnosticType[] = ['LAB', 'RADIOLOGY']

  const mut = useMutation({
    mutationFn: () => {
      const payload = { ...form }
      if (payload.type !== 'PATIENT') payload.paramValue = undefined
      if (payload.type !== 'CHARGE') {
        payload.chargeCategoryType = undefined
        payload.diagnosticType = undefined
      } else if (payload.chargeCategoryType !== 'DIAGNOSTICS') {
        payload.diagnosticType = undefined
      }
      return editing
        ? categoryMasterApi.update(editing.id!, payload)
        : categoryMasterApi.create(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['masterCategories'] })
      reset()
      toast({ title: editing ? 'Category updated successfully' : 'Category saved successfully', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  function reset() {
    setShowForm(false)
    setEditing(null)
    setForm(blank)
  }

  function startEdit(c: Category) {
    setEditing(c)
    setForm({
      name: c.name,
      type: c.type,
      paramValue: c.paramValue ?? '',
      chargeCategoryType: c.chargeCategoryType,
      diagnosticType: c.diagnosticType,
      status: c.status ?? 'ACTIVE',
    })
    setShowForm(true)
  }

  return (
    <Section
      title="Categories"
      description="General categories used to group patients, items, charges, equipment, and instruments"
      action={
        <div className="flex items-center gap-3">
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="Add Category" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">

            <div className="bg-gradient-to-r from-blue-600 to-indigo-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">{editing ? 'Update Category' : 'Create Category'}</h3>
              <button
                onClick={reset}
                className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="p-6 overflow-y-auto space-y-6 flex-1 bg-gray-50/50">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">

                <Field label="Category Name *">
                  <input
                    className={inputCls}
                    value={form.name}
                    onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                  />
                </Field>

                <Field label="Category Type *">
                  <select
                    className={inputCls}
                    value={form.type}
                    onChange={e => {
                      const newType = e.target.value as CategoryType
                      setForm(f => ({
                        ...f,
                        type: newType,
                        chargeCategoryType: newType === 'CHARGE' ? 'DIAGNOSTICS' : undefined,
                        diagnosticType: newType === 'CHARGE' ? 'LAB' : undefined
                      }))
                    }}
                  >
                    {CATEGORY_TYPES.map(t => (
                      <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
                    ))}
                  </select>
                </Field>

                {form.type === 'PATIENT' && (
                  <Field label="Value">
                    <input
                      className={inputCls}
                      value={form.paramValue || ''}
                      onChange={e => setForm(f => ({ ...f, paramValue: e.target.value }))}
                    />
                  </Field>
                )}

                {form.type === 'CHARGE' && (
                  <>
                    <Field label="Charge Category Type *">
                      <select
                        className={inputCls}
                        value={form.chargeCategoryType || 'DIAGNOSTICS'}
                        onChange={e => {
                          const newCct = e.target.value as ChargeCategoryType
                          setForm(f => ({
                            ...f,
                            chargeCategoryType: newCct,
                            diagnosticType: newCct === 'DIAGNOSTICS' ? 'LAB' : undefined
                          }))
                        }}
                      >
                        {CHARGE_CATEGORY_TYPES.map(t => (
                          <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
                        ))}
                      </select>
                    </Field>

                    {form.chargeCategoryType === 'DIAGNOSTICS' && (
                      <Field label="Diagnostic Type *">
                        <select
                          className={inputCls}
                          value={form.diagnosticType || 'LAB'}
                          onChange={e => setForm(f => ({ ...f, diagnosticType: e.target.value as DiagnosticType }))}
                        >
                          {DIAGNOSTIC_TYPES.map(t => (
                            <option key={t} value={t}>{t === 'LAB' ? 'LABORATORY' : 'RADIOLOGY'}</option>
                          ))}
                        </select>
                      </Field>
                    )}
                  </>
                )}

                {editing && (
                  <Field label="Status">
                    <select
                      className={inputCls}
                      value={form.status}
                      onChange={e => setForm(f => ({ ...f, status: e.target.value as EntityStatus }))}
                    >
                      <option value="ACTIVE">Active</option>
                      <option value="INACTIVE">Inactive</option>
                    </select>
                  </Field>
                )}
              </div>
            </div>

            <div className="px-6 py-4 border-t bg-white flex justify-end gap-3">
              <button onClick={reset}
                className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors">
                Cancel
              </button>
              <button onClick={() => mut.mutate()} disabled={!form.name || !form.type || mut.isPending}
                className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Category' : 'Create Category')}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden flex flex-col mt-4">
        <Table headers={['S.NO', 'CATEGORY NAME', 'TYPE', 'DETAILS', 'STATUS', 'ACTION']} className="border-0 shadow-none rounded-none border-b border-gray-200">
          {isLoading ? (
            <LoadingRow />
          ) : cats.length === 0 ? (
            <EmptyState label="categories" />
          ) : (
            cats.map((c: any, i: number) => {
              let details = '-'
              if (c.type === 'PATIENT' && c.paramValue) {
                details = `Value: ${c.paramValue}`
              } else if (c.type === 'CHARGE') {
                const cct = c.chargeCategoryType?.replace(/_/g, ' ') || '-'
                const dt = c.diagnosticType ? ` (${c.diagnosticType === 'LAB' ? 'LABORATORY' : 'RADIOLOGY'})` : ''
                details = `${cct}${dt}`
              }

              return (
                <tr key={c.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-500">{page * 10 + i + 1}</td>
                  <td className="px-4 py-3 font-medium text-gray-900">{c.name}</td>
                  <td className="px-4 py-3 text-gray-600 text-xs font-semibold uppercase">{c.type?.replace(/_/g, ' ')}</td>
                  <td className="px-4 py-3 text-gray-500 text-sm">{details}</td>
                  <td className="px-4 py-3">
                    <StatusBadge active={c.status === 'ACTIVE' || (c.status as any) === 1} />
                  </td>
                  <td className="px-4 py-3 text-center">
                    <EditBtn onClick={() => startEdit(c)} />
                  </td>
                </tr>
              )
            })
          )}
        </Table>
        <div className="px-6 py-4 bg-gray-50 flex items-center justify-between">
          <div className="text-xs text-gray-500">
            Page <span className="font-medium text-gray-900">{page + 1}</span> of{' '}
            <span className="font-medium text-gray-900">{totalPages || 1}</span>
            <span className="ml-2">· {pageData?.totalElements || 0} total records</span>
          </div>
          <div className="flex items-center gap-1">
            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0 || isLoading} className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" /></svg>
            </button>
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              let pageNum = i
              if (totalPages > 5 && page > 2) pageNum = Math.min(page - 2 + i, totalPages - 5 + i)
              return (
                <button key={pageNum} onClick={() => setPage(pageNum)} className={cn('min-w-[32px] h-8 flex items-center justify-center rounded text-xs font-semibold transition-all', page === pageNum ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-600 hover:bg-gray-100')}>
                  {pageNum + 1}
                </button>
              )
            })}
            <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1 || isLoading} className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" /></svg>
            </button>
          </div>
        </div>
      </div>
    </Section>
  )
}
