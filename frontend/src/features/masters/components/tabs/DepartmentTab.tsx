
import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, labelCls, Field, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow, StatusBadge } from '../MasterSharedUI';
import { deptCreateApi, templateApi, categoryMasterApi } from '../../../../services/masters/masterApi';
import { templateApi as casesheetTemplateApi } from '../../../../services/casesheet/casesheetApi';

export default function DepartmentTab() {
  const qc = useQueryClient()
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    const timer = setTimeout(() => { setDebouncedSearch(searchTerm); setPage(0) }, 400)
    return () => clearTimeout(timer)
  }, [searchTerm])

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['departments', 'page', page, debouncedSearch],
    queryFn: () => deptCreateApi.getPaginated({ start: page * 10, limit: 10, value: debouncedSearch }),
    placeholderData: (prev) => prev
  })

  const { data: itemCategories = [] } = useQuery({
    queryKey: ['itemCategories'],
    queryFn: () => categoryMasterApi.getAll('ITEM_CATEGORY')
  })

  const depts = pageData?.content ?? []
  const totalPages = pageData ? Math.ceil(pageData.totalElements / 10) : 0

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<any>(null)
  
  const blank = {
    name: '',
    departmentType: 'Clinical',
    displayOrder: '',
    status: 'ACTIVE' as string,
    departmentCategories: [] as Array<{ category: { id: string; name?: string } }>,
    stockDepartmentAccesses: [] as string[],
    departmentTemplates: [] as Array<{ template: { id: string; templateName?: string } }>
  }
  const [form, setForm] = useState(blank)

  // Autocomplete templates state
  const [templateSearch, setTemplateSearch] = useState('')
  const [templateResults, setTemplateResults] = useState<any[]>([])
  const [selectedTemplate, setSelectedTemplate] = useState<any>(null)
  const [isTemplateDropdownOpen, setIsTemplateDropdownOpen] = useState(false)

  useEffect(() => {
    if (!templateSearch.trim()) {
      setTemplateResults([])
      return
    }
    const timer = setTimeout(() => {
      casesheetTemplateApi.list().then((res: any) => {
        const filtered = (res || []).filter((t: any) =>
          t.name.toLowerCase().includes(templateSearch.toLowerCase())
        );
        const mapped = filtered.map((t: any) => ({
          id: t.id,
          templateName: `${t.name} (${t.specialization} - ${t.visitType})`
        }));
        setTemplateResults(mapped)
      }).catch(() => {})
    }, 300)
    return () => clearTimeout(timer)
  }, [templateSearch])

  const TYPES = ['Diagnostics', 'Clinical', 'Stock', 'Other']

  const mut = useMutation({
    mutationFn: () => editing ? deptCreateApi.update(editing.id, form) : deptCreateApi.create(form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['departments'] }); reset(); toast({ title: editing ? 'department updated successfully' : 'department saved successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  function reset() {
    setShowForm(false)
    setEditing(null)
    setForm(blank)
    setTemplateSearch('')
    setTemplateResults([])
    setSelectedTemplate(null)
    setIsTemplateDropdownOpen(false)
  }

  async function startEdit(r: any) {
    setEditing(r)
    
    let departmentCategories = [] as any[]
    let stockDepartmentAccesses = [] as string[]
    let departmentTemplates = [] as any[]

    if (r.departmentType === 'Stock') {
      try {
        const cats = await deptCreateApi.getCategories(r.id)
        departmentCategories = cats.map((c: any) => ({ category: { id: c.id, name: c.name } }))
      } catch (err) {}
      try {
        stockDepartmentAccesses = await deptCreateApi.getAccesses(r.id)
      } catch (err) {}
    } else if (r.departmentType === 'Clinical') {
      try {
        const tmpl = await templateApi.getDepartmentTemplates(r.id)
        departmentTemplates = tmpl.map((t: any) => ({
          template: {
            id: t.id,
            templateName: t.name ? `${t.name} (${t.specialization} - ${t.visitType})` : t.templateName
          }
        }))
      } catch (err) {}
    }

    setForm({
      name: r.name,
      departmentType: r.departmentType ?? 'Clinical',
      displayOrder: r.displayOrder ?? '',
      status: r.status === 1 || r.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE',
      departmentCategories,
      stockDepartmentAccesses,
      departmentTemplates
    })
    setShowForm(true)
  }

  return (
    <Section
      title="Departments"
      description="Hospital departments for staff assignment and inventory routing"
      action={
        <div className="flex gap-4 items-center">
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => { setSearchTerm(e.target.value); setPage(0); }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="ADD DEPARTMENT" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >

      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">
            
            {/* Modal Header */}
            <div className="bg-gradient-to-r from-blue-600 to-indigo-600 px-6 py-4 flex justify-between items-center text-white rounded-t-2xl">
              <h3 className="text-lg font-bold tracking-tight">
                {editing ? 'Edit Department' : 'Add Department'}
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
            <div className="p-6 overflow-visible space-y-4 flex-1 bg-gray-50/50">
              <div className="space-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
                      value={form.departmentType}
                      onChange={e => setForm(f => ({ ...f, departmentType: e.target.value }))}
                      required
                    >
                      {TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                    </select>
                  </Field>

                  {form.departmentType === 'Diagnostics' && (
                    <Field label="Display Order *">
                      <input
                        type="text"
                        className={inputCls}
                        value={form.displayOrder}
                        onChange={e => setForm(f => ({ ...f, displayOrder: e.target.value.replace(/[^0-9]/g, '') }))}
                        required
                      />
                    </Field>
                  )}
                </div>

                {form.departmentType === 'Stock' && (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-gray-100 pt-4">
                    <Field label="Item Category">
                      <select
                        multiple
                        className={cn(inputCls, "h-32")}
                        value={form.departmentCategories.map(dc => dc.category.id)}
                        onChange={e => {
                          const selectedIds = Array.from(e.target.selectedOptions, option => option.value);
                          const updated = selectedIds.map(id => {
                            const cat = itemCategories.find((c: any) => c.id === id);
                            return { category: { id, name: cat?.name || '' } };
                          });
                          setForm(f => ({ ...f, departmentCategories: updated }));
                        }}
                      >
                        {itemCategories.map((cat: any) => (
                          <option key={cat.id} value={cat.id}>
                            {cat.name}
                          </option>
                        ))}
                      </select>
                    </Field>

                    <Field label="Can Access">
                      <select
                        multiple
                        className={cn(inputCls, "h-32")}
                        value={form.stockDepartmentAccesses}
                        onChange={e => {
                          const selectedOptions = Array.from(e.target.selectedOptions, option => option.value);
                          setForm(f => ({ ...f, stockDepartmentAccesses: selectedOptions }));
                        }}
                      >
                        <option value="SALES">SALES</option>
                        <option value="PURCHASE">PURCHASE</option>
                        <option value="ISSUE">ISSUE</option>
                      </select>
                    </Field>
                  </div>
                )}

                {form.departmentType === 'Clinical' && (
                  <div className="border-t border-gray-100 pt-4 space-y-4 pb-2">
                    <Field label="Template">
                      <div className="relative">
                        <div className="flex gap-2">
                          <div className="relative flex-1">
                            <input
                              type="text"
                              className={inputCls}
                              value={templateSearch}
                              onChange={e => {
                                setTemplateSearch(e.target.value)
                                setIsTemplateDropdownOpen(true)
                                setSelectedTemplate(null)
                              }}
                              onFocus={() => setIsTemplateDropdownOpen(true)}
                              onBlur={() => setTimeout(() => setIsTemplateDropdownOpen(false), 200)}
                            />
                            {isTemplateDropdownOpen && templateResults.length > 0 && (
                              <div className="absolute left-0 right-0 top-full mt-1 bg-white border border-gray-200 rounded-xl shadow-xl z-[60] max-h-48 overflow-y-auto divide-y divide-gray-50">
                                {templateResults.map((t: any) => (
                                  <button
                                    key={t.id}
                                    type="button"
                                    onClick={() => {
                                      setTemplateSearch(t.templateName)
                                      setSelectedTemplate(t)
                                      setIsTemplateDropdownOpen(false)
                                    }}
                                    className="w-full text-left px-4 py-2.5 text-xs text-gray-700 hover:bg-blue-50 hover:text-blue-700 transition-colors font-medium"
                                  >
                                    {t.templateName}
                                  </button>
                                ))}
                              </div>
                            )}
                          </div>
                          <button
                            type="button"
                            onClick={() => {
                              if (selectedTemplate) {
                                if (form.departmentTemplates.some(dt => dt.template.id === selectedTemplate.id)) {
                                  toast({ title: 'Template already added', variant: 'destructive' })
                                  return
                                }
                                setForm(f => ({
                                  ...f,
                                  departmentTemplates: [
                                    ...f.departmentTemplates,
                                    { template: { id: selectedTemplate.id, templateName: selectedTemplate.templateName } }
                                  ]
                                }))
                                setTemplateSearch('')
                                setSelectedTemplate(null)
                              }
                            }}
                            disabled={!selectedTemplate}
                            className="px-3.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors font-bold disabled:opacity-50"
                          >
                            +
                          </button>
                        </div>
                      </div>
                    </Field>

                    {form.departmentTemplates.length > 0 && (
                      <div className="mt-2 space-y-1.5 max-h-40 overflow-y-auto pl-1">
                        {form.departmentTemplates.map((dt, idx) => (
                          <div key={dt.template.id} className="flex items-center gap-2 text-xs font-semibold text-gray-700">
                            <span>{dt.template.templateName}</span>
                            <button
                              type="button"
                              onClick={() => {
                                if (editing && dt.template.id) {
                                  templateApi.removeDepartmentTemplate(dt.template.id, editing.id)
                                    .then(() => {
                                      setForm(f => ({
                                        ...f,
                                        departmentTemplates: f.departmentTemplates.filter((_, i) => i !== idx)
                                      }))
                                      toast({ title: 'Template removed successfully', variant: 'success' })
                                    })
                                    .catch(err => {
                                      toast({ title: 'Error', description: err.message, variant: 'destructive' })
                                    })
                                } else {
                                  setForm(f => ({
                                    ...f,
                                    departmentTemplates: f.departmentTemplates.filter((_, i) => i !== idx)
                                  }))
                                }
                              }}
                              className="text-gray-900 hover:text-red-600 transition-colors font-bold"
                            >
                              ✖
                            </button>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

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
                            ? "bg-blue-600 text-white border-blue-600 shadow-sm"
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
            <div className="bg-gray-50 px-6 py-4 flex justify-end gap-3 border-t border-gray-150 rounded-b-2xl">
              <button
                type="button"
                onClick={reset}
                className="px-4 py-2 text-xs font-bold rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-100 active:bg-gray-200 transition-all focus:outline-none"
              >Cancel</button>
              <button
                type="button"
                onClick={() => mut.mutate()}
                disabled={mut.isPending || !form.name}
                className="px-5 py-2 text-xs font-bold rounded-lg bg-blue-600 hover:bg-blue-700 text-white shadow-md active:scale-[0.98] transition-all disabled:opacity-50 disabled:pointer-events-none focus:outline-none"
              >
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Department' : 'Create')}
              </button>
            </div>

          </div>
        </div>
      )}

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden flex flex-col mt-4">
        <Table headers={['S.NO', 'NAME', 'TYPE', 'DISPLAY ORDER', 'STATUS', 'ACTION']} className="border-0 shadow-none rounded-none border-b border-gray-200">
          {isLoading ? <LoadingRow /> : depts.length === 0 ? <EmptyState label="departments" /> :
          depts.map((d: any, idx: number) => (
            <tr key={d.id} className="hover:bg-gray-50/70 transition-colors">
              <td className="px-4 py-3 text-xs font-bold text-gray-400">{page * 10 + idx + 1}</td>
              <td className="px-4 py-3 font-medium text-gray-900">{d.name}</td>
              <td className="px-4 py-3 text-gray-600 text-sm">{d.departmentType}</td>
              <td className="px-4 py-3 text-gray-500 text-sm text-center">{d.displayOrder || '—'}</td>
              <td className="px-4 py-3 text-center">
                <StatusBadge active={d.status === 'ACTIVE' || d.status === 1} />
              </td>
              <td className="px-4 py-3 text-center"><EditBtn onClick={() => startEdit(d)} /></td>
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