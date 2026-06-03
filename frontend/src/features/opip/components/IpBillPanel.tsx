/**
 * IpBillPanel.tsx — Live IP Draft Bill viewer for the IpCaseSheetPage.
 *
 * Shows per the MD spec:
 *  - Clear running total (billed amount vs. due)
 *  - Breakdown: Bed charges | Diagnostics | Other charges | Pharmacy (pending)
 *  - Pending pharmacy sales (not yet committed to bill)
 *  - State indicators: DRAFT / GENERATED / WITH_DUE
 *  - Quick "Add Charge" shortcut for manual add-ons
 */
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { billingVisitApi, type AddChargeCmd } from '../../../services/billing/billingApi'
import { catalogApi } from '../../../services/catalog/catalogApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'
import type { ChargeLineItem } from '../../../types/billing'

interface Props {
  encounterId: string
  readOnly?:   boolean
}

function fmt(paise: number) {
  return '₹' + (paise / 100).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const STATUS_CONFIG = {
  DRAFT:     { label: 'Draft',      cls: 'bg-amber-50 text-amber-700 border-amber-200' },
  SETTLED:   { label: 'Settled',    cls: 'bg-green-50 text-green-700 border-green-200' },
  WITH_DUE:  { label: 'Balance Due',cls: 'bg-red-50 text-red-700 border-red-200' },
  REFUNDED:  { label: 'Refunded',   cls: 'bg-purple-50 text-purple-700 border-purple-200' },
  CANCELLED: { label: 'Cancelled',  cls: 'bg-gray-50 text-gray-500 border-gray-200' },
}

export function IpBillPanel({ encounterId, readOnly }: Props) {
  const qc = useQueryClient()
  const [showAddCharge, setShowAddCharge] = useState(false)
  const [chargeService, setChargeService] = useState<{ id: string; name: string; rate: number } | null>(null)
  const [chargeQty, setChargeQty] = useState(1)
  const [serviceSearch, setServiceSearch] = useState('')

  const {
    data: bill,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['ip-draft-bill', encounterId],
    queryFn:  () => billingVisitApi.getBillByVisit(encounterId),
    enabled:  !!encounterId,
    refetchInterval: 30_000, // poll every 30s for running total updates
    retry: false, // don't retry 404 (no bill yet)
  })

  const { data: serviceResults = [] } = useQuery({
    queryKey: ['catalog-search', serviceSearch],
    queryFn:  () => serviceSearch.length >= 2
      ? catalogApi.search(serviceSearch).then(r => r.content ?? [])
      : Promise.resolve([]),
    enabled: serviceSearch.length >= 2,
  })

  const addChargeMut = useMutation({
    mutationFn: (cmd: AddChargeCmd) => billingVisitApi.addChargeByVisit(encounterId, cmd),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['ip-draft-bill', encounterId] })
      setShowAddCharge(false); setChargeService(null); setChargeQty(1); setServiceSearch('')
      toast({ title: 'Charge added to IP bill', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Failed to add charge', description: e.message, variant: 'destructive' }),
  })

  if (isLoading) {
    return (
      <div className="space-y-3">
        {[1, 2, 3].map(i => <div key={i} className="h-14 bg-gray-100 rounded-xl animate-pulse" />)}
      </div>
    )
  }

  if (error || !bill) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <div className="text-4xl mb-3">🧾</div>
        <p className="text-sm font-semibold text-gray-600">No draft bill yet</p>
        <p className="text-xs text-gray-400 mt-1">A draft bill is created automatically when diagnostics are ordered or a bed is assigned.</p>
      </div>
    )
  }

  const statusCfg = STATUS_CONFIG[bill.status] ?? STATUS_CONFIG.DRAFT
  const isDraft   = bill.status === 'DRAFT'

  // Categorise charge line items
  const lines: ChargeLineItem[] = bill.chargeLineItems ?? []
  const bedLines   = lines.filter(l => l.bedChargeFrom != null)
  const diagLines  = lines.filter(l => l.bedChargeFrom == null && l.status == null)
  const otherLines = lines.filter(l => l.bedChargeFrom == null && l.status === 'MODIFIED')


  return (
    <div className="space-y-4">
      {/* Bill status header */}
      <div className="bg-white border border-gray-200 rounded-2xl overflow-hidden">
        <div className="px-5 py-4 bg-gradient-to-r from-slate-700 to-slate-800 text-white">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs text-slate-300 font-medium">IP Running Bill</p>
              {bill.billNumber && <p className="text-sm font-bold mt-0.5">{bill.billNumber}</p>}
            </div>
            <span className={cn('inline-flex items-center px-3 py-1 rounded-full text-xs font-bold border', statusCfg.cls)}>
              {statusCfg.label}
            </span>
          </div>
          <div className="grid grid-cols-3 gap-4 mt-4">
            <Stat label="Total Charges" value={fmt(bill.billAmount)} highlight />
            <Stat label="Paid" value={fmt(bill.paymentTotal)} />
            <Stat label="Balance Due" value={fmt(bill.dueAmount)} highlight={bill.dueAmount > 0} warn={bill.dueAmount > 0} />
          </div>
          {bill.discountTotal > 0 && (
            <p className="text-xs text-slate-300 mt-2">Discount applied: {fmt(bill.discountTotal)}</p>
          )}
        </div>

        {/* Admission dates */}
        {(bill.admissionAt || bill.bedNumber) && (
          <div className="px-5 py-3 border-b border-gray-100 flex items-center gap-4 text-xs text-gray-500 bg-gray-50">
            {bill.bedNumber && <span>🛏️ Bed <strong className="text-gray-700">{bill.bedNumber}</strong></span>}
            {bill.admissionAt && <span>Admitted: <strong className="text-gray-700">{formatDateTime(bill.admissionAt)}</strong></span>}
            {bill.dischargeAt && <span>Discharged: <strong className="text-gray-700">{formatDateTime(bill.dischargeAt)}</strong></span>}
          </div>
        )}
      </div>

      {/* Charge breakdown sections */}
      <ChargeSection
        title="🛏️ Bed / Room Charges"
        lines={bedLines}
        emptyMsg="Bed charges are calculated at bill generation based on stay duration."
        bgColor="bg-blue-50/50"
      />

      <ChargeSection
        title="🧪 Diagnostic Orders"
        lines={diagLines.filter(l => !bedLines.includes(l) && !otherLines.includes(l))}
        emptyMsg="Diagnostic orders placed for this admission appear here automatically."
        bgColor="bg-purple-50/50"
      />

      <ChargeSection
        title="💰 Other Charges"
        lines={otherLines}
        emptyMsg="Manual additional charges added during the stay."
        bgColor="bg-amber-50/50"
      />

      {/* Pharmacy pending notice */}
      <div className="border border-orange-200 rounded-xl bg-orange-50 px-4 py-3">
        <div className="flex items-start gap-2">
          <span className="text-orange-500 text-lg mt-0.5">💊</span>
          <div>
            <p className="text-xs font-bold text-orange-800">Pharmacy / Drug Charges</p>
            <p className="text-[11px] text-orange-600 mt-0.5">
              IP prescriptions are billed through the Pharmacy module. Drug sales are automatically consolidated into this bill when the final bill is generated.
            </p>
          </div>
        </div>
      </div>

      {/* Add Manual Charge */}
      {isDraft && !readOnly && (
        <div>
          {!showAddCharge ? (
            <button onClick={() => setShowAddCharge(true)}
              className="w-full py-2.5 border-2 border-dashed border-gray-300 rounded-xl text-sm font-medium text-gray-500 hover:border-blue-400 hover:text-blue-600 hover:bg-blue-50 transition-all">
              + Add Manual Charge
            </button>
          ) : (
            <div className="border border-gray-200 rounded-xl bg-white p-4 space-y-3">
              <p className="text-xs font-bold text-gray-700">Add Charge to IP Bill</p>
              {/* Service search */}
              <div className="relative">
                <input
                  value={serviceSearch}
                  onChange={e => setServiceSearch(e.target.value)}
                  placeholder="Search service/charge name (min 2 chars)…"
                  className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                {serviceResults.length > 0 && (
                  <div className="absolute top-full left-0 right-0 bg-white border border-gray-200 rounded-lg mt-1 shadow-lg z-20 max-h-48 overflow-y-auto">
                    {serviceResults.map((svc: any) => (
                      <button key={svc.id}
                        onClick={() => { setChargeService({ id: svc.id, name: svc.name, rate: svc.pricingTiers?.find((t: any) => t.billType === 'CASH')?.unitRate ?? 0 }); setServiceSearch('') }}
                        className="w-full flex items-center justify-between px-3 py-2 text-xs text-left hover:bg-blue-50 transition-colors">
                        <span className="font-medium text-gray-900">{svc.name}</span>
                        <span className="text-gray-400">₹{((svc.pricingTiers?.find((t: any) => t.billType === 'CASH')?.unitRate ?? 0) / 100).toFixed(2)}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
              {chargeService && (
                <div className="flex items-center gap-3">
                  <div className="flex-1 px-3 py-2 bg-blue-50 text-blue-800 rounded-lg text-xs font-semibold">
                    {chargeService.name} — {fmt(chargeService.rate)}
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs text-gray-500">Qty:</span>
                    <input type="number" min={1} value={chargeQty} onChange={e => setChargeQty(parseInt(e.target.value) || 1)}
                      className="w-14 px-2 py-1.5 border border-gray-200 rounded-lg text-xs text-center" />
                  </div>
                </div>
              )}
              <div className="flex gap-2 pt-1">
                <button onClick={() => { setShowAddCharge(false); setChargeService(null); setServiceSearch('') }}
                  className="flex-1 py-2 text-xs font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                  Cancel
                </button>
                <button
                  onClick={() => {
                    if (!chargeService) { toast({ title: 'Select a service first', variant: 'destructive' }); return }
                    addChargeMut.mutate({
                      serviceCatalogItemId: chargeService.id,
                      unitRate: chargeService.rate,
                      quantity: chargeQty,
                    })
                  }}
                  disabled={!chargeService || addChargeMut.isPending}
                  className="flex-1 py-2 text-xs font-semibold bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
                  {addChargeMut.isPending ? 'Adding…' : 'Add Charge'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Payments summary */}
      {(bill.payments?.length ?? 0) > 0 && (
        <div className="border border-green-200 rounded-xl bg-green-50 p-4">
          <p className="text-xs font-bold text-green-800 mb-2">💳 Payments Recorded</p>
          {(bill.payments ?? []).map((p, idx) => (
            <div key={idx} className="flex items-center justify-between text-xs text-green-700 py-0.5">
              <span>{p.paymentMode} · {p.paymentType}</span>
              <span className="font-bold">{fmt(p.amount)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function Stat({ label, value, highlight, warn }: { label: string; value: string; highlight?: boolean; warn?: boolean }) {
  return (
    <div>
      <p className="text-[10px] text-slate-400 font-medium">{label}</p>
      <p className={cn('text-lg font-bold mt-0.5', warn ? 'text-red-300' : highlight ? 'text-white' : 'text-slate-300')}>
        {value}
      </p>
    </div>
  )
}

function ChargeSection({ title, lines, emptyMsg, bgColor }: {
  title: string; lines: ChargeLineItem[]; emptyMsg: string; bgColor: string
}) {
  const [open, setOpen] = useState(true)
  const total = lines.reduce((sum, l) => sum + l.amount, 0)

  return (
    <div className={cn('border border-gray-200 rounded-xl overflow-hidden', bgColor)}>
      <button onClick={() => setOpen(v => !v)}
        className="w-full flex items-center justify-between px-4 py-3">
        <span className="text-xs font-bold text-gray-700">{title}</span>
        <div className="flex items-center gap-2">
          {lines.length > 0 && (
            <span className="text-xs font-bold text-gray-900">₹{(total / 100).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
          )}
          <span className="text-gray-400 text-xs">{open ? '▲' : '▼'}</span>
        </div>
      </button>
      {open && (
        <div className="border-t border-gray-200 bg-white/70">
          {lines.length === 0 ? (
            <p className="px-4 py-3 text-[11px] text-gray-400 italic">{emptyMsg}</p>
          ) : (
            <table className="w-full text-xs">
              <tbody className="divide-y divide-gray-100">
                {lines.map(l => (
                  <tr key={l.id} className={cn(l.status === 'CANCELLED' && 'opacity-40 line-through')}>
                    <td className="px-4 py-2 text-gray-700 font-medium">{l.itemName}</td>
                    <td className="px-3 py-2 text-gray-400 text-right">×{l.quantity}</td>
                    <td className="px-4 py-2 text-gray-900 font-bold text-right">
                      {(l.amount / 100).toLocaleString('en-IN', { minimumFractionDigits: 2, style: 'currency', currency: 'INR' })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  )
}
