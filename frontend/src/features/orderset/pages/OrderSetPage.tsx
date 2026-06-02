/**
 * OrderSetPage.tsx — enhanced Order Sets management.
 * Supports: PRESCRIPTION | DIAGNOSTICS | BOTH type filtering,
 * GLOBAL | DEPARTMENT | CONSULTANT scope,
 * full CRUD with item-level drug/test entry.
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { orderSetApi, type OrderSet, type OrderSetItem, type OrderSetType, type OrderSetScope } from '../../../services/orderset/orderSetApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'

const TYPE_STYLES: Record<string, string> = {
  PRESCRIPTION: 'bg-blue-50 text-blue-700 border-blue-200',
  DIAGNOSTICS:  'bg-purple-50 text-purple-700 border-purple-200',
  BOTH:         'bg-amber-50 text-amber-700 border-amber-200',
}
const SCOPE_STYLES: Record<string, string> = {
  GLOBAL:     'bg-green-50 text-green-700 border-green-200',
  DEPARTMENT: 'bg-orange-50 text-orange-700 border-orange-200',
  CONSULTANT: 'bg-pink-50 text-pink-700 border-pink-200',
}

const BLANK_ITEM: Omit<OrderSetItem, 'id'> = {
  itemType: 'PHARMACY', itemName: '', quantity: 1,
  frequency: '', duration: '', instruction: '', routeLabel: '',
}

export default function OrderSetPage() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [typeFilter, setTypeFilter] = useState<OrderSetType | 'ALL'>('ALL')
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<OrderSet | null>(null)

  const { data: orderSets = [], isLoading } = useQuery({
    queryKey: ['order-sets', search],
    queryFn: () => orderSetApi.search(search || undefined),
  })

  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants-all'],
    queryFn: () => consultantApi.getAll(),
  })

  // Form state
  const [name, setName]             = useState('')
  const [description, setDesc]      = useState('')
  const [setType, setSetType]       = useState<OrderSetType>('BOTH')
  const [scope, setScope]           = useState<OrderSetScope>('GLOBAL')
  const [isOutpatient, setIsOP]     = useState(true)
  const [consultantId, setConsId]   = useState('')
  const [items, setItems]           = useState<Omit<OrderSetItem, 'id'>[]>([{ ...BLANK_ITEM }])

  const resetForm = () => {
    setName(''); setDesc(''); setSetType('BOTH'); setScope('GLOBAL')
    setIsOP(true); setConsId('')
    setItems([{ ...BLANK_ITEM }])
    setEditing(null)
  }

  const handleEdit = (os: OrderSet) => {
    setEditing(os)
    setName(os.name)
    setDesc(os.description ?? '')
    setSetType(os.setType)
    setScope(os.scope ?? 'GLOBAL')
    setIsOP(os.isOutpatient)
    setConsId(os.consultantId ?? '')
    setItems(os.items?.length ? os.items.map(i => ({
      itemType:  i.itemType, itemName: i.itemName ?? '', quantity: i.quantity,
      frequency: i.frequency ?? '', duration: i.duration ?? '',
      instruction: i.instruction ?? '', routeLabel: i.routeLabel ?? '',
      serviceCatalogItemId: i.serviceCatalogItemId,
    })) : [{ ...BLANK_ITEM }])
    setShowForm(true)
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload: any = {
        name, description, setType, isOutpatient, isFavorite: false,
        scope, consultantId: consultantId || undefined,
        items: items.filter(i => i.itemName?.trim()),
        status: 'ACTIVE',
      }
      return editing ? orderSetApi.update(editing.id, payload) : orderSetApi.create(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['order-sets'] })
      toast({ title: editing ? 'Order set updated' : 'Order set created', variant: 'success' })
      setShowForm(false); resetForm()
    },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const deactivateMutation = useMutation({
    mutationFn: (id: string) => orderSetApi.deactivate(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['order-sets'] }); toast({ title: 'Order set removed', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const addItem = () => setItems(prev => [...prev, { ...BLANK_ITEM }])
  const removeItem = (i: number) => setItems(prev => prev.filter((_, idx) => idx !== i))
  const setItem = (i: number, patch: Partial<Omit<OrderSetItem, 'id'>>) =>
    setItems(prev => prev.map((it, idx) => idx === i ? { ...it, ...patch } : it))

  const displayed = typeFilter === 'ALL' ? orderSets : orderSets.filter(os => os.setType === typeFilter || os.setType === 'BOTH')

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Order Sets</h2>
          <p className="text-sm text-gray-500 mt-0.5">Pre-configured drug or diagnostic groups for one-click batch ordering</p>
        </div>
        <button onClick={() => { resetForm(); setShowForm(true) }}
          className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-xl hover:bg-blue-700 transition-colors shadow-sm">
          <span className="text-lg leading-none">+</span> New Order Set
        </button>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3 flex-wrap">
        <input type="search" value={search} onChange={e => setSearch(e.target.value)}
          placeholder="Search order sets…"
          className="w-60 px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all" />
        <div className="flex gap-1">
          {(['ALL', 'PRESCRIPTION', 'DIAGNOSTICS', 'BOTH'] as const).map(t => (
            <button key={t} onClick={() => setTypeFilter(t)}
              className={cn('px-3 py-1.5 text-xs font-semibold rounded-lg border transition-colors',
                typeFilter === t ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300')}>
              {t === 'ALL' ? 'All Types' : t}
            </button>
          ))}
        </div>
        <span className="text-xs text-gray-400 ml-auto">{displayed.length} order set{displayed.length !== 1 ? 's' : ''}</span>
      </div>

      {/* List */}
      {isLoading ? (
        <div className="text-center py-12 text-gray-400 text-sm">Loading order sets…</div>
      ) : displayed.length === 0 ? (
        <div className="text-center py-16 border border-dashed border-gray-200 rounded-xl text-gray-400">
          <div className="text-4xl mb-3">📋</div>
          <p className="font-medium">No order sets found</p>
          <p className="text-xs mt-1">Create one to speed up clinical ordering workflows</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {displayed.map(os => (
            <div key={os.id} className="bg-white border border-gray-200 rounded-2xl overflow-hidden hover:shadow-md transition-shadow">
              <div className="px-4 pt-4 pb-3">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap mb-1">
                      <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border', TYPE_STYLES[os.setType] ?? TYPE_STYLES.BOTH)}>
                        {os.setType}
                      </span>
                      <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border', SCOPE_STYLES[os.scope ?? 'GLOBAL'])}>
                        {os.scope ?? 'GLOBAL'}
                      </span>
                      {os.isFavorite && (
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold bg-yellow-50 text-yellow-700 border-yellow-200 border">
                          ⭐ FAVORITE
                        </span>
                      )}
                    </div>
                    <h3 className="font-bold text-gray-900 text-sm truncate">{os.name}</h3>
                    {os.description && <p className="text-xs text-gray-500 mt-0.5 line-clamp-1">{os.description}</p>}
                  </div>
                </div>

                {/* Items preview */}
                <div className="mt-3 space-y-1">
                  {(os.items ?? []).slice(0, 4).map((item, idx) => (
                    <div key={idx} className="flex items-center gap-2 text-xs text-gray-600">
                      <span className={cn('w-1.5 h-1.5 rounded-full flex-shrink-0',
                        item.itemType === 'PHARMACY' ? 'bg-blue-400' : 'bg-purple-400')} />
                      <span className="truncate">{item.itemName ?? 'Unnamed'}</span>
                      {item.quantity > 1 && <span className="text-gray-400 ml-auto">×{item.quantity}</span>}
                    </div>
                  ))}
                  {(os.items ?? []).length > 4 && (
                    <p className="text-xs text-gray-400 pl-3.5">+{os.items.length - 4} more items</p>
                  )}
                  {(!os.items || os.items.length === 0) && (
                    <p className="text-xs text-gray-400 italic">No items</p>
                  )}
                </div>
              </div>

              <div className="px-4 py-3 bg-gray-50 border-t border-gray-100 flex items-center gap-2">
                <span className="text-xs text-gray-400">{isOutpatient ? 'OP+IP' : 'IP Only'} · {os.items?.length ?? 0} items</span>
                <div className="flex gap-1.5 ml-auto">
                  <button onClick={() => handleEdit(os)}
                    className="px-3 py-1 text-xs font-semibold text-blue-600 bg-blue-50 rounded-lg hover:bg-blue-100 transition-colors">
                    Edit
                  </button>
                  <button onClick={() => deactivateMutation.mutate(os.id)}
                    className="px-3 py-1 text-xs font-semibold text-red-500 bg-red-50 rounded-lg hover:bg-red-100 transition-colors">
                    Remove
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Form Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl flex flex-col max-h-[92vh]">
            {/* Header */}
            <div className="bg-gradient-to-r from-blue-600 to-indigo-600 px-6 py-4 flex items-center justify-between text-white rounded-t-2xl">
              <h3 className="text-lg font-bold">{editing ? 'Edit Order Set' : 'New Order Set'}</h3>
              <button onClick={() => { setShowForm(false); resetForm() }} className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" /></svg>
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-6 space-y-5 bg-gray-50/50">
              {/* Basic info */}
              <div className="bg-white rounded-xl border border-gray-200 p-4 space-y-4">
                <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider">Basic Information</h4>
                <div className="grid grid-cols-2 gap-4">
                  <div className="col-span-2">
                    <label className="block text-xs font-semibold text-gray-600 mb-1">Name *</label>
                    <input className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
                      value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Fever Protocol, CBC Panel…" />
                  </div>
                  <div className="col-span-2">
                    <label className="block text-xs font-semibold text-gray-600 mb-1">Description</label>
                    <textarea rows={2} className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all resize-none"
                      value={description} onChange={e => setDesc(e.target.value)} placeholder="Optional clinical context…" />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-600 mb-1">Type</label>
                    <select className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
                      value={setType} onChange={e => setSetType(e.target.value as OrderSetType)}>
                      <option value="BOTH">Both (Drugs + Tests)</option>
                      <option value="PRESCRIPTION">Prescription (Drugs only)</option>
                      <option value="DIAGNOSTICS">Diagnostics (Tests only)</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-gray-600 mb-1">Scope</label>
                    <select className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
                      value={scope} onChange={e => { setScope(e.target.value as OrderSetScope); if (e.target.value !== 'CONSULTANT') setConsId('') }}>
                      <option value="GLOBAL">Global (all users)</option>
                      <option value="DEPARTMENT">Department-specific</option>
                      <option value="CONSULTANT">Consultant-specific</option>
                    </select>
                  </div>
                  {scope === 'CONSULTANT' && (
                    <div className="col-span-2">
                      <label className="block text-xs font-semibold text-gray-600 mb-1">Consultant</label>
                      <select className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
                        value={consultantId} onChange={e => setConsId(e.target.value)}>
                        <option value="">— Select consultant —</option>
                        {consultants.map((c: any) => (
                          <option key={c.id} value={c.id}>{c.firstName} {c.lastName}</option>
                        ))}
                      </select>
                    </div>
                  )}
                  <div className="col-span-2 flex items-center gap-3">
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input type="checkbox" checked={isOutpatient} onChange={e => setIsOP(e.target.checked)}
                        className="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                      <span className="text-sm text-gray-700">Available for Outpatients</span>
                    </label>
                  </div>
                </div>
              </div>

              {/* Items */}
              <div className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider">Items ({items.filter(i => i.itemName?.trim()).length})</h4>
                  <button onClick={addItem} className="text-xs text-blue-600 font-semibold hover:text-blue-700 flex items-center gap-1">
                    <span className="text-base leading-none">+</span> Add Item
                  </button>
                </div>
                <div className="space-y-2">
                  {items.map((item, idx) => (
                    <div key={idx} className="border border-gray-200 rounded-xl p-3 space-y-2 bg-gray-50/50">
                      <div className="grid grid-cols-12 gap-2">
                        <div className="col-span-2">
                          <label className="block text-[10px] font-semibold text-gray-500 mb-1">TYPE</label>
                          <select value={item.itemType} onChange={e => setItem(idx, { itemType: e.target.value as any })}
                            className="w-full px-2 py-1.5 border border-gray-200 rounded-lg text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all">
                            <option value="PHARMACY">💊 Drug</option>
                            <option value="DIAGNOSTIC">🧪 Test</option>
                          </select>
                        </div>
                        <div className="col-span-7">
                          <label className="block text-[10px] font-semibold text-gray-500 mb-1">ITEM NAME *</label>
                          <input value={item.itemName ?? ''} onChange={e => setItem(idx, { itemName: e.target.value })}
                            placeholder={item.itemType === 'PHARMACY' ? 'Drug name…' : 'Test name…'}
                            className="w-full px-2 py-1.5 border border-gray-200 rounded-lg text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all" />
                        </div>
                        <div className="col-span-2">
                          <label className="block text-[10px] font-semibold text-gray-500 mb-1">QTY</label>
                          <input type="number" min={1} value={item.quantity} onChange={e => setItem(idx, { quantity: parseInt(e.target.value) || 1 })}
                            className="w-full px-2 py-1.5 border border-gray-200 rounded-lg text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all" />
                        </div>
                        <div className="col-span-1 flex items-end pb-0.5">
                          {items.length > 1 && (
                            <button onClick={() => removeItem(idx)} className="text-red-400 hover:text-red-600 hover:bg-red-50 p-1 rounded transition-colors">
                              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" /></svg>
                            </button>
                          )}
                        </div>
                      </div>
                      {item.itemType === 'PHARMACY' && (
                        <div className="grid grid-cols-3 gap-2">
                          {['frequency', 'duration', 'routeLabel'].map(field => (
                            <div key={field}>
                              <label className="block text-[10px] font-semibold text-gray-500 mb-1">
                                {field === 'routeLabel' ? 'ROUTE' : field.toUpperCase()}
                              </label>
                              <input value={(item as any)[field] ?? ''} onChange={e => setItem(idx, { [field]: e.target.value })}
                                placeholder={field === 'frequency' ? 'e.g. 1-0-1' : field === 'duration' ? '5 days' : 'Oral'}
                                className="w-full px-2 py-1.5 border border-gray-200 rounded-lg text-xs bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all" />
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Footer */}
            <div className="px-6 py-4 border-t bg-white rounded-b-2xl flex justify-between items-center">
              <span className="text-xs text-gray-400">{items.filter(i => i.itemName?.trim()).length} valid item{items.filter(i => i.itemName?.trim()).length !== 1 ? 's' : ''}</span>
              <div className="flex gap-3">
                <button onClick={() => { setShowForm(false); resetForm() }}
                  className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                <button onClick={() => saveMutation.mutate()}
                  disabled={!name.trim() || saveMutation.isPending || items.filter(i => i.itemName?.trim()).length === 0}
                  className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                  {saveMutation.isPending ? (editing ? 'Updating…' : 'Creating…') : (editing ? 'Update Order Set' : 'Create Order Set')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
