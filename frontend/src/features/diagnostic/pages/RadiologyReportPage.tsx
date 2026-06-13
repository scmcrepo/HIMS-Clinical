import { useState, useEffect, useMemo } from 'react'
import { PrintButton } from '../../../components/shared/PrintButton'
import { useNavigate, useParams } from 'react-router-dom'
import { cn } from '../../../lib/utils'
import { FileEdit, Save, Activity, Paperclip, Trash2, Download, FileText, Eye } from 'lucide-react'
import { diagnosticReportApi } from '../../../services/diagnostic/diagnosticReportApi'
import { diagnosticApi } from '../../../services/diagnostic/diagnosticApi'
import { useDiagnosticMutations, useTemplates } from '../../../hooks/diagnostic/useDiagnostic'
import type { DiagnosticOrderLine } from '../../../types/diagnostic'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import BackButton from '../../../components/shared/BackButton'
import { RichTextEditor } from '../../../components/shared/RichTextEditor'
import { attachmentApi, type Attachment } from '../../../services/attachment/attachmentApi'
import { toast } from '../../../hooks/useToast'

export default function RadiologyReportPage() {
  const navigate = useNavigate()
  const { orderId } = useParams<{ orderId: string }>()
  const { saveCustomReport } = useDiagnosticMutations()
  const { data: allTemplates = [] } = useTemplates()
  const qc = useQueryClient()

  const { data: order, isLoading } = useQuery({
    queryKey: ['diagnosticOrder', orderId],
    queryFn: () => diagnosticApi.getById(orderId!),
    enabled: !!orderId,
  })

  // Resolve template mapping based on service catalog item ID (chargeId)
  const lineTemplates = useMemo(() => {
    const map = new Map<string, any>()
    for (const line of order?.lines ?? []) {
      const t = allTemplates.find(t => t.chargeId === line.serviceCatalogItemId) ?? null
      map.set(line.id, t)
    }
    return map
  }, [order?.lines, allTemplates])

  const [activeLine, setActiveLine] = useState<DiagnosticOrderLine | null>(null)
  const [reportData, setReportData] = useState<Record<string, { findings: string; impression: string; conclusion: string; id?: string }>>({})
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [previewAttachment, setPreviewAttachment] = useState<Attachment | null>(null)
  const [uploading, setUploading] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'report' | 'attachments'>('report')

  const fetchAttachments = async () => {
    if (!order || !activeLine) return
    try {
      const list = await attachmentApi.getByPatient(order.patientId)
      const filtered = list.filter(a => a.category === activeLine.id)
      setAttachments(filtered)
    } catch (e) {
      console.error('Failed to load attachments', e)
    }
  }

  useEffect(() => {
    if (activeLine) {
      fetchAttachments()
      setActiveTab('report') // default to report tab when switching studies
    }
  }, [activeLine])

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !order || !activeLine) return
    setUploading(true)
    try {
      await attachmentApi.upload(
        file,
        'DIAGNOSTIC',
        order.encounterId,
        order.patientId,
        undefined,
        activeLine.id
      )
      qc.invalidateQueries({ queryKey: ['attachments', order.encounterId] })
      toast({ title: 'Attachment uploaded successfully', variant: 'success' })
      await fetchAttachments()
    } catch (err: any) {
      toast({ title: 'Upload failed', description: err.message, variant: 'destructive' })
    } finally {
      setUploading(false)
    }
  }

  const handleDeleteAttachment = async (id: string) => {
    if (!order) return
    try {
      await attachmentApi.delete(id)
      qc.invalidateQueries({ queryKey: ['attachments', order.encounterId] })
      toast({ title: 'Attachment deleted', variant: 'success' })
      if (previewAttachment?.id === id) {
        setPreviewAttachment(null)
      }
      await fetchAttachments()
    } catch (err: any) {
      toast({ title: 'Delete failed', description: err.message, variant: 'destructive' })
    }
  }

  useEffect(() => {
    if (!order) return
    if (order.lines.length > 0) {
      setActiveLine(prev => {
        if (prev) {
          const found = order.lines.find(l => l.id === prev.id)
          if (found) return found
        }
        return order.lines[0]
      })
    }

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
  }, [order])

  const updateField = (lineId: string, field: string, value: string) => {
    setReportData(prev => ({
      ...prev,
      [lineId]: { ...(prev[lineId] || { findings: '', impression: '', conclusion: '' }), [field]: value }
    }))
  }

  const handleSaveReport = () => {
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

    saveCustomReport.mutate(
      {
        orderLineId: activeLine.id,
        templateId: template.id,
        templateData: JSON.stringify({ findings: data.findings, impression: data.impression, conclusion: data.conclusion }),
      },
      {
        onSuccess: (savedReport) => {
          qc.invalidateQueries({ queryKey: ['diagnosticOrder', orderId] })
          setReportData(prev => ({
            ...prev,
            [activeLine.id]: { ...(prev[activeLine.id] || {}), id: savedReport.id }
          }))
        }
      }
    )
  }

  const current = activeLine ? reportData[activeLine.id] : null

  if (isLoading) return <div className="flex items-center justify-center h-64 text-gray-500">Loading…</div>
  if (!order) return <div className="text-center py-12 text-gray-400">Order not found</div>

  return (
    <div className="max-w-6xl mx-auto px-4 py-6 space-y-6">
      {/* Title Header */}
      <div className="flex items-center justify-between border-b border-gray-150 pb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-neutral-100 rounded-xl flex items-center justify-center shrink-0">
            <Activity className="w-5 h-5 text-neutral-700" />
          </div>
          <div className="space-y-1">
            <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Radiology Report</h2>
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
        </div>
        <BackButton />
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm flex overflow-hidden" style={{ minHeight: '60vh' }}>
        {/* Left: Study Navigation Sidebar */}
        <div className="w-60 border-r border-gray-200 overflow-y-auto bg-slate-50/50">
          <div className="px-4 py-3 text-xs font-bold text-purple-700 uppercase tracking-wider bg-purple-50/40 border-b border-purple-100">
            Studies
          </div>
          <div className="divide-y divide-gray-150">
            {order.lines.map(line => (
              <button
                key={line.id}
                onClick={() => setActiveLine(line)}
                className={cn(
                  'w-full text-left px-4 py-4 text-sm transition-all duration-150 font-medium',
                  activeLine?.id === line.id
                    ? 'bg-white text-neutral-800 font-bold border-l-4 border-l-neutral-600 shadow-sm'
                    : 'text-slate-600 hover:bg-slate-50'
                )}
              >
                {line.itemName ?? 'Unknown Study'}
              </button>
            ))}
          </div>
        </div>

        {/* Right Content Area */}
        <div className="flex-1 flex flex-col bg-white">
          {!activeLine ? (
            <div className="flex flex-col items-center justify-center flex-1 text-slate-400 p-8">
              <FileText className="w-12 h-12 text-slate-300 mb-2" />
              <p className="text-sm">Select a study from the left sidebar to edit report</p>
            </div>
          ) : (
            <div className="flex-1 flex flex-col">
              {/* Tab Header Bar */}
              <div className="flex items-center justify-between px-6 py-2.5 border-b border-gray-200 bg-slate-50/50">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider">
                  {activeLine.itemName} REPORT
                </h3>
                <div className="flex border border-gray-200 rounded-lg overflow-hidden bg-white">
                  <button
                    onClick={() => setActiveTab('report')}
                    className={cn(
                      'px-4 py-2 text-xs font-bold transition-colors flex items-center gap-1.5',
                      activeTab === 'report'
                        ? 'bg-neutral-600 text-white'
                        : 'text-slate-600 hover:bg-slate-50'
                    )}
                  >
                    <FileText className="w-3.5 h-3.5" />
                    Report
                  </button>
                  <button
                    onClick={() => setActiveTab('attachments')}
                    className={cn(
                      'px-4 py-2 text-xs font-bold transition-colors flex items-center gap-1.5 border-l border-gray-200',
                      activeTab === 'attachments'
                        ? 'bg-neutral-600 text-white'
                        : 'text-slate-600 hover:bg-slate-50'
                    )}
                  >
                    <Paperclip className="w-3.5 h-3.5" />
                    Attachments ({attachments.length})
                  </button>
                </div>
              </div>

              {/* Tab Content Body */}
              <div className="flex-1 p-6 overflow-y-auto">
                {activeTab === 'report' ? (
                  <div className="space-y-5 max-w-4xl">
                    {!lineTemplates.get(activeLine.id) && (
                      <div className="p-4 bg-amber-50 text-amber-800 border border-amber-200 rounded-xl text-sm">
                        No diagnostic template has been configured for <strong>{activeLine.itemName}</strong>.
                        Please define a template for this item under Masters &rarr; Diagnostic Templates before saving a report.
                      </div>
                    )}
                    <div>
                      <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Findings</label>
                      <RichTextEditor
                        value={current?.findings ?? ''}
                        onChange={val => updateField(activeLine.id, 'findings', val)}
                        placeholder="Describe radiological findings…"
                        minHeight="180px"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Impression</label>
                      <RichTextEditor
                        value={current?.impression ?? ''}
                        onChange={val => updateField(activeLine.id, 'impression', val)}
                        placeholder="Radiological impression…"
                        minHeight="140px"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Conclusion</label>
                      <RichTextEditor
                        value={current?.conclusion ?? ''}
                        onChange={val => updateField(activeLine.id, 'conclusion', val)}
                        placeholder="Final conclusion…"
                        minHeight="100px"
                      />
                    </div>
                  </div>
                ) : (
                  <div className="space-y-4 max-w-4xl">
                    <div className="flex items-center justify-between border-b border-gray-150 pb-3">
                      <h4 className="text-sm font-bold text-slate-700">Attached Documents</h4>
                      <label className="cursor-pointer inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-neutral-700 bg-neutral-50 hover:bg-neutral-100 rounded-xl transition-all border border-neutral-200 shadow-sm">
                        {uploading ? 'Uploading...' : 'Attach File'}
                        <input type="file" className="hidden" onChange={handleFileUpload} disabled={uploading} />
                      </label>
                    </div>

                    {attachments.length > 0 ? (
                      <div className="border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="bg-slate-50 text-xs font-bold text-slate-500 uppercase tracking-wider border-b border-gray-200">
                              <th className="px-4 py-3 text-left">Attached File</th>
                              <th className="px-4 py-3 text-center w-48">Actions</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-gray-150">
                            {attachments.map(att => (
                              <tr key={att.id} className="hover:bg-slate-50/50 transition-colors">
                                <td className="px-4 py-3 font-medium text-slate-700 flex items-center gap-2">
                                  <Paperclip className="w-4 h-4 text-slate-400 shrink-0" />
                                  <span className="truncate" title={att.fileName}>{att.fileName}</span>
                                </td>
                                <td className="px-4 py-3 text-center">
                                  <div className="flex items-center justify-center gap-1.5">
                                    <button
                                      onClick={() => setPreviewAttachment(att)}
                                      className="px-3 py-1.5 text-xs font-bold text-neutral-700 bg-neutral-50 hover:bg-neutral-100 rounded-lg transition-all inline-flex items-center gap-1"
                                      title="View"
                                    >
                                      <Eye className="w-3.5 h-3.5" />
                                      View
                                    </button>
                                    <a
                                      href={attachmentApi.getDownloadUrl(att.id)}
                                      download={att.fileName}
                                      className="p-1.5 text-slate-500 hover:text-neutral-600 hover:bg-neutral-50 rounded-lg transition-all"
                                      title="Download"
                                    >
                                      <Download className="w-4 h-4" />
                                    </a>
                                    <button
                                      onClick={() => setDeleteId(att.id)}
                                      className="p-1.5 text-slate-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-all"
                                      title="Delete"
                                    >
                                      <Trash2 className="w-4 h-4" />
                                    </button>
                                  </div>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <div className="text-center py-10 bg-slate-50 rounded-2xl border-2 border-dashed border-gray-200">
                        <Paperclip className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                        <p className="text-sm text-slate-500 font-medium">No attachments uploaded yet</p>
                        <p className="text-xs text-slate-400 mt-1">Upload images, PDFs, or scan reports here</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Main Footer Close Button */}
      <div className="flex justify-end gap-3 pt-4 border-t border-gray-150">
        {/* <button
          onClick={() => navigate('/diagnostics?tab=radiology')}
          className="px-5 py-2.5 text-sm font-medium text-slate-700 bg-white border border-gray-200 rounded-xl hover:bg-slate-50 transition-colors"
        >
          Cancel
        </button> */}
        {order && (
          <PrintButton
            templateType="RADIOLOGY"
            params={{ id: order.id }}
            variant="outline"
            label="Print Report"
          />
        )}
        <button
          onClick={handleSaveReport}
          disabled={!activeLine || !lineTemplates.get(activeLine.id) || saveCustomReport.isPending}
          className="px-6 py-2.5 text-sm font-bold text-white bg-gradient-to-r from-neutral-600 to-neutral-600 rounded-xl hover:from-neutral-700 hover:to-neutral-700 disabled:opacity-50 shadow-md flex items-center gap-2 active:scale-[0.98] transition-all"
        >
          {saveCustomReport.isPending ? (
            'Saving…'
          ) : current?.id ? (
            <><FileEdit className="w-4 h-4" />Update Report</>
          ) : (
            <><Save className="w-4 h-4" />Save Report</>
          )}
        </button>
      </div>

      {/* Attachment Image/PDF View Modal Overlay */}
      {previewAttachment && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-[2rem] shadow-2xl border border-gray-150 p-6 max-w-4xl w-full relative overflow-hidden transform animate-in zoom-in-95 duration-300 flex flex-col max-h-[90vh]">
            <div className="flex items-center justify-between border-b border-gray-200 pb-3 mb-4">
              <span className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-1.5">
                <Eye className="w-4 h-4 text-neutral-600" />
                View Attachment: {previewAttachment.fileName}
              </span>
              <button
                onClick={() => setPreviewAttachment(null)}
                className="text-slate-400 hover:text-slate-600 text-lg font-bold p-1 rounded-lg hover:bg-slate-100 transition-all"
              >
                ✕
              </button>
            </div>

            <div className="flex-1 overflow-auto flex items-center justify-center bg-slate-50 rounded-xl">
              {(() => {
                const ext = previewAttachment.fileName.split('.').pop()?.toLowerCase() || ''
                const isImage = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext) || previewAttachment.contentType?.startsWith('image/')
                const isPdf = ext === 'pdf' || previewAttachment.contentType === 'application/pdf'
                const fileUrl = attachmentApi.getDownloadUrl(previewAttachment.id)

                if (isImage) {
                  return (
                    <img src={fileUrl} className="max-w-full max-h-[60vh] object-contain rounded-lg shadow-sm" alt="Radiology Scan Preview" />
                  )
                }

                if (isPdf) {
                  return (
                    <iframe src={fileUrl} className="w-full h-[60vh] rounded-xl border border-gray-200 shadow-inner" title="Radiology PDF Report" />
                  )
                }

                return (
                  <div className="flex flex-col items-center justify-center p-8 text-center">
                    <FileText className="w-16 h-16 text-slate-300 mb-4" />
                    <p className="text-sm font-semibold text-slate-700 mb-1">Preview not available for this file type</p>
                    <p className="text-xs text-slate-400 mb-4">{previewAttachment.fileName}</p>
                    <a
                      href={fileUrl}
                      download
                      className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-white bg-neutral-600 hover:bg-neutral-700 rounded-xl shadow-sm transition-all"
                    >
                      <Download className="w-3.5 h-3.5" />
                      Download to View
                    </a>
                  </div>
                )
              })()}
            </div>

            <div className="border-t border-gray-150 pt-4 flex justify-end gap-3 mt-4">
              {/* <a
                href={attachmentApi.getDownloadUrl(previewAttachment.id)}
                download
                className="px-5 py-2 text-sm font-bold text-neutral-700 bg-neutral-50 border border-neutral-200 rounded-xl hover:bg-neutral-100 transition-colors inline-flex items-center gap-1.5"
              >
                <Download className="w-4 h-4" />
                Download
              </a> */}
              <button
                onClick={() => setPreviewAttachment(null)}
                className="px-5 py-2 text-sm font-bold text-slate-600 bg-slate-50 hover:bg-slate-100 rounded-xl transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteId && (
        <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-[2rem] shadow-xl border border-gray-150 p-8 max-w-sm w-full relative overflow-hidden transform animate-in zoom-in-95 duration-300">
            <h3 className="text-xl font-extrabold text-slate-900 mb-2">Delete Attachment</h3>
            <p className="text-slate-500 text-sm mb-6 leading-relaxed">
              Are you sure you want to delete this attachment? This action cannot be undone.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDeleteId(null)}
                className="px-5 py-2.5 text-sm font-bold text-slate-600 bg-slate-50 hover:bg-slate-100 rounded-2xl transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={async () => {
                  if (deleteId) {
                    await handleDeleteAttachment(deleteId)
                    setDeleteId(null)
                  }
                }}
                className="px-6 py-2.5 text-sm font-bold text-white bg-gradient-to-r from-neutral-600 to-neutral-600 hover:from-neutral-700 hover:to-neutral-700 rounded-2xl shadow-md transition-all active:scale-[0.98]"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
