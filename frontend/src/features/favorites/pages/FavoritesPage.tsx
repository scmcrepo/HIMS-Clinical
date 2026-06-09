/**
 * FavoritesPage.tsx
 * Manage consultant-specific clinical favorites (drugs and tests).
 * Under the hood, each favorite is an OrderSet with isFavorite=true
 * and scope=CONSULTANT, tied to a specific consultantId.
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { orderSetApi, type OrderSet } from '../../../services/orderset/orderSetApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import { Star, Pill, TestTube, Clock, Calendar, Syringe, FileText, User } from 'lucide-react'

type FavType = 'ALL' | 'PRESCRIPTION' | 'DIAGNOSTICS'

export default function FavoritesPage() {
  const qc = useQueryClient()
  const [selectedConsultant, setSelectedConsultant] = useState('')
  const [favTypeFilter, setFavTypeFilter] = useState<FavType>('ALL')
  const [showForm, setShowForm] = useState(false)
  const [editingFav, setEditingFav] = useState<OrderSet | null>(null)

  // Form state
  const [itemType, setItemType]     = useState<'DRUG' | 'TEST'>('DRUG')
  const [itemName, setItemName]     = useState('')
  const [frequency, setFrequency]   = useState('')
  const [duration, setDuration]     = useState('')
  const [routeLabel, setRouteLabel] = useState('')
  const [instruction, setInstruction] = useState('')

  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants-all'],
    queryFn: () => consultantApi.getAll(),
  })

  const { data: allFavorites = [], isLoading } = useQuery({
    queryKey: ['favorites-page', selectedConsultant],
    queryFn: () => selectedConsultant
      ? orderSetApi.getFavorites(selectedConsultant)
      : orderSetApi.search(),
    select: (data: OrderSet[]) => data.filter(os => os.isFavorite),
    enabled: true,
  })

  const filtered = allFavorites.filter(fav => {
    if (favTypeFilter === 'ALL') return true
    return fav.setType === favTypeFilter
  })

  const saveMut = useMutation({
    mutationFn: () => {
      const payload = {
        name: `⭐ ${itemName}`,
        description: null,
        setType: itemType === 'DRUG' ? 'PRESCRIPTION' as const : 'DIAGNOSTICS' as const,
        isOutpatient: true,
        isFavorite: true,
        scope: 'CONSULTANT' as const,
        consultantId: selectedConsultant || undefined,
        items: [{
          itemType:    itemType === 'DRUG' ? 'PHARMACY' as const : 'DIAGNOSTIC' as const,
          itemName,
          quantity:    1,
          frequency:   frequency || undefined,
          duration:    duration  || undefined,
          routeLabel:  routeLabel || undefined,
          instruction: instruction || undefined,
        }],
        status: 'ACTIVE' as any,
      }
      if (editingFav) {
        return orderSetApi.update(editingFav.id, payload)
      }
      return orderSetApi.create(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['favorites-page'] })
      qc.invalidateQueries({ queryKey: ['favorites', selectedConsultant] })
      toast({ title: editingFav ? 'Favorite updated' : 'Favorite added', variant: 'success' })
      resetForm()
    },
    onError: (e: Error) => toast({ title: 'Failed to save', description: e.message, variant: 'destructive' }),
  })

  const deleteMut = useMutation({
    mutationFn: (id: string) => orderSetApi.deactivate(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['favorites-page'] })
      qc.invalidateQueries({ queryKey: ['favorites', selectedConsultant] })
      toast({ title: 'Favorite removed', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  function resetForm() {
    setShowForm(false); setEditingFav(null)
    setItemType('DRUG'); setItemName(''); setFrequency('')
    setDuration(''); setRouteLabel(''); setInstruction('')
  }

  function startEdit(fav: OrderSet) {
    const item = fav.items?.[0]
    setEditingFav(fav)
    setItemType(item?.itemType === 'DIAGNOSTIC' ? 'TEST' : 'DRUG')
    setItemName(item?.itemName ?? fav.name.replace('⭐ ', ''))
    setFrequency(item?.frequency ?? '')
    setDuration(item?.duration ?? '')
    setRouteLabel(item?.routeLabel ?? '')
    setInstruction(item?.instruction ?? '')
    setShowForm(true)
  }

  const inputCls = 'w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50 focus:outline-none focus:ring-2 focus:ring-neutral-500 focus:bg-white transition-all'

  return (
    <div className="min-h-screen bg-gray-50/50">
      <div className="max-w-5xl mx-auto px-6 py-8 space-y-6">
        {/* Page header */}
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
              <Star className="w-6 h-6 fill-amber-400 text-amber-400" />
              <span>Favorites</span>
            </h1>
            <p className="text-sm text-gray-500 mt-1">
              Consultant-specific saved drugs and tests for one-click ordering in prescriptions and diagnostics.
              These appear in the Quick-Add panel of every casesheet.
            </p>
          </div>
          <button
            onClick={() => { resetForm(); setShowForm(true) }}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-neutral-600 text-white text-sm font-semibold rounded-xl hover:bg-neutral-700 transition-colors shadow-sm shrink-0">
            <span className="text-lg leading-none">+</span> Add Favorite
          </button>
        </div>

        {/* Filters bar */}
        <div className="bg-white border border-gray-200 rounded-2xl px-5 py-4 flex items-center gap-4 flex-wrap shadow-sm">
          <div className="flex flex-col gap-1 min-w-[200px]">
            <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Consultant</label>
            <div className="w-56">
              <ConsultantSearchInput
                consultants={consultants as any[]}
                value={selectedConsultant}
                onChange={setSelectedConsultant}
                placeholder="All Consultants"
              />
            </div>
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Type</label>
            <div className="flex gap-1">
              {(['ALL', 'PRESCRIPTION', 'DIAGNOSTICS'] as FavType[]).map(t => (
                <button key={t} onClick={() => setFavTypeFilter(t)}
                  className={cn('px-3 py-1.5 text-xs font-semibold rounded-lg border transition-colors flex items-center gap-1.5',
                    favTypeFilter === t
                      ? 'bg-neutral-600 text-white border-neutral-600'
                      : 'bg-white text-gray-600 border-gray-200 hover:border-neutral-300')}>
                  {t === 'ALL' ? 'All' : t === 'PRESCRIPTION' ? (
                    <>
                      <Pill size={13} />
                      <span>Drugs</span>
                    </>
                  ) : (
                    <>
                      <TestTube size={13} />
                      <span>Tests</span>
                    </>
                  )}
                </button>
              ))}
            </div>
          </div>
          <div className="ml-auto text-xs text-gray-400">
            {filtered.length} favorite{filtered.length !== 1 ? 's' : ''}
          </div>
        </div>

        {/* Favorites grid */}
        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[1,2,3,4,5,6].map(i => (
              <div key={i} className="h-28 bg-white border border-gray-200 rounded-2xl animate-pulse" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 bg-white border border-dashed border-gray-200 rounded-2xl text-center">
            <Star className="w-12 h-12 text-gray-300 mx-auto mb-4" />
            <p className="text-base font-semibold text-gray-600">No favorites yet</p>
            <p className="text-sm text-gray-400 mt-1 max-w-xs">
              Add frequently used drugs or tests as favorites to speed up clinical ordering
              {selectedConsultant ? ' for this consultant' : ''}.
            </p>
            <button onClick={() => { resetForm(); setShowForm(true) }}
              className="mt-4 px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-xl hover:bg-neutral-700 transition-colors">
              Add First Favorite
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filtered.map(fav => {
              const item = fav.items?.[0]
              const isDrug = fav.setType === 'PRESCRIPTION'
              const consultantName = (consultants as any[]).find((c: any) => c.id === fav.consultantId)
              const cName = consultantName
                ? `${consultantName.salutation ? consultantName.salutation + ' ' : ''}${consultantName.firstName} ${consultantName.lastName}`
                : null
              return (
                <div key={fav.id}
                  className="bg-white border border-gray-200 rounded-2xl overflow-hidden hover:shadow-md transition-all group">
                  <div className="p-4">
                    <div className="flex items-start gap-3">
                      <div className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 bg-neutral-50">
                        {isDrug ? <Pill className="text-neutral-500 w-5 h-5" /> : <TestTube className="text-neutral-500 w-5 h-5" />}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="font-bold text-gray-900 text-sm truncate">
                          {item?.itemName ?? fav.name.replace('⭐ ', '')}
                        </p>
                        <span className={cn('inline-flex items-center mt-1 px-2 py-0.5 rounded-full text-[10px] font-bold border',
                          isDrug ? 'bg-blue-50 text-blue-700 border-blue-200' : 'bg-purple-50 text-purple-700 border-purple-200')}>
                          {isDrug ? 'DRUG' : 'TEST'}
                        </span>
                      </div>
                    </div>

                    {/* Drug-specific details */}
                    {isDrug && (item?.frequency || item?.duration || item?.routeLabel) && (
                      <div className="mt-3 flex flex-wrap gap-1.5">
                        {item?.frequency && (
                          <span className="inline-flex items-center px-2 py-0.5 bg-gray-100 text-gray-600 rounded-full text-[10px] font-medium gap-1">
                            <Clock size={10} className="text-gray-400" />
                            {item.frequency}
                          </span>
                        )}
                        {item?.duration && (
                          <span className="inline-flex items-center px-2 py-0.5 bg-gray-100 text-gray-600 rounded-full text-[10px] font-medium gap-1">
                            <Calendar size={10} className="text-gray-400" />
                            {item.duration}
                          </span>
                        )}
                        {item?.routeLabel && (
                          <span className="inline-flex items-center px-2 py-0.5 bg-gray-100 text-gray-600 rounded-full text-[10px] font-medium gap-1">
                            <Syringe size={10} className="text-gray-400" />
                            {item.routeLabel}
                          </span>
                        )}
                        {item?.instruction && (
                          <span className="inline-flex items-center px-2 py-0.5 bg-gray-100 text-gray-600 rounded-full text-[10px] font-medium gap-1">
                            <FileText size={10} className="text-gray-400" />
                            {item.instruction}
                          </span>
                        )}
                      </div>
                    )}

                    {cName && (
                      <p className="mt-2 text-[11px] text-gray-400 flex items-center gap-1.5">
                        <User size={12} className="text-gray-400 shrink-0" />
                        <span>{cName}</span>
                      </p>
                    )}
                  </div>

                  <div className="px-4 py-2.5 bg-gray-50 border-t border-gray-100 flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button onClick={() => startEdit(fav)}
                      className="flex-1 py-1.5 text-xs font-semibold text-neutral-600 bg-neutral-50 rounded-lg hover:bg-neutral-100 transition-colors">
                      Edit
                    </button>
                    <button onClick={() => deleteMut.mutate(fav.id)}
                      className="flex-1 py-1.5 text-xs font-semibold text-red-500 bg-red-50 rounded-lg hover:bg-red-100 transition-colors">
                      Remove
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Add/Edit modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-150 p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl border border-gray-100 flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150">
            <div className="bg-gradient-to-r from-neutral-600 to-neutral-600 px-6 py-4 flex justify-between items-center text-white rounded-t-2xl">
              <h3 className="text-lg font-bold tracking-tight flex items-center gap-2">
                <Star size={18} className="fill-white text-white" />
                <span>{editingFav ? 'Edit Favorite' : 'Add Favorite'}</span>
              </h3>
              <button onClick={resetForm} className="text-white/80 hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-white/20">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="p-6 overflow-visible space-y-4 flex-1 bg-gray-50/50 overflow-y-auto">
              <div className="space-y-4 bg-white p-5 rounded-xl border border-gray-150 shadow-sm">
                {/* Consultant selector */}
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1">Consultant *</label>
                  <ConsultantSearchInput
                    consultants={consultants as any[]}
                    value={selectedConsultant}
                    onChange={setSelectedConsultant}
                    placeholder="— Select consultant —"
                  />
                </div>

                {/* Type selector */}
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1">Favorite Type *</label>
                  <div className="grid grid-cols-2 gap-2">
                    {(['DRUG', 'TEST'] as const).map(t => (
                      <button key={t} type="button"
                        onClick={() => setItemType(t)}
                        className={cn('py-2.5 rounded-xl text-sm font-semibold border-2 transition-all flex items-center justify-center gap-2',
                          itemType === t
                            ? 'bg-neutral-600 border-neutral-600 text-white'
                            : 'bg-white border-gray-200 text-gray-600 hover:border-gray-300')}>
                        {t === 'DRUG' ? (
                          <>
                            <Pill size={16} />
                            <span>Drug</span>
                          </>
                        ) : (
                          <>
                            <TestTube size={16} />
                            <span>Test</span>
                          </>
                        )}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Item name */}
                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1">
                    {itemType === 'DRUG' ? 'Drug Name' : 'Test Name'} *
                  </label>
                  <input value={itemName} onChange={e => setItemName(e.target.value)}
                    placeholder={itemType === 'DRUG' ? 'e.g. Paracetamol 500mg' : 'e.g. Complete Blood Count'}
                    className={inputCls} />
                </div>

                {/* Drug-specific fields */}
                {itemType === 'DRUG' && (
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 mb-1">Frequency</label>
                      <input value={frequency} onChange={e => setFrequency(e.target.value)}
                        placeholder="e.g. 1-0-1" className={inputCls} />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 mb-1">Duration</label>
                      <input value={duration} onChange={e => setDuration(e.target.value)}
                        placeholder="e.g. 5 days" className={inputCls} />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 mb-1">Route</label>
                      <input value={routeLabel} onChange={e => setRouteLabel(e.target.value)}
                        placeholder="e.g. Oral" className={inputCls} />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-gray-600 mb-1">Instruction</label>
                      <input value={instruction} onChange={e => setInstruction(e.target.value)}
                        placeholder="e.g. After Food" className={inputCls} />
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className="bg-gray-50 px-6 py-4 flex justify-end gap-3 border-t border-gray-150 rounded-b-2xl">
              <button type="button" onClick={resetForm}
                className="px-4 py-2 text-xs font-bold rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-100 active:bg-gray-200 transition-all focus:outline-none">
                Cancel
              </button>
              <button
                type="button"
                onClick={() => saveMut.mutate()}
                disabled={!itemName.trim() || !selectedConsultant || saveMut.isPending}
                className="px-5 py-2 text-xs font-bold rounded-lg bg-neutral-600 hover:bg-neutral-700 text-white shadow-md active:scale-[0.98] transition-all disabled:opacity-50 disabled:pointer-events-none focus:outline-none flex items-center justify-center gap-1.5">
                <Star size={14} className="fill-current" />
                <span>{saveMut.isPending ? 'Saving…' : (editingFav ? 'Update Favorite' : 'Add Favorite')}</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
