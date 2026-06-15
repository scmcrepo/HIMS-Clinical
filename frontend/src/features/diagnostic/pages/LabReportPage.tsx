import { useState, useEffect, useMemo } from 'react'
import { PrintButton } from '../../../components/shared/PrintButton'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { cn } from '../../../lib/utils'
import { diagTemplateApi } from '../../../services/diagnostic/diagTemplateApi'
import { diagnosticReportApi } from '../../../services/diagnostic/diagnosticReportApi'
import { diagnosticApi } from '../../../services/diagnostic/diagnosticApi'
import { useSpecimenCollections } from '../../../hooks/diagnostic/useDiagnostic'
import type { DiagnosticTemplate } from '../../../types/diagnostic'
import BackButton from '../../../components/shared/BackButton'
import { toast } from '../../../hooks/useToast'

/*
function evaluateResult(value: string, range: string | null): string {
  if (!value || !range) return ''
  const num = parseFloat(value)
  if (isNaN(num)) return ''
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
*/

export default function LabReportPage() {
  const { orderId } = useParams<{ orderId: string }>()
  const qc = useQueryClient()

  const { data: order, isLoading: orderLoading } = useQuery({
    queryKey: ['diagnosticOrder', orderId],
    queryFn: () => diagnosticApi.getById(orderId!),
    enabled: !!orderId,
  })

  const { data: collections = [] } = useSpecimenCollections(orderId!)
  const { data: allTemplates = [] } = useQuery({ queryKey: ['diagTemplates'], queryFn: diagTemplateApi.getAll })
  const [values, setValues] = useState<Record<string, Record<string, string>>>({})
  const [isSavingAll, setIsSavingAll] = useState(false)
  const [hasExistingReports, setHasExistingReports] = useState(false)

  const lineTemplates = useMemo(() => {
    const map = new Map<string, DiagnosticTemplate | null>()
    if (!order) return map
    for (const line of order.lines) {
      const t = allTemplates.find(t => t.chargeId === line.serviceCatalogItemId) ?? null
      map.set(line.id, t)
    }
    return map
  }, [order?.lines, allTemplates])

  useEffect(() => {
    if (!order) return
    const loadReports = async () => {
      const newValues: Record<string, Record<string, string>> = {}
      let existing = false
      for (const line of order.lines) {
        try {
          const reports = await diagnosticReportApi.getReportsByOrderLine(line.id)
          if (reports.length > 0) existing = true
          const lineVals: Record<string, string> = {}
          for (const r of reports) {
            if (r.labTemplateDetailId && r.value) lineVals[r.labTemplateDetailId] = r.value
          }
          newValues[line.id] = lineVals
        } catch { newValues[line.id] = {} }
      }
      if (order.lines.some((l: any) => l.resultValue)) existing = true
      setValues(newValues)
      setHasExistingReports(existing)
    }
    loadReports()
  }, [order])

  const getCollectionForLine = (line: any) => {
    const exactMatch = collections.find(c => c.orderLineId === line.id)
    if (exactMatch) return exactMatch
    return collections.find(c => c.specimenId === line.specimenId && !c.orderLineId)
  }

  const groupedDepartments = useMemo(() => {
    if (!order) return []
    const groups = new Map<string, { dept: any; lines: any[] }>()
    for (const line of order.lines) {
      const t = lineTemplates.get(line.id)
      const dept = t?.department || { id: 'other', name: 'Other', displayOrder: 999 }
      const key = dept.id || 'other'
      if (!groups.has(key)) {
        groups.set(key, { dept, lines: [] })
      }
      groups.get(key)!.lines.push(line)
    }
    return [...groups.values()].sort((a, b) => a.dept.displayOrder - b.dept.displayOrder)
  }, [order?.lines, lineTemplates])

  const updateValue = (lineId: string, ltdId: string, val: string) => {
    setValues(prev => ({ ...prev, [lineId]: { ...(prev[lineId] || {}), [ltdId]: val } }))
  }

  const saveAllReports = async () => {
    if (!order) return
    setIsSavingAll(true)
    try {
      for (const line of order.lines) {
        const template = lineTemplates.get(line.id)
        const reports = values[line.id] || {}
        if (template) {
          await diagnosticReportApi.saveLabReports(line.id, template.id, reports)
        } else {
          const directValue = reports['direct']
          if (directValue !== undefined) {
            await diagnosticApi.recordResult(order.id, { lineId: line.id, resultValue: directValue })
          }
        }
      }
      setHasExistingReports(true)
      toast({ title: 'All reports saved successfully', variant: 'success' })
      qc.invalidateQueries({ queryKey: ['diagReports'] })
      qc.invalidateQueries({ queryKey: ['diagnosticOrder', orderId] })
      qc.invalidateQueries({ queryKey: ['diagnostics'] })
    } catch (e: any) {
      toast({ title: 'Error saving reports', description: e.message, variant: 'destructive' })
    } finally {
      setIsSavingAll(false)
    }
  }

  if (orderLoading) {
    return <div className="flex items-center justify-center h-64 text-gray-500">Loading…</div>
  }

  if (!order) {
    return <div className="text-center py-12 text-gray-400">Order not found</div>
  }

  return (
    <div className="max-w-6xl mx-auto px-4 py-6 space-y-6">
      <div className="flex items-center justify-between border-b border-gray-150 pb-4">
        <div className="space-y-1.5">
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Lab Report Entry</h2>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500">
            <span>Order No: <span className="font-semibold text-slate-800">{order.sequenceNumber || order.id.slice(0, 8)}</span></span>
            {order.patientName && (
              <>
                <span className="text-slate-300">|</span>
                <span className="text-base font-bold text-slate-900">Patient: {order.patientName}</span>
              </>
            )}
            {order.patientNumber && (
              <>
                <span className="text-slate-300">|</span>
                <span className="text-base font-semibold text-slate-800">Patient Number: {order.patientNumber}</span>
              </>
            )}
          </div>
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {/* Table Header */}
        <div className="grid grid-cols-12 gap-2 px-6 py-3.5 bg-slate-100/80 text-xs font-bold text-slate-600 uppercase tracking-wider border-b border-gray-200">
          <div className="col-span-4">TEST</div>
          <div className="col-span-3 text-center">RESULT</div>
          <div className="col-span-1 text-center">UNIT</div>
          <div className="col-span-3 text-left">NORMAL RANGE</div>
          {/* <div className="col-span-1 text-center">RESULT</div> */}
        </div>

        {/* Scrollable Body */}
        <div className="divide-y divide-gray-200 max-h-[calc(100vh-280px)] overflow-y-auto">
          {groupedDepartments.map(({ dept, lines }) => (
            <div key={dept.id || 'other'} className="divide-y divide-gray-100">
              {/* Department Subheader */}
              <div className="px-6 py-2.5 bg-neutral-50/40 border-b border-neutral-100/50">
                <h3 className="text-sm font-bold text-neutral-900 uppercase tracking-wider">{dept.name}</h3>
              </div>

              {/* Department Lines */}
              {lines.map(line => {
                const template = lineTemplates.get(line.id)
                const details = template?.labTemplateDetails
                  ? [...template.labTemplateDetails].sort((a, b) => a.orderNumber - b.orderNumber)
                  : []

                const requiresSpecimen = !!line.specimenId
                const collection = getCollectionForLine(line)
                const isCollected = !requiresSpecimen || !!collection

                // Case 1: Specimen not collected
                if (!isCollected) {
                  return (
                    <div key={line.id} className="grid grid-cols-12 gap-2 px-6 py-3.5 items-center hover:bg-slate-50/30 transition-colors">
                      <div className="col-span-4 text-sm font-bold text-slate-600 italic pl-4">
                        {line.itemName}
                      </div>
                      <div className="col-span-3 text-sm font-semibold italic text-amber-600 text-center">
                        Specimen not Collected
                      </div>
                      <div className="col-span-1 text-center">—</div>
                      <div className="col-span-3 text-slate-400">—</div>
                      <div className="col-span-1 text-center">—</div>
                    </div>
                  )
                }

                // Case 2: Specimen collected, but no template details
                if (details.length === 0) {
                  const val = values[line.id]?.['direct'] ?? line.resultValue ?? ''

                  return (
                    <div key={line.id} className="grid grid-cols-12 gap-2 px-6 py-3.5 items-center hover:bg-slate-50/30 transition-colors">
                      <div className="col-span-4 text-sm font-bold text-slate-600 italic pl-4">
                        {line.itemName}
                      </div>
                      <div className="col-span-3 text-center">
                        <input
                          type="text"
                          value={val}
                          onChange={e => updateValue(line.id, 'direct', e.target.value)}
                          placeholder="Enter result…"
                          className="w-full max-w-[180px] mx-auto px-3 py-1.5 border border-gray-200 rounded-lg text-sm text-center focus:outline-none focus:ring-2 focus:ring-neutral-400 bg-gray-50/30 focus:bg-white transition-all font-semibold"
                        />
                      </div>
                      <div className="col-span-1 text-center text-xs text-slate-400">—</div>
                      <div className="col-span-3 text-xs text-slate-400">—</div>
                      <div className="col-span-1 text-center text-xs text-slate-400">—</div>
                    </div>
                  )
                }

                // Case 3: Multiple parameters (details.length > 1) or single detail (details.length === 1)
                const showHeader = details.length > 1

                return (
                  <div key={line.id} className="divide-y divide-gray-50 bg-white">
                    {/* Header for multi-parameter test */}
                    {showHeader && (
                      <div className="grid grid-cols-12 gap-2 px-6 py-2 bg-slate-50/50">
                        <div className="col-span-12 text-xs font-bold text-slate-500 italic pl-4 uppercase tracking-wider">
                          {template?.header || line.itemName}
                        </div>
                      </div>
                    )}

                    {details.map(ltd => {
                      const val = values[line.id]?.[ltd.id] ?? ''
                      // const evaluation = evaluateResult(val, ltd.normalRange)

                      if (ltd.labType === 'HEADER') {
                        return (
                          <div key={ltd.id} className="grid grid-cols-12 gap-2 px-6 py-2 bg-neutral-50/10">
                            <div className="col-span-12 text-xs font-bold text-neutral-800 pl-6 uppercase tracking-wider">
                              {ltd.resultName}
                            </div>
                          </div>
                        )
                      }

                      // Indent parameter name if test has multiple parameters
                      const namePadding = showHeader ? 'pl-8' : 'pl-4 font-bold text-slate-600 italic'

                      return (
                        <div key={ltd.id} className="grid grid-cols-12 gap-2 px-6 py-3 items-center hover:bg-slate-50/30 transition-colors">
                          <div className={cn('col-span-4 text-sm text-slate-700', namePadding)}>
                            {ltd.resultName}
                          </div>
                          <div className="col-span-3 text-center">
                            <input
                              type="text"
                              value={val}
                              onChange={e => updateValue(line.id, ltd.id, e.target.value)}
                              placeholder="—"
                              className={cn(
                                'w-full max-w-[150px] mx-auto px-3 py-1.5 border rounded-lg text-sm text-center focus:outline-none focus:ring-2 focus:ring-offset-1 transition-all font-bold',
                                // evaluation === 'NORMAL' ? 'border-emerald-300 bg-emerald-50/20 text-emerald-900 focus:ring-emerald-400' :
                                //   evaluation === 'HIGH' || evaluation === 'LOW' ? 'border-amber-300 bg-amber-50/20 text-amber-900 focus:ring-amber-400' :
                                //     evaluation === 'CRITICAL' ? 'border-red-300 bg-red-50/20 text-red-900 focus:ring-red-400' :
                                //       'border-gray-200 focus:ring-neutral-400 bg-gray-50/30 focus:bg-white'
                              )}
                            />
                          </div>
                          <div className="col-span-1 text-center text-xs text-slate-500 font-semibold">{ltd.unit || ''}</div>
                          <div className="col-span-3 text-xs text-slate-500 whitespace-pre-wrap">{ltd.normalRange || ''}</div>
                          {/* <div className="col-span-1 text-center">
                            {val ? (
                              <span className={cn(
                                'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold',
                                evaluation === 'NORMAL' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' :
                                  evaluation === 'HIGH' ? 'bg-amber-50 text-amber-700 border border-amber-200' :
                                    evaluation === 'LOW' ? 'bg-amber-50 text-amber-700 border border-amber-200' :
                                      evaluation === 'CRITICAL' ? 'bg-red-50 text-red-700 border border-red-200 animate-pulse' :
                                        'bg-gray-50 text-gray-500 border border-gray-200'
                              )}>
                                {evaluation || '—'}
                              </span>
                            ) : (
                              <span className="text-gray-300">—</span>
                            )}
                          </div> */}
                        </div>
                      )
                    })}
                  </div>
                )
              })}
            </div>
          ))}
        </div>
      </div>

      {/* Save Actions Footer */}
      <div className="flex justify-end gap-3 border-t border-gray-150 pt-6">
        {/* <button
          onClick={() => navigate('/diagnostics?tab=lab')}
          className="px-5 py-2.5 text-sm font-medium text-slate-700 bg-white border border-gray-200 rounded-xl hover:bg-slate-50 transition-colors"
        >
          Cancel
        </button> */}
        {order && (
          <PrintButton
            templateType="LAB"
            params={{ id: order.id }}
            variant="outline"
            label="Print Report"
          />
        )}
        <button
          onClick={saveAllReports}
          disabled={isSavingAll}
          className="px-6 py-2.5 text-sm font-bold text-white bg-gradient-to-r from-neutral-600 to-neutral-600 rounded-xl hover:from-neutral-700 hover:to-neutral-700 disabled:opacity-50 transition-all shadow-md active:scale-[0.98]"
        >
          {isSavingAll ? 'Saving All…' : hasExistingReports ? 'Update Report' : 'Save Report'}
        </button>
      </div>
    </div>
  )
}
