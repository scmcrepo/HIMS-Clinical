/**
 * usePrint.ts — Universal Print Hook
 *
 * Implements the VitalSoft print pipeline from the spec:
 *  - HTML mode: opens blank window, injects @page CSS, writes template HTML, calls window.print()
 *  - DOT_MATRIX mode: connects QZ Tray WebSocket, dispatches raw ESC/POS pages
 *
 * Usage:
 *   const { print, printing } = usePrint()
 *   await print('BILL',     { id: billId })
 *   await print('OP_RECEIPT', { id: billId, collectionId: paymentId })
 *   await print('LAB',     { id: orderId })
 *   await print('SALES',   { id: saleId })
 */
import { useState, useCallback } from 'react'
import api from '../../lib/axios'

export interface PrintParams {
  [key: string]: string
}

export interface PrintResponse {
  printMode: 'HTML' | 'DOT_MATRIX'
  printData?: string
  rawPages?: string[]
  width?: string
  height?: string
  marginTop?: string
  marginBottom?: string
  marginLeft?: string
  marginRight?: string
  defaultPrinter?: string
  serversidePrinters?: string[]
}

export function usePrint() {
  const [printing, setPrinting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const print = useCallback(async (
    templateType: string,
    params: PrintParams = {},
    printerNameOverride?: string,
  ) => {
    setPrinting(true)
    setError(null)
    try {
      const query = new URLSearchParams({ templateType, ...params })
      if (printerNameOverride) query.set('printerName', printerNameOverride)

      const res = await api.get<{ data: PrintResponse }>(`/print?${query.toString()}`)
      const template = res.data.data

      if (!template) throw new Error('No print data received from server')

      if (template.printMode === 'HTML') {
        await printHTML(template)
      } else if (template.printMode === 'DOT_MATRIX') {
        await printDotMatrix(template, printerNameOverride)
      }
    } catch (err: any) {
      const msg = err?.response?.data?.message ?? err?.message ?? 'Print failed. Check if a default template exists for this document type.'
      setError(msg)
      throw new Error(msg)
    } finally {
      setPrinting(false)
    }
  }, [])

  return { print, printing, error }
}

// ── HTML Print Mode ─────────────────────────────────────────────────────────
// Mirrors the AngularJS print.js directive from the spec:
//   1. Open blank window out of view
//   2. Inject @page CSS with template margins/dimensions
//   3. Write compiled HTML
//   4. Adjust header/footer space heights
//   5. Call window.print(), then close

async function printHTML(template: PrintResponse): Promise<void> {
  const {
    width       = '210mm',
    height      = '297mm',
    marginTop   = '10mm',
    marginBottom = '10mm',
    marginLeft  = '10mm',
    marginRight = '10mm',
  } = template

  // Create a hidden iframe for print delivery
  const iframe = document.createElement('iframe')
  iframe.style.position = 'fixed'
  iframe.style.right = '0'
  iframe.style.bottom = '0'
  iframe.style.width = '0'
  iframe.style.height = '0'
  iframe.style.border = '0'
  iframe.style.zIndex = '-9999'
  document.body.appendChild(iframe)

  const pw = iframe.contentWindow
  if (!pw) {
    document.body.removeChild(iframe)
    throw new Error('Could not initialize print delivery channel.')
  }

  const pageStyle = `
    <style>
      * { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }
      @media print {
        @page {
          size: ${width} ${height};
          margin-top: ${marginTop};
          margin-bottom: ${marginBottom};
          margin-left: ${marginLeft};
          margin-right: ${marginRight};
        }
        body { margin: 0; }
      }
    </style>
  `

  pw.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>Print</title>${pageStyle}</head><body>${template.printData ?? ''}</body></html>`)
  pw.document.close()

  await new Promise<void>((resolve) => {
    const trigger = () => {
      // Spec: adjust header/footer space heights before printing
      try {
        const body  = pw.document.body
        const header = body.querySelector('.header') as HTMLElement | null
        const footer = body.querySelector('.footer') as HTMLElement | null
        const hSpace = body.querySelector('#headerSpace') as HTMLElement | null
        const fSpace = body.querySelector('#footerSpace') as HTMLElement | null
        if (header && hSpace) hSpace.style.height = header.offsetHeight + 'px'
        if (footer && fSpace) fSpace.style.height = footer.offsetHeight + 'px'
      } catch (_) { /* non-critical */ }

      setTimeout(() => {
        pw.focus()
        pw.print()
        setTimeout(() => {
          try {
            document.body.removeChild(iframe)
          } catch (_) {}
          resolve()
        }, 1000)
      }, 250)
    }

    if (pw.document.readyState === 'complete') {
      trigger()
    } else {
      iframe.onload = trigger
    }
  })
}

// ── DOT_MATRIX / QZ Tray Mode ───────────────────────────────────────────────
// Spec: connects to QZ Tray WebSocket, dispatches rawPages to the printer
async function printDotMatrix(template: PrintResponse, printerNameOverride?: string): Promise<void> {
  const printerName = printerNameOverride ?? template.defaultPrinter ?? 'Default'
  const pages = template.rawPages ?? []
  if (pages.length === 0) throw new Error('No DOT_MATRIX data received from server.')

  const qz = await loadQzTray()

  try {
    if (!qz.websocket.isActive()) {
      await qz.websocket.connect()
    }
    const config = qz.configs.create(printerName)
    const printData = pages.map((page: string) => ({ type: 'raw', format: 'plain', data: page }))
    await qz.print(config, printData)
  } catch (err: any) {
    throw new Error(`QZ Tray error: ${err?.message ?? 'Unknown error'}. Ensure QZ Tray is running on this computer.`)
  }
}

// Lazy-load QZ Tray client library from CDN
async function loadQzTray(): Promise<any> {
  if ((window as any).qz) return (window as any).qz

  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src   = 'https://cdn.jsdelivr.net/npm/qz-tray@2.2.4/qz-tray.js'
    script.onload  = () => (window as any).qz ? resolve((window as any).qz) : reject(new Error('QZ Tray not available after load'))
    script.onerror = () => reject(new Error('Failed to load QZ Tray. Download from https://qz.io'))
    document.head.appendChild(script)
  })
}
