import { useQuery, useQueryClient } from '@tanstack/react-query'
import { PrintButton } from '../../../components/shared/PrintButton'
import { useParams, useNavigate } from 'react-router-dom'
import { salesApi } from '../../../services/sales/salesApi'
import { inventoryApi } from '../../../services/inventory/inventoryApi'
import { patientApi } from '../../../services/patient/patientApi'
import { formatDate } from '../../../lib/dateUtils'
import { useEffect, useState } from 'react'
import { format, parseISO } from 'date-fns'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'

export default function SalesViewPage() {
  const { saleId } = useParams<{ saleId: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()

  const { data: sale, isLoading } = useQuery({
    queryKey: ['sales', saleId],
    queryFn: () => salesApi.getById(saleId!),
    enabled: !!saleId,
  })

  // We need to fetch patient details to get consultant, if applicable.
  // We also need batch details to display item names and expiry dates.
  const [lineDetails, setLineDetails] = useState<any[]>([])
  const [consultantName, setConsultantName] = useState<string>('')
  
  // Payment states
  const [paymentType, setPaymentType] = useState<'due_amount' | 'partial_pay'>('due_amount')
  const [paymentMode, setPaymentMode] = useState<string>('Cash')
  const [partialAmount, setPartialAmount] = useState<string>('')
  const [bankName, setBankName] = useState<string>('')
  const [cardType, setCardType] = useState<string>('')
  const [cardNumber, setCardNumber] = useState<string>('')
  const [submitting, setSubmitting] = useState<boolean>(false)

  useEffect(() => {
    if (sale) {
      // Fetch batch details
      Promise.all(sale.lines.map(async (line) => {
        try {
          const batch = await inventoryApi.getBatch(line.inventoryBatchId)
          return { ...line, batch }
        } catch (e) {
          return { ...line, batch: null }
        }
      })).then(setLineDetails)

      // Fetch patient to get primary provider (consultant) if not stored directly in sale
      if (sale.consultantName) {
        setConsultantName(sale.consultantName)
      } else if (sale.patientId) {
        patientApi.getById(sale.patientId).then(p => {
          if (p.primaryProviderId) {
            // we don't have providerApi immediately available, so we'll just show the ID or a placeholder if we can't resolve it easily.
            // Ideally we fetch provider details.
            setConsultantName(p.primaryProviderId)
          }
        }).catch(() => {})
      }
    }
  }, [sale])

  const [billInfoOpen, setBillInfoOpen] = useState(false)
  const [paymentHistoryOpen, setPaymentHistoryOpen] = useState(false)

  const handleCollectPayment = async () => {
    if (!sale) return

    let amountPaise = 0
    if (paymentType === 'due_amount') {
      amountPaise = sale.dueAmount
    } else {
      const amt = Number(partialAmount)
      if (isNaN(amt) || amt <= 0) {
        toast({ title: 'Validation Error', description: 'Please enter a valid partial payment amount.', variant: 'destructive' })
        return
      }
      amountPaise = Math.round(amt * 100)
      if (amountPaise > sale.dueAmount) {
        toast({ title: 'Validation Error', description: 'Partial payment amount cannot exceed the due amount.', variant: 'destructive' })
        return
      }
    }

    try {
      setSubmitting(true)
      
      await salesApi.collectPayment(sale.id, {
        amount: amountPaise,
        paymentMode,
        bankName: paymentMode === 'Card' ? bankName || undefined : undefined,
        cardType: paymentMode === 'Card' ? cardType || undefined : undefined,
        cardNumber: paymentMode === 'Card' ? cardNumber || undefined : undefined,
      })

      toast({ title: 'Success', description: 'Payment collected successfully.' })
      
      setPartialAmount('')
      setBankName('')
      setCardType('')
      setCardNumber('')
      
      qc.invalidateQueries({ queryKey: ['sales'] })
    } catch (e: any) {
      toast({
        title: 'Error',
        description: e?.response?.data?.message || 'Failed to collect payment.',
        variant: 'destructive',
      })
    } finally {
      setSubmitting(false)
    }
  }

  if (isLoading || !sale) {
    return <div className="p-8 text-center text-gray-500">Loading Sale Details...</div>
  }

  const formatAmount = (amt: number) => Math.round(amt / 100).toString()

  return (
    <div className="bg-white border border-gray-200 rounded-xl flex flex-col h-full max-w-6xl mx-auto shadow-sm">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-100">
        <button 
          onClick={() => navigate('/sales/salesHistory')}
          className="text-gray-500 hover:text-gray-800 text-sm font-semibold flex items-center gap-1 transition-colors"
        >
          <span className="text-lg leading-none">&lsaquo;</span> SALES HISTORY
        </button>
        <h2 className="text-lg font-bold text-gray-800 absolute left-1/2 -translate-x-1/2">SALES</h2>
        <div className="flex items-center gap-4">
          <span className="text-sm text-gray-500 font-medium">Sale Date : <span className="text-gray-800 font-semibold">{formatDate(sale.saleDate)}</span></span>
          <PrintButton
            templateType="SALES"
            params={saleId ? { id: saleId } : {}}
            variant="icon"
            label="Print Sale"
          />
        </div>
      </div>

      <div className="p-4 flex items-center">
        <h3 className="text-lg font-medium text-gray-700 uppercase tracking-wide">
          SALE NO : <span className="text-red-500 font-bold">{sale.sequenceNumber}</span>
        </h3>
      </div>

      {/* Items Table */}
      <div className="flex-1 overflow-auto">
        <table className="w-full text-sm text-left border-t border-b border-gray-200">
          <thead>
            <tr className="bg-gray-50/50 text-gray-500 text-xs font-bold uppercase tracking-wider">
              <th className="px-4 py-3 w-16 text-left">S.NO.</th>
              <th className="px-4 py-3 min-w-[200px] text-left">ITEM</th>
              <th className="px-4 py-3 w-32 text-left">BATCH NO</th>
              <th className="px-4 py-3 w-36 text-left">EXP DATE</th>
              <th className="px-4 py-3 w-24 text-right">QTY</th>
              <th className="px-4 py-3 w-44 text-right">MRP</th>
              <th className="px-4 py-3 w-20 text-right">TAX</th>
              <th className="px-4 py-3 w-28 text-right">VALUE</th>
              <th className="px-4 py-3 w-40 text-right">SUB TOTAL</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {lineDetails.map((line, idx) => {
              const b = line.batch
              const value = line.quantity * line.unitRate
              const taxRate = b?.taxRate ?? 0
              const taxAmount = taxRate > 0 ? (value * taxRate) / (100 + taxRate) : 0
              return (
                <tr key={line.id} className="text-gray-700">
                  <td className="px-4 py-3 w-16 text-left">{idx + 1}</td>
                  <td className="px-4 py-3 min-w-[200px] text-left font-medium text-gray-900 uppercase">{b?.itemName || 'Loading...'}</td>
                  <td className="px-4 py-3 w-32 text-left">{b?.batchNumber || 'N/A'}</td>
                  <td className="px-4 py-3 w-36 text-left">{b?.expiryDate ? formatDate(b.expiryDate) : 'N/A'}</td>
                  <td className="px-4 py-3 w-24 text-right">{line.quantity}</td>
                  <td className="px-4 py-3 w-44 text-right">{formatAmount(line.unitRate)}</td>
                  <td className="px-4 py-3 w-20 text-right">
                    {taxRate}%
                  </td>
                  <td className="px-4 py-3 w-28 text-right">{formatAmount(taxAmount)}</td>
                  <td className="px-4 py-3 w-40 text-right font-semibold text-gray-900">{formatAmount(line.amount)}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Footer Totals & Summary */}
      <div className="grid grid-cols-3 border-t border-gray-200 divide-x divide-gray-200">
        <div className="col-span-2 flex flex-col divide-y divide-gray-200">
          <div className="p-4">
            <h4 
              onClick={() => setBillInfoOpen(!billInfoOpen)}
              className="text-sm font-medium text-gray-900 uppercase flex items-center gap-1 cursor-pointer select-none hover:text-blue-600 transition-colors"
            >
              BILL INFORMATION <span className="text-[10px]">{billInfoOpen ? '▲' : '▼'}</span>
            </h4>
            {billInfoOpen && (
              <div className="mt-4 flex gap-24 text-sm text-gray-700">
                <p>Consultant - <span className="font-bold text-gray-900 uppercase">{consultantName || 'NA'}</span></p>
                <p>Patient - <span className="font-bold text-gray-900 uppercase">{sale.patientName || 'WALK-IN'}</span></p>
              </div>
            )}
          </div>
          <div className="p-4">
            <h4 
              onClick={() => setPaymentHistoryOpen(!paymentHistoryOpen)}
              className="text-sm font-medium text-gray-900 uppercase flex items-center gap-1 cursor-pointer select-none hover:text-blue-600 transition-colors"
            >
              PAYMENT HISTORY <span className="text-[10px]">{paymentHistoryOpen ? '▲' : '▼'}</span>
            </h4>
            {paymentHistoryOpen && (
              <div className="mt-4 space-y-1">
                {sale.status === 'BILLED' || sale.paymentMode === 'Add to Bill' ? (
                  <div className="flex items-center gap-2 bg-blue-50 border border-blue-200 text-blue-700 px-4 py-3 rounded-lg text-xs font-semibold uppercase tracking-wider">
                    <span>ℹ</span>
                    <span>Billed to  - {sale.patientNumber ||" "}</span>
                  </div>
                ) : sale.payments && sale.payments.length > 0 ? (
                  sale.payments.map((p, idx) => (
                    <div key={p.id} className="grid grid-cols-4 text-xs font-semibold text-gray-700 py-2 border-t border-gray-100 items-center">
                      <div className="text-gray-900 font-bold uppercase">
                        SCMCR-{sale.sequenceNumber?.replace('SL-', '').replace(/^0+/, '') || '56'}-{idx + 1}
                      </div>
                      <div className="text-gray-500">
                        {(() => {
                          try {
                            const dateVal = p.createdAt
                            if (dateVal) {
                              const dateObj = typeof dateVal === 'string' ? parseISO(dateVal) : new Date(dateVal)
                              return format(dateObj, 'MMM d, yyyy h:mm:ss a')
                            }
                            return '—'
                          } catch (e) {
                            return '—'
                          }
                        })()}
                      </div>
                      <div className="text-gray-600 uppercase">
                        {p.paymentMode ? (
                          p.paymentMode === 'Card' ? (
                            `CARD${p.cardType || p.cardNumber || p.bankName ? ` (${[p.cardType, p.cardNumber, p.bankName].filter(Boolean).join(' - ')})` : ''}`
                          ) : p.paymentMode.toUpperCase()
                        ) : 'CASH'}
                      </div>
                      <div className="text-right font-bold text-gray-900">{formatAmount(p.amount ?? 0)}</div>
                    </div>
                  ))
                ) : (
                  <div className="grid grid-cols-4 text-xs font-semibold text-gray-700 py-2 border-t border-gray-100 items-center">
                    <div className="text-gray-900 font-bold uppercase">SCMCR-{sale.sequenceNumber?.replace('SL-', '').replace(/^0+/, '') || '56'}</div>
                    <div className="text-gray-500">
                      {(() => {
                        try {
                          const dateVal = sale.createdAt || sale.saleDate
                          if (dateVal) {
                            const dateObj = typeof dateVal === 'string' ? parseISO(dateVal) : new Date(dateVal)
                            return format(dateObj, 'MMM d, yyyy h:mm:ss a')
                          }
                          return '—'
                        } catch (e) {
                          return formatDate(sale.saleDate)
                        }
                      })()}
                    </div>
                    <div className="text-gray-600 uppercase">
                      {sale.paymentMode ? (
                        sale.paymentMode === 'Card' ? (
                          `CARD${sale.cardType || sale.cardNumber || sale.bankName ? ` (${[sale.cardType, sale.cardNumber, sale.bankName].filter(Boolean).join(' - ')})` : ''}`
                        ) : sale.paymentMode.toUpperCase()
                      ) : 'CASH'}
                    </div>
                    <div className="text-right font-bold text-gray-900">{formatAmount(sale.paidAmount ?? 0)}</div>
                  </div>
                )}
              </div>
            )}
          </div>
          {sale.dueAmount > 0 && (
            <div className="p-6">
              <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden animate-in fade-in slide-in-from-bottom-2 duration-300">
                {/* Tabs */}
                <div className="flex border-b border-gray-200 bg-gray-50">
                  <button
                    type="button"
                    onClick={() => setPaymentType('due_amount')}
                    className={cn(
                      "px-5 py-3 text-xs font-bold uppercase tracking-wider transition-all duration-200 border-r border-gray-200",
                      paymentType === 'due_amount'
                        ? "bg-gray-800 text-white"
                        : "text-gray-600 hover:text-gray-900 hover:bg-gray-100/50"
                    )}
                  >
                    Collect Due Amount
                  </button>
                  <button
                    type="button"
                    onClick={() => setPaymentType('partial_pay')}
                    className={cn(
                      "px-5 py-3 text-xs font-bold uppercase tracking-wider transition-all duration-200",
                      paymentType === 'partial_pay'
                        ? "bg-gray-800 text-white"
                        : "text-gray-600 hover:text-gray-900 hover:bg-gray-100/50"
                    )}
                  >
                    Collect Partial Amount
                  </button>
                </div>

                {/* Content Area */}
                <div className="p-6">
                  <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
                    <div className="flex-1 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 items-end">
                      <div>
                        <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Mode</label>
                        <select
                          value={paymentMode}
                          onChange={(e) => {
                            setPaymentMode(e.target.value)
                            setBankName('')
                            setCardType('')
                            setCardNumber('')
                          }}
                          className="w-full bg-white border border-gray-300 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 h-10 shadow-sm"
                        >
                          <option value="Cash">Cash</option>
                          <option value="Card">Card</option>
                        </select>
                      </div>

                      {paymentType === 'partial_pay' && (
                        <div>
                          <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Amount to Collect (₹)</label>
                          <input
                            type="number"
                            min={1}
                            max={Math.round(sale.dueAmount / 100)}
                            value={partialAmount}
                            onKeyDown={(e) => {
                              if (e.key === '-' || e.key === 'e' || e.key === '+' || e.key === '.') {
                                e.preventDefault()
                              }
                            }}
                            onChange={(e) => {
                              const val = e.target.value.replace(/[^0-9]/g, '')
                              if (val === '') {
                                setPartialAmount('')
                                return
                              }
                              const numVal = parseInt(val, 10)
                              const maxVal = Math.round(sale.dueAmount / 100)
                              if (numVal > maxVal) {
                                setPartialAmount(maxVal.toString())
                              } else {
                                setPartialAmount(numVal.toString())
                              }
                            }}
                            placeholder="0"
                            className="w-full bg-white border border-gray-300 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 h-10 shadow-sm"
                          />
                        </div>
                      )}

                      {paymentMode === 'Card' && (
                        <>
                          <div>
                            <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Card Type</label>
                            <select
                              value={cardType}
                              onChange={(e) => setCardType(e.target.value)}
                              className="w-full bg-white border border-gray-300 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 h-10 shadow-sm"
                            >
                              <option value="">Select Card Type</option>
                              <option value="Credit">Credit</option>
                              <option value="Debit">Debit</option>
                            </select>
                          </div>
                          <div>
                            <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Card No</label>
                            <input
                              type="text"
                              value={cardNumber}
                              onChange={(e) => setCardNumber(e.target.value)}
                              placeholder="Card No"
                              className="w-full bg-white border border-gray-300 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 h-10 shadow-sm"
                            />
                          </div>
                        </>
                      )}
                    </div>

                    <div className="shrink-0 flex items-center justify-end">
                      <button
                        type="button"
                        onClick={handleCollectPayment}
                        disabled={submitting}
                        className="w-64 py-3.5 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white text-sm font-bold uppercase tracking-wider rounded-lg shadow-md hover:shadow-lg transition-all duration-200 disabled:opacity-50"
                      >
                        {submitting ? 'Collecting...' : (paymentType === 'due_amount' ? 'Collect Due Amount' : 'Collect Partial Payment')}
                      </button>
                    </div>
                  </div>
                  {paymentType === 'partial_pay' && (
                    <p className="text-xs text-gray-400 mt-2">Outstanding due amount: ₹{formatAmount(sale.dueAmount)}</p>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
        <div className="col-span-1 bg-gray-50/30 p-0 flex flex-col divide-y divide-gray-200">
          {sale.discountAmount > 0 && (
            <>
              <div className="flex justify-between items-center px-6 py-3">
                <span className="text-sm font-semibold text-gray-500">Gross Total</span>
                <span className="text-sm font-medium text-gray-700">{formatAmount(sale.totalAmount + sale.discountAmount)}</span>
              </div>
              <div className="flex justify-between items-center px-6 py-3">
                <span className="text-sm font-semibold text-gray-500">Discount</span>
                <span className="text-sm font-medium text-gray-700">-{formatAmount(sale.discountAmount)}</span>
              </div>
            </>
          )}
          <div className="flex justify-between items-center px-6 py-4">
            <span className="text-sm font-semibold text-gray-600">Bill Amount</span>
            <span className="text-sm font-medium text-gray-900">{formatAmount(sale.totalAmount)}</span>
          </div>
          <div className="flex justify-between items-center px-6 py-4 bg-gray-100/50">
            <span className="text-base font-bold text-gray-800 uppercase tracking-wide">PAID AMOUNT</span>
            <span className="text-base font-bold text-gray-900">{formatAmount(sale.paidAmount ?? 0)}</span>
          </div>
          {sale.dueAmount > 0 && (
            <div className="flex justify-between items-center px-6 py-4 bg-red-50/30">
              <span className="text-base font-bold text-red-700 uppercase tracking-wide">DUE AMOUNT</span>
              <span className="text-base font-bold text-red-700">{formatAmount(sale.dueAmount)}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
