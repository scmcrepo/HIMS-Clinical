import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useBill, useBillingMutations } from '../../../hooks/billing/useBilling'
import { AmountDisplay } from '../../../components/shared/AmountDisplay'
import { toast } from '../../../hooks/useToast'
import BackButton from '../../../components/shared/BackButton'

export default function TotalDiscountPage() {
  const navigate = useNavigate()
  const { billId } = useParams<{ billId: string }>()
  const { data: bill, isLoading, error } = useBill(billId)
  const mutations = useBillingMutations(billId ?? '')

  const [totalDiscountAmount, setTotalDiscountAmount] = useState('')
  const [totalDiscountReason, setTotalDiscountReason] = useState('')

  if (isLoading) return <div className="text-sm text-gray-500 p-6">Loading bill…</div>
  if (error || !bill) return <div className="text-sm text-red-600 p-6">Failed to load bill</div>

  const isOp = bill.encounterType === 'OUTPATIENT'
  const parsedAmount = Math.round(parseFloat(totalDiscountAmount || '0') * 100)
  const isZeroOrNegative = totalDiscountAmount !== '' && parsedAmount <= 0
  const discountIsInvalid = isZeroOrNegative || parsedAmount >= bill.billAmount

  const handleApplyTotalDiscount = () => {
    if (!totalDiscountAmount || isNaN(parseFloat(totalDiscountAmount))) {
      toast({ title: 'Please enter a valid discount amount', variant: 'destructive' })
      return
    }
    const total = Math.round(parseFloat(totalDiscountAmount) * 100)
    if (total <= 0) {
      toast({ title: 'Total discount amount must be greater than zero', variant: 'destructive' })
      return
    }
    if (bill.paymentTotal > 0 && isOp) {
      toast({ title: 'Discounts cannot be added after payments have been recorded for OP bills', variant: 'destructive' })
      return
    }
    if (total >= bill.billAmount) {
      toast({ title: 'Enter valid amount', variant: 'destructive' })
      return
    }

    const activeLines = bill.chargeLineItems.filter(c => c.status !== 'CANCELLED')
    if (activeLines.length === 0) return

    const billTotal = activeLines.reduce((sum, c) => sum + c.amount, 0)
    if (billTotal === 0) return

    let remainingDiscount = total
    const lineDiscounts = activeLines.map((line, index) => {
      let amount = 0
      if (index === activeLines.length - 1) {
        amount = Math.min(remainingDiscount, line.amount)
      } else {
        const proportional = Math.round((line.amount / billTotal) * total)
        amount = Math.min(remainingDiscount, line.amount, proportional)
      }
      remainingDiscount -= amount
      return { chargeLineItemId: line.id, amount, maxAmount: line.amount }
    })

    if (remainingDiscount > 0) {
      for (const ld of lineDiscounts) {
        const capacity = ld.maxAmount - ld.amount
        if (capacity > 0) {
          const add = Math.min(remainingDiscount, capacity)
          ld.amount += add
          remainingDiscount -= add
          if (remainingDiscount === 0) break
        }
      }
    }

    const cleanedLineDiscounts = lineDiscounts.map(ld => ({
      chargeLineItemId: ld.chargeLineItemId,
      amount: ld.amount
    }))

    mutations.applyDiscount.mutate(
      {
        totalDiscount: total,
        lineDiscounts: cleanedLineDiscounts,
        reason: totalDiscountReason.trim() || undefined,
      },
      { onSuccess: () => navigate(-1) }
    )
  }

  return (
    <div className="max-w-lg mx-auto px-4 py-8 space-y-6">
      <div className="flex justify-between items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Apply Total Bill Discount</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            {bill.patientName} {bill.patientNumber && `· ${bill.patientNumber}`} {bill.billNumber && `· ${bill.billNumber}`}
          </p>
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-5">
        {/* Bill amount summary */}
        <div className="bg-amber-50 rounded-xl border border-amber-100 p-4">
          <p className="text-xs font-semibold text-amber-800 uppercase tracking-wider mb-1">Bill Amount</p>
          <div className="text-2xl font-black text-amber-900">
            <AmountDisplay amount={bill.billAmount} hideDecimals />
          </div>
          {bill.discountTotal > 0 && (
            <p className="text-xs text-amber-700 mt-1">
              Existing discount: <AmountDisplay amount={bill.discountTotal} hideDecimals />
            </p>
          )}
        </div>

        {/* Discount amount input */}
        <div className="space-y-1.5">
          <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">
            Discount Amount (₹)
          </label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-medium">₹</span>
            <input
              type="number"
              step="1"
              min="0"
              value={totalDiscountAmount}
              onChange={e => setTotalDiscountAmount(e.target.value)}
              onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
              autoFocus
              placeholder="0.00"
              className={`w-full pl-8 pr-4 py-2.5 bg-gray-50 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:bg-white transition-all font-bold text-gray-900 ${
                discountIsInvalid
                  ? 'border-red-300 focus:ring-red-500 text-red-900 bg-red-50/50'
                  : 'border-gray-200 focus:ring-amber-500'
              }`}
            />
          </div>
          {discountIsInvalid && (
            <p className="text-xs text-red-500 font-semibold">Enter valid amount</p>
          )}
        </div>

        {/* Reason */}
        {parseFloat(totalDiscountAmount) > 0 && (
          <div className="space-y-1.5">
            <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">
              Reason (optional)
            </label>
            <textarea
              value={totalDiscountReason}
              onChange={e => setTotalDiscountReason(e.target.value)}
              placeholder="Optional reason for discount..."
              rows={3}
              className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500 focus:bg-white transition-all resize-none"
            />
          </div>
        )}

        {/* Actions */}
        <div className="pt-2 flex gap-3">
          <button
            onClick={() => navigate(-1)}
            className="flex-1 px-4 py-3 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleApplyTotalDiscount}
            disabled={mutations.applyDiscount.isPending || !totalDiscountAmount || discountIsInvalid}
            className="flex-[2] px-4 py-3 bg-amber-600 text-white text-sm font-bold rounded-xl hover:bg-amber-700 disabled:opacity-50 transition-all shadow-lg shadow-amber-200 active:scale-[0.98]"
          >
            {mutations.applyDiscount.isPending ? 'Applying…' : 'Apply Discount'}
          </button>
        </div>
      </div>
    </div>
  )
}
