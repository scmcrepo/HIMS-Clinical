import { useState } from 'react'
import { useNavigate, useParams, useLocation } from 'react-router-dom'
import { useBill, useBillingMutations } from '../../../hooks/billing/useBilling'
import { AmountDisplay } from '../../../components/shared/AmountDisplay'
import BackButton from '../../../components/shared/BackButton'

/**
 * Handles two refund scenarios:
 *  1. Line-item refund  — navigated to with state: { lineId, lineName, lineAmount }
 *  2. Advance / overpayment refund — navigated to with state: { advanceRefund: true }
 */
export default function RefundChargePage() {
  const navigate = useNavigate()
  const { billId } = useParams<{ billId: string }>()
  const location = useLocation()
  const { data: bill, isLoading, error } = useBill(billId)
  const mutations = useBillingMutations(billId ?? '')

  const state = location.state as {
    lineId?: string
    lineName?: string
    lineAmount?: number   // in paise/cents already
    advanceRefund?: boolean
  } | null

  const isAdvanceRefund = !!state?.advanceRefund
  const lineId = state?.lineId ?? null
  const lineName = state?.lineName ?? ''
  // line amount comes pre-calculated (net of discount), already as display integer (rupees)
  const lineAmountRupees = state?.lineAmount ?? 0

  const [refundAmount, setRefundAmount] = useState(
    isAdvanceRefund ? '' : String(lineAmountRupees)
  )
  const [refundNotes, setRefundNotes] = useState('')
  const [success, setSuccess] = useState(false)

  if (isLoading) return <div className="text-sm text-gray-500 p-6">Loading bill…</div>
  if (error || !bill) return <div className="text-sm text-red-600 p-6">Failed to load bill</div>

  const availableForRefund = bill.paymentTotal - (bill.refundTotal || 0)
  const parsedPaise = Math.round(parseFloat(refundAmount || '0') * 100)
  const advanceIsInvalid = isAdvanceRefund && parsedPaise > availableForRefund

  const handleRefund = () => {
    if (isAdvanceRefund) {
      mutations.recordPayment.mutate(
        {
          payments: [{
            amount: parsedPaise,
            paymentMode: 'CASH',
            paymentType: 'ADVANCE_REFUND',
            notes: refundNotes || undefined,
          }],
        },
        { onSuccess: () => navigate(-1) }
      )
    } else {
      if (!lineId) return
      mutations.refund.mutate(
        {
          amount: Math.round(parseFloat(refundAmount) * 100),
          lineItemIds: [lineId],
          paymentMode: 'CASH',
          notes: refundNotes || undefined,
        },
        { onSuccess: () => setSuccess(true) }
      )
    }
  }

  // ─── Success state (line-item refund only) ───────────────────────────────
  if (success) {
    return (
      <div className="max-w-lg mx-auto px-4 py-8 space-y-6">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Refund Charge</h2>
          <BackButton />
        </div>

        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-10 flex flex-col items-center text-center space-y-4">
          <div className="w-20 h-20 rounded-full bg-green-100 flex items-center justify-center">
            <span className="text-4xl text-green-600">✓</span>
          </div>
          <h3 className="text-xl font-bold text-gray-900">Refund Successful</h3>
          <p className="text-sm text-gray-500">
            The refund for <span className="font-semibold text-gray-700">{lineName}</span> has been processed successfully.
          </p>
          <button
            onClick={() => navigate(-1)}
            className="mt-4 px-8 py-3 bg-green-600 text-white text-sm font-bold rounded-xl hover:bg-green-700 transition-colors"
          >
            Back to Bill
          </button>
        </div>
      </div>
    )
  }

  // ─── Main form ────────────────────────────────────────────────────────────
  return (
    <div className="max-w-lg mx-auto px-4 py-8 space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">
            {isAdvanceRefund ? 'Refund Overpayment' : 'Refund Charge'}
          </h2>
          <p className="text-sm text-gray-500 mt-0.5">
            {bill.patientName} {bill.patientNumber && `· ${bill.patientNumber}`} {bill.billNumber && `· ${bill.billNumber}`}
          </p>
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-5">
        {/* Context summary */}
        {isAdvanceRefund ? (
          <div className="bg-red-50 rounded-xl border border-red-100 p-4 flex justify-between items-center">
            <div>
              <p className="text-[10px] font-bold text-red-800 uppercase tracking-wider mb-0.5">
                Available for Refund
              </p>
              <div className="text-xl font-black text-red-900">
                <AmountDisplay amount={availableForRefund} hideDecimals />
              </div>
            </div>
            {bill.dueAmount < 0 && (
              <div className="text-right">
                <p className="text-[10px] font-bold text-red-800 uppercase tracking-wider mb-0.5">Overpayment</p>
                <div className="text-sm font-bold text-red-700">
                  <AmountDisplay amount={Math.abs(bill.dueAmount)} hideDecimals />
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="bg-red-50 rounded-xl border border-red-100 p-4">
            <p className="text-xs font-semibold text-red-800 uppercase tracking-wider mb-1">Item Details</p>
            <p className="text-sm font-bold text-gray-900">{lineName}</p>
          </div>
        )}

        {/* Refund amount */}
        <div className="space-y-1.5">
          <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">
            Refund Amount (₹)
          </label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-medium">₹</span>
            <input
              type="number"
              step="1"
              min="1"
              value={refundAmount}
              onChange={isAdvanceRefund ? e => setRefundAmount(e.target.value) : undefined}
              readOnly={!isAdvanceRefund}
              onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
              autoFocus={isAdvanceRefund}
              placeholder="0.00"
              className={`w-full pl-8 pr-4 py-2.5 border rounded-xl text-sm focus:outline-none transition-all font-bold text-gray-900 ${
                !isAdvanceRefund
                  ? 'bg-gray-100 border-gray-200 cursor-default'
                  : advanceIsInvalid
                  ? 'bg-red-50/50 border-red-300 focus:ring-2 focus:ring-red-500'
                  : 'bg-gray-50 border-gray-200 focus:ring-2 focus:ring-red-500 focus:bg-white'
              }`}
            />
          </div>
          {advanceIsInvalid && (
            <p className="text-xs text-red-500 font-semibold">Cannot refund more than paid amount</p>
          )}
        </div>

        {/* Notes */}
        <div className="space-y-1.5">
          <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">
            Reason / Notes
          </label>
          <textarea
            value={refundNotes}
            onChange={e => setRefundNotes(e.target.value)}
            placeholder={isAdvanceRefund ? 'Reason for advance refund' : `Refund for ${lineName}`}
            rows={3}
            className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-red-500 focus:bg-white transition-all resize-none"
          />
        </div>

        {/* Actions */}
        <div className="pt-2 flex gap-3">
          <button
            onClick={() => navigate(-1)}
            className="flex-1 px-4 py-3 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleRefund}
            disabled={
              (mutations.refund.isPending || mutations.recordPayment.isPending) ||
              !refundAmount ||
              (isAdvanceRefund && advanceIsInvalid)
            }
            className="flex-[2] px-4 py-3 bg-red-600 text-white text-sm font-bold rounded-xl hover:bg-red-700 disabled:opacity-50 transition-all shadow-lg shadow-red-200 active:scale-[0.98]"
          >
            {(mutations.refund.isPending || mutations.recordPayment.isPending)
              ? 'Processing…'
              : 'Confirm Refund'}
          </button>
        </div>
      </div>
    </div>
  )
}
