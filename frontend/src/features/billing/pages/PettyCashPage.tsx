import { useState, useEffect } from 'react'
import api from '../../../lib/axios'
import type { ApiResponse } from '../../../types/api'
import { AmountDisplay } from '../../../components/shared/AmountDisplay'
import { Plus, X, Search, Calendar, AlertCircle, AlertTriangle } from 'lucide-react'
import DatePicker from '../../../components/shared/DatePicker'
import { format } from 'date-fns'
import { useAuthStore } from '../../../store/authStore'

interface PettyCashRecord {
  id: string
  sequenceNumber: string
  reason: string
  givenTo: string
  amount: number
  paymentDate: string
  paymentMode: string
  status: string
}

export default function PettyCashPage() {
  const selectedBranchId = useAuthStore(s => s.selectedBranchId)
  // Filters
  const [fromDate, setFromDate] = useState(() => new Date().toISOString().split('T')[0])
  const [toDate, setToDate] = useState(() => new Date().toISOString().split('T')[0])
  const [searchValue, setSearchValue] = useState('')

  // List State
  const [records, setRecords] = useState<PettyCashRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [modalDate, setModalDate] = useState(() => new Date().toISOString().split('T')[0])
  const [modalGivenTo, setModalGivenTo] = useState('')
  const [modalAmount, setModalAmount] = useState('')
  const [modalReason, setModalReason] = useState('')
  const [modalPaymentMode, setModalPaymentMode] = useState('CASH')
  const [modalError, setModalError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [recordToCancel, setRecordToCancel] = useState<PettyCashRecord | null>(null)
  const [cancelling, setCancelling] = useState(false)

  // Fetch Records
  const fetchRecords = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await api.get<ApiResponse<PettyCashRecord[]>>('/payment', {
        params: {
          dateSearch: fromDate,
          toDateSearch: toDate,
          searchValue: searchValue.trim() || undefined,
          start: 0,
          limit: 100,
        },
      })
      setRecords(res.data.data ?? [])
    } catch (err: any) {
      console.error(err)
      setError('Failed to fetch petty cash records.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchRecords()
  }, [fromDate, toDate, searchValue, selectedBranchId])

  // Create Record Action
  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault()
    setModalError(null)

    // Validations
    if (!modalGivenTo || modalGivenTo.trim().length < 3 || modalGivenTo.trim().length > 15) {
      setModalError('Paid To name must be between 3 and 15 characters.')
      return
    }

    const amt = parseFloat(modalAmount)
    if (isNaN(amt) || amt <= 0) {
      setModalError('Amount must be a positive number.')
      return
    }


    setSaving(true)
    try {
      await api.post('/payment', {
        paymentDate: modalDate,
        givenTo: modalGivenTo.trim(),
        amount: Math.round(amt * 100), // convert to paise
        reason: modalReason.trim(),
        paymentMode: modalPaymentMode,
      })
      // Reset & Close
      setModalGivenTo('')
      setModalAmount('')
      setModalReason('')
      setModalPaymentMode('CASH')
      setIsModalOpen(false)
      fetchRecords()
    } catch (err: any) {
      console.error(err)
      setModalError(err.response?.data?.message || 'Failed to save petty cash record.')
    } finally {
      setSaving(false)
    }
  }

  // Cancel Record Action
  const handleCancelConfirm = async () => {
    if (!recordToCancel) return
    setCancelling(true)
    try {
      await api.put(`/payment/${recordToCancel.id}`)
      setRecordToCancel(null)
      fetchRecords()
    } catch (err: any) {
      console.error(err)
      alert(err.response?.data?.message || 'Failed to cancel record.')
    } finally {
      setCancelling(false)
    }
  }

  return (
    <div className="p-6 space-y-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight flex items-center gap-2">
            Petty Cash
          </h1>
        </div>

        <button
          onClick={() => setIsModalOpen(true)}
          className="flex items-center gap-2 px-4 py-2.5 bg-neutral-800 text-white rounded-xl text-sm font-semibold hover:bg-neutral-900 transition-colors shadow-sm active:scale-[0.98]"
        >
          <Plus className="w-4 h-4" />
          Record Petty Cash
        </button>
      </div>

      {/* Filters Panel */}
      <div className="bg-white border border-gray-200 rounded-2xl p-5 shadow-sm flex flex-wrap items-center gap-4">
        {/* Search */}
        <div className="relative flex-1 min-w-[240px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            type="text"
            placeholder="Search by Paid To or Petty Cash No..."
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            className="w-full pl-9 pr-4 py-1.5 border border-gray-200 rounded-xl text-sm bg-gray-50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all font-medium"
          />
        </div>

        {/* Date Filters */}
        <div className="flex items-center gap-2">
          <Calendar className="w-4 h-4 text-gray-400 mr-1" />
          <DatePicker
            value={fromDate}
            onChange={setFromDate}
            maxDate={new Date().toISOString().split('T')[0]}
            placeholder="From Date"
          />
          <span className="text-gray-400 text-sm font-semibold mx-1">to</span>
          <DatePicker
            value={toDate}
            onChange={setToDate}
            maxDate={new Date().toISOString().split('T')[0]}
            placeholder="To Date"
          />
        </div>
      </div>

      {/* Data Table */}
      <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-sm text-gray-500 italic">Loading records...</div>
        ) : error ? (
          <div className="p-8 text-center text-sm text-red-600 font-semibold">{error}</div>
        ) : records.length === 0 ? (
          <div className="p-8 text-center text-sm text-gray-500">No records found matching filters.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm text-gray-600">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  <th className="px-5 py-3.5">Date</th>
                  <th className="px-5 py-3.5">Petty Cash No</th>
                  <th className="px-5 py-3.5">Paid To</th>
                  <th className="px-5 py-3.5">Payment Mode</th>
                  <th className="px-5 py-3.5">Reason / Remark</th>
                  <th className="px-5 py-3.5 text-right">Amount</th>
                  <th className="px-5 py-3.5 text-center">Status</th>
                  <th className="px-5 py-3.5 text-center">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {records.map((r) => (
                  <tr key={r.id} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-3.5 whitespace-nowrap font-medium text-gray-900">
                      {r.paymentDate ? format(new Date(r.paymentDate), 'dd/MM/yyyy') : '—'}
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap text-gray-500 font-mono text-xs">{r.sequenceNumber}</td>
                    <td className="px-5 py-3.5 whitespace-nowrap text-gray-900 font-semibold">{r.givenTo}</td>
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <span className="inline-flex items-center px-2 py-1 rounded-md text-xs font-bold bg-neutral-100 text-neutral-800">
                        {r.paymentMode}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 max-w-[240px] truncate" title={r.reason}>
                      {r.reason}
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap text-right font-bold text-gray-900">
                      <AmountDisplay amount={r.amount} />
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap text-center">
                      <span
                        className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-bold ${r.status === 'Active'
                          ? 'bg-green-50 text-green-700 border border-green-200'
                          : 'bg-red-50 text-red-700 border border-red-200'
                          }`}
                      >
                        {r.status}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap text-center">
                      {r.status === 'Active' && (
                        <button
                          onClick={() => setRecordToCancel(r)}
                          className="p-1 rounded-md text-red-500 hover:text-red-700 hover:bg-red-50 transition-all duration-150"
                          title="Cancel"
                        >
                          <X className="w-4 h-4 mx-auto" />
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Record Petty Cash Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm" style={{ marginTop: 0 }}>
          <div className="bg-white rounded-2xl border border-gray-200 shadow-2xl w-full max-w-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="flex justify-between items-center px-6 py-4 border-b border-gray-100 bg-gray-50">
              <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
                Record Petty Cash
              </h2>
              <button
                onClick={() => {
                  setModalGivenTo('')
                  setModalAmount('')
                  setModalReason('')
                  setModalPaymentMode('CASH')
                  setModalError(null)
                  setIsModalOpen(false)
                }}
                className="p-1 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-all"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Modal Form */}
            <form onSubmit={handleSave} className="p-6 space-y-4">
              {modalError && (
                <div className="flex items-start gap-2.5 p-3.5 bg-red-50 border border-red-200 rounded-xl text-xs text-red-700 font-semibold">
                  <AlertCircle className="w-4 h-4 shrink-0 text-red-600 mt-0.5" />
                  <span>{modalError}</span>
                </div>
              )}

              <div className="grid grid-cols-2 gap-4">
                {/* Date Input */}
                <div className="space-y-1.5 col-span-2 sm:col-span-1">
                  <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">Date</label>
                  <DatePicker
                    value={modalDate}
                    onChange={setModalDate}
                    maxDate={new Date().toISOString().split('T')[0]}
                    placeholder="Select Date"
                    clearable={false}
                  />
                </div>

                {/* Given To Input */}
                <div className="space-y-1.5 col-span-2 sm:col-span-1">
                  <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">Paid To</label>
                  <input
                    type="text"
                    required
                    placeholder="Enter recipient name"
                    value={modalGivenTo}
                    onChange={(e) => setModalGivenTo(e.target.value)}
                    className="w-full px-3.5 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-neutral-500 focus:outline-none transition-all font-semibold text-gray-900"
                  />
                  <p className="text-[10px] text-gray-400 font-medium">Must be 3 to 15 characters long.</p>
                </div>

                {/* Amount Input */}
                <div className="space-y-1.5 col-span-2 sm:col-span-1">
                  <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">Amount (₹)</label>
                  <div className="relative">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-medium">₹</span>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      required
                      placeholder="0.00"
                      value={modalAmount}
                      onChange={(e) => setModalAmount(e.target.value)}
                      className="w-full pl-8 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-neutral-500 focus:outline-none transition-all font-bold text-gray-900"
                    />
                  </div>
                </div>

                {/* Payment Mode */}
                <div className="space-y-1.5 col-span-2 sm:col-span-1">
                  <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">Payment Mode</label>
                  <select
                    value={modalPaymentMode}
                    onChange={(e) => setModalPaymentMode(e.target.value)}
                    className="w-full px-3.5 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-neutral-500 focus:outline-none transition-all font-bold text-gray-900"
                  >
                    <option value="CASH">CASH</option>
                    <option value="UPI">UPI</option>
                  </select>
                </div>
              </div>

              {/* Remark/Reason Input */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-gray-700 uppercase tracking-tight">Reason / Remark</label>
                <textarea
                  rows={3}
                  placeholder="Enter explanation for expense..."
                  value={modalReason}
                  onChange={(e) => setModalReason(e.target.value)}
                  className="w-full px-3.5 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:bg-white focus:ring-2 focus:ring-neutral-500 focus:outline-none transition-all text-gray-900"
                />
              </div>

              {/* Footer Actions */}
              <div className="pt-2 flex gap-3">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="flex-1 px-4 py-2.5 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-[2] px-4 py-2.5 bg-neutral-800 text-white text-sm font-bold rounded-xl hover:bg-neutral-900 disabled:opacity-50 transition-all shadow-md active:scale-[0.98]"
                >
                  {saving ? 'Saving...' : 'Save Record'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
       {/* Cancel Confirmation Modal */}
      {recordToCancel && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-in fade-in duration-150" style={{ marginTop: 0 }}>
          <div className="bg-white rounded-2xl shadow-2xl p-6 max-w-sm w-full text-center space-y-4 animate-in zoom-in-95 duration-150">
            <div className="w-14 h-14 rounded-full bg-red-50 flex items-center justify-center mx-auto border border-red-100 text-red-600">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <h4 className="text-lg font-bold text-gray-900">Cancel Petty Cash</h4>
            <p className="text-sm text-gray-500">
              Are you sure you want to cancel this petty cash record?
              <br />
              <span className="font-semibold text-gray-900">
                {recordToCancel.sequenceNumber || recordToCancel.id} ({recordToCancel.givenTo} - ₹{(recordToCancel.amount / 100).toLocaleString('en-IN')})
              </span>
              <br />
              This action cannot be undone.
            </p>
            <div className="flex gap-3 pt-2">
              <button
                onClick={() => setRecordToCancel(null)}
                disabled={cancelling}
                className="flex-1 px-4 py-2.5 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleCancelConfirm}
                disabled={cancelling}
                className="flex-1 px-4 py-2.5 bg-red-600 text-white text-sm font-bold rounded-xl hover:bg-red-700 disabled:opacity-50 transition-all shadow-md active:scale-[0.98]"
              >
                {cancelling ? 'Cancelling...' : 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
