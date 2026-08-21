import { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useBill, useBillingMutations } from '../../../hooks/billing/useBilling'
import BackButton from '../../../components/shared/BackButton'

export default function DisallowedChargesPage() {
  const navigate = useNavigate()
  const { billId } = useParams<{ billId: string }>()
  const { data: bill, isLoading, error } = useBill(billId)
  const mutations = useBillingMutations(billId ?? '')

  // Disallowed amounts per line item ID (in rupees, default '0')
  const [disallowances, setDisallowances] = useState<Record<string, string>>({})

  useEffect(() => {
    if (bill?.chargeLineItems) {
      const initial: Record<string, string> = {}
      bill.chargeLineItems.forEach(line => {
        if (line.status !== 'CANCELLED') {
          initial[line.id] = line.disallowedAmount ? String(Math.round(line.disallowedAmount / 100)) : '0'
        }
      })
      setDisallowances(initial)
    }
  }, [bill])

  if (isLoading) {
    return <div className="text-sm text-gray-500 p-6">Loading bill details…</div>
  }

  if (error || !bill) {
    return <div className="text-sm text-red-600 p-6">Failed to load bill details</div>
  }

  const activeLines = bill.chargeLineItems.filter(c => c.status !== 'CANCELLED')

  // Total Billed in rupees
  const totalBilledRupees = (
    activeLines.reduce((sum, line) => sum + line.amount, 0) / 100
  ).toFixed(2)

  // Total Disallowed sum in rupees
  const totalDisallowedSum = activeLines.reduce((sum, line) => {
    const val = parseFloat(disallowances[line.id] || '0') || 0
    return sum + val
  }, 0)

  const totalDisallowedDisplay = String(totalDisallowedSum)

  // Distribute total disallowed amount across line items if total box is edited
  const handleTotalDisallowedChange = (valStr: string) => {
    if (valStr === '') {
      const cleared: Record<string, string> = {}
      activeLines.forEach(line => {
        cleared[line.id] = '0'
      })
      setDisallowances(cleared)
      return
    }

    const totalVal = parseFloat(valStr) || 0
    const totalBilledPaise = activeLines.reduce((sum, line) => sum + line.amount, 0)

    const updated: Record<string, string> = {}
    if (totalBilledPaise > 0) {
      let remRupees = totalVal
      activeLines.forEach((line, idx) => {
        if (idx === activeLines.length - 1) {
          updated[line.id] = remRupees > 0 ? String(Math.round(remRupees * 100) / 100) : '0'
        } else {
          const prop = Math.round((line.amount / totalBilledPaise) * totalVal * 100) / 100
          const allocated = Math.min(remRupees, prop)
          updated[line.id] = allocated > 0 ? String(allocated) : '0'
          remRupees -= allocated
        }
      })
    } else {
      const count = activeLines.length
      let remRupees = totalVal
      activeLines.forEach((line, idx) => {
        if (idx === count - 1) {
          updated[line.id] = remRupees > 0 ? String(Math.round(remRupees * 100) / 100) : '0'
        } else {
          const prop = Math.round((totalVal / (count || 1)) * 100) / 100
          updated[line.id] = prop > 0 ? String(prop) : '0'
          remRupees -= prop
        }
      })
    }
    setDisallowances(updated)
  }

  const handleSave = () => {
    const payload = activeLines.map(line => {
      const valStr = disallowances[line.id] || '0'
      const valRupees = parseFloat(valStr) || 0
      return {
        id: line.id,
        disallowedAmount: Math.round(valRupees * 100),
      }
    })

    mutations.updateDisallowance.mutate(payload, {
      onSuccess: () => {
        navigate(-1)
      },
    })
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8 space-y-6">
      {/* Top Header section matching RecordPaymentPage layout */}
      <div className="flex justify-between items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Disallowed Charges</h2>
          <p className="text-sm text-gray-500 mt-0.5">
            {bill.patientName} {bill.patientNumber && `· ${bill.patientNumber}`} {bill.billNumber && `· ${bill.billNumber}`}
          </p>
        </div>
        <BackButton />
      </div>

      {/* Main card container */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-5">
        {/* Card Header matching prompt reference */}
        <h3 className="text-xs font-bold text-gray-700 uppercase tracking-wider">
          DISALLOWED CHARGES
        </h3>

        {/* Charges Table */}
        <div className="border border-gray-100 rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50/70 border-b border-gray-100 text-xs">
                <th className="px-4 py-3 text-left font-semibold text-gray-600">Charge</th>
                <th className="px-4 py-3 text-right font-semibold text-gray-600">Billed</th>
                <th className="px-4 py-3 text-right font-semibold text-gray-600 w-48">Disallowed</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {activeLines.map(line => {
                const billedAmountRupees = (line.amount / 100).toFixed(2)
                return (
                  <tr key={line.id} className="hover:bg-gray-50/40">
                    <td className="px-4 py-3 text-xs font-semibold text-gray-800">
                      {line.itemName}
                    </td>
                    <td className="px-4 py-3 text-right text-xs font-semibold text-gray-600 tabular-nums">
                      ₹{billedAmountRupees}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="relative inline-block w-36">
                        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-medium text-xs">₹</span>
                        <input
                          type="number"
                          min="0"
                          step="any"
                          value={disallowances[line.id] ?? '0'}
                          onChange={e => setDisallowances({ ...disallowances, [line.id]: e.target.value })}
                          onFocus={e => { if (e.target.value === '0') e.target.select() }}
                          onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
                          placeholder="0"
                          className="w-full pl-7 pr-3 py-1.5 bg-white border border-gray-300 rounded-xl text-xs font-medium text-gray-900 text-left focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all"
                        />
                      </div>
                    </td>
                  </tr>
                )
              })}
              {activeLines.length === 0 && (
                <tr>
                  <td colSpan={3} className="px-4 py-6 text-center text-gray-400 text-xs">
                    No active charges found on this bill
                  </td>
                </tr>
              )}
            </tbody>
            {activeLines.length > 0 && (
              <tfoot className="border-t border-gray-200 bg-gray-50/70 font-bold">
                <tr>
                  <td className="px-4 py-3 text-xs font-bold text-gray-900">Total</td>
                  <td className="px-4 py-3 text-right text-xs font-bold text-gray-900 tabular-nums">
                    ₹{totalBilledRupees}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="relative inline-block w-36">
                      <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-medium text-xs">₹</span>
                      <input
                        type="number"
                        min="0"
                        step="any"
                        value={totalDisallowedDisplay}
                        onChange={e => handleTotalDisallowedChange(e.target.value)}
                        onFocus={e => { if (e.target.value === '0') e.target.select() }}
                        onKeyDown={e => { if (e.key === '-') e.preventDefault() }}
                        placeholder="0"
                        className="w-full pl-7 pr-3 py-1.5 bg-white border border-gray-300 rounded-xl text-xs font-bold text-gray-900 text-left focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all"
                      />
                    </div>
                  </td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>

        {/* Action Buttons */}
        <div className="pt-2 flex gap-3">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="flex-1 px-4 py-3 bg-gray-100 text-gray-700 text-sm font-bold rounded-xl hover:bg-gray-200 transition-colors"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={mutations.updateDisallowance.isPending}
            className="flex-[2] px-4 py-3 bg-neutral-600 text-white text-sm font-bold rounded-xl hover:bg-neutral-700 disabled:opacity-50 transition-all shadow-lg shadow-neutral-200 active:scale-[0.98]"
          >
            {mutations.updateDisallowance.isPending ? 'Saving…' : 'Save Disallowance'}
          </button>
        </div>
      </div>
    </div>
  )
}
