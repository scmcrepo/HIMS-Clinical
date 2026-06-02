import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useCatalogSearch, useCatalogCategories } from '../../../hooks/catalog/useCatalog'
import { catalogApi, type CreateItemCmd } from '../../../services/catalog/catalogApi'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../hooks/useToast'
import { formatCurrency } from '../../../lib/currency'
import { cn } from '../../../lib/utils'
import type { ServiceItem } from '../../../types/catalog'
import { useQuery } from '@tanstack/react-query'
import {
  categoryMasterApi,
  type Category, type CategoryType, type ChargeCategoryType, type DiagnosticType, type EntityStatus
} from '../../../services/masters/masterApi'

type Tab = 'catalog' | 'categories' | 'users' | 'specimens'

export default function SettingsPage() {
  const [sp, setSp] = useSearchParams()
  const tabParam = sp.get('tab') as Tab
  const tab = tabParam || 'catalog'
  const setTab = (newTab: Tab) => {
    setSp(prev => {
      const next = new URLSearchParams(prev)
      next.set('tab', newTab)
      return next
    })
  }

  return (
    <div className="space-y-5">
      <h2 className="text-xl font-bold text-gray-900">Settings</h2>
      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit" role="tablist">
        {(['catalog', 'categories', 'specimens', 'users'] as const).map(t => (
          <button key={t} role="tab" aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={cn('px-4 py-1.5 text-sm font-medium rounded-md transition-colors capitalize',
              tab === t ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700')}>
            {t === 'catalog' ? 'Service Catalog' : t === 'categories' ? 'Categories' : t === 'specimens' ? 'Specimens' : 'Users'}
          </button>
        ))}
      </div>
      {tab === 'catalog'     && <ServiceCatalogTab />}
      {tab === 'categories'  && <CategoriesTab />}
      {tab === 'specimens'   && <SpecimensConfigPage />}
      {tab === 'users'       && <UsersTab />}
    </div>
  )
}

import SpecimensConfigPage from '../../config/pages/SpecimensConfigPage'

function ServiceCatalogTab() {
  const [q, setQ] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [editItem, setEditItem] = useState<ServiceItem | null>(null)
  const { data, isLoading } = useCatalogSearch(q || ' ')
  const { data: categories } = useCatalogCategories()
  const qc = useQueryClient()

  const [form, setForm] = useState<CreateItemCmd>({
    name: '', categoryId: '', serviceType: 'INDIVIDUAL', requiresOrder: false,
    pricingTiers: [
      { billType: 'CASH', unitRate: 0 },
      { billType: 'CREDIT', unitRate: 0 },
      { billType: 'INSURANCE', unitRate: 0 },
    ]
  })

  const createMutation = useMutation({
    mutationFn: (cmd: CreateItemCmd) => catalogApi.createItem(cmd),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['catalog'] })
      toast({ title: 'Service item created', variant: 'success' })
      setShowForm(false)
      setForm({ name: '', categoryId: '', serviceType: 'INDIVIDUAL', requiresOrder: false, pricingTiers: [{ billType: 'CASH', unitRate: 0 }, { billType: 'CREDIT', unitRate: 0 }, { billType: 'INSURANCE', unitRate: 0 }] })
    },
    onError: (e: Error) => toast({ title: 'Create failed', description: e.message, variant: 'destructive' }),
  })

  const updateMutation = useMutation({
    mutationFn: (cmd: CreateItemCmd) => catalogApi.updateItem(editItem!.id, cmd),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['catalog'] })
      toast({ title: 'Service item updated', variant: 'success' })
      setShowForm(false)
      setEditItem(null)
    },
    onError: (e: Error) => toast({ title: 'Update failed', description: e.message, variant: 'destructive' }),
  })

  const deactivateMutation = useMutation({
    mutationFn: (itemId: string) => catalogApi.deactivate(itemId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['catalog'] }); toast({ title: 'Item deactivated', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const activateMutation = useMutation({
    mutationFn: (itemId: string) => catalogApi.activate(itemId),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['catalog'] }); toast({ title: 'Item activated', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  })

  const inputCls = "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
  const labelCls = "block text-xs font-medium text-gray-700 mb-1"

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <input type="search" value={q} onChange={e => setQ(e.target.value)}
          placeholder="Search services by name…"
          className="w-full max-w-sm px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          aria-label="Search service catalog" />
        <button onClick={() => {
            setEditItem(null)
            setForm({ name: '', categoryId: '', serviceType: 'INDIVIDUAL', requiresOrder: false, pricingTiers: [{ billType: 'CASH', unitRate: 0 }, { billType: 'CREDIT', unitRate: 0 }, { billType: 'INSURANCE', unitRate: 0 }] })
            setShowForm(v => !v)
          }}
          className="px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 transition-colors whitespace-nowrap">
          + Add Service
        </button>
      </div>

      {/* Add service form */}
      {showForm && (
        <div className="bg-gray-50 border border-gray-200 rounded-xl p-5 space-y-4">
          <h3 className="text-sm font-semibold text-gray-900">{editItem ? 'Edit Service Item' : 'New Service Item'}</h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={labelCls}>Service Name *</label>
              <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} className={inputCls} placeholder="e.g. Complete Blood Count" />
            </div>
            <div>
              <label className={labelCls}>Category *</label>
              <select value={form.categoryId} onChange={e => setForm(f => ({ ...f, categoryId: e.target.value }))} className={inputCls}>
                <option value="">Select category</option>
                {categories?.filter((c: any) => c.status !== 'INACTIVE' && c.status !== 0).map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div>
              <label className={labelCls}>Service Type</label>
              <select value={form.serviceType} onChange={e => setForm(f => ({ ...f, serviceType: e.target.value as 'INDIVIDUAL' | 'PACKAGE' | 'INPATIENT' }))} className={inputCls}>
                <option value="INDIVIDUAL">Individual</option>
                <option value="PACKAGE">Package</option>
                <option value="INPATIENT">Inpatient</option>
              </select>
            </div>
            <div className="flex items-center gap-2 pt-5">
              <input type="checkbox" id="requiresOrder" checked={form.requiresOrder}
                onChange={e => setForm(f => ({ ...f, requiresOrder: e.target.checked }))}
                className="w-4 h-4 rounded border-gray-300 text-blue-600" />
              <label htmlFor="requiresOrder" className="text-sm text-gray-700">Requires order</label>
            </div>
          </div>

          <div>
            <label className={labelCls}>Pricing Tiers</label>
            <div className="grid grid-cols-3 gap-3">
              {form.pricingTiers.map((tier, i) => (
                <div key={tier.billType} className="flex items-center gap-2 bg-white border border-gray-200 rounded-lg p-3">
                  <span className="text-xs font-semibold text-gray-600 w-16">{tier.billType}</span>
                  <span className="text-xs text-gray-400">₹</span>
                  <input type="number" min={0} step={0.01}
                    value={tier.unitRate / 100}
                    onChange={e => setForm(f => ({ ...f, pricingTiers: f.pricingTiers.map((t, idx) => idx === i ? { ...t, unitRate: Math.round(parseFloat(e.target.value || '0') * 100) } : t) }))}
                    className="flex-1 px-2 py-1 border-0 border-b border-gray-200 text-sm focus:outline-none focus:border-blue-500 text-right"
                    aria-label={`${tier.billType} rate in rupees`} />
                </div>
              ))}
            </div>
          </div>

          <div className="flex gap-3">
            {editItem ? (
              <button onClick={() => updateMutation.mutate(form)}
                disabled={!form.name || !form.categoryId || updateMutation.isPending}
                className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                {updateMutation.isPending ? 'Updating…' : 'Update Service'}
              </button>
            ) : (
              <button onClick={() => createMutation.mutate(form)}
                disabled={!form.name || !form.categoryId || createMutation.isPending}
                className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                {createMutation.isPending ? 'Creating…' : 'Create Service'}
              </button>
            )}
            <button onClick={() => { setShowForm(false); setEditItem(null); }}
              className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Catalog table */}
      {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Searching…</p>}
      {data && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm" aria-label="Service catalog">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                <th className="px-4 py-3 font-semibold text-gray-600">Service Name</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Type</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Cash Rate</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Credit Rate</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Insurance Rate</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Status</th>
                <th className="px-4 py-3 font-semibold text-gray-600">Actions</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.content.map(item => {
                const cashRate     = item.pricingTiers.find(t => t.billType === 'CASH')?.unitRate ?? 0
                const creditRate   = item.pricingTiers.find(t => t.billType === 'CREDIT')?.unitRate ?? 0
                const insurRate    = item.pricingTiers.find(t => t.billType === 'INSURANCE')?.unitRate ?? 0
                return (
                  <tr key={item.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 font-medium text-gray-900">{item.name}</td>
                    <td className="px-4 py-3 text-gray-500 capitalize text-xs">{item.serviceType.toLowerCase()}</td>
                    <td className="px-4 py-3 font-mono text-sm text-gray-700">{formatCurrency(cashRate)}</td>
                    <td className="px-4 py-3 font-mono text-sm text-gray-700">{formatCurrency(creditRate)}</td>
                    <td className="px-4 py-3 font-mono text-sm text-gray-700">{formatCurrency(insurRate)}</td>
                    <td className="px-4 py-3">
                      <span className={cn('inline-flex items-center px-2 py-0.5 rounded text-xs font-medium',
                        item.status === 'ACTIVE' ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500')}>
                        {item.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 flex gap-2">
                      <button onClick={() => {
                          setEditItem(item)
                          setForm({
                            name: item.name,
                            categoryId: item.categoryId,
                            serviceType: item.serviceType,
                            requiresOrder: item.requiresOrder,
                            pricingTiers: (['CASH', 'CREDIT', 'INSURANCE'] as const).map(bt => {
                              const existing = item.pricingTiers.find(t => t.billType === bt)
                              return { billType: bt, unitRate: existing?.unitRate ?? 0 }
                            })
                          })
                          setShowForm(true)
                          window.scrollTo({ top: 0, behavior: 'smooth' })
                        }}
                        className="text-xs text-blue-600 hover:text-blue-800">
                        Edit
                      </button>
                      {item.status === 'ACTIVE' ? (
                        <button onClick={() => deactivateMutation.mutate(item.id)}
                          disabled={deactivateMutation.isPending}
                          className="text-xs text-red-500 hover:text-red-700 disabled:opacity-40">
                          Deactivate
                        </button>
                      ) : (
                        <button onClick={() => activateMutation.mutate(item.id)}
                          disabled={activateMutation.isPending}
                          className="text-xs text-green-600 hover:text-green-800 disabled:opacity-40">
                          Activate
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
              {data.content.length === 0 && (
                <tr><td colSpan={7} className="px-4 py-10 text-center text-gray-400 text-sm">No services found</td></tr>
              )}
            </tbody>
          </table>
          <div className="px-4 py-3 border-t border-gray-100 text-xs text-gray-500">
            {data.totalElements} total services
          </div>
        </div>
      )}
    </div>
  )
}

function CategoriesTab() {
  const qc = useQueryClient()
  const { data: cats = [], isLoading } = useQuery({ queryKey: ['masterCategories'], queryFn: () => categoryMasterApi.getAll() })
  
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
      toast({ title: 'Category saved successfully', variant: 'success' })
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

  const inputCls = "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
  const labelCls = "block text-xs font-medium text-gray-700 mb-1"

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center gap-3">
        <h3 className="text-lg font-bold text-gray-800">Categories</h3>
        <button onClick={() => { reset(); setShowForm(v => !v) }}
          className="px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 transition-colors whitespace-nowrap">
          + Add Category
        </button>
      </div>

      {showForm && (
        <div className="bg-gray-50 border border-gray-200 rounded-xl p-5 space-y-4 animate-in slide-in-from-top-2 duration-150">
          <h4 className="text-sm font-semibold text-gray-950">{editing ? 'Edit Category' : 'New Category'}</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className={labelCls}>Category Name *</label>
              <input 
                className={inputCls} 
                value={form.name} 
                onChange={e => setForm(f => ({ ...f, name: e.target.value }))} 
                placeholder="e.g. Laboratory Charges" 
              />
            </div>
            
            <div>
              <label className={labelCls}>Category Type *</label>
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
            </div>

            {form.type === 'PATIENT' && (
              <div>
                <label className={labelCls}>Value (paramValue)</label>
                <input 
                  className={inputCls} 
                  value={form.paramValue || ''} 
                  onChange={e => setForm(f => ({ ...f, paramValue: e.target.value }))} 
                  placeholder="e.g. VIP, Regular" 
                />
              </div>
            )}

            {form.type === 'CHARGE' && (
              <>
                <div>
                  <label className={labelCls}>Charge Category Type *</label>
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
                </div>

                {form.chargeCategoryType === 'DIAGNOSTICS' && (
                  <div>
                    <label className={labelCls}>Diagnostic Type *</label>
                    <select 
                      className={inputCls} 
                      value={form.diagnosticType || 'LAB'} 
                      onChange={e => setForm(f => ({ ...f, diagnosticType: e.target.value as DiagnosticType }))}
                    >
                      {DIAGNOSTIC_TYPES.map(t => (
                        <option key={t} value={t}>{t === 'LAB' ? 'LABORATORY' : 'RADIOLOGY'}</option>
                      ))}
                    </select>
                  </div>
                )}
              </>
            )}

            {editing && (
              <div>
                <label className={labelCls}>Status</label>
                <select 
                  className={inputCls} 
                  value={form.status} 
                  onChange={e => setForm(f => ({ ...f, status: e.target.value as EntityStatus }))}
                >
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              </div>
            )}
          </div>

          <div className="flex gap-3">
            <button onClick={() => mut.mutate()}
              disabled={!form.name || mut.isPending}
              className="px-5 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
              {mut.isPending ? 'Saving…' : 'Save'}
            </button>
            <button onClick={reset}
              className="px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
          </div>
        </div>
      )}

      <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
        <table className="w-full text-sm" aria-label="Service categories">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
              <th className="px-4 py-3 font-semibold text-gray-600">Category Name</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Type</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Details</th>
              <th className="px-4 py-3 font-semibold text-gray-600">Status</th>
              <th className="px-4 py-3 font-semibold text-gray-600 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400 text-sm">Loading…</td></tr>
            ) : cats.length === 0 ? (
              <tr><td colSpan={5} className="px-4 py-10 text-center text-gray-400 text-sm">No categories found</td></tr>
            ) : (
              cats.map(c => {
                let details = '-'
                if (c.type === 'PATIENT' && c.paramValue) {
                  details = `Value: ${c.paramValue}`
                } else if (c.type === 'CHARGE') {
                  const cct = c.chargeCategoryType?.replace(/_/g, ' ') || '-'
                  const dt = c.diagnosticType ? ` (${c.diagnosticType === 'LAB' ? 'LABORATORY' : 'RADIOLOGY'})` : ''
                  details = `${cct}${dt}`
                }

                return (
                  <tr key={c.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 font-medium text-gray-900">{c.name}</td>
                    <td className="px-4 py-3 text-gray-600 text-xs font-semibold uppercase">{c.type?.replace(/_/g, ' ')}</td>
                    <td className="px-4 py-3 text-gray-500 text-sm">{details}</td>
                    <td className="px-4 py-3">
                      <span className={cn('inline-flex items-center px-2 py-0.5 rounded text-xs font-medium',
                        c.status === 'ACTIVE' || (c.status as any) === 1 ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500')}>
                        {c.status === 'ACTIVE' || (c.status as any) === 1 ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button onClick={() => startEdit(c)} className="text-xs text-blue-600 hover:text-blue-800 font-semibold">
                        Edit
                      </button>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function UsersTab() {
  return (
    <div className="bg-white border border-gray-200 rounded-xl p-8 text-center">
      <p className="text-2xl mb-3">👤</p>
      <h3 className="text-lg font-bold text-gray-700">User Management</h3>
      <p className="text-sm text-gray-400 mt-1 max-w-sm mx-auto">
        Create users, assign roles, manage feature permissions and department access.
        Type CONTINUE to generate this module.
      </p>
    </div>
  )
}


