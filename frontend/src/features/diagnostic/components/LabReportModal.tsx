import { useState, useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cn } from '../../../lib/utils'
import { diagTemplateApi } from '../../../services/diagnostic/diagTemplateApi'
import { diagnosticReportApi } from '../../../services/diagnostic/diagnosticReportApi'
import { useDiagnosticMutations } from '../../../hooks/diagnostic/useDiagnostic'
import type { DiagnosticOrder, DiagnosticTemplate } from '../../../types/diagnostic'

interface Props { order: DiagnosticOrder; onClose: () => void }

export function LabReportModal({ order, onClose }: Props) {
  const { saveLabReports, recordResult } = useDiagnosticMutations()
  const { data: allTemplates = [] } = useQuery({ queryKey: ['diagTemplates'], queryFn: diagTemplateApi.getAll })
  const [values, setValues] = useState<Record<string, Record<string, string>>>({}) // lineId -> { ltdId: value }
  const [activeLineId, setActiveLineId] = useState<string | null>(null)

  // Build line → template mapping
  const lineTemplates = useMemo(() => {
    const map = new Map<string, DiagnosticTemplate | null>()
    for (const line of order.lines) {
      const t = allTemplates.find(t => t.chargeId === line.serviceCatalogItemId) ?? null
      map.set(line.id, t)
    }
    return map
  }, [order.lines, allTemplates])

  // Load existing reports
  useEffect(() => {
    const loadReports = async () => {
      const newValues: Record<string, Record<string, string>> = {}
      for (const line of order.lines) {
        try {
          const reports = await diagnosticReportApi.getReportsByOrderLine(line.id)
          const lineVals: Record<string, string> = {}
          for (const r of reports) {
            if (r.labTemplateDetailId && r.value) lineVals[r.labTemplateDetailId] = r.value
          }
          newValues[line.id] = lineVals
        } catch { newValues[line.id] = {} }
      }
      setValues(newValues)
      if (order.lines.length > 0) setActiveLineId(order.lines[0].id)
    }
    loadReports()
  }, [order.lines])

  const updateValue = (lineId: string, ltdId: string, val: string) => {
    setValues(prev => ({ ...prev, [lineId]: { ...(prev[lineId] || {}), [ltdId]: val } }))
  }

  const saveLineReport = (lineId: string) => {
    const template = lineTemplates.get(lineId)
    if (template) {
      saveLabReports.mutate({
        orderLineId: lineId,
        templateId: template.id,
        reports: values[lineId] || {},
      })
    } else {
      const directValue = values[lineId]?.['direct']
      if (directValue !== undefined) {
        recordResult.mutate({
          orderId: order.id,
          cmd: { lineId: lineId, resultValue: directValue }
        })
      }
    }
  }

  // Group templates by department for display
  const groupedLines = useMemo(() => {
    const groups = new Map<string, Array<{ lineId: string; lineName: string; template: DiagnosticTemplate | null }>>()
    for (const line of order.lines) {
      const t = lineTemplates.get(line.id) ?? null
      const dept = t?.department?.name ?? 'Other'
      if (!groups.has(dept)) groups.set(dept, [])
      groups.get(dept)!.push({ lineId: line.id, lineName: line.itemName ?? 'Unknown Test', template: t })
    }
    return groups
  }, [order.lines, lineTemplates])

  const activeLine = order.lines.find(l => l.id === activeLineId)
  const activeTemplate = activeLineId ? lineTemplates.get(activeLineId) : null
  const activeDetails = activeTemplate?.labTemplateDetails
    ? [...activeTemplate.labTemplateDetails].sort((a, b) => a.orderNumber - b.orderNumber)
    : []

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-[90vw] max-w-5xl max-h-[85vh] flex flex-col" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gradient-to-r from-emerald-50 to-white rounded-t-2xl">
          <div>
            <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
              Lab Report Entry
            </h2>
            <p className="text-xs text-gray-500 mt-0.5">Order: {order.sequenceNumber || order.id.slice(0, 8)}</p>
          </div>
          <button onClick={onClose} className="w-8 h-8 rounded-lg bg-gray-100 hover:bg-gray-200 flex items-center justify-center text-gray-500 text-lg transition-colors">&times;</button>
        </div>

        <div className="flex flex-1 overflow-hidden">
          {/* Left: Test navigation */}
          <div className="w-56 border-r border-gray-100 overflow-y-auto bg-gray-50/50">
            {[...groupedLines.entries()].map(([dept, items]) => (
              <div key={dept}>
                <div className="px-3 py-2 text-xs font-bold text-indigo-700 uppercase tracking-wider bg-indigo-50/50 border-b border-indigo-100">
                  {dept}
                </div>
                {items.map(item => (
                  <button key={item.lineId} onClick={() => setActiveLineId(item.lineId)}
                    className={cn('w-full text-left px-3 py-2.5 text-sm border-b border-gray-100 transition-colors',
                      activeLineId === item.lineId ? 'bg-emerald-50 text-emerald-800 font-semibold border-l-3 border-l-emerald-500' : 'text-gray-600 hover:bg-gray-100')}>
                    {item.lineName}
                  </button>
                ))}
              </div>
            ))}
          </div>

          {/* Right: Result entry */}
          <div className="flex-1 overflow-y-auto">
            {!activeLine ? (
              <div className="flex items-center justify-center h-full text-gray-400 text-sm">Select a test from the left panel</div>
            ) : activeDetails.length === 0 ? (
              <div className="p-6">
                <p className="text-sm text-gray-500 mb-3">No lab parameters defined for <strong>{activeLine.itemName}</strong>.</p>
                <p className="text-xs text-gray-400">Direct result entry:</p>
                <input type="text" placeholder="Enter result…"
                  value={values[activeLine.id]?.['direct'] ?? activeLine.resultValue ?? ''}
                  onChange={e => updateValue(activeLine.id, 'direct', e.target.value)}
                  className="mt-2 w-full max-w-md px-3 py-2 border border-gray-200 rounded-lg text-sm" />
              </div>
            ) : (
              <div>
                {/* Table header */}
                <div className="grid grid-cols-12 gap-1 px-4 py-2.5 bg-gray-50 text-xs font-semibold text-gray-500 uppercase tracking-wider border-b border-gray-200 sticky top-0 z-10">
                  <div className="col-span-4">Test Parameter</div>
                  <div className="col-span-3 text-center">Result</div>
                  <div className="col-span-1 text-center">Unit</div>
                  <div className="col-span-3">Normal Range</div>
                  <div className="col-span-1 text-center">Status</div>
                </div>

                {activeDetails.map(ltd => {
                  const val = values[activeLine.id]?.[ltd.id] ?? ''
                  const evaluation = evaluateResult(val, ltd.normalRange)

                  if (ltd.labType === 'HEADER') {
                    return (
                      <div key={ltd.id} className="px-4 py-2 bg-indigo-50/60 text-sm font-bold text-indigo-800 border-b border-indigo-100">
                        {ltd.resultName}
                      </div>
                    )
                  }

                  return (
                    <div key={ltd.id} className="grid grid-cols-12 gap-1 px-4 py-2 border-b border-gray-50 items-center hover:bg-gray-50/50 transition-colors">
                      <div className="col-span-4 text-sm text-gray-700">{ltd.resultName}</div>
                      <div className="col-span-3 text-center">
                        <input type="text" value={val} onChange={e => updateValue(activeLine.id, ltd.id, e.target.value)}
                          placeholder="—"
                          className={cn('w-full max-w-[140px] px-2.5 py-1.5 border rounded-lg text-sm text-center transition-colors',
                            evaluation === 'NORMAL' ? 'border-emerald-300 bg-emerald-50/30' :
                              evaluation === 'HIGH' || evaluation === 'LOW' ? 'border-amber-300 bg-amber-50/30' :
                                evaluation === 'CRITICAL' ? 'border-red-300 bg-red-50/30' :
                                  'border-gray-200')} />
                      </div>
                      <div className="col-span-1 text-center text-xs text-gray-500">{ltd.unit || ''}</div>
                      <div className="col-span-3 text-xs text-gray-500 whitespace-pre-wrap">{ltd.normalRange || ''}</div>
                      <div className="col-span-1 text-center">
                        {val && (
                          <span className={cn('inline-block px-2 py-0.5 rounded-full text-xs font-semibold',
                            evaluation === 'NORMAL' ? 'bg-emerald-100 text-emerald-700' :
                              evaluation === 'HIGH' ? 'bg-amber-100 text-amber-700' :
                                evaluation === 'LOW' ? 'bg-amber-100 text-amber-700' :
                                  evaluation === 'CRITICAL' ? 'bg-red-100 text-red-700' :
                                    'bg-gray-100 text-gray-500')}>
                            {evaluation || '—'}
                          </span>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50/30 rounded-b-2xl">
          <button onClick={onClose}
            className="px-5 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors">
            Close
          </button>
          {activeLineId && (
            <button onClick={() => saveLineReport(activeLineId)} disabled={saveLabReports.isPending}
              className="px-6 py-2 text-sm font-bold text-white bg-gradient-to-r from-emerald-600 to-teal-600 rounded-xl hover:from-emerald-700 hover:to-teal-700 disabled:opacity-50 transition-all shadow-md">
              {saveLabReports.isPending ? 'Saving…' : 'Save Report'}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

/** Simple auto-evaluation: parse numeric value against range like "12-17" or "4000-11000" */
function evaluateResult(value: string, range: string | null): string {
  if (!value || !range) return ''
  const num = parseFloat(value)
  if (isNaN(num)) return ''
  // Parse range patterns like "12-17", "4000 - 11000", "< 200", "> 100"
  const dashMatch = range.match(/([\d.]+)\s*[-–]\s*([\d.]+)/)
  if (dashMatch) {
    const low = parseFloat(dashMatch[1])
    const high = parseFloat(dashMatch[2])
    if (num < low) return num < low * 0.7 ? 'CRITICAL' : 'LOW'
    if (num > high) return num > high * 1.3 ? 'CRITICAL' : 'HIGH'
    return 'NORMAL'
  }
  return ''
}
