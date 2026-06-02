import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useBill, useBillingMutations } from '../../../hooks/billing/useBilling'
import { AmountDisplay } from '../../../components/shared/AmountDisplay'
import BackButton from '../../../components/shared/BackButton'
import { PrintButton } from '../../../components/shared/PrintButton'
import type { PaymentMode } from '../../../types/billing'

export default function RecordPaymentPage() {
  const navigate = useNavigate()
  const { billId } = useParams<{ billId: string }>()
  const { data: bill, isLoading, error } = useBill(billId)
  const mutations = useBillingMutations(billId ?? '')

  const [paymentAmount, setPaymentAmount] = useState('')
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH')
  const [cardType, setCardType] = useState('Credit')
  const [cardNo, setCardNo] = useState('')
  const [bankName] = useState('')
  const [printedBillId, setPrintedBillId] = useState<string | null>(null)

  if (isLoading) return <div className="text-sm text-gray-500 p-6">Loading bill…</div>
  if (error || !bill) return <div className="text-sm text-red-600 p-6">Failed to load bill</div>

  const isGenerated = bill.status !== 'DRAFT'
  const paymentIsInvalid = Math.round(parseFloat(paymentAmount || '0') * 100) > bill.dueAmount && (isGenerated || bill.encounterType === 'OUTPATIENT')

  const handleRecordPayment = () => {
    if (!paymentAmount) return
    let notesStr: string | undefined
    if (paymentMode === 'CARD') {
      notesStr = `Card Type: ${cardType}, Card No: ${cardNo}, Bank Name: ${bankName}`
    }
    mutations.recordPayment.mutate(
      {
        payments: [{
          amount: Math.round(parseFloat(paymentAmount) * 100),
          paymentMode,
          paymentType: bill.status === 'DRAFT' ? 'DEPOSIT' : 'PAYMENT',
          notes: notesStr,
        }],
      },
      { onSuccess: () => { setPrintedBillId(billId ?? null) } }
    )
  }

  return (
    <div className="max-w-lg mx-auto px-4 py-8 space-y-6">
      <div className="flex justify-between items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Record Payment</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            {bill.patientName} {bill.patientNumber && `· ${bill.patientNumber}`} {bill.billNumber && `· ${bill.billNumber}`}
          </p>
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-5">
        {/* Due amount summary */}
        <div className="bg-blue-50 rounded-xl border border-blue-100 p-4">
          <p className="text-xs font-semibold text-blue-800 uppercase tracking-wider mb-1">Due Amount</p>
          <div className="text-2xl font-black text-blue-900">
            <AmountDisplay amount={bill.dueAmount} hideDecimals />
          </div>
        </div>

        {/* Amount input */}
        <div className="space-y-1.5">
          <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">
            Collection Amount (₹)
          </label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-medium">₹</span>
            <input
              type="number"
              step="1"
              min="1"
              value={paymentAmount}
              onChange={e => setPaymentAmount(e.target.value)}
              onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
              autoFocus
              placeholder="0.00"
              className={`w-full pl-8 pr-4 py-2.5 bg-gray-50 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:bg-white transition-all font-bold text-gray-900 ${paymentIsInvalid
                ? 'border-red-300 focus:ring-red-500 text-red-900 bg-red-50/50'
                : 'border-gray-200 focus:ring-blue-500'
                }`}
            />
          </div>
          {paymentIsInvalid && (
            <p className="text-xs text-red-500 font-semibold">Enter valid amount</p>
          )}
        </div>

        {/* Payment mode */}
        <div className="space-y-1.5">
          <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">
            Payment Mode
          </label>
          <div className="grid grid-cols-3 gap-2">
            {(['CASH', 'UPI', 'CARD'] as PaymentMode[]).map(mode => (
              <button
                key={mode}
                type="button"
                onClick={() => setPaymentMode(mode)}
                className={`py-2.5 px-4 rounded-xl text-xs font-bold border transition-all ${paymentMode === mode
                  ? 'bg-blue-600 border-blue-600 text-white shadow-md shadow-blue-100'
                  : 'bg-white border-gray-200 text-gray-600 hover:border-blue-300 hover:bg-blue-50'
                  }`}
              >
                {mode}
              </button>
            ))}
          </div>
        </div>

        {/* Card details */}
        {paymentMode === 'CARD' && (
          <div className="grid grid-cols-2 gap-4 pt-1">
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">Card Type</label>
              <select
                value={cardType}
                onChange={e => setCardType(e.target.value)}
                className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none transition-all"
              >
                <option value="Credit">Credit</option>
                <option value="Debit">Debit</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">Card No</label>
              <input
                type="text"
                value={cardNo}
                onChange={e => setCardNo(e.target.value)}
                className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none transition-all"
              />
            </div>
            {/* <div className="space-y-1.5">
              <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">Bank Name</label>
              <input
                type="text"
                value={bankName}
                onChange={e => setBankName(e.target.value)}
                className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none transition-all"
              />
            </div> */}
          </div>
        )}

        {/* Success state with print button */}
        {printedBillId ? (
          <div className="space-y-3">
            <div className="bg-green-50 border border-green-200 rounded-xl p-4 text-center">
              <div className="text-green-700 font-bold text-sm mb-1">&#10003; Payment Recorded Successfully</div>
              <div className="text-green-600 text-xs">Would you like to print a receipt?</div>
            </div>
            <div className="flex gap-3">
              <PrintButton
                templateType={bill.encounterType === 'OUTPATIENT' ? 'OP_RECEIPT' : 'IP_RECEIPT'}
                params={{ id: printedBillId }}
                variant="default"
                label="Print Receipt"
                className="flex-1 justify-center"
              />
              <button
                onClick={() => navigate(-1)}
                className="flex-1 px-4 py-3 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
              >
                Done
              </button>
            </div>
          </div>
        ) : (
        <div className="pt-2 flex gap-3">
          <button
            onClick={() => navigate(-1)}
            className="flex-1 px-4 py-3 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleRecordPayment}
            disabled={mutations.recordPayment.isPending || !paymentAmount || paymentIsInvalid}
            className="flex-[2] px-4 py-3 bg-blue-600 text-white text-sm font-bold rounded-xl hover:bg-blue-700 disabled:opacity-50 transition-all shadow-lg shadow-blue-200 active:scale-[0.98]"
          >
            {mutations.recordPayment.isPending ? 'Saving…' : 'Confirm Payment'}
          </button>
        </div>
        )}
      </div>
    </div>
  )
}
