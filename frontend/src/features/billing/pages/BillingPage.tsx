import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useBill, useBillsByPatient, useBillingMutations } from '../../../hooks/billing/useBilling'
import { BillStatusBadge } from '../../../components/shared/StatusBadge'
import { AmountDisplay } from '../../../components/shared/AmountDisplay'
import { ServiceSearchInput } from '../../../components/shared/ServiceSearchInput'
import BackButton from '../../../components/shared/BackButton'
import { PrintButton } from '../../../components/shared/PrintButton'
import { formatDate, formatDateTime } from '../../../lib/dateUtils'
import { toast } from '../../../hooks/useToast'
import { chargeApi, payerApi } from '../../../services/masters/masterApi'
import { insuranceApi } from '../../../services/insurance/insuranceApi'
import { usePrint } from '../../../hooks/print/usePrint'
import { useAuthStore } from '../../../store/authStore'
import { Modal } from '../../../components/ui/Modal'

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
  const { data: patientBills } = useBillsByPatient(bill?.patientId)
  const oldBills = bill?.encounterType === 'OUTPATIENT'
    ? (patientBills || []).filter(b => b.id !== bill?.id && b.encounterType === 'INPATIENT' && b.status !== 'CANCELLED')
    : []
  const mutations = useBillingMutations(billId ?? '')
  const { print } = usePrint()

  // Inline editing state
  const [editingLineId, setEditingLineId] = useState<string | null>(null)
  const [editQty, setEditQty] = useState<number | ''>(0)
  const [editAmount, setEditAmount] = useState<number | ''>(0)

  // Remove confirmation state
  const [itemToRemove, setItemToRemove] = useState<{ id: string; name: string } | null>(null)
  const [showGenerateModal, setShowGenerateModal] = useState(false)

  // Discount modal state
  const [showDiscountModal, setShowDiscountModal] = useState(false)
  const [discountMode, setDiscountMode] = useState<'item' | 'total'>('total')
  const [itemDiscounts, setItemDiscounts] = useState<Record<string, number>>({})
  const [totalDiscountAmount, setTotalDiscountAmount] = useState('')
  const [discountReason, setDiscountReason] = useState('')
  const [showCancelDiscountConfirm, setShowCancelDiscountConfirm] = useState(false)

  // Insurance modal state & queries
  const [showInsuranceModal, setShowInsuranceModal] = useState(false)
  const [selectedPayorId, setSelectedPayorId] = useState<string>('')

  const { data: payers = [] } = useQuery({
    queryKey: ['payers'],
    queryFn: payerApi.getAll,
  })

  const { data: insuranceRecords = [] } = useQuery({
    queryKey: ['insurance', 'bill', billId],
    queryFn: () => insuranceApi.getByBill(billId!),
    enabled: !!billId,
  })

  const { data: patientInsurances = [] } = useQuery({
    queryKey: ['insurance', 'patient', bill?.patientId],
    queryFn: () => insuranceApi.getByPatient(bill!.patientId),
    enabled: !!bill?.patientId,
  })

  const activeInsurance = (insuranceRecords && insuranceRecords.length > 0)
    ? insuranceRecords[0]
    : (patientInsurances && patientInsurances.length > 0 ? patientInsurances[0] : null)

  const { data: deskData } = useQuery({
    queryKey: ['insurance-desk', activeInsurance?.id],
    queryFn: () => insuranceApi.getDesk(activeInsurance!.id),
    enabled: !!activeInsurance?.id,
  })

  const isPreauthApproved = Boolean(
    (deskData && (
      deskData.preauthApprovalStatus === 'APPROVED' ||
      (deskData.currentStage &&
        deskData.currentStage !== 'PREAUTHORISATION' &&
        deskData.currentStage !== 'PREAUTHORISATION_REJECTED')
    )) ||
    (activeInsurance && (
      activeInsurance.insuranceStatus === 'PRE_AUTH_RECEIVED' ||
      (activeInsurance.rejectionReason === null && activeInsurance.preAuthNumber != null)
    ))
  )

  const currentPayor = payers.find((p: any) => p.id === bill?.payorId)
  const selectedInsuranceName = currentPayor?.name || activeInsurance?.insurerName || deskData?.insurerName || ''

  const { user } = useAuthStore()

  useEffect(() => {
    if (!bill) return undefined
    
    const originalTitle = document.title
    const hName = user?.branchName || originalTitle || 'Hospital'
    document.title = `${hName} - ${bill.billNumber || 'Draft'}`
    return () => {
      document.title = originalTitle
    }
  }, [bill, user])

  if (isLoading) return <div className="text-sm text-gray-500 p-6" aria-live="polite">Loading bill…</div>
  if (error || !bill) return <div className="text-sm text-red-600 p-6" role="alert">Failed to load bill</div>

  const isGenerated = bill.status !== 'DRAFT'
  const isOp = bill.encounterType === 'OUTPATIENT'
  const canEditLineItems = !isGenerated && (bill.paymentTotal === 0 || !isOp)

  const startEditing = (item: any) => {
    if (bill.status !== 'DRAFT') return
    setEditingLineId(item.id)
    setEditQty(item.quantity)
    setEditAmount(Math.round(item.amount / 100))
  }

  const saveEdit = () => {
    if (!editingLineId) return

    // Find existing line to preserve rate and discount
    const existingLine = bill.chargeLineItems.find(c => c.id === editingLineId)
    const existingDiscount = existingLine?.discountAmount ?? 0

    if (existingLine?.bedChargeFrom != null) {
      const amountVal = editAmount === '' ? 0 : editAmount
      if (amountVal < 0) {
        toast({ title: 'Amount cannot be negative', variant: 'destructive' })
        return
      }
      const qty = existingLine.quantity > 0 ? existingLine.quantity : 1
      const calculatedRate = Math.round((amountVal * 100) / qty)
      mutations.updateCharge.mutate(
        { lineItemId: editingLineId, rate: calculatedRate, quantity: qty, discount: existingDiscount },
        { onSuccess: () => setEditingLineId(null) }
      )
    } else {
      const qtyVal = editQty === '' ? 0 : editQty
      const existingRate = existingLine?.unitRate ?? 0
      if (qtyVal < 1) {
        toast({ title: 'Quantity must be at least 1', variant: 'destructive' })
        return
      }
      mutations.updateCharge.mutate(
        { lineItemId: editingLineId, rate: existingRate, quantity: qtyVal, discount: existingDiscount },
        { onSuccess: () => setEditingLineId(null) }
      )
    }
  }

  const updateTotalDiscount = (value: string) => {
    setTotalDiscountAmount(value)
    const total = Math.round(parseFloat(value || '0') * 100)
    const activeLines = bill.chargeLineItems.filter(c => c.status !== 'CANCELLED')
    if (activeLines.length === 0) return

    if (total <= 0 || total >= bill.billAmount) {
      const resets: Record<string, number> = {}
      activeLines.forEach(line => {
        resets[line.id] = 0
      })
      setItemDiscounts(resets)
      return
    }

    const billTotal = activeLines.reduce((sum, c) => sum + c.amount, 0)
    if (billTotal === 0) return

    let remaining = total
    const newDiscounts: Record<string, number> = {}

    activeLines.forEach((line, index) => {
      let amount = 0
      if (index === activeLines.length - 1) {
        amount = Math.min(remaining, line.amount)
      } else {
        const proportionalRupees = Math.round((line.amount / billTotal) * (total / 100))
        const proportional = proportionalRupees * 100
        amount = Math.min(remaining, line.amount, proportional)
      }
      remaining -= amount
      newDiscounts[line.id] = Math.round(amount / 100)
    })

    if (remaining > 0) {
      for (const line of activeLines) {
        const maxVal = Math.round(line.amount / 100)
        const currentVal = newDiscounts[line.id] || 0
        const capacity = maxVal - currentVal
        if (capacity > 0) {
          const add = Math.min(Math.round(remaining / 100), capacity)
          newDiscounts[line.id] = currentVal + add
          remaining -= add * 100
          if (remaining <= 0) break
        }
      }
    }

    setItemDiscounts(newDiscounts)
  }

  const openDiscountModal = () => {
    const activeLines = bill.chargeLineItems.filter(c => c.status !== 'CANCELLED')
    const existingItemDiscounts: Record<string, number> = {}
    activeLines.forEach(line => {
      existingItemDiscounts[line.id] = Math.round(line.discountAmount / 100)
    })
    
    setItemDiscounts(existingItemDiscounts)
    if (bill.discountTotal > 0) {
      // Default to total (Full Discount) if total discount is present
      setDiscountMode('total')
      setTotalDiscountAmount(String(Math.round(bill.discountTotal / 100)))
    } else {
      setDiscountMode('total')
      setTotalDiscountAmount('')
    }
    setDiscountReason('')
    setShowDiscountModal(true)
  }

  const handleApplyDiscount = () => {
    const activeLines = bill.chargeLineItems.filter(c => c.status !== 'CANCELLED')
    if (activeLines.length === 0) return

    if (discountMode === 'item') {
      // Item-wise discount
      const lineDiscounts = activeLines.map(line => ({
        chargeLineItemId: line.id,
        amount: Math.round((itemDiscounts[line.id] || 0) * 100)
      }))
      const totalDisc = lineDiscounts.reduce((s, ld) => s + ld.amount, 0)
      if (totalDisc <= 0) {
        toast({ title: 'Please enter at least one item discount', variant: 'destructive' })
        return
      }
      // Validate no item discount exceeds item amount
      for (const line of activeLines) {
        const disc = Math.round((itemDiscounts[line.id] || 0) * 100)
        if (disc > line.amount) {
          toast({ title: `Discount for "${line.itemName}" exceeds its amount`, variant: 'destructive' })
          return
        }
      }
      mutations.applyDiscount.mutate(
        { totalDiscount: totalDisc, lineDiscounts, reason: discountReason.trim() || undefined },
        { onSuccess: () => setShowDiscountModal(false) }
      )
    } else {
      // Total discount — distribute proportionally
      const total = Math.round(parseFloat(totalDiscountAmount || '0') * 100)
      if (total <= 0) {
        toast({ title: 'Please enter a valid discount amount', variant: 'destructive' })
        return
      }
      if (total >= bill.billAmount) {
        toast({ title: 'Discount cannot exceed bill amount', variant: 'destructive' })
        return
      }
      const billTotal = activeLines.reduce((sum, c) => sum + c.amount, 0)
      if (billTotal === 0) return
      let remaining = total
      const lineDiscounts = activeLines.map((line, index) => {
        let amount = 0
        if (index === activeLines.length - 1) {
          amount = Math.min(remaining, line.amount)
        } else {
          const proportionalRupees = Math.round((line.amount / billTotal) * (total / 100))
          const proportional = proportionalRupees * 100
          amount = Math.min(remaining, line.amount, proportional)
        }
        remaining -= amount
        return { chargeLineItemId: line.id, amount, maxAmount: line.amount }
      })
      if (remaining > 0) {
        for (const ld of lineDiscounts) {
          const capacity = ld.maxAmount - ld.amount
          if (capacity > 0) {
            const add = Math.min(remaining, capacity)
            ld.amount += add
            remaining -= add
            if (remaining === 0) break
          }
        }
      }
      mutations.applyDiscount.mutate(
        {
          totalDiscount: total,
          lineDiscounts: lineDiscounts.map(ld => ({ chargeLineItemId: ld.chargeLineItemId, amount: ld.amount })),
          reason: discountReason.trim() || undefined,
        },
        { onSuccess: () => setShowDiscountModal(false) }
      )
    }
  }

  const bedCharges = bill.chargeLineItems
    .filter(c => c.bedChargeFrom != null && c.status !== 'CANCELLED')
    .sort((a, b) => new Date(a.bedChargeFrom!).getTime() - new Date(b.bedChargeFrom!).getTime())

  const otherCharges = bill.chargeLineItems
    .filter(c => c.bedChargeFrom == null && c.status !== 'CANCELLED')
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())

  const disallowedTotal = bill.chargeLineItems?.reduce((sum, item) => {
    return item.status !== 'CANCELLED' ? sum + (item.disallowedAmount || 0) : sum
  }, 0) || 0

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
        {oldBills && oldBills.length > 0 && (
          <div>
            <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-0.5">
              Disallowance amount
            </p>
            <div className="flex flex-wrap gap-1.5 items-center">
              {oldBills.map(oldBill => {
                const label = oldBill.billNumber || `Draft ${oldBill.encounterType === 'INPATIENT' ? 'IP' : 'OP'} Bill`
                const dueInRupees = oldBill.dueAmount > 0 ? Math.round(oldBill.dueAmount / 100) : 0
                return (
                  <button
                    key={oldBill.id}
                    onClick={() => navigate(`/billing/${oldBill.id}`)}
                    className="px-2.5 py-1 bg-red-50 text-red-700 border border-red-300 rounded-lg text-xs font-bold font-mono hover:bg-red-100 hover:border-red-400 transition-all flex items-center gap-1.5 shadow-sm cursor-pointer"
                    title={`Click to view bill (${oldBill.encounterType})`}
                  >
                    <span>{label}</span>
                    {dueInRupees > 0 && (
                      <span className="text-[10px] px-1 py-0.2 bg-red-200/60 text-red-950 rounded font-sans font-semibold">
                        Due: ₹{dueInRupees}
                      </span>
                    )}
                  </button>
                )
              })}
            </div>
          </div>
        )}
        <div className="ml-auto text-right flex items-center gap-6">
          {bill.billType === 'CREDIT' && bill.encounterType === 'INPATIENT' && (
            <div className="text-right border-r border-gray-200 pr-6">
              <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-0.5">Insurance Provider</p>
              <div className="flex items-center gap-2 justify-end">
                <span className="text-sm font-bold text-gray-900">
                  {selectedInsuranceName || 'No Insurance Selected'}
                </span>
                {isPreauthApproved ? (
                  <span
                    className="px-2 py-0.5 bg-emerald-50 text-emerald-700 border border-emerald-200 rounded text-[10px] font-bold flex items-center gap-1 cursor-not-allowed"
                    title="Pre-authorisation is approved. Insurance cannot be changed."
                  >
                    <svg className="w-3.5 h-3.5 text-emerald-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                    </svg>
                    Approved
                  </span>
                ) : (
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedPayorId(bill.payorId || '')
                      setShowInsuranceModal(true)
                    }}
                    className="px-2 py-0.5 bg-blue-50 hover:bg-blue-100 text-blue-700 border border-blue-200 rounded text-[10px] font-bold transition-colors cursor-pointer"
                    title="Change selected insurance provider"
                  >
                    Change
                  </button>
                )}
              </div>
            </div>
          )}
          <div>
            <p className="text-[10px] text-gray-500 font-bold uppercase tracking-wider mb-0.5">Encounter</p>
            <div className="flex items-center gap-2 justify-end">
              <span className="text-sm font-bold text-gray-900">{bill.encounterType}</span>
              <span className="px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded text-[10px] font-bold">{bill.billType}</span>
            </div>
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
      <div className={`grid grid-cols-2 ${disallowedTotal > 0 ? 'md:grid-cols-6' : 'md:grid-cols-5'} gap-4`}>
        {[
          { label: 'Bill Amount', value: Number(bill.billAmount || 0) },
          { label: 'Paid', value: Number(bill.paymentTotal || 0) },
          { label: 'Discount', value: Number(bill.discountTotal || 0), negative: true },
          ...(disallowedTotal > 0 ? [{ label: 'Disallowed', value: Number(disallowedTotal) }] : []),
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
                <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Discount (₹)</th>
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
                      <div className="relative inline-block">
                        <span className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400 text-xs">₹</span>
                        <input
                          type="number"
                          min="0"
                          className="w-24 pl-5 pr-2 py-1 text-right border border-neutral-300 rounded focus:ring-1 focus:ring-neutral-500 outline-none text-xs font-bold"
                          value={editAmount}
                          onChange={e => {
                            const val = e.target.value
                            setEditAmount(val === '' ? '' : (parseInt(val) || 0))
                          }}
                          onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
                        />
                      </div>
                    ) : (
                      <AmountDisplay amount={item.amount} hideDecimals />
                    )}
                  </td>
                  <td className="px-4 py-2.5 text-right text-gray-500">
                    <AmountDisplay amount={item.discountAmount} hideDecimals />
                  </td>
                  <td className="px-4 py-2.5 text-right font-bold text-gray-900">
                    {editingLineId === item.id ? (
                      <AmountDisplay amount={(Number(editAmount || 0) * 100) - item.discountAmount} hideDecimals />
                    ) : (
                      <AmountDisplay amount={item.amount - item.discountAmount} hideDecimals />
                    )}
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
                excludeRoomCharges={true}
                diagnosticsAndConsultationsOnly={false}
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
              <th className="px-4 py-2.5 font-semibold text-gray-600 text-xs text-right">Discount (₹)</th>
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
                  <span className="text-gray-600"><AmountDisplay amount={item.unitRate} hideDecimals /></span>
                </td>
                <td className="px-4 py-2.5 text-right">
                  {editingLineId === item.id && item.quantitative ? (
                    <input type="number" step="1" min="1"
                      className="w-16 px-2 py-1 text-right border border-neutral-300 rounded focus:ring-1 focus:ring-neutral-500 outline-none text-xs"
                      value={editQty} onChange={e => { const val = e.target.value; setEditQty(val === '' ? '' : (parseInt(val) || 1)) }}
                      onKeyDown={e => { if (e.key === '-') e.preventDefault() }} />
                  ) : <span className="text-gray-600">{item.quantity}</span>}
                </td>
                <td className="px-4 py-2.5 text-right font-medium text-gray-900">
                  <AmountDisplay amount={item.unitRate * (editingLineId === item.id ? Number(editQty || 0) : item.quantity)} hideDecimals />
                </td>
                <td className="px-4 py-2.5 text-right text-gray-500">
                  <AmountDisplay amount={item.discountAmount} hideDecimals />
                </td>
                <td className="px-4 py-2.5 text-right font-bold text-gray-900">
                  <AmountDisplay amount={item.unitRate * (editingLineId === item.id ? Number(editQty || 0) : item.quantity) - item.discountAmount} hideDecimals />
                </td>
                <td className="px-4 py-2.5 text-right">
                  {item.itemName?.toLowerCase().includes('pharmacy sale') ? (
                    <div className="flex items-center justify-end">
                      <span className="text-gray-400 font-bold">-</span>
                    </div>
                  ) : editingLineId === item.id ? (
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
                  <td className="px-4 py-2.5 text-gray-600 capitalize">
                    {p.paymentMode === 'UPI' ? 'UPI' : p.paymentMode.toLowerCase()}
                  </td>
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
            className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 disabled:opacity-50 transition-colors"
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
            className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors"
          >
            Refund
          </button>
        )}

        {canEditLineItems && (bedCharges.length > 0 || otherCharges.length > 0) && (
          bill.discountTotal > 0 ? (
            <button
              onClick={openDiscountModal}
              className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors"
            >
              Edit Discount
            </button>
          ) : (
            <button
              onClick={openDiscountModal}
              className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors"
            >
              Add Discount
            </button>
          )
        )}

        {!isOp && (bedCharges.length > 0 || otherCharges.length > 0) && (
          <button
            type="button"
            onClick={() => navigate(`/billing/${billId}/disallowance`)}
            className="px-5 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors"
          >
            Add Disallowance
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
                <span className="font-semibold text-gray-455 uppercase tracking-wider">Total Items</span>
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
                        if (billId) {
                          print(isOp ? 'BILL' : 'IP_BILL_CONSOLIDATED', { id: billId })
                            .catch(err => console.error('Auto-print error:', err))
                        }
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

      {/* Add Discount Modal */}
      {showDiscountModal && (() => {
        const activeLines = bill.chargeLineItems.filter(c => c.status !== 'CANCELLED')
        const itemDiscountTotal = activeLines.reduce((s, l) => s + (itemDiscounts[l.id] || 0), 0)
        const modalTotal = discountMode === 'item' ? itemDiscountTotal : parseFloat(totalDiscountAmount || '0')
        return (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
            style={{ marginTop: 0 }}
          >
            <div className="bg-white rounded-2xl shadow-2xl max-w-3xl w-full max-h-[85vh] flex flex-col animate-in zoom-in-95 duration-200">
              {/* Header */}
              <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
                <h3 className="text-lg font-bold text-gray-900">Add Discount</h3>
                <button onClick={() => setShowDiscountModal(false)} className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors text-lg">×</button>
              </div>

              {/* Mode & Reason Row */}
              <div className="px-6 pt-4 pb-3 flex items-center gap-6 border-b border-gray-100">
                <div className="flex items-center gap-2">
                  <label htmlFor="discountModeSelect" className="text-xs font-bold text-gray-500 uppercase tracking-wider shrink-0">Mode</label>
                  <select
                    id="discountModeSelect"
                    value={discountMode}
                    onChange={e => {
                      const mode = e.target.value as 'item' | 'total'
                      setDiscountMode(mode)
                      if (mode === 'item') {
                        setTotalDiscountAmount('')
                      } else {
                        setItemDiscounts({})
                        setTotalDiscountAmount('')
                      }
                    }}
                    className="px-3 py-1.5 bg-gray-50 border border-gray-300 rounded-lg text-xs font-bold text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:bg-white transition-all w-36"
                  >
                    <option value="item">Charge Wise</option>
                    <option value="total">Full Discount</option>
                  </select>
                </div>

                <div className="flex-1 flex items-center gap-2">
                  <label htmlFor="discountReasonInput" className="text-xs font-bold text-gray-500 uppercase tracking-wider shrink-0">Reason</label>
                  <input
                    id="discountReasonInput"
                    type="text"
                    value={discountReason}
                    onChange={e => setDiscountReason(e.target.value)}
                    placeholder="Optional reason for discount..."
                    className="flex-1 px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-amber-500 focus:bg-white transition-all"
                  />
                </div>
              </div>

              {/* Table Container - Scrollable */}
              <div className="flex-1 overflow-y-auto px-6 py-1 min-h-[150px] max-h-[40vh] border-b border-gray-100">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 text-xs">
                      <th className="pb-2 text-left font-semibold text-gray-500 w-8">S.No</th>
                      <th className="pb-2 text-left font-semibold text-gray-500">Charge Name</th>
                      <th className="pb-2 text-right font-semibold text-gray-500 w-28">Amount</th>
                      <th className="pb-2 text-right font-semibold text-gray-500 w-32">Discount</th>
                      <th className="pb-2 text-right font-semibold text-gray-500 w-28">Subtotal</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {activeLines.map((item, idx) => {
                      const amt = Math.round(item.amount / 100)
                      const disc = itemDiscounts[item.id] || 0
                      return (
                        <tr key={item.id}>
                          <td className="py-2.5 text-gray-400 text-xs">{idx + 1}</td>
                          <td className="py-2.5 font-medium text-gray-900 text-xs">{item.itemName}</td>
                          <td className="py-2.5 text-right text-gray-700 tabular-nums">₹{amt.toLocaleString('en-IN')}</td>
                          <td className="py-2.5 text-right">
                            {discountMode === 'item' ? (
                              <input
                                type="number"
                                min={0}
                                max={amt}
                                value={itemDiscounts[item.id] || ''}
                                onChange={e => {
                                  let val = parseFloat(e.target.value)
                                  if (isNaN(val) || val < 0) val = 0
                                  if (val > amt) val = amt
                                  setItemDiscounts(prev => ({ ...prev, [item.id]: val }))
                                }}
                                onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
                                placeholder="0"
                                className="w-24 px-2 py-1.5 text-right border border-gray-300 rounded-lg text-xs focus:outline-none focus:ring-1 focus:ring-amber-500 no-spinner"
                              />
                            ) : (
                              <span className="text-gray-500 font-semibold text-xs pr-2">₹{disc}</span>
                            )}
                          </td>
                          <td className="py-2.5 text-right font-bold text-gray-900 tabular-nums">₹{(amt - disc).toLocaleString('en-IN')}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>

              {/* Calculations - Non-Scrollable */}
              <div className="px-6 py-4 bg-gray-50/50 border-b border-gray-100">
                {/* Total Discount input (for total mode) */}
                {discountMode === 'total' && (
                  <div className="flex items-center justify-end gap-4">
                    <span className="text-sm font-bold text-gray-500 uppercase tracking-wide">Total Discount</span>
                    <div className="relative">
                      <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm font-semibold">₹</span>
                      <input
                        type="number"
                        min={0}
                        value={totalDiscountAmount}
                        onChange={e => updateTotalDiscount(e.target.value)}
                        onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
                        placeholder="0"
                        className="w-32 pl-7 pr-3 py-2 text-right border border-gray-300 rounded-lg text-sm font-bold focus:outline-none focus:ring-2 focus:ring-amber-500 no-spinner"
                      />
                    </div>
                    <span className="text-sm font-bold text-gray-900 tabular-nums w-28 text-right">
                      ₹{(activeLines.reduce((s, l) => s + Math.round(l.amount / 100), 0) - (parseFloat(totalDiscountAmount || '0') || 0)).toLocaleString('en-IN')}
                    </span>
                  </div>
                )}

                {/* Item-wise total row */}
                {discountMode === 'item' && (
                  <div className="flex items-center justify-end gap-4">
                    <span className="text-xs font-bold text-gray-500 uppercase tracking-wide">Total Discount</span>
                    <span className="text-sm font-bold text-amber-700 tabular-nums">₹{itemDiscountTotal.toLocaleString('en-IN')}</span>
                  </div>
                )}
              </div>

              {/* Footer */}
              <div className="px-6 py-4 flex items-center justify-between gap-3">
                <div className="flex gap-2">
                  <button
                    onClick={() => setShowDiscountModal(false)}
                    className="px-5 py-2.5 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
                  >
                    Cancel
                  </button>
                  {bill.discountTotal > 0 && (
                    <button
                      onClick={() => setShowCancelDiscountConfirm(true)}
                      className="px-5 py-2.5 bg-red-100 text-red-700 text-sm font-bold rounded-xl hover:bg-red-200 transition-colors"
                    >
                      Cancel Discount
                    </button>
                  )}
                </div>
                <button
                  onClick={handleApplyDiscount}
                  disabled={mutations.applyDiscount.isPending || modalTotal <= 0}
                  className="px-6 py-2.5 bg-amber-600 text-white text-sm font-bold rounded-xl hover:bg-amber-700 disabled:opacity-50 transition-all shadow-lg shadow-amber-200/50 active:scale-[0.98]"
                >
                  {mutations.applyDiscount.isPending ? 'Applying…' : bill.discountTotal > 0 ? 'Update Discount' : 'Add Discount'}
                </button>
              </div>
            </div>
          </div>
        )
      })()}

      {showCancelDiscountConfirm && (
        <div
          className="fixed inset-0 z-[60] flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-2xl shadow-2xl p-6 max-w-sm w-full text-center space-y-4">
            <div className="w-14 h-14 rounded-full bg-red-100 flex items-center justify-center mx-auto">
              <span className="text-2xl text-red-600">⚠</span>
            </div>
            <h4 className="text-lg font-bold text-gray-900">Cancel Discount</h4>
            <p className="text-sm text-gray-500 font-medium">
              Are you sure you want to cancel the discount from this bill? This action cannot be undone.
            </p>
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setShowCancelDiscountConfirm(false)}
                disabled={mutations.cancelDiscount.isPending}
                className="flex-1 px-4 py-2.5 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
              >
                No, Keep It
              </button>
              <button
                onClick={() => {
                  mutations.cancelDiscount.mutate(undefined, {
                    onSuccess: () => {
                      setShowCancelDiscountConfirm(false)
                      setShowDiscountModal(false)
                    }
                  })
                }}
                disabled={mutations.cancelDiscount.isPending}
                className="flex-1 px-4 py-2.5 bg-red-600 text-white text-sm font-bold rounded-xl hover:bg-red-700 disabled:opacity-50 transition-all"
              >
                {mutations.cancelDiscount.isPending ? 'Cancelling…' : 'Yes, Cancel'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Change Insurance Modal */}
      <Modal
        isOpen={showInsuranceModal}
        onClose={() => setShowInsuranceModal(false)}
        title="Select Insurance Provider"
        size="md"
      >
        <div className="p-6">
          <h3 className="text-lg font-bold text-neutral-900 mb-1">Select Insurance Provider</h3>
          <p className="text-xs text-neutral-500 mb-5">
            Choose the insurance provider / payor for this credit bill. Note: Insurance cannot be changed after pre-authorisation approval.
          </p>

          <div className="space-y-5">
            <div>
              <label className="block text-xs font-medium text-neutral-700 mb-1.5">
                Insurance / Payor
              </label>
              <select
                value={selectedPayorId}
                onChange={e => setSelectedPayorId(e.target.value)}
                className="w-full text-sm border border-neutral-200 bg-white px-3.5 py-2.5 rounded-lg text-neutral-900 transition focus:border-neutral-900 focus:outline-none focus:ring-4 focus:ring-neutral-900/5 cursor-pointer font-medium"
              >
                <option value="">-- Select Insurance Payor --</option>
                {payers
                  .filter((p: any) => p.status === 1 || p.status === 'ACTIVE')
                  .map((p: any) => (
                    <option key={p.id} value={p.id}>
                      {p.name} {p.code ? `(${p.code})` : ''}
                    </option>
                  ))}
              </select>
            </div>

            <div className="flex items-center justify-end gap-3 pt-3 border-t border-neutral-100">
              <button
                type="button"
                onClick={() => setShowInsuranceModal(false)}
                className="px-4 py-2.5 border border-neutral-200 rounded-lg text-xs font-medium text-neutral-700 hover:bg-neutral-50 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  mutations.updatePayor.mutate(selectedPayorId || null, {
                    onSuccess: () => setShowInsuranceModal(false),
                  })
                }}
                disabled={mutations.updatePayor.isPending}
                className="px-5 py-2.5 bg-neutral-900 hover:bg-neutral-800 text-white rounded-lg text-xs font-semibold shadow-sm transition-colors cursor-pointer disabled:opacity-50"
              >
                {mutations.updatePayor.isPending ? 'Saving...' : 'Save Insurance'}
              </button>
            </div>
          </div>
        </div>
      </Modal>

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
