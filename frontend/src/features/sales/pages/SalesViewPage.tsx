import { useQuery, useQueryClient } from '@tanstack/react-query'
import { PrintButton } from '../../../components/shared/PrintButton'
import { useParams, useNavigate } from 'react-router-dom'
import { salesApi } from '../../../services/sales/salesApi'
import { inventoryApi } from '../../../services/inventory/inventoryApi'
import { patientApi } from '../../../services/patient/patientApi'
import { formatDate } from '../../../lib/dateUtils'
import { useEffect, useState, useMemo } from 'react'
import { format, parseISO } from 'date-fns'
import { cn } from '../../../lib/utils'
import { toast } from '../../../hooks/useToast'
import { salesReturnApi } from '../../../services/sales/salesReturnApi'
import { Modal } from '../../../components/ui/Modal'
import { RotateCcw } from 'lucide-react'

export default function SalesViewPage() {
  const { saleId } = useParams<{ saleId: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()

  const { data: sale, isLoading } = useQuery({
    queryKey: ['sales', saleId],
    queryFn: () => salesApi.getById(saleId!),
    enabled: !!saleId,
  })

  // Return states & query
  const [isReturnOpen, setIsReturnOpen] = useState(false)
  const [returnRows, setReturnRows] = useState<Record<string, number>>({})
  const [returning, setReturning] = useState(false)
  const [returnHistoryOpen, setReturnHistoryOpen] = useState(false)

  const { data: existingReturns } = useQuery({
    queryKey: ['salesReturns', 'sale', saleId],
    queryFn: () => salesReturnApi.getBySale(saleId!),
    enabled: !!saleId,
  })

  const alreadyReturnedQtyMap = useMemo(() => {
    const map: Record<string, number> = {}
    if (existingReturns) {
      existingReturns.forEach(ret => {
        ret.lines.forEach(line => {
          map[line.inventoryBatchId] = (map[line.inventoryBatchId] || 0) + line.quantity
        })
      })
    }
    return map
  }, [existingReturns])

  const grossSaleAmount = useMemo(() => {
    if (!sale) return 0
    return sale.lines.reduce((sum: number, l: any) => sum + l.amount, 0)
  }, [sale])

  const discountRatio = useMemo(() => {
    if (!sale || grossSaleAmount === 0) return 1
    return sale.totalAmount / grossSaleAmount
  }, [sale, grossSaleAmount])

  const isFullyReturned = useMemo(() => {
    if (!sale) return true
    return sale.lines.every((line: any) => {
      const returnedQty = alreadyReturnedQtyMap[line.inventoryBatchId] || 0
      return (line.quantity - returnedQty) <= 0
    })
  }, [sale, alreadyReturnedQtyMap])

  const isPartiallyReturned = useMemo(() => {
    if (!sale || isFullyReturned) return false
    return Object.values(alreadyReturnedQtyMap).some(qty => qty > 0)
  }, [sale, alreadyReturnedQtyMap, isFullyReturned])

  const handleQtyChange = (batchId: string, valStr: string, availableQty: number) => {
    if (valStr === '') {
      setReturnRows(prev => ({ ...prev, [batchId]: 0 }))
      return
    }
    let val = parseInt(valStr)
    if (isNaN(val) || val < 0) {
      val = 0
    }
    if (val > availableQty) {
      val = availableQty
    }
    setReturnRows(prev => ({ ...prev, [batchId]: val }))
  }

  const handleSubmitReturn = async () => {
    if (!sale) return
    const linesToReturn = Object.entries(returnRows)
      .map(([inventoryBatchId, quantity]) => ({ inventoryBatchId, quantity }))
      .filter(l => l.quantity > 0)

    if (linesToReturn.length === 0) {
      toast({ title: 'Validation Error', description: 'Please enter a return quantity for at least one item.', variant: 'destructive' })
      return
    }

    try {
      setReturning(true)
      await salesReturnApi.create({
        saleId: sale.id,
        lines: linesToReturn,
      })
      toast({ title: 'Success', description: 'Return processed successfully.' })
      setIsReturnOpen(false)
      setReturnRows({})
      qc.invalidateQueries({ queryKey: ['sales'] })
      qc.invalidateQueries({ queryKey: ['salesReturns'] })
    } catch (e: any) {
      toast({
        title: 'Error',
        description: e?.response?.data?.message || 'Failed to process return.',
        variant: 'destructive',
      })
    } finally {
      setReturning(false)
    }
  }

  // We need to fetch patient details to get consultant, if applicable.
  // We also need batch details to display item names and expiry dates.
  const [batches, setBatches] = useState<Record<string, any>>({})
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
      // Fetch batch details asynchronously
      sale.lines.forEach(async (line) => {
        if (line.inventoryBatchId) {
          try {
            const batch = await inventoryApi.getBatch(line.inventoryBatchId)
            setBatches(prev => ({ ...prev, [line.inventoryBatchId]: batch }))
          } catch (e) {
            // ignore
          }
        }
      })

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

  const [billInfoOpen, setBillInfoOpen] = useState(true)
  const [paymentHistoryOpen, setPaymentHistoryOpen] = useState(true)

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
      amountPaise = Number(amt.toFixed(2))
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

  const formatAmount = (amt: number) => amt.toFixed(2)

  return (
    <div className="bg-white border border-gray-200 rounded-xl w-full max-w-6xl mx-auto shadow-sm">
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
          {!isFullyReturned && (
            <button
              onClick={() => {
                setReturnRows({})
                setIsReturnOpen(true)
              }}
              className="flex items-center gap-1.5 bg-[#4b4b4b] hover:bg-[#3d3d3d] text-white font-bold px-3 py-1.5 rounded-lg shadow-sm hover:shadow transition-all duration-200 text-xs uppercase tracking-wider"
              title="Return Items"
            >
              <RotateCcw size={14} /> Return
            </button>
          )}
        </div>
      </div>

      <div className="p-4 flex items-center justify-between">
        <h3 className="text-lg font-medium text-gray-700 uppercase tracking-wide">
          SALE NO : <span className="text-red-500 font-bold">{sale.sequenceNumber}</span>
        </h3>
        {isFullyReturned ? (
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold bg-[#4b4b4b] text-white uppercase tracking-wider">
            Fully Returned
          </span>
        ) : isPartiallyReturned ? (
          <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-bold bg-gray-100 text-gray-800 border border-gray-200 uppercase tracking-wider">
            Partially Returned
          </span>
        ) : null}
      </div>

      {/* Items Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-sm text-left border-t border-b border-gray-200">
          <thead>
            <tr className="bg-gray-50/50 text-gray-500 text-xs font-bold uppercase tracking-wider">
              <th className="px-4 py-3 w-16 text-left">S.NO.</th>
              <th className="px-4 py-3 min-w-[200px] text-left">ITEM</th>
              <th className="px-4 py-3 w-32 text-left">BATCH NO</th>
              <th className="px-4 py-3 w-36 text-left">EXP DATE</th>
              <th className="px-4 py-3 w-24 text-right">QTY</th>
              <th className="px-4 py-3 w-44 text-right">MRP</th>
              <th className="px-4 py-3 w-28 text-right">DISCOUNT</th>
              <th className="px-4 py-3 w-40 text-right">SUB TOTAL</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {sale.lines.map((line, idx) => {
              const b = batches[line.inventoryBatchId]
              // const taxRate = b?.taxRate ?? 0
              // Tax extracted from tax-inclusive purchase price (matching GRN)
              // const purchaseAmount = line.quantity * (b?.purchaseRate ?? 0)
              // const taxAmount = taxRate > 0 ? purchaseAmount * taxRate / (100 + taxRate) : 0
              // Subtotal is qty × unitRate (without tax)
              const subTotal = line.quantity * line.unitRate
              return (
                <tr key={line.id} className="text-gray-700">
                  <td className="px-4 py-3 w-16 text-left">{idx + 1}</td>
                  <td className="px-4 py-3 min-w-[200px] text-left font-medium text-gray-900 uppercase">
                    {line.itemName || b?.itemName || 'Loading...'}
                  </td>
                  <td className="px-4 py-3 w-32 text-left">{b?.batchNumber || 'Loading...'}</td>
                  <td className="px-4 py-3 w-36 text-left">{b?.expiryDate ? formatDate(b.expiryDate) : 'Loading...'}</td>
                  <td className="px-4 py-3 w-24 text-right">{line.quantity}</td>
                  <td className="px-4 py-3 w-44 text-right">{formatAmount(line.unitRate)}</td>
                  <td className="px-4 py-3 w-28 text-right text-red-600 font-medium">
                    {line.discountAmount > 0 ? `-${formatAmount(line.discountAmount)}` : '—'}
                  </td>
                  <td className="px-4 py-3 w-40 text-right font-semibold text-gray-900">{formatAmount(subTotal - (line.discountAmount || 0))}</td>
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
              className="text-sm font-medium text-gray-900 uppercase flex items-center gap-1 cursor-pointer select-none hover:text-neutral-600 transition-colors"
            >
              BILL INFORMATION <span className="text-[10px]">{billInfoOpen ? '▲' : '▼'}</span>
            </h4>
            {billInfoOpen && (
              <div className="mt-4 flex gap-24 text-sm text-gray-700">
                <p>Consultant : <span className="font-bold text-gray-900 uppercase">{consultantName || '-'}</span></p>
                <p>Patient : <span className="font-bold text-gray-900 uppercase">{sale.patientName || 'WALK-IN'}</span></p>
              </div>
            )}
          </div>
          <div className="p-4">
            <h4 
              onClick={() => setPaymentHistoryOpen(!paymentHistoryOpen)}
              className="text-sm font-medium text-gray-900 uppercase flex items-center gap-1 cursor-pointer select-none hover:text-neutral-600 transition-colors"
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
                  sale.payments.map((p, _) => (
                    <div key={p.id} className="grid grid-cols-4 text-xs font-semibold text-gray-700 py-2 border-t border-gray-100 items-center">
                      <div className="text-gray-900 font-bold uppercase">
                        {sale.sequenceNumber?.replace('SL-', '').replace(/^0+/, '') || '56'}
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
                    <div className="text-gray-900 font-bold uppercase">sddss-{sale.sequenceNumber?.replace('SL-', '').replace(/^0+/, '') || '56'}</div>
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
                    <div className="flex-1 flex flex-wrap gap-4 items-end">
                      {paymentType === 'partial_pay' && (
                        <div className="w-full sm:w-48 shrink-0">
                          <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest whitespace-nowrap">Amount to Collect (₹)</label>
                          <input
                            type="number"
                            min={0.01}
                            step="any"
                            max={sale.dueAmount}
                            value={partialAmount}
                            onKeyDown={(e) => {
                              if (e.key === '-' || e.key === 'e' || e.key === '+') {
                                e.preventDefault()
                              }
                            }}
                            onChange={(e) => {
                              const val = e.target.value
                              if (val === '') {
                                setPartialAmount('')
                                return
                              }
                              const numVal = parseFloat(val)
                              if (isNaN(numVal)) return
                              const maxVal = sale.dueAmount
                              if (numVal > maxVal) {
                                setPartialAmount(maxVal.toString())
                              } else {
                                setPartialAmount(val)
                              }
                            }}
                            placeholder="0.00"
                            className="w-full bg-white border border-gray-300 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500 h-10 shadow-sm"
                          />
                        </div>
                      )}

                      <div className="w-full sm:w-40 shrink-0">
                        <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Mode</label>
                        <select
                          value={paymentMode}
                          onChange={(e) => {
                            setPaymentMode(e.target.value)
                            setBankName('')
                            setCardType('')
                            setCardNumber('')
                          }}
                          className="w-full bg-white border border-gray-300 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500 h-10 shadow-sm"
                        >
                          <option value="Cash">Cash</option>
                          <option value="Card">Card</option>
                          <option value="UPI">UPI</option>
                        </select>
                      </div>

                      {paymentMode === 'Card' && (
                        <>
                          <div className="w-full sm:w-44 shrink-0">
                            <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Card Type</label>
                            <select
                              value={cardType}
                              onChange={(e) => setCardType(e.target.value)}
                              className="w-full bg-white border border-gray-300 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500 h-10 shadow-sm"
                            >
                              <option value="">Select Card Type</option>
                              <option value="Credit">Credit</option>
                              <option value="Debit">Debit</option>
                            </select>
                          </div>
                          <div className="w-full sm:w-48 shrink-0">
                            <label className="block text-[10px] uppercase font-bold text-gray-400 mb-1.5 tracking-widest">Card No</label>
                            <input
                              type="text"
                              value={cardNumber}
                              onChange={(e) => setCardNumber(e.target.value)}
                              placeholder="Card No"
                              className="w-full bg-white border border-gray-300 rounded px-2.5 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500 h-10 shadow-sm"
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
                        className="w-full sm:w-auto px-6 h-10 bg-neutral-600 hover:bg-neutral-700 active:bg-neutral-800 text-white text-xs font-bold uppercase tracking-wider rounded-lg shadow-md hover:shadow-lg transition-all duration-200 disabled:opacity-50 flex items-center justify-center"
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
          {existingReturns && existingReturns.length > 0 && (
            <div className="p-4 border-t border-gray-200">
              <h4 
                onClick={() => setReturnHistoryOpen(!returnHistoryOpen)}
                className="text-sm font-medium text-gray-900 uppercase flex items-center gap-1 cursor-pointer select-none hover:text-neutral-600 transition-colors"
              >
                RETURN HISTORY <span className="text-[10px]">{returnHistoryOpen ? '▲' : '▼'}</span>
              </h4>
              {returnHistoryOpen && (
                <div className="mt-4 space-y-3">
                  {existingReturns.map((ret) => (
                    <div key={ret.id} className="border border-gray-200 bg-gray-50/30 rounded-lg p-3 space-y-2 text-xs">
                      <div className="flex justify-between font-bold text-gray-900">
                        <span className="uppercase text-gray-900">{ret.sequenceNumber}</span>
                        <span>{ret.createdAt ? format(parseISO(ret.createdAt), 'MMM d, yyyy h:mm:ss a') : (ret.returnDate ? formatDate(ret.returnDate) : '—')}</span>
                      </div>
                      <div className="divide-y divide-gray-100">
                        {ret.lines.map((line, lIdx) => {
                          const detail = sale.lines.find(ld => ld.inventoryBatchId === line.inventoryBatchId)
                          const b = batches[line.inventoryBatchId]
                          return (
                            <div key={lIdx} className="flex justify-between py-1.5 text-gray-700">
                              <span>{detail?.itemName || b?.itemName || 'Loading...'} (Qty: {line.quantity})</span>
                              <span className="font-semibold text-gray-900">₹{formatAmount(line.returnAmount ?? 0)}</span>
                            </div>
                          )
                        })}
                      </div>
                      <div className="flex justify-between font-bold pt-1 border-t border-gray-200 text-gray-900">
                        <span>TOTAL RETURN</span>
                        <span>₹{formatAmount(ret.totalReturnAmount)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
        <div className="col-span-1 bg-gray-50/30 p-0 flex flex-col divide-y divide-gray-200">
          {(() => {
            const totalItemDiscount = sale.lines.reduce((sum: number, l: any) => sum + (l.discountAmount || 0), 0)
            const overallDiscount = sale.discountAmount || 0
            const totalDiscount = totalItemDiscount + overallDiscount
            
            if (totalDiscount > 0) {
              return (
                <>
                  <div className="flex justify-between items-center px-6 py-3">
                    <span className="text-sm font-semibold text-gray-500">Gross Total</span>
                    <span className="text-sm font-medium text-gray-700">{formatAmount(sale.totalAmount + totalDiscount)}</span>
                  </div>
                  {totalItemDiscount > 0 && (
                    <div className="flex justify-between items-center px-6 py-2">
                      <span className="text-sm font-semibold text-gray-500">Item Discounts</span>
                      <span className="text-sm font-medium text-gray-700">-{formatAmount(totalItemDiscount)}</span>
                    </div>
                  )}
                  {overallDiscount > 0 && (
                    <div className="flex justify-between items-center px-6 py-2">
                      <span className="text-sm font-semibold text-gray-500">Total Discount</span>
                      <span className="text-sm font-medium text-gray-700">-{formatAmount(overallDiscount)}</span>
                    </div>
                  )}
                </>
              )
            }
            return null
          })()}
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

      {/* Return Modal */}
      <Modal
        isOpen={isReturnOpen}
        onClose={() => setIsReturnOpen(false)}
        title={`Return Items - ${sale.sequenceNumber}`}
        size="max"
      >
        <div className="flex flex-col max-h-[85vh]">
          {/* Modal Header */}
          <div className="p-6 border-b border-gray-100 shrink-0">
            <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2 uppercase tracking-wide">
              <RotateCcw className="text-gray-900" size={20} />
              Return Items - {sale.sequenceNumber}
            </h3>
            {/* <p className="text-xs text-gray-500 mt-1">Select the quantity of each item you wish to return. The return amount will be calculated proportionally based on any applied discount.</p> */}
          </div>

          {/* Modal Content */}
          <div className="flex-1 overflow-y-auto p-6">
            <table className="w-full text-sm text-left border border-gray-200 rounded-lg overflow-hidden">
              <thead className="bg-gray-50 text-gray-500 text-xs font-bold uppercase tracking-wider">
                <tr>
                  <th className="px-4 py-3 text-left min-w-[200px]">Item</th>
                  <th className="px-4 py-3 text-left">Batch</th>
                  <th className="px-4 py-3 text-right">Purchased</th>
                  <th className="px-4 py-3 text-right">Returned</th>
                  <th className="px-4 py-3 text-right">Available</th>
                  <th className="px-4 py-3 text-right">Price (Net)</th>
                  <th className="px-4 py-3 text-right w-28">Return Qty</th>
                  <th className="px-4 py-3 text-right">Return Amt</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-gray-700">
                {sale.lines.map((line) => {
                  const b = batches[line.inventoryBatchId]
                  const returnedQty = alreadyReturnedQtyMap[line.inventoryBatchId] || 0
                  const availableQty = Math.max(0, line.quantity - returnedQty)
                  const returnQty = returnRows[line.inventoryBatchId] || 0
                  const netUnitRate = (line.amount / line.quantity) * discountRatio
                  const returnSubtotal = Math.round(returnQty * netUnitRate)

                  return (
                    <tr key={line.id} className={cn("hover:bg-gray-50/50 transition-colors", availableQty === 0 && "opacity-50")}>
                      <td className="px-4 py-3 font-semibold text-gray-900 uppercase whitespace-nowrap min-w-[200px]">
                        {line.itemName || b?.itemName || 'Loading...'}
                      </td>
                      <td className="px-4 py-3 text-xs font-mono">{b?.batchNumber || 'Loading...'}</td>
                      <td className="px-4 py-3 text-right font-medium">{line.quantity}</td>
                      <td className="px-4 py-3 text-right text-gray-400 font-medium">{returnedQty}</td>
                      <td className="px-4 py-3 text-right text-gray-600 font-semibold">{availableQty}</td>
                      <td className="px-4 py-3 text-right font-medium">₹{formatAmount(netUnitRate)}</td>
                      <td className="px-4 py-3 text-right">
                        <input
                          type="number"
                          min="0"
                          max={availableQty}
                          value={returnQty || ''}
                          placeholder="0"
                          disabled={availableQty <= 0}
                          onChange={(e) => handleQtyChange(line.inventoryBatchId, e.target.value, availableQty)}
                          className="w-16 px-2 py-1 border border-gray-300 rounded text-right text-xs focus:outline-none focus:ring-1 focus:ring-gray-800 disabled:bg-gray-50 disabled:cursor-not-allowed font-bold"
                        />
                      </td>
                      <td className="px-4 py-3 text-right font-bold text-gray-900 tabular-nums">
                        {returnQty > 0 ? `₹${formatAmount(returnSubtotal)}` : '—'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* Modal Footer */}
          <div className="p-6 border-t border-gray-100 bg-gray-50/50 flex flex-col items-end gap-4 shrink-0">
            <div className="flex items-baseline gap-2">
              <span className="text-xs text-gray-500 font-bold uppercase tracking-wider">Total Return :</span>
              <span className="text-xl font-extrabold text-gray-900 tabular-nums">
                ₹{formatAmount(
                  sale.lines.reduce((sum, line) => {
                    const returnQty = returnRows[line.inventoryBatchId] || 0
                    const netUnitRate = (line.amount / line.quantity) * discountRatio
                    return sum + Math.round(returnQty * netUnitRate)
                  }, 0)
                )}
              </span>
            </div>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setIsReturnOpen(false)}
                className="px-4 py-2 border border-gray-200 text-gray-600 hover:bg-gray-100 font-bold text-xs uppercase tracking-wider rounded-lg transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleSubmitReturn}
                disabled={returning || sale.lines.reduce((sum, l) => sum + (returnRows[l.inventoryBatchId] || 0), 0) === 0}
                className="px-6 py-2 bg-[#4b4b4b] hover:bg-[#3d3d3d] disabled:bg-gray-300 text-white font-bold text-xs uppercase tracking-wider rounded-lg transition-colors shadow-sm"
              >
                {returning ? 'Processing...' : 'Submit Return'}
              </button>
            </div>
          </div>
        </div>
      </Modal>
    </div>
  )
}
