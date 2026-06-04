/**
 * QuickAddPanel.tsx — right sidebar for rapid prescription/diagnostic ordering.
 * Four panels:
 *   1. Favorites     — consultant-specific persisted favorites (backed by OrderSet)
 *   2. Frequently Used — auto-computed from encounter history
 *   3. Last Prescribed — drugs from patient's last visit (Rx only)
 *   4. Order Sets     — pre-configured batch groups
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { cn } from '../../../lib/utils'
import {
  favoritesApi, frequentlyUsedApi, lastPrescribedApi,
} from '../../../services/opip/opipApi'
import { orderSetApi, type OrderSet } from '../../../services/orderset/orderSetApi'
import { toast } from '../../../hooks/useToast'

type Panel = 'favorites' | 'frequently' | 'lastPrescribed' | 'orderSets'

interface AddDrugPayload {
  drugItemId: string; drugName: string;
  frequency?: string; duration?: string; qty?: number;
  instructionLabel?: string; routeLabel?: string
}
interface AddTestPayload {
  diagnosticTestId: string; testName: string; category?: string
}

interface Props {
  mode: 'DRUG' | 'TEST'
  consultantId?: string | undefined
  encounterId: string
  onAddDrug?: (drug: AddDrugPayload) => void
  onAddTest?: (test: AddTestPayload) => void
  readOnly?: boolean
}

const PANEL_LABELS: Record<Panel, { label: string; icon: string; tooltip: string }> = {
  favorites: { label: 'Favorites', icon: '⭐', tooltip: 'Your saved favorites' },
  frequently: { label: 'Frequently Used', icon: '🔄', tooltip: 'Most ordered by you' },
  lastPrescribed: { label: 'Last Prescribed', icon: '📋', tooltip: 'From patient\'s last visit' },
  orderSets: { label: 'Order Sets', icon: '📦', tooltip: 'Pre-configured groups' },
}

export function QuickAddPanel({ mode, consultantId, encounterId, onAddDrug, onAddTest, readOnly }: Props) {
  const [activePanel, setActivePanel] = useState<Panel>(mode === 'DRUG' ? 'lastPrescribed' : 'orderSets')
  const qc = useQueryClient()

  // Favorites
  const { data: favItems = [], isLoading: favLoading } = useQuery({
    queryKey: ['favorites', consultantId, mode],
    queryFn: () => consultantId ? favoritesApi.list(consultantId, mode) : Promise.resolve([]),
    enabled: !!consultantId,
  })

  // Frequently Used
  const { data: freqItems = [], isLoading: freqLoading } = useQuery({
    queryKey: ['frequently-used', consultantId, mode],
    queryFn: () => consultantId
      ? (mode === 'DRUG' ? frequentlyUsedApi.drugs(consultantId) : frequentlyUsedApi.tests(consultantId))
      : Promise.resolve([]),
    enabled: !!consultantId,
  })

  // Last Prescribed (DRUG mode only)
  const { data: lastItems = [], isLoading: lastLoading } = useQuery({
    queryKey: ['last-prescribed', encounterId],
    queryFn: () => lastPrescribedApi.get(encounterId),
    enabled: mode === 'DRUG' && activePanel === 'lastPrescribed' && !!encounterId,
  })

  // Order Sets
  const { data: allSets = [], isLoading: setsLoading } = useQuery({
    queryKey: ['order-sets-panel', mode],
    queryFn: () => orderSetApi.search(),
    enabled: activePanel === 'orderSets',
  })
  const filteredSets = allSets.filter(s => {
    if (s.setType === 'BOTH') return true
    return mode === 'DRUG' ? s.setType === 'PRESCRIPTION' : s.setType === 'DIAGNOSTICS'
  })

  // Add to favorite mutation
  const addFavMut = useMutation({
    mutationFn: (payload: { itemId: string; itemName: string; favoriteType: 'DRUG' | 'TEST'; frequency?: string; duration?: string }) =>
      favoritesApi.add({ ...payload, consultantId: consultantId! }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['favorites', consultantId, mode] })
      toast({ title: 'Added to favorites', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  // Remove favorite
  const removeFavMut = useMutation({
    mutationFn: (id: string) => favoritesApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['favorites', consultantId, mode] })
      toast({ title: 'Removed from favorites', variant: 'success' })
    },
  })

  function handleAddDrug(drug: Partial<AddDrugPayload>) {
    if (readOnly || !onAddDrug) return
    const payload: AddDrugPayload = {
      drugItemId: drug.drugItemId ?? '',
      drugName: drug.drugName ?? 'Unknown',
      qty: drug.qty ?? 1,
    }
    if (drug.frequency !== undefined) payload.frequency = drug.frequency
    if (drug.duration !== undefined) payload.duration = drug.duration
    if (drug.instructionLabel !== undefined) payload.instructionLabel = drug.instructionLabel
    if (drug.routeLabel !== undefined) payload.routeLabel = drug.routeLabel
    onAddDrug(payload)
  }

  function handleAddTest(test: Partial<AddTestPayload>) {
    if (readOnly || !onAddTest) return
    onAddTest({ diagnosticTestId: test.diagnosticTestId ?? '', testName: test.testName ?? 'Unknown' })
  }

  const applyOrderSet = (os: OrderSet) => {
    const targetItemType = mode === 'DRUG' ? 'PHARMACY' : 'DIAGNOSTIC'
    const items = (os.items ?? []).filter(i => i.itemType === targetItemType)
    if (items.length === 0) { toast({ title: 'No matching items in this set', variant: 'destructive' }); return }
    items.forEach(item => {
      if (mode === 'DRUG') {
        const drugParam: Partial<AddDrugPayload> = {
          drugItemId: item.serviceCatalogItemId ?? '',
          drugName: item.itemName ?? '',
          qty: item.quantity,
        }
        if (item.frequency !== undefined) drugParam.frequency = item.frequency
        if (item.duration !== undefined) drugParam.duration = item.duration
        if (item.routeLabel !== undefined) drugParam.routeLabel = item.routeLabel
        if (item.instruction !== undefined) drugParam.instructionLabel = item.instruction
        handleAddDrug(drugParam)
      } else {
        handleAddTest({ diagnosticTestId: item.serviceCatalogItemId ?? '', testName: item.itemName ?? '' })
      }
    })
    toast({ title: `Applied "${os.name}" — ${items.length} item${items.length !== 1 ? 's' : ''} added`, variant: 'success' })
  }

  const visiblePanels = (Object.keys(PANEL_LABELS) as Panel[])
    .filter(p => p !== 'favorites' && p !== 'frequently')
    .filter(p => mode === 'TEST' ? p !== 'lastPrescribed' : true)

  return (
    <div className="flex flex-col h-full bg-white border border-gray-200 rounded-xl overflow-hidden w-80 shrink-0">
      {/* Panel tabs */}
      {visiblePanels.length > 1 && (
        <div className={cn(
          "grid border-b border-gray-200 bg-gray-50",
          visiblePanels.length === 2 ? "grid-cols-2" : "grid-cols-1"
        )}>
          {visiblePanels.map(p => (
            <button key={p} onClick={() => setActivePanel(p)} title={PANEL_LABELS[p].tooltip}
              className={cn('flex flex-col items-center gap-1 py-2.5 text-xs font-bold transition-all border-b-2',
                activePanel === p
                  ? 'border-blue-600 text-blue-700 bg-white'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:bg-white/50')}>
              <span className="text-lg">{PANEL_LABELS[p].icon}</span>
              <span className="leading-tight text-center px-0.5">{PANEL_LABELS[p].label.split(' ')[0]}</span>
            </button>
          ))}
        </div>
      )}

      {/* Panel content */}
      <div className="flex-1 overflow-y-auto p-2 space-y-1">

        {/* FAVORITES */}
        {activePanel === 'favorites' && (
          <>
            {favLoading ? <LoadingRows /> :
              favItems.length === 0 ? <EmptyState icon="⭐" msg="No Favorites Found!" sub="Click ⭐ next to any item to save it" /> :
                favItems.map(fav => (
                  <QuickItem key={fav.id}
                    label={fav.itemName ?? ''}
                    sublabel={[fav.frequency, fav.duration].filter(Boolean).join(' · ')}
                    onAdd={() => {
                      if (mode === 'DRUG') {
                        const p: Partial<AddDrugPayload> = { drugItemId: fav.itemId, drugName: fav.itemName ?? '' }
                        if (fav.frequency !== undefined) p.frequency = fav.frequency
                        if (fav.duration !== undefined) p.duration = fav.duration
                        if (fav.instructionLabel !== undefined) p.instructionLabel = fav.instructionLabel
                        if (fav.routeLabel !== undefined) p.routeLabel = fav.routeLabel
                        handleAddDrug(p)
                      } else {
                        handleAddTest({ diagnosticTestId: fav.itemId, testName: fav.itemName ?? '' })
                      }
                    }}
                    onRemoveFav={() => removeFavMut.mutate(fav.id)}
                    readOnly={readOnly}
                    showFavBtn
                    isFav
                  />
                ))}
          </>
        )}

        {/* FREQUENTLY USED */}
        {activePanel === 'frequently' && (
          <>
            {freqLoading ? <LoadingRows /> :
              freqItems.length === 0 ? <EmptyState icon="🔄" msg="No data yet" sub="Usage auto-tracks as you prescribe" /> :
                freqItems.map(freq => (
                  <QuickItem key={freq.itemId}
                    label={freq.itemName}
                    sublabel={`${freq.count}× prescribed`}
                    onAdd={() => {
                      if (mode === 'DRUG') {
                        const p: Partial<AddDrugPayload> = { drugItemId: freq.itemId, drugName: freq.itemName }
                        if (freq.frequency !== undefined) p.frequency = freq.frequency
                        if (freq.duration !== undefined) p.duration = freq.duration
                        if (freq.instructionLabel !== undefined) p.instructionLabel = freq.instructionLabel
                        if (freq.routeLabel !== undefined) p.routeLabel = freq.routeLabel
                        handleAddDrug(p)
                      } else {
                        handleAddTest({ diagnosticTestId: freq.itemId, testName: freq.itemName })
                      }
                    }}
                    onAddFav={consultantId ? () => {
                      const p: any = { itemId: freq.itemId, itemName: freq.itemName, favoriteType: mode }
                      if (freq.frequency !== undefined) p.frequency = freq.frequency
                      if (freq.duration !== undefined) p.duration = freq.duration
                      addFavMut.mutate(p)
                    } : undefined}
                    readOnly={readOnly}
                    showFavBtn
                  />
                ))}
          </>
        )}

        {/* LAST PRESCRIBED (DRUG only) */}
        {activePanel === 'lastPrescribed' && mode === 'DRUG' && (
          <>
            {lastLoading ? <LoadingRows /> :
              lastItems.length === 0 ? <EmptyState icon="📋" msg="No prior prescriptions" sub="Patient's last encounter drugs appear here" /> :
                (lastItems as any[]).map((item: any, idx: number) => (
                  <QuickItem key={item.id ?? idx}
                    label={item.drugName ?? ''}
                    sublabel={[item.frequency, item.duration].filter(Boolean).join(' · ')}
                    onAdd={() => {
                      const p: Partial<AddDrugPayload> = {
                        drugItemId: item.drugItemId ?? '',
                        drugName: item.drugName ?? '',
                        qty: item.qty,
                      }
                      if (item.frequency !== undefined) p.frequency = item.frequency
                      if (item.duration !== undefined) p.duration = item.duration
                      if (item.instructionLabel !== undefined) p.instructionLabel = item.instructionLabel
                      if (item.routeLabel !== undefined) p.routeLabel = item.routeLabel
                      handleAddDrug(p)
                    }}
                    readOnly={readOnly}
                  />
                ))}
          </>
        )}

        {/* ORDER SETS */}
        {activePanel === 'orderSets' && (
          <>
            {setsLoading ? <LoadingRows /> :
              filteredSets.length === 0 ? <EmptyState icon="📦" msg="No Order Sets" sub="Create order sets in the Order Sets module" /> :
                filteredSets.map(os => (
                  <div key={os.id} className="border border-gray-200 rounded-xl overflow-hidden mb-1">
                    <button onClick={() => applyOrderSet(os)} disabled={readOnly}
                      className="w-full flex items-start gap-2 p-2.5 hover:bg-blue-50 transition-colors text-left disabled:opacity-50">
                      <span className="text-base mt-0.5">📦</span>
                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-semibold text-gray-900 truncate">{os.name}</p>
                        <p className="text-[10px] text-gray-500 mt-0.5">
                          {(os.items ?? []).filter(i => mode === 'DRUG' ? i.itemType === 'PHARMACY' : i.itemType === 'DIAGNOSTIC').length} item(s) · tap to add all
                        </p>
                      </div>
                      {!readOnly && (
                        <span className="text-xs font-bold text-blue-600 bg-blue-100 px-2 py-0.5 rounded-full mt-0.5 flex-shrink-0">
                          Add All
                        </span>
                      )}
                    </button>
                    {/* Preview items */}
                    <div className="px-3 pb-2 space-y-0.5">
                      {(os.items ?? [])
                        .filter(i => mode === 'DRUG' ? i.itemType === 'PHARMACY' : i.itemType === 'DIAGNOSTIC')
                        .slice(0, 3)
                        .map((item, idx) => (
                          <p key={idx} className="text-[10px] text-gray-400 flex items-center gap-1.5">
                            <span className={cn('w-1 h-1 rounded-full flex-shrink-0',
                              mode === 'DRUG' ? 'bg-blue-300' : 'bg-purple-300')} />
                            {item.itemName ?? 'Item'}
                          </p>
                        ))}
                    </div>
                  </div>
                ))}
          </>
        )}
      </div>
    </div>
  )
}

// ── Sub-components ────────────────────────────────────────────────────────────

function QuickItem({ label, sublabel, onAdd, onAddFav, onRemoveFav, readOnly, showFavBtn, isFav }: {
  label: string; sublabel?: string | undefined; onAdd: () => void
  onAddFav?: (() => void) | undefined; onRemoveFav?: (() => void) | undefined
  readOnly?: boolean | undefined; showFavBtn?: boolean | undefined; isFav?: boolean | undefined
}) {
  return (
    <div className="flex items-center gap-1.5 px-2 py-1.5 rounded-lg hover:bg-gray-50 group transition-colors">
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-gray-900 truncate">{label}</p>
        {sublabel && <p className="text-xs text-gray-400 truncate">{sublabel}</p>}
      </div>
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {showFavBtn && (
          isFav
            ? <button onClick={onRemoveFav} title="Remove from favorites" className="p-1 text-yellow-500 hover:text-yellow-600 rounded transition-colors">★</button>
            : onAddFav && <button onClick={onAddFav} title="Add to favorites" className="p-1 text-gray-300 hover:text-yellow-500 rounded transition-colors">☆</button>
        )}
        {!readOnly && (
          <button onClick={onAdd} className="px-2.5 py-1 text-xs font-bold bg-blue-600 text-white rounded-full hover:bg-blue-700 transition-colors">
            +Add
          </button>
        )}
      </div>
    </div>
  )
}

function LoadingRows() {
  return (
    <div className="space-y-2 p-1">
      {[1, 2, 3].map(i => (
        <div key={i} className="h-8 bg-gray-100 rounded-lg animate-pulse" />
      ))}
    </div>
  )
}

function EmptyState({ icon, msg, sub }: { icon: string; msg: string; sub: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-8 text-center px-3">
      <span className="text-4xl mb-2">{icon}</span>
      <p className="text-sm font-bold text-gray-700">{msg}</p>
      <p className="text-xs text-gray-400 mt-1">{sub}</p>
    </div>
  )
}
