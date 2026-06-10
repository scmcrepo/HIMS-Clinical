import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useBill, useBillingMutations } from '../../../hooks/billing/useBilling'
import { BillStatusBadge } from '../../../components/shared/StatusBadge'
import { AmountDisplay } from '../../../components/shared/AmountDisplay'
import { ServiceSearchInput } from '../../../components/shared/ServiceSearchInput'
import BackButton from '../../../components/shared/BackButton'
import { PrintButton } from '../../../components/shared/PrintButton'
import { formatDate, formatDateTime } from '../../../lib/dateUtils'
import { toast } from '../../../hooks/useToast'
import { chargeApi } from '../../../services/masters/masterApi'

function formatDuration(from: string | null, to: string | null): string {
  if (!from) return '—'
  const start = new Date(from)
  const end = to ? new Date(to) : new Date()
  const diffMs = end.getTime() - start.getTime()
  if (diffMs < 0) return '0m'
  const days = Math.floor(diffMs / (1000 * 60 * 60 * 24))
  const hours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
  const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60))
  const parts = []
  if (days > 0) parts.push(`${days}d`)
  if (hours > 0) parts.push(`${hours}h`)
  if (days === 0 && hours === 0) parts.push(`${mins}m`)
  return parts.join(' ') || '0m'
}

export default function BillingPage() {
  const { billId } = useParams<{ billId: string }>()
  const navigate = useNavigate()
  const { data: bill, isLoading, error } = useBill(billId)
  const mutations = useBillingMutations(billId ?? '')

  // Inline editing state — unchanged
  const [editingLineId, setEditingLineId] = useState<string | null>(null)
  const [editRate, setEditRate] = useState<number>(0)
  const [editQty, setEditQty] = useState<number>(0)
  const [editDiscount, setEditDiscount] = useState<number>(0)

  // Remove confirmation state — kept inline (it's a small confirm, not a form)
  const [itemToRemove, setItemToRemove] = useState<{ id: string; name: string } | null>(null)
  const [showGenerateModal, setShowGenerateModal] = useState(false)

  if (isLoading) return <div className="text-sm text-gray-500 p-6" aria-live="polite">Loading bill…</div>
  if (error || !bill) return <div className="text-sm text-red-600 p-6" role="alert">Failed to load bill</div>

  const isGenerated = bill.status !== 'DRAFT'
  const isOp = bill.encounterType === 'OUTPATIENT'
  const canEditLineItems = !isGenerated && (bill.paymentTotal === 0 || !isOp)

  const startEditing = (item: any) => {
    if (bill.status !== 'DRAFT') return
    setEditingLineId(item.id)

    // For bed charges, user requested only manual entry and not to show master's bed charge
    if (item.bedChargeFrom != null) {
      setEditRate(0)
    } else {
      setEditRate(Math.round(item.unitRate / 100))
    }

    setEditQty(item.quantity)
    setEditDiscount(Math.round(item.discountAmount / 100))
  }

  const saveEdit = () => {
    if (!editingLineId) return
    const newRate = Math.round(editRate * 100)
    const discount = Math.round(editDiscount) * 100
    if (editRate < 0) { toast({ title: 'Rate must be non-negative', variant: 'destructive' }); return }
    if (editQty < 1) { toast({ title: 'Quantity must be at least 1', variant: 'destructive' }); return }
    if (editDiscount < 0) { toast({ title: 'Discount must be non-negative', variant: 'destructive' }); return }
    if (discount > (newRate * editQty)) { toast({ title: 'Item discount cannot be greater than the item total amount', variant: 'destructive' }); return }
    mutations.updateCharge.mutate(
      { lineItemId: editingLineId, rate: newRate, quantity: editQty, discount },
      { onSuccess: () => setEditingLineId(null) }
    )
  }

  const bedCharges = bill.chargeLineItems
    .filter(c => c.bedChargeFrom != null && c.status !== 'CANCELLED')
    .sort((a, b) => new Date(a.bedChargeFrom!).getTime() - new Date(b.bedChargeFrom!).getTime())

  const otherCharges = bill.chargeLineItems
    .filter(c => c.bedChargeFrom == null && c.status !== 'CANCELLED')
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Patient Info Banner */}
      <div className="bg-white border border-gray-200 rounded-xl p-4 shadow-sm flex flex-wrap gap-x-8 gap-y-4 items-center">
        <div>
          <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-0.5">Patient</p>
          <div className="flex items-center gap-2">
            <span className="font-bold text-gray-900">{bill.patientName || 'Unknown Patient'}</span>
            <span className="px-1.5 py-0.5 bg-blue-50 text-blue-700 border border-blue-100 rounded text-[10px] font-bold font-mono">
              {bill.patientNumber || '-'}
            </span>
            {bill.patientGender && (
              <span className="px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded text-[10px] font-bold">
                {bill.patientGender.substring(0, 1)}
              </span>
            )}
          </div>
        </div>
        {bill.consultantName && (
          <div>
            <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-0.5">Primary Consultant</p>
            <p className="font-medium text-gray-900 text-sm">{bill.consultantName}</p>
          </div>
        )}
        <div className="ml-auto text-right">
          <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-0.5">Encounter</p>
          <div className="flex items-center gap-2 justify-end">
            <span className="text-sm font-bold text-gray-900">{bill.encounterType}</span>
            <span className="px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded text-[10px] font-bold">{bill.billType}</span>
          </div>
        </div>
      </div>

      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-3">
          <h2 className="text-xl font-bold text-gray-900">Bill Details</h2>
          {bill.billNumber && (
            <span className="px-2 py-1 bg-gray-100 text-gray-600 rounded text-xs font-bold font-mono border border-gray-200">
              {bill.billNumber}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <BillStatusBadge status={bill.status} />
          <BackButton />
        </div>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        {[
          { label: 'Bill Amount', value: Number(bill.billAmount || 0) },
          { label: 'Paid', value: Number(bill.paymentTotal || 0) },
          { label: 'Discount', value: Number(bill.discountTotal || 0), negative: true },
          { label: 'Refunded', value: Number(bill.refundTotal || 0), negative: true },
          { label: 'Due', value: Number(bill.dueAmount || 0), highlight: (bill.dueAmount || 0) > 0 },
        ].map(({ label, value, negative, highlight }) => (
          <div key={label} className={`bg-white rounded-xl border p-4 ${highlight ? 'border-amber-300 bg-amber-50' : 'border-gray-200'}`}>
            <p className="text-xs text-gray-500 mb-1">{label}</p>
            <AmountDisplay amount={value} negative={negative} className={`text-lg font-bold ${highlight ? 'text-amber-700' : 'text-gray-900'}`} hideDecimals />
          </div>
        ))}
      </div>

      {/* Bed Charges Table */}
      {bedCharges.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
          <div className="px-5 py-3 border-b border-gray-100 bg-neutral-50/30">
            <h3 className="font-bold text-neutral-900 text-xs uppercase tracking-wider">Room Charges</h3>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 text-left border-b border-gray-100">
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs">Bed / Room</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs">Stay Period</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-center">Duration</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Amount (₹)</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Discount</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Net Amount (₹)</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {bedCharges.map(item => (
                <tr key={item.id} className={editingLineId === item.id ? 'bg-neutral-50/50' : ''}>
                  <td className="px-4 py-2.5">
                    {(() => {
                      const nameStr = String(item.itemName || '')
                      const [bedName, details] = nameStr.includes('(')
                        ? nameStr.split('(').map(s => s.trim().replace(')', ''))
                        : [nameStr, '']
                      return (
                        <div className="flex flex-col gap-0.5">
                          <div className="font-bold text-gray-900 text-xs leading-none">{bedName}</div>
                          {details && (
                            <div className="text-[9px] text-neutral-500/70 font-bold uppercase tracking-wide flex items-center gap-1.5">
                              {details.split('|').map((part, i) => (
                                <span key={i} className="flex items-center gap-1.5">
                                  {i > 0 && <span className="w-1 h-1 rounded-full bg-neutral-100" />}
                                  {part.trim()}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                      )
                    })()}
                  </td>
                  <td className="px-4 py-2.5 text-gray-500 text-[10px]">
                    {formatDateTime(item.bedChargeFrom)} {item.bedChargeTo ? `— ${formatDateTime(item.bedChargeTo)}` : '(Active)'}
                  </td>
                  <td className="px-4 py-2.5 text-center">
                    <span className="bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded-full text-[10px] font-bold">
                      {formatDuration(item.bedChargeFrom, item.bedChargeTo)}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-right font-bold text-gray-900">
                    {editingLineId === item.id ? (
                      <input type="number" step="1" min="0"
                        className="w-24 px-2 py-1 text-right border border-neutral-300 rounded focus:ring-1 focus:ring-neutral-500 outline-none text-xs"
                        value={editRate} onChange={e => { setEditRate(parseFloat(e.target.value) || 0); setEditQty(1) }}
                        onKeyDown={e => { if (e.key === '-') e.preventDefault() }} />
                    ) : <AmountDisplay amount={item.amount} hideDecimals />}
                  </td>
                  <td className="px-4 py-2.5 text-right text-gray-500">
                    {editingLineId === item.id ? (
                      <input type="number" step="1" min="0"
                        className="w-20 px-2 py-1 text-right border border-neutral-300 rounded focus:ring-1 focus:ring-neutral-500 outline-none text-xs disabled:bg-gray-100 disabled:text-gray-400"
                        value={editDiscount} onChange={e => setEditDiscount(parseFloat(e.target.value) || 0)}
                        onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
                        disabled={!canEditLineItems} />
                    ) : <AmountDisplay amount={item.discountAmount} hideDecimals />}
                  </td>
                  <td className="px-4 py-2.5 text-right font-bold text-gray-900">
                    {editingLineId === item.id ? (
                      <AmountDisplay amount={(editRate - editDiscount) * 100} hideDecimals />
                    ) : <AmountDisplay amount={item.amount - item.discountAmount} hideDecimals />}
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    {editingLineId === item.id ? (
                      <div className="flex justify-end gap-2">
                        <button onClick={saveEdit} className="text-[10px] bg-neutral-600 text-white px-2 py-1 rounded font-bold">Save</button>
                        <button onClick={() => setEditingLineId(null)} className="text-[10px] bg-gray-200 text-gray-600 px-2 py-1 rounded font-bold">Cancel</button>
                      </div>
                    ) : (
                      <div className="flex items-center justify-end gap-2">
                        {canEditLineItems && (
                          <>
                            <button onClick={() => startEditing(item)} className="text-[10px] text-neutral-600 hover:underline font-bold">Edit</button>
                            <button onClick={() => setItemToRemove({ id: item.id, name: item.itemName })}
                              className="w-6 h-6 flex items-center justify-center rounded-full bg-red-50 text-red-600 hover:bg-red-600 hover:text-white transition-colors">
                              <span className="text-sm font-bold">×</span>
                            </button>
                          </>
                        )}
                        {isGenerated && bill.dueAmount <= 0 && item.status !== 'REFUNDED' && (
                          /* CHANGED: Navigate to RefundChargePage with line info */
                          <button onClick={() => navigate(`/billing/${billId}/refund`, {
                            state: {
                              lineId: item.id,
                              lineName: item.itemName,
                              lineAmount: Math.round((item.amount - (item.discountAmount || 0)) / 100),
                            }
                          })} className="text-[10px] text-red-600 hover:underline font-bold">Refund</button>
                        )}
                        {!(canEditLineItems || (isGenerated && bill.dueAmount <= 0 && item.status !== 'REFUNDED')) && (
                          <span className="text-gray-400 font-bold">—</span>
                        )}
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Services & Diagnostics Table */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
        <div className="px-5 py-4 border-b border-gray-100 flex flex-wrap items-center justify-between gap-4">
          <h3 className="font-bold text-gray-900 text-xs uppercase tracking-wider">Services & Diagnostics</h3>
          {canEditLineItems && (
            <div className="flex-1 max-w-xs">
              <ServiceSearchInput
                diagnosticsAndConsultationsOnly={true}
                onSelect={async (item) => {
                  let rate = 0
                  try {
                    // Fetch full charge details to get specific payor tariffs
                    const fullCharge = await chargeApi.getById(item.id)
                    const tariffs = fullCharge.tariffs || []

                    if (bill.encounterType === 'INPATIENT') {
                      // IP priority: Payor (INSURANCE) > CREDIT > CASH
                      if (bill.payorId) {
                        const payorTariff = tariffs.find(
                          t => t.billType === 'INSURANCE' && t.payorId === bill.payorId && (t.rate ?? 0) > 0
                        )
                        if (payorTariff) {
                          rate = payorTariff.rate
                        }
                      }
                      // Fallback to standard CREDIT tariff (no payor)
                      if (rate <= 0) {
                        const creditTariff = tariffs.find(
                          t => t.billType === 'CREDIT' && !t.payorId && (t.rate ?? 0) > 0
                        )
                        if (creditTariff) rate = creditTariff.rate
                      }
                      // Fallback to CASH tariff
                      if (rate <= 0) {
                        const cashTariff = tariffs.find(
                          t => t.billType === 'CASH' && !t.payorId && (t.rate ?? 0) > 0
                        )
                        if (cashTariff) rate = cashTariff.rate
                      }
                    } else {
                      // OP: CASH tariff
                      const cashTariff = tariffs.find(
                        t => t.billType === 'CASH' && !t.payorId && (t.rate ?? 0) > 0
                      )
                      if (cashTariff) rate = cashTariff.rate
                    }
                  } catch (err) {
                    console.error('Failed to fetch full charge tariffs, falling back to pricingTiers', err)
                  }

                  // Fallback to pricingTiers if we couldn't resolve from full charge tariffs
                  if (rate <= 0) {
                    rate = item.pricingTiers.find(t => t.billType === bill.billType)?.unitRate
                      ?? item.pricingTiers.find(t => t.billType === 'CREDIT')?.unitRate
                      ?? item.pricingTiers.find(t => t.billType === 'CASH')?.unitRate
                      ?? item.pricingTiers[0]?.unitRate ?? 0
                  }

                  if (rate <= 0) {
                    toast({ title: 'Invalid Rate', description: `No pricing configured for "${item.name}". Please update the Service Catalog.`, variant: 'destructive' })
                    return
                  }
                  mutations.addCharge.mutate({ serviceCatalogItemId: item.id, unitRate: rate, quantity: 1 })
                }}
                placeholder="Add service "
              />
            </div>
          )}
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 text-left border-b border-gray-100">
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs">Service Name</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Rate</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Qty</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Amount (₹)</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Discount</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Net Amount (₹)</th>
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {otherCharges.map(item => (
              <tr key={item.id} className={editingLineId === item.id ? 'bg-neutral-50/50' : ''}>
                <td className="px-4 py-2.5">
                  <div className="font-medium text-gray-900 text-xs">{item.itemName}</div>
                  <div className="text-[10px] text-gray-500">{item.status ?? 'Active'}</div>
                </td>
                <td className="px-4 py-2.5 text-right">
                  {editingLineId === item.id ? (
                    <input type="number" step="1" min="0"
                      className="w-20 px-2 py-1 text-right border border-neutral-300 rounded focus:ring-1 focus:ring-neutral-500 outline-none text-xs"
                      value={editRate} onChange={e => setEditRate(parseFloat(e.target.value) || 0)}
                      onKeyDown={e => { if (e.key === '-') e.preventDefault() }} />
                  ) : <span className="text-gray-600"><AmountDisplay amount={item.unitRate} hideDecimals /></span>}
                </td>
                <td className="px-4 py-2.5 text-right">
                  {editingLineId === item.id && item.quantitative ? (
                    <input type="number" step="1" min="1"
                      className="w-16 px-2 py-1 text-right border border-neutral-300 rounded focus:ring-1 focus:ring-neutral-500 outline-none text-xs"
                      value={editQty} onChange={e => setEditQty(parseInt(e.target.value) || 1)}
                      onKeyDown={e => { if (e.key === '-') e.preventDefault() }} />
                  ) : <span className="text-gray-600">{item.quantity}</span>}
                </td>
                <td className="px-4 py-2.5 text-right font-medium text-gray-900">
                  {editingLineId === item.id ? (
                    <AmountDisplay amount={editRate * editQty * 100} hideDecimals />
                  ) : <AmountDisplay amount={item.amount} hideDecimals />}
                </td>
                <td className="px-4 py-2.5 text-right text-gray-500">
                  {editingLineId === item.id ? (
                    <input type="number" step="1" min="0"
                      className="w-20 px-2 py-1 text-right border border-neutral-300 rounded focus:ring-1 focus:ring-neutral-500 outline-none text-xs disabled:bg-gray-100 disabled:text-gray-400"
                      value={editDiscount} onChange={e => setEditDiscount(parseFloat(e.target.value) || 0)}
                      onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
                      disabled={!canEditLineItems} />
                  ) : <AmountDisplay amount={item.discountAmount} hideDecimals />}
                </td>
                <td className="px-4 py-2.5 text-right font-bold text-gray-900">
                  {editingLineId === item.id ? (
                    <AmountDisplay amount={(editRate * editQty - editDiscount) * 100} hideDecimals />
                  ) : <AmountDisplay amount={item.amount - item.discountAmount} hideDecimals />}
                </td>
                <td className="px-4 py-2.5 text-right">
                  {editingLineId === item.id ? (
                    <div className="flex justify-end gap-2">
                      <button onClick={saveEdit} className="text-[10px] bg-neutral-600 text-white px-2 py-1 rounded font-bold">Save</button>
                      <button onClick={() => setEditingLineId(null)} className="text-[10px] bg-gray-200 text-gray-600 px-2 py-1 rounded font-bold">Cancel</button>
                    </div>
                  ) : (
                    <div className="flex items-center justify-end gap-2">
                      {canEditLineItems && (
                        <>
                          <button onClick={() => startEditing(item)} className="text-[10px] text-neutral-600 hover:underline font-bold">Edit</button>
                          <button onClick={() => setItemToRemove({ id: item.id, name: item.itemName })}
                            className="w-6 h-6 flex items-center justify-center rounded-full bg-red-50 text-red-600 hover:bg-red-600 hover:text-white transition-colors">
                            <span className="text-sm font-bold">×</span>
                          </button>
                        </>
                      )}
                      {isGenerated && bill.dueAmount <= 0 && item.status !== 'REFUNDED' && (
                        /* CHANGED: Navigate to RefundChargePage with line info */
                        <button onClick={() => navigate(`/billing/${billId}/refund`, {
                          state: {
                            lineId: item.id,
                            lineName: item.itemName,
                            lineAmount: Math.round((item.amount - (item.discountAmount || 0)) / 100),
                          }
                        })} className="text-[10px] text-red-600 hover:underline font-bold">Refund</button>
                      )}
                      {!(canEditLineItems || (isGenerated && bill.dueAmount <= 0 && item.status !== 'REFUNDED')) && (
                        <span className="text-gray-400 font-bold">—</span>
                      )}
                    </div>
                  )}
                </td>
              </tr>
            ))}
            {otherCharges.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-400 text-xs">No services added yet</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Payments */}
      {bill.payments.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <h3 className="px-5 py-4 font-semibold text-gray-900 text-sm border-b border-gray-100">Payments</h3>
          <table className="w-full text-sm" aria-label="Payment history">
            <thead>
              <tr className="bg-gray-50 text-left border-b border-gray-100 text-xs">
                <th className="px-4 py-2.5 font-semibold text-gray-600">Date</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Type</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600">Mode</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-right">Amount (₹)</th>
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-center w-16">Print</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {bill.payments.map(p => (
                <tr key={p.id}>
                  <td className="px-4 py-2.5 text-gray-600">{formatDateTime(p.recordedAt)}</td>
                  <td className="px-4 py-2.5 text-gray-700 capitalize">{p.paymentType.toLowerCase().replace('_', ' ')}</td>
                  <td className="px-4 py-2.5 text-gray-600 capitalize">{p.paymentMode.toLowerCase()}</td>
                  <td className="px-4 py-2.5 text-right font-medium text-gray-900"><AmountDisplay amount={p.amount} hideDecimals /></td>
                  <td className="px-4 py-2.5 text-center">
                    <PrintButton
                      templateType={
                        p.paymentType.includes('REFUND')
                          ? 'REFUND_RECEIPT'
                          : isOp ? 'OP_RECEIPT' : 'IP_RECEIPT'
                      }
                      params={{ id: billId!, paymentId: p.id }}
                      variant="icon"
                      label="Print Receipt"
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Actions */}
      <div className="flex gap-3 flex-wrap items-center">
        {isGenerated && billId && (
          <PrintButton
            templateType={isOp ? 'BILL' : 'IP_BILL_CONSOLIDATED'}
            params={{ id: billId }}
            variant="outline"
            label="Print Bill"
          />
        )}
        {bill.status === 'DRAFT' && (bedCharges.length > 0 || otherCharges.length > 0) && (
          <button
            onClick={() => setShowGenerateModal(true)}
            disabled={mutations.generateBill.isPending}
            className="px-5 py-2 bg-green-600 text-white text-sm font-semibold rounded-lg hover:bg-green-700 disabled:opacity-50 transition-colors"
          >
            Generate Bill
          </button>
        )}

        {(bill.dueAmount > 0 || bill.status === 'DRAFT') && (
          /* CHANGED: Navigate to RecordPaymentPage */
          <button
            onClick={() => navigate(`/billing/${billId}/payment`)}
            className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors"
          >
            {bill.dueAmount <= 0 ? 'Record Advance' : 'Record Payment'}
          </button>
        )}

        {bill.status === 'DRAFT' && bill.paymentTotal > 0 && !isOp && (
          /* CHANGED: Navigate to RefundChargePage (advance refund mode) */
          <button
            onClick={() => {
              const defaultAmt = bill.dueAmount < 0 ? Math.abs(bill.dueAmount) : 0
              navigate(`/billing/${billId}/refund`, {
                state: {
                  advanceRefund: true,
                  defaultAmount: Math.round(defaultAmt / 100),
                }
              })
            }}
            className="px-5 py-2 bg-red-600 text-white text-sm font-semibold rounded-lg hover:bg-red-700 transition-colors"
          >
            Refund
          </button>
        )}

        {canEditLineItems && (bedCharges.length > 0 || otherCharges.length > 0) && (
          /* CHANGED: Navigate to TotalDiscountPage */
          <button
            onClick={() => navigate(`/billing/${billId}/discount`)}
            className="px-5 py-2 bg-amber-600 text-white text-sm font-semibold rounded-lg hover:bg-amber-700 transition-colors"
          >
            Add Discount
          </button>
        )}
      </div>

      {/* Remove Charge confirmation — kept inline: it's a single-click confirm with no form fields */}
      {itemToRemove && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-2xl shadow-2xl p-6 max-w-sm w-full text-center space-y-4">
            <div className="w-14 h-14 rounded-full bg-red-100 flex items-center justify-center mx-auto">
              <span className="text-2xl">⚠</span>
            </div>
            <h4 className="text-lg font-bold text-gray-900">Remove Charge</h4>
            <p className="text-sm text-gray-500">
              Are you sure you want to remove{' '}
              <span className="font-semibold text-gray-900">{itemToRemove.name}</span>{' '}
              from the bill? This action cannot be undone.
            </p>
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setItemToRemove(null)}
                disabled={mutations.removeCharge.isPending}
                className="flex-1 px-4 py-2.5 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
              >
                No, Cancel
              </button>
              <button
                onClick={() => mutations.removeCharge.mutate({ lineItemId: itemToRemove.id }, { onSuccess: () => setItemToRemove(null) })}
                disabled={mutations.removeCharge.isPending}
                className="flex-1 px-4 py-2.5 bg-red-600 text-white text-sm font-bold rounded-xl hover:bg-red-700 disabled:opacity-50 transition-all"
              >
                {mutations.removeCharge.isPending ? 'Removing…' : 'Yes, Remove'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Generate Bill Confirmation Modal */}
      {showGenerateModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-3xl shadow-2xl p-7 max-w-md w-full space-y-6 animate-in zoom-in-95 duration-200 relative overflow-hidden">
            {/* Background Decorative Element */}
            <div className="absolute -top-24 -right-24 w-48 h-48 bg-green-500/5 rounded-full blur-3xl" />

            <div className="flex flex-col items-center text-center">
              <div className="w-20 h-20 rounded-full bg-green-50 flex items-center justify-center mb-4">
                <span className="text-4xl">📄</span>
              </div>
              <h3 className="text-xl font-bold text-gray-900">Generate Final Bill?</h3>
              <p className="text-xs text-gray-500 leading-relaxed mt-2 px-4">
                Once generated, the bill will be locked for editing. You will be able to settle any remaining dues after generation.
              </p>
            </div>

            {/* Bill Details List */}
            <div className="bg-gray-50 rounded-2xl border border-gray-100 divide-y divide-gray-100/80 text-xs">
              <div className="flex justify-between items-center px-4 py-2.5">
                <span className="font-semibold text-gray-405 uppercase tracking-wider">Patient</span>
                <span className="font-bold text-gray-800">{bill.patientName}</span>
              </div>
              {bill.patientNumber && (
                <div className="flex justify-between items-center px-4 py-2.5">
                  <span className="font-semibold text-gray-405 uppercase tracking-wider">Patient ID</span>
                  <span className="font-bold text-gray-800">{bill.patientNumber}</span>
                </div>
              )}
              <div className="flex justify-between items-center px-4 py-2.5">
                <span className="font-semibold text-gray-405 uppercase tracking-wider">Encounter Type</span>
                <span className="font-bold text-gray-800">{bill.encounterType}</span>
              </div>
              <div className="flex justify-between items-center px-4 py-2.5">
                <span className="font-semibold text-gray-405 uppercase tracking-wider">Bill Type</span>
                <span className="font-bold text-gray-800">{bill.billType}</span>
              </div>
              <div className="flex justify-between items-center px-4 py-2.5">
                <span className="font-semibold text-gray-450 uppercase tracking-wider">Total Items</span>
                <span className="font-bold text-gray-800">
                  {bill.chargeLineItems.filter(c => c.status !== 'CANCELLED').length} charge(s)
                </span>
              </div>
              <div className="flex justify-between items-center px-4 py-2.5">
                <span className="font-semibold text-gray-405 uppercase tracking-wider">Bill Amount</span>
                <span className="font-extrabold text-green-700">
                  <AmountDisplay amount={bill.billAmount} hideDecimals />
                </span>
              </div>
              {bill.discountTotal > 0 && (
                <div className="flex justify-between items-center px-4 py-2.5">
                  <span className="font-semibold text-gray-405 uppercase tracking-wider">Discount</span>
                  <span className="font-bold text-amber-600">
                    − <AmountDisplay amount={bill.discountTotal} hideDecimals />
                  </span>
                </div>
              )}
              {bill.paymentTotal > 0 && (
                <div className="flex justify-between items-center px-4 py-2.5">
                  <span className="font-semibold text-gray-405 uppercase tracking-wider">Paid</span>
                  <span className="font-bold text-neutral-700">
                    <AmountDisplay amount={bill.paymentTotal} hideDecimals />
                  </span>
                </div>
              )}
              <div className="flex justify-between items-center px-4 py-2.5 bg-amber-50/40 rounded-b-2xl">
                <span className="font-semibold text-gray-405 uppercase tracking-wider">Due</span>
                <span className="text-sm font-black text-amber-700">
                  <AmountDisplay amount={bill.dueAmount} hideDecimals />
                </span>
              </div>
            </div>

            {/* Modal Actions */}
            <div className="flex gap-3">
              <button
                onClick={() => setShowGenerateModal(false)}
                disabled={mutations.generateBill.isPending}
                className="flex-1 px-4 py-2.5 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  const todayStr = new Date().toISOString().split('T')[0]
                  mutations.generateBill.mutate(
                    { billDate: todayStr },
                    {
                      onSuccess: () => {
                        setShowGenerateModal(false)
                      }
                    }
                  )
                }}
                disabled={mutations.generateBill.isPending}
                className="flex-1 px-4 py-2.5 bg-green-600 text-white text-sm font-bold rounded-xl hover:bg-green-700 disabled:opacity-50 transition-all active:scale-[0.98] shadow-md hover:shadow-lg shadow-green-200/50"
              >
                {mutations.generateBill.isPending ? 'Generating…' : 'Yes, Generate'}
              </button>
            </div>
          </div>
        </div>
      )}

      {bill.billDate && (
        <p className="text-xs text-gray-400">
          Bill date: {formatDate(bill.billDate)}
          {bill.admissionAt && ` · Admitted: ${formatDateTime(bill.admissionAt)}`}
          {bill.dischargeAt && ` · Discharged: ${formatDateTime(bill.dischargeAt)}`}
        </p>
      )}
    </div>
  )
}
