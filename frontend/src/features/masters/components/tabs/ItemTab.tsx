
import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { cn } from '../../../../lib/utils';
import { toast } from '../../../../hooks/useToast';
import { inputCls, labelCls, Field, StatusBadge, EmptyState, AddButton, Section, Table, EditBtn, LoadingRow } from '../MasterSharedUI';
import { itemMasterApi, taxApi, categoryMasterApi, uomApi, ItemPayload } from '../../../../services/masters/masterApi';

interface ItemFormValues {
  name: string;
  hsnCode: string;
  taxRate: string | number;
  reorderLevel: string | number;
  unitOfMeasureId: string;
  conversionFactor: string | number;
  requiresBatch: boolean;
  requiresPrescription: boolean;
  secondLevelUnit: string;
  categoryId: string;
  manufacturer: string;
  mrp: string;
  scheduledDrug: string;
  status: string | number;
}

export default function ItemTab() {
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
    queryKey: ['inventoryItems', 'page', page, query],
    queryFn: () => itemMasterApi.getPaginated({ start: page * 10, limit: 10, ...(query ? { value: query } : {}) })
  })

  const { data: taxes = [] } = useQuery({ queryKey: ['taxes'], queryFn: taxApi.getAll })
  const { data: uoms = [] } = useQuery({ queryKey: ['uoms'], queryFn: uomApi.getAll })
  const { data: itemCats = [] } = useQuery({ queryKey: ['masterCategories', 'ITEM_CATEGORY'], queryFn: () => categoryMasterApi.getAll('ITEM_CATEGORY') })

  const items = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<any>(null)

  const blank: any = {
    name: '',
    hsnCode: '',
    taxRate: '',
    reorderLevel: '',
    unitOfMeasureId: '',
    conversionFactor: 1,
    requiresBatch: false,
    requiresPrescription: false,
    secondLevelUnit: '',
    categoryId: '',
    manufacturer: '',
    mrp: '',
    scheduledDrug: '',
    status: 1
  }
  const [form, setForm] = useState<ItemFormValues>(blank)

  const mut = useMutation({
    mutationFn: () => {
      const payload: ItemPayload = {
        name: form.name,
        taxRate: form.taxRate === '' ? 0 : Number(form.taxRate),
        reorderLevel: form.reorderLevel === '' ? 10 : Number(form.reorderLevel),
        conversionFactor: form.conversionFactor === '' ? 1 : Number(form.conversionFactor),
        requiresBatch: form.requiresBatch,
        requiresPrescription: form.requiresPrescription,
        status: form.status
      }
      if (form.hsnCode) payload.hsnCode = form.hsnCode
      if (form.unitOfMeasureId) payload.unitOfMeasureId = form.unitOfMeasureId
      if (form.secondLevelUnit) payload.secondLevelUnit = form.secondLevelUnit
      if (form.categoryId) payload.categoryId = form.categoryId
      if (form.manufacturer) payload.manufacturer = form.manufacturer
      if (form.mrp) payload.mrp = form.mrp
      if (form.scheduledDrug) payload.scheduledDrug = form.scheduledDrug
      return editing ? itemMasterApi.update(editing.id, payload) : itemMasterApi.create(payload)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['inventoryItems'] }); reset(); toast({ title: editing ? 'Item updated successfully' : 'Item saved successfully', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  function reset() { setShowForm(false); setEditing(null); setForm(blank) }
  function startEdit(r: any) {
    setEditing(r)
    setForm({
      name: r.name,
      hsnCode: r.hsnCode ?? '',
      taxRate: r.taxRate ?? '',
      reorderLevel: r.reorderLevel ?? '',
      unitOfMeasureId: r.unitOfMeasureId ?? '',
      conversionFactor: r.conversionFactor ?? 1,
      requiresBatch: !!r.requiresBatch,
      requiresPrescription: !!r.requiresPrescription,
      secondLevelUnit: r.secondLevelUnit ?? '',
      categoryId: r.categoryId ?? '',
      manufacturer: r.manufacturer ?? '',
      mrp: r.mrp ?? '',
      scheduledDrug: r.scheduledDrug ?? '',
      status: r.status === 'INACTIVE' || r.status === 0 ? 0 : 1
    })
    setShowForm(true)
  }

  const currentTaxId = taxes.find(t => Math.abs(t.rate - Number(form.taxRate || 0)) < 0.01)?.id || '';
  const showCustomOption = form.taxRate !== '' && Number(form.taxRate) > 0 && !currentTaxId;

  return (
    <Section
      title="Inventory Items"
      description="Drugs, consumables, and medical supplies"
      action={
        <div className="flex items-center gap-3">
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            className="px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all w-64"
          />
          <AddButton label="Add Item" onClick={() => { reset(); setShowForm(true) }} />
        </div>
      }
    >
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl overflow-hidden border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">
            <div className="bg-gradient-to-r from-blue-600 to-indigo-600 px-6 py-4 flex justify-between items-center text-white">
              <h3 className="text-lg font-bold tracking-tight">{editing ? 'Update Item' : 'Create Item'}</h3>
              <button onClick={reset} className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            </div>
            <div className="p-6 overflow-y-auto space-y-6 flex-1 bg-gray-50/50">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                <Field label="Name *"><input className={inputCls} value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} /></Field>
                <Field label="Base Unit">
                  <select className={inputCls} value={form.unitOfMeasureId} onChange={e => setForm(f => ({ ...f, unitOfMeasureId: e.target.value, ...(e.target.value ? {} : { secondLevelUnit: '', conversionFactor: 1 }) }))}>
                    <option value="">Select Base Unit</option>
                    {uoms.filter((u: any) => u.status !== 'INACTIVE' && u.status !== 0).map((u: any) => <option key={u.id} value={u.id}>{u.name}</option>)}
                  </select>
                </Field>
                <Field label="Second Level Unit">
                  <select disabled={!form.unitOfMeasureId} className={cn(inputCls, !form.unitOfMeasureId && "opacity-50 cursor-not-allowed")} value={form.secondLevelUnit} onChange={e => setForm(f => ({ ...f, secondLevelUnit: e.target.value }))}>
                    <option value="">Select Second Level Unit</option>
                    {form.unitOfMeasureId && uoms.filter((u: any) => u.id !== form.unitOfMeasureId && u.status !== 'INACTIVE' && u.status !== 0).map((u: any) => <option key={u.id} value={u.id}>{u.name}</option>)}
                  </select>
                </Field>
                {form.unitOfMeasureId && form.secondLevelUnit && (
                  <Field label="Conversion Value">
                    <input type="number" min="1" className={inputCls} value={form.conversionFactor} onChange={e => setForm(f => ({ ...f, conversionFactor: e.target.value === '' ? '' : parseInt(e.target.value, 10) }))} />
                  </Field>
                )}
                <Field label="GST">
                  <select className={inputCls} value={showCustomOption ? 'custom' : currentTaxId} onChange={e => { const t = taxes.find((tx: any) => tx.id === e.target.value); setForm(f => ({ ...f, taxRate: t ? t.rate : 0 })) }}>
                    <option value="">Select GST</option>
                    {taxes.filter((t: any) => t.status !== 'INACTIVE' && t.status !== 0).map((t: any) => <option key={t.id} value={t.id}>{t.name}</option>)}
                    {showCustomOption && <option value="custom" disabled>Custom ({form.taxRate}%)</option>}
                  </select>
                </Field>
                <Field label="Item Category">
                  <select className={inputCls} value={form.categoryId} onChange={e => setForm(f => ({ ...f, categoryId: e.target.value }))}>
                    <option value="">Select Item Category</option>
                    {itemCats.filter((c: any) => c.status !== 'INACTIVE' && c.status !== 0).map((c: any) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                </Field>
                <Field label="HSN CODE"><input className={inputCls} value={form.hsnCode} onChange={e => setForm(f => ({ ...f, hsnCode: e.target.value }))} /></Field>
                <Field label="Manufacturer"><input className={inputCls} value={form.manufacturer} onChange={e => setForm(f => ({ ...f, manufacturer: e.target.value }))} /></Field>
                <Field label="Scheduled Drug">
                  <select className={inputCls} value={form.scheduledDrug} onChange={e => setForm(f => ({ ...f, scheduledDrug: e.target.value }))}>
                    <option value="">Select Scheduled Drug</option>
                    <option value="H">H</option>
                    <option value="H1">H1</option>
                  </select>
                </Field>
                <Field label="Reorder Level"><input type="number" min="0" className={inputCls} value={form.reorderLevel} onChange={e => setForm(f => ({ ...f, reorderLevel: e.target.value === '' ? '' : parseInt(e.target.value, 10) }))} /></Field>
                <div className="flex items-center gap-2 pt-8">
                  <input type="checkbox" id="requiresBatch" checked={form.requiresBatch} onChange={e => setForm(f => ({ ...f, requiresBatch: e.target.checked }))} className="rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                  <label htmlFor="requiresBatch" className="text-xs font-semibold text-gray-600">Batch Required</label>
                </div>
                <Field label="MRP"><input className={inputCls} value={form.mrp} onChange={e => setForm(f => ({ ...f, mrp: e.target.value }))} /></Field>
                {editing && (
                  <div className="border-t border-gray-100 pt-4 mt-2 md:col-span-2">
                    <span className={labelCls}>Status</span>
                    <div className="flex gap-2 mt-1">
                      <button type="button" onClick={() => setForm(f => ({ ...f, status: 1 }))} className={cn("px-4 py-2 text-xs font-bold rounded-lg border transition-all duration-150", (form.status === 1 || form.status === 'ACTIVE') ? "bg-blue-600 text-white border-blue-600 shadow-sm" : "bg-white text-gray-700 border-gray-200 hover:bg-gray-50")}>Active</button>
                      <button type="button" onClick={() => setForm(f => ({ ...f, status: 0 }))} className={cn("px-4 py-2 text-xs font-bold rounded-lg border transition-all duration-150", (form.status === 0 || form.status === 'INACTIVE') ? "bg-red-600 text-white border-red-600 shadow-sm" : "bg-white text-gray-700 border-gray-200 hover:bg-gray-50")}>Inactive</button>
                    </div>
                  </div>
                )}
              </div>
            </div>
            <div className="px-6 py-4 border-t bg-white flex justify-end gap-3">
              <button onClick={reset} className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-white transition-colors">
                Cancel
              </button>
              <button onClick={() => mut.mutate()} disabled={!form.name || mut.isPending} className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                {mut.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Item' : 'Create Item')}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden flex flex-col mt-4">
        <Table headers={['S.NO', 'NAME', 'CATEGORY', 'STATUS', 'ACTION']} className="border-0 shadow-none rounded-none border-b border-gray-200">
          {isLoading ? <LoadingRow /> : items.length === 0 ? <EmptyState label="items" /> :
            items.map((r: any, i: number) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium text-gray-500">{page * 10 + i + 1}</td>
                <td className="px-4 py-3 font-medium text-gray-900 uppercase text-xs font-semibold">{r.name}</td>
                <td className="px-4 py-3 text-gray-600 uppercase text-xs font-semibold">{itemCats.find((c:any) => c.id === r.categoryId)?.name || 'PHARMACY'}</td>
                <td className="px-4 py-3"><StatusBadge active={r.status === 1 || r.status === 'ACTIVE'} /></td>
                <td className="px-4 py-3 text-center"><EditBtn onClick={() => startEdit(r)} /></td>
              </tr>
            ))}
        </Table>
        <div className="px-6 py-4 bg-gray-50 flex items-center justify-between">
          <div className="text-xs text-gray-500">
            Page <span className="font-medium text-gray-900">{page + 1}</span> of{' '}
            <span className="font-medium text-gray-900">{totalPages || 1}</span>
            <span className="ml-2">· {totalElements} total records</span>
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