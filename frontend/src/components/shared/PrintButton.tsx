/**
 * PrintButton.tsx
 *
 * Drop-in print trigger. Place anywhere in the app:
 *
 *   <PrintButton templateType="BILL"    params={{ id: billId }} />
 *   <PrintButton templateType="OP_RECEIPT" params={{ id: billId }} variant="outline" label="Print Receipt" />
 *   <PrintButton templateType="SALES"   params={{ id: saleId }} variant="icon" />
 *   <PrintButton templateType="LAB"     params={{ id: orderId }} variant="outline" label="Print Report" />
 *
 * Spec compliance:
 *  - Calls GET /print?templateType=X&...params
 *  - HTML mode → window.open + @page CSS + window.print()
 *  - DOT_MATRIX mode → QZ Tray WebSocket dispatch
 */
import { useState } from 'react'
import { usePrint } from '../../hooks/print/usePrint'
import { toast } from '../../hooks/useToast'

export interface PrintButtonProps {
  templateType: string
  params?: Record<string, string>
  label?: string
  /** default | outline | ghost | icon */
  variant?: 'default' | 'outline' | 'ghost' | 'icon'
  className?: string
  disabled?: boolean
  onPrinted?: () => void
}

const PrinterSVG = () => (
  <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
  </svg>
)

const SpinSVG = () => (
  <svg className="w-4 h-4 animate-spin shrink-0" fill="none" viewBox="0 0 24 24">
    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
  </svg>
)

export function PrintButton({
  templateType,
  params = {},
  label = 'Print',
  variant = 'default',
  className = '',
  disabled = false,
  onPrinted,
}: PrintButtonProps) {
  const { print, printing } = usePrint()
  const [showPrinterModal, setShowPrinterModal] = useState(false)
  const [printerName, setPrinterName]           = useState('')

  const handleClick = async () => {
    try {
      await print(templateType, params)
      onPrinted?.()
    } catch (err: any) {
      toast({ title: 'Print Error', description: err?.message ?? 'Could not print.', variant: 'destructive' })
    }
  }

  const handlePrinterConfirm = async () => {
    setShowPrinterModal(false)
    try {
      await print(templateType, params, printerName || undefined)
      onPrinted?.()
    } catch (err: any) {
      toast({ title: 'Print Error', description: err?.message ?? 'Could not print.', variant: 'destructive' })
    }
  }

  const BASE = 'inline-flex items-center justify-center gap-1.5 font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-500 disabled:opacity-50 disabled:pointer-events-none'

  const VARIANTS: Record<string, string> = {
    default: `${BASE} px-3 py-1.5 text-sm bg-neutral-600 text-white rounded-lg hover:bg-neutral-700 shadow-sm`,
    outline: `${BASE} px-3 py-1.5 text-sm bg-white border border-gray-200 text-gray-700 rounded-lg hover:bg-gray-50`,
    ghost:   `${BASE} px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 hover:text-gray-800 rounded-lg`,
    icon:    `${BASE} p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-700 rounded border border-gray-200`,
  }

  return (
    <>
      <button
        type="button"
        onClick={handleClick}
        disabled={printing || disabled}
        title={variant === 'icon' ? label : undefined}
        className={`${VARIANTS[variant] ?? VARIANTS.default} ${className}`}
      >
        {printing ? <SpinSVG /> : <PrinterSVG />}
        {variant !== 'icon' && (printing ? 'Printing…' : label)}
      </button>

      {/* Optional: printer selection modal for DOT_MATRIX with multiple printers */}
      {showPrinterModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 backdrop-blur-sm">
          <div className="bg-white rounded-xl shadow-2xl p-6 w-80 space-y-4">
            <h3 className="text-base font-bold text-gray-900">Select Printer</h3>
            <input
              type="text"
              value={printerName}
              onChange={e => setPrinterName(e.target.value)}
              placeholder="Enter printer name…"
              className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500"
            />
            <div className="flex gap-3">
              <button type="button" onClick={() => setShowPrinterModal(false)} className="flex-1 px-4 py-2 border border-gray-200 text-sm text-gray-600 rounded-lg hover:bg-gray-50">Cancel</button>
              <button type="button" onClick={handlePrinterConfirm} className="flex-1 px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700">Print</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default PrintButton
