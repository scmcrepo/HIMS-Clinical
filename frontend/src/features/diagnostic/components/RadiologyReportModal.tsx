import { useState, useEffect, useMemo } from 'react'
import { cn } from '../../../lib/utils'
import { FileEdit, Save, X, Activity } from 'lucide-react'
import { RichTextEditor } from '../../../components/shared/RichTextEditor'
import { diagnosticReportApi } from '../../../services/diagnostic/diagnosticReportApi'
import { useDiagnosticMutations, useTemplates } from '../../../hooks/diagnostic/useDiagnostic'
import { toast } from '../../../hooks/useToast'
import type { DiagnosticOrder, DiagnosticOrderLine } from '../../../types/diagnostic'

interface Props { order: DiagnosticOrder; onClose: () => void }

export function RadiologyReportModal({ order, onClose }: Props) {
  const { saveCustomReport } = useDiagnosticMutations()
  const { data: allTemplates = [] } = useTemplates()
  const [activeLine, setActiveLine] = useState<DiagnosticOrderLine | null>(order.lines[0] ?? null)
  const [reportData, setReportData] = useState<Record<string, { findings: string; impression: string; conclusion: string; id?: string }>>({})

  // Resolve template mapping based on service catalog item ID (chargeId)
  const lineTemplates = useMemo(() => {
    const map = new Map<string, any>()
    for (const line of order.lines) {
      const t = allTemplates.find(t => t.chargeId === line.serviceCatalogItemId) ?? null
      map.set(line.id, t)
    }
    return map
  }, [order.lines, allTemplates])

  useEffect(() => {
    const loadReports = async () => {
      const data: typeof reportData = {}
      for (const line of order.lines) {
        try {
          const reports = await diagnosticReportApi.getReportsByOrderLine(line.id)
          if (reports.length > 0 && reports[0].templateData) {
            const parsed = JSON.parse(reports[0].templateData)
            data[line.id] = { ...parsed, id: reports[0].id }
          } else {
            data[line.id] = { findings: '', impression: '', conclusion: '' }
          }
        } catch {
          data[line.id] = { findings: '', impression: '', conclusion: '' }
        }
      }
      setReportData(data)
    }
    loadReports()
  }, [order.lines])

  const updateField = (lineId: string, field: string, value: string) => {
    setReportData(prev => ({
      ...prev,
      [lineId]: { ...(prev[lineId] || { findings: '', impression: '', conclusion: '' }), [field]: value }
    }))
  }

  const saveReport = () => {
    if (!activeLine) return
    const data = reportData[activeLine.id]
    if (!data) return

    const template = lineTemplates.get(activeLine.id)
    if (!template) {
      toast({
        title: 'Template Not Found',
        description: `No diagnostic template has been configured for "${activeLine.itemName}".`,
        variant: 'destructive'
      })
      return
    }

    saveCustomReport.mutate({
      orderLineId: activeLine.id,
      templateId: template.id,
      templateData: JSON.stringify({ findings: data.findings, impression: data.impression, conclusion: data.conclusion }),
    })
  }

  const current = activeLine ? reportData[activeLine.id] : null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-[85vw] max-w-4xl max-h-[80vh] flex flex-col" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gradient-to-r from-neutral-50 to-white rounded-t-2xl">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-neutral-100 rounded-lg flex items-center justify-center">
              <Activity className="w-5 h-5 text-neutral-700" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-gray-900">Radiology Report</h2>
              <p className="text-xs text-gray-500 mt-0.5">Order: {order.sequenceNumber || order.id.slice(0, 8)}</p>
            </div>
          </div>
          <button onClick={onClose} className="w-8 h-8 rounded-lg bg-gray-100 hover:bg-gray-200 flex items-center justify-center text-gray-500 transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex flex-1 overflow-hidden">
          {/* Left: study list */}
          <div className="w-52 border-r border-gray-100 overflow-y-auto bg-gray-50/50">
            <div className="px-3 py-2 text-xs font-bold text-purple-700 uppercase tracking-wider bg-purple-50/50 border-b border-purple-100">
              Studies
            </div>
            {order.lines.map(line => (
              <button key={line.id} onClick={() => setActiveLine(line)}
                className={cn('w-full text-left px-3 py-2.5 text-sm border-b border-gray-100 transition-colors',
                  activeLine?.id === line.id ? 'bg-neutral-50 text-neutral-800 font-semibold' : 'text-gray-600 hover:bg-gray-100')}>
                {line.itemName ?? 'Unknown Study'}
              </button>
            ))}
          </div>

          {/* Right: report form */}
          <div className="flex-1 overflow-y-auto p-6 space-y-4">
            {!activeLine ? (
              <p className="text-gray-400 text-sm text-center mt-8">Select a study from the left</p>
            ) : (
              <>
                <div className="px-4 py-2.5 bg-neutral-50 rounded-xl border border-neutral-100">
                  <span className="text-sm font-bold text-neutral-800 uppercase">{activeLine.itemName} Report</span>
                </div>

                {!lineTemplates.get(activeLine.id) && (
                  <div className="p-4 bg-amber-50 text-amber-800 border border-amber-200 rounded-xl text-sm">
                    No diagnostic template has been configured for <strong>{activeLine.itemName}</strong>.
                    Please define a template for this item under Masters &rarr; Diagnostic Templates before saving a report.
                  </div>
                )}

                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1.5">Findings</label>
                  <RichTextEditor value={current?.findings ?? ''} onChange={val => updateField(activeLine.id, 'findings', val)}
                    placeholder="Describe radiological findings…" minHeight="160px" />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1.5">Impression</label>
                  <RichTextEditor value={current?.impression ?? ''} onChange={val => updateField(activeLine.id, 'impression', val)}
                    placeholder="Radiological impression…" minHeight="120px" />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-600 mb-1.5">Conclusion</label>
                  <RichTextEditor value={current?.conclusion ?? ''} onChange={val => updateField(activeLine.id, 'conclusion', val)}
                    placeholder="Final conclusion…" minHeight="90px" />
                </div>
              </>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-3 px-6 py-4 border-t border-gray-100 bg-gray-50/30 rounded-b-2xl">
          <button onClick={onClose}
            className="px-5 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-xl hover:bg-gray-50">
            Close
          </button>
          <button onClick={saveReport} disabled={!activeLine || !lineTemplates.get(activeLine.id) || saveCustomReport.isPending}
            className="px-6 py-2 text-sm font-bold text-white bg-gradient-to-r from-neutral-600 to-neutral-600 rounded-xl hover:from-neutral-700 hover:to-neutral-700 disabled:opacity-50 shadow-md flex items-center gap-2">
            {saveCustomReport.isPending ? (
              'Saving…'
            ) : current?.id ? (
              <>
                <FileEdit className="w-4 h-4" />
                Update Report
              </>
            ) : (
              <>
                <Save className="w-4 h-4" />
                Save Report
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  )
}
