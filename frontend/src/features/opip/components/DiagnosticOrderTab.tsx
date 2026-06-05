/**
 * DiagnosticOrderTab.tsx
 * Diagnostic Order clinical tab — works for both OP (inline) and IP (modal).
 */
import { useState, useEffect, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../hooks/useToast'
import { QuickAddPanel } from './QuickAddPanel'
import {
  opDiagnosticApi, ipDiagnosticApi, diagTestSearchApi,
  type DiagnosticOrderLinePayload, type DiagnosticOrderResponse,
} from '../../../services/opip/opipApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import { PrintButton } from '../../../components/shared/PrintButton'
import { attachmentApi, type Attachment } from '../../../services/attachment/attachmentApi'
import { cn } from '../../../lib/utils'
import { Eye, FileText, Download } from 'lucide-react'
import { diagnosticReportApi } from '../../../services/diagnostic/diagnosticReportApi'
import { diagTemplateApi } from '../../../services/diagnostic/diagTemplateApi'
import type { DiagnosticTemplate, DiagnosticReport } from '../../../types/diagnostic'


interface Props {
  encounterId:   string
  mode:          'OP' | 'IP'
  consultantId?: string
  readOnly?:     boolean
}

// ─────────────────────────────────────────────────────────────────────────────

export function DiagnosticOrderTab({ encounterId, mode, consultantId, readOnly }: Props) {
  const qc = useQueryClient()
  const [showModal, setShowModal] = useState(false)

  const { data: orders = [], isLoading } = useQuery({
    queryKey: ['diagnostic-orders', encounterId],
    queryFn:  () => mode === 'OP'
      ? opDiagnosticApi.list(encounterId)
      : ipDiagnosticApi.list(encounterId),
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['diagnostic-orders', encounterId] })

  useEffect(() => {
    invalidate()
  }, [encounterId])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-gray-800">Diagnostic Orders</h3>
        {mode === 'IP' && !readOnly && (
          <button
            onClick={() => setShowModal(true)}
            className="px-3 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
            + ADD DIAGNOSTIC ORDER
          </button>
        )}
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-400 text-center py-6">Loading…</p>
      ) : orders.length === 0 ? (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg px-4 py-3 text-xs text-yellow-800">
          No Diagnostics Order! There is no Diagnostics order for the Encounter.
        </div>
      ) : (
        <div className="space-y-3">
          {[...orders]
            .sort((a, b) => {
              const timeA = a.orderedAt ? new Date(a.orderedAt).getTime() : 0
              const timeB = b.orderedAt ? new Date(b.orderedAt).getTime() : 0
              return timeB - timeA
            })
            .map((order, i) => (
              <DiagnosticOrderCard key={order.id ?? i} order={order} />
            ))}
        </div>
      )}

      {/* OP: inline entry */}
      {mode === 'OP' && !readOnly && (
        <InlineDiagnosticForm
          encounterId={encounterId}
          consultantId={consultantId}
          onSaved={invalidate}
        />
      )}

      {mode === 'IP' && showModal && (
        <DiagnosticOrderModal
          encounterId={encounterId}
          consultantId={consultantId}
          onClose={() => setShowModal(false)}
          onSaved={() => { invalidate(); setShowModal(false) }}
        />
      )}
    </div>
  )
}

// ─── Card ─────────────────────────────────────────────────────────────────────


function DiagnosticOrderCard({ order }: { order: DiagnosticOrderResponse }) {
  const [selectedLineForAttachments, setSelectedLineForAttachments] = useState<any | null>(null)
  const [selectedLineAttachments, setSelectedLineAttachments] = useState<Attachment[] | null>(null)
  const [selectedLineForReport, setSelectedLineForReport] = useState<any | null>(null)

  const { data: attachments = [] } = useQuery({
    queryKey: ['attachments', order.encounterId],
    queryFn: () => attachmentApi.getByEncounter(order.encounterId),
    enabled: !!order.encounterId,
  })

  const orderApproved = order.items.length > 0 && order.items.every(item => item.isApproved)
  const anyApproved = order.items.some(item => item.isApproved)
  const allResulted = order.items.every(item => item.status === 'RESULTED')
  const anyResulted = order.items.some(item => item.status === 'RESULTED')

  let headerStatusBadge = null
  if (orderApproved || anyApproved) {
    headerStatusBadge = (
      <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
        Approved
      </span>
    )
  } else if (allResulted || anyResulted) {
    headerStatusBadge = (
      <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-green-50 text-green-700 border border-green-200">
        Result Entered
      </span>
    )
  }

  return (
    <div className="border border-gray-200 rounded-xl bg-white overflow-hidden">
      <div className="px-4 py-2 bg-gray-50 border-b border-gray-100 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-xs text-gray-500">{formatDateTime(order.orderedAt)}</span>
          {headerStatusBadge}
        </div>
        <div className="flex items-center gap-3">
          {order.requestedByName && (
            <span className="text-xs text-blue-600 font-medium">Dr. {order.requestedByName}</span>
          )}
          {order.realOrderId && order.items.some(item => item.isApproved) && (
            <PrintButton
              templateType={order.diagnosticType === 'RADIOLOGY' ? 'RADIOLOGY' : 'LAB'}
              params={{ id: order.realOrderId }}
              label="View Report"
              variant="outline"
              className="text-[10px] py-0.5 px-2 font-bold h-6 rounded-md bg-white hover:bg-slate-50 text-blue-600 border border-blue-200"
            />
          )}
        </div>
      </div>
      <ul className="divide-y divide-gray-50">
        {order.items.map((line, i) => {
          const lineAttachments = attachments.filter(
            a =>
              (line.realOrderLineId && a.category === line.realOrderLineId) ||
              (line.id && a.category === line.id)
          )

          return (
            <li key={line.id ?? i} className="px-4 py-2 flex items-center justify-between text-xs">
              <div>
                <span className="font-medium text-gray-900">{line.testName}</span>
                {line.category && (
                  <span className="ml-2 text-gray-400">({line.category})</span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${line.status === 'RESULTED' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>
                  {line.status === 'RESULTED' ? 'Result Entered' : 'Pending'}
                </span>
                {/* {line.isApproved && (
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700">
                    Approved
                  </span>
                )} */}
                {order.realOrderId && (line.status === 'RESULTED' || line.isApproved) && (
                  <button
                    onClick={() => setSelectedLineForReport(line)}
                    className="text-[10px] py-0.5 px-2 font-bold h-6 rounded-md bg-white hover:bg-slate-50 text-blue-600 border border-blue-200 inline-flex items-center gap-1 transition-colors"
                  >
                    View Report
                  </button>
                )}
                {lineAttachments.length > 0 && (
                  <button
                    onClick={() => {
                      setSelectedLineForAttachments(line)
                      setSelectedLineAttachments(lineAttachments)
                    }}
                    className="inline-flex items-center gap-1 text-[10px] py-0.5 px-2 font-bold h-6 rounded-md bg-purple-50 hover:bg-purple-100 text-purple-700 border border-purple-200 transition-colors shadow-sm"
                  >
                    <Eye className="w-3.5 h-3.5" />
                    View Image ({lineAttachments.length})
                  </button>
                )}
              </div>
            </li>
          )
        })}
      </ul>

      {selectedLineAttachments && selectedLineForAttachments && (
        <LineAttachmentsModal
          lineName={selectedLineForAttachments.testName}
          attachments={selectedLineAttachments}
          onClose={() => {
            setSelectedLineForAttachments(null)
            setSelectedLineAttachments(null)
          }}
        />
      )}

      {selectedLineForReport && (
        <LineReportModal
          line={selectedLineForReport}
          diagnosticType={order.diagnosticType || 'LAB'}
          onClose={() => setSelectedLineForReport(null)}
        />
      )}
    </div>
  )
}

function LineAttachmentsModal({
  lineName,
  attachments,
  onClose,
}: {
  lineName: string
  attachments: Attachment[]
  onClose: () => void
}) {
  const [selectedAttachment, setSelectedAttachment] = useState<Attachment>(attachments[0])

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
      style={{ marginTop: 0 }}
    >
      <div className="bg-white rounded-[2rem] shadow-2xl border border-gray-150 p-6 max-w-5xl w-full relative overflow-hidden transform animate-in zoom-in-95 duration-300 flex flex-col max-h-[90vh]">
        <div className="flex items-center justify-between border-b border-gray-200 pb-3 mb-4">
          <span className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-1.5">
            <Eye className="w-4 h-4 text-purple-600" />
            Attachments for {lineName}
          </span>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 text-lg font-bold p-1 rounded-lg hover:bg-slate-100 transition-all"
          >
            ✕
          </button>
        </div>

        <div className="flex-1 flex gap-4 min-h-0">
          {/* Left: Attachment List (only if multiple) */}
          {attachments.length > 1 && (
            <div className="w-64 border-r border-gray-200 pr-4 overflow-y-auto space-y-2">
              <span className="text-xs font-bold text-gray-400 uppercase tracking-wider block mb-2">
                Files ({attachments.length})
              </span>
              {attachments.map(a => (
                <button
                  key={a.id}
                  onClick={() => setSelectedAttachment(a)}
                  className={cn(
                    "w-full text-left p-3 rounded-xl border transition-all flex flex-col gap-1",
                    selectedAttachment.id === a.id
                      ? "border-purple-500 bg-purple-50/50 text-purple-900"
                      : "border-gray-200 hover:bg-slate-50 text-gray-700"
                  )}
                >
                  <span className="text-xs font-semibold truncate w-full">{a.fileName}</span>
                  {a.createdAt && (
                    <span className="text-[10px] text-gray-400">
                      {new Date(a.createdAt).toLocaleDateString()}
                    </span>
                  )}
                </button>
              ))}
            </div>
          )}

          {/* Right: Preview Area */}
          <div className="flex-1 overflow-auto flex flex-col bg-slate-50 rounded-xl p-4 items-center justify-center relative">
            {(() => {
              const ext = selectedAttachment.fileName.split('.').pop()?.toLowerCase() || ''
              const isImage = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext) || selectedAttachment.contentType?.startsWith('image/')
              const isPdf = ext === 'pdf' || selectedAttachment.contentType === 'application/pdf'
              const fileUrl = attachmentApi.getDownloadUrl(selectedAttachment.id)

              if (isImage) {
                return (
                  <img src={fileUrl} className="max-w-full max-h-[60vh] object-contain rounded-lg shadow-sm" alt="Attachment Preview" />
                )
              }

              if (isPdf) {
                return (
                  <iframe src={fileUrl} className="w-full h-[60vh] rounded-xl border border-gray-200 shadow-inner" title="Attachment PDF" />
                )
              }

              return (
                <div className="flex flex-col items-center justify-center p-8 text-center">
                  <FileText className="w-16 h-16 text-slate-300 mb-4" />
                  <p className="text-sm font-semibold text-slate-700 mb-1">Preview not available for this file type</p>
                  <p className="text-xs text-gray-400 mb-4">{selectedAttachment.fileName}</p>
                  <a
                    href={fileUrl}
                    download
                    className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-white bg-purple-600 hover:bg-purple-700 rounded-xl shadow-sm transition-all"
                  >
                    <Download className="w-3.5 h-3.5" />
                    Download to View
                  </a>
                </div>
              )
            })()}
          </div>
        </div>

        <div className="border-t border-gray-150 pt-4 flex justify-between items-center mt-4">
          <span className="text-xs text-gray-400 truncate max-w-md">
            Showing: {selectedAttachment.fileName}
          </span>
          <div className="flex gap-3">
            <a
              href={attachmentApi.getDownloadUrl(selectedAttachment.id)}
              download
              className="px-5 py-2 text-sm font-bold text-purple-700 bg-purple-50 border border-purple-200 rounded-xl hover:bg-purple-100 transition-colors inline-flex items-center gap-1.5"
            >
              <Download className="w-4 h-4" />
              Download
            </a>
            <button
              onClick={onClose}
              className="px-5 py-2 text-sm font-bold text-gray-700 bg-white border border-gray-200 rounded-xl hover:bg-slate-50 transition-colors"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function LineReportModal({
  line,
  diagnosticType,
  onClose,
}: {
  line: any
  diagnosticType: string
  onClose: () => void
}) {
  const lineId = line.realOrderLineId || line.id

  const { data: reports = [], isLoading: loadingReports } = useQuery<DiagnosticReport[]>({
    queryKey: ['line-reports', lineId],
    queryFn: () => diagnosticReportApi.getReportsByOrderLine(lineId),
    enabled: !!lineId,
  })

  const { data: templates = [] } = useQuery<DiagnosticTemplate[]>({
    queryKey: ['diagTemplates'],
    queryFn: diagTemplateApi.getAll,
  })

  const template = useMemo(() => {
    return templates.find(t => t.chargeId === line.serviceCatalogItemId) ?? null
  }, [templates, line.serviceCatalogItemId])

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
      style={{ marginTop: 0 }}
    >
      <div className="bg-white rounded-[2rem] shadow-2xl border border-gray-150 p-6 max-w-3xl w-full relative overflow-hidden transform animate-in zoom-in-95 duration-300 flex flex-col max-h-[85vh]">
        <div className="flex items-center justify-between border-b border-gray-200 pb-3 mb-4">
          <span className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-1.5">
            <FileText className="w-4 h-4 text-blue-600" />
            Report: {line.testName}
          </span>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 text-lg font-bold p-1 rounded-lg hover:bg-slate-100 transition-all"
          >
            ✕
          </button>
        </div>

        <div className="flex-1 overflow-y-auto space-y-4 pr-1">
          {loadingReports ? (
            <p className="text-sm text-gray-400 text-center py-8">Loading report details…</p>
          ) : reports.length === 0 ? (
            <div className="bg-yellow-50 border border-yellow-200 rounded-xl px-4 py-3 text-xs text-yellow-800">
              No report entered yet for this test.
            </div>
          ) : (
            <div>
              {diagnosticType === 'RADIOLOGY' ? (
                (() => {
                  let findings = ''
                  let impression = ''
                  let conclusion = ''

                  try {
                    if (reports[0]?.templateData) {
                      const parsed = JSON.parse(reports[0].templateData)
                      findings = parsed.findings || ''
                      impression = parsed.impression || ''
                      conclusion = parsed.conclusion || ''
                    }
                  } catch (e) {
                    console.error('Failed to parse radiology templateData', e)
                  }

                  return (
                    <div className="space-y-4">
                      {findings && (
                        <div className="bg-slate-50 p-4 rounded-xl border border-slate-100">
                          <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Findings</h4>
                          <div
                            className="text-sm text-slate-700 prose max-w-none"
                            dangerouslySetInnerHTML={{ __html: findings }}
                          />
                        </div>
                      )}
                      {impression && (
                        <div className="bg-slate-50 p-4 rounded-xl border border-slate-100">
                          <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Impression</h4>
                          <div
                            className="text-sm text-slate-700 prose max-w-none"
                            dangerouslySetInnerHTML={{ __html: impression }}
                          />
                        </div>
                      )}
                      {conclusion && (
                        <div className="bg-slate-50 p-4 rounded-xl border border-slate-100">
                          <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2">Conclusion</h4>
                          <div
                            className="text-sm text-slate-700 prose max-w-none"
                            dangerouslySetInnerHTML={{ __html: conclusion }}
                          />
                        </div>
                      )}
                      {!findings && !impression && !conclusion && (
                        <p className="text-sm text-gray-500 text-center py-6">The report does not contain any findings or impressions.</p>
                      )}
                    </div>
                  )
                })()
              ) : (
                /* LAB report */
                <div>
                  {template && template.labTemplateDetails && template.labTemplateDetails.length > 0 ? (
                    <div className="border border-gray-200 rounded-xl overflow-hidden">
                      <table className="w-full text-left border-collapse">
                        <thead>
                          <tr className="bg-slate-50 text-slate-500 uppercase text-[10px] font-bold border-b border-gray-200">
                            <th className="px-4 py-2">Test Parameter</th>
                            <th className="px-4 py-2 text-center">Result</th>
                            <th className="px-4 py-2 text-center">Unit</th>
                            <th className="px-4 py-2">Normal Range</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100 text-xs">
                          {template.labTemplateDetails
                            .sort((a: any, b: any) => a.orderNumber - b.orderNumber)
                            .map((ltd: any) => {
                              const r = reports.find((rep: any) => rep.labTemplateDetailId === ltd.id)
                              const val = r?.value || '—'

                              if (ltd.labType === 'HEADER') {
                                return (
                                  <tr key={ltd.id} className="bg-indigo-50/40 font-bold text-indigo-900">
                                    <td colSpan={4} className="px-4 py-2 text-sm">{ltd.resultName}</td>
                                  </tr>
                                )
                              }

                              return (
                                <tr key={ltd.id} className="hover:bg-slate-50/50">
                                  <td className="px-4 py-2 text-slate-700 font-medium">{ltd.resultName}</td>
                                  <td className="px-4 py-2 text-center font-bold text-slate-900">{val}</td>
                                  <td className="px-4 py-2 text-center text-slate-500">{ltd.unit || '—'}</td>
                                  <td className="px-4 py-2 text-slate-500 whitespace-pre-wrap">{ltd.normalRange || '—'}</td>
                                </tr>
                              )
                            })}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <div className="bg-slate-50 p-4 rounded-xl border border-slate-100">
                      <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-1">Direct Result</h4>
                      <p className="text-sm font-semibold text-slate-800">{reports[0]?.value || 'No direct result recorded'}</p>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="border-t border-gray-150 pt-4 flex justify-end gap-3 mt-4">
          <button
            onClick={onClose}
            className="px-5 py-2 text-sm font-bold text-gray-700 bg-white border border-gray-200 rounded-xl hover:bg-slate-50 transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  )
}


// ─── OP Inline Form ───────────────────────────────────────────────────────────

function InlineDiagnosticForm({ encounterId, consultantId, onSaved }:
  { encounterId: string; consultantId?: string; onSaved: () => void }) {

  const [tests, setTests] = useState<DiagnosticOrderLinePayload[]>([])
  const [query, setQuery] = useState('')

  const { data: results = [] } = useQuery({
    queryKey: ['test-search', query],
    queryFn:  () => diagTestSearchApi.search(query),
    enabled:  query.length >= 2,
  })

  const saveMut = useMutation({
    mutationFn: () => opDiagnosticApi.save(encounterId, { items: tests }),
    onSuccess: () => {
      toast({ title: 'Diagnostic order saved', variant: 'success' })
      setTests([])
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const addTest = (test: DiagnosticOrderLinePayload) => {
    if (!tests.find(t => t.testName === test.testName)) {
      setTests(ts => [...ts, test])
    }
    setQuery('')
  }

  const removeTest = (idx: number) => setTests(ts => ts.filter((_, i) => i !== idx))

  return (
    <div className="border-t border-gray-200 pt-4">
      <div className="flex gap-4">
        <div className="flex-1 min-w-0 space-y-3">
          <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wide">Add Tests</h4>

          {/* Test search */}
          <div className="flex gap-2 relative">
            <div className="relative flex-1">
              <input
                value={query}
                onChange={e => setQuery(e.target.value)}
                placeholder="Search test name (min. 2 chars)…"
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {query.length >= 2 && results.length > 0 && (
                <ul className="absolute z-20 top-full left-0 right-0 bg-white border border-gray-300 rounded-lg shadow-md max-h-40 overflow-y-auto">
                  {results.map(t => (
                    <li key={t.id}>
                      <button className="w-full text-left px-3 py-1.5 text-xs hover:bg-[#C25727] hover:text-white transition-colors text-gray-900"
                        onClick={() => addTest({ diagnosticTestId: t.id, testName: t.name, category: t.category })}>
                        <span className="font-medium">{t.name}</span>
                        {t.category && <span className="opacity-75 ml-2">({t.category})</span>}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {/* Added tests list */}
          {tests.length > 0 && (
            <ul className="space-y-1">
              {tests.map((t, i) => (
                <li key={i} className="flex items-center justify-between px-3 py-1.5 bg-blue-50 rounded-lg text-xs">
                  <span className="font-medium text-blue-800">{t.testName}</span>
                  <button onClick={() => removeTest(i)} className="text-blue-400 hover:text-red-500">✕</button>
                </li>
              ))}
            </ul>
          )}

          {tests.length > 0 && (
            <button
              onClick={() => saveMut.mutate()}
              disabled={saveMut.isPending}
              className="px-4 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors">
              {saveMut.isPending ? 'Saving…' : 'SAVE ORDER'}
            </button>
          )}
        </div>

        <QuickAddPanel
          mode="TEST"
          consultantId={consultantId}
          encounterId={encounterId}
          onAddTest={test => addTest(test)}
        />
      </div>
    </div>
  )
}

// ─── IP Modal ────────────────────────────────────────────────────────────────

function DiagnosticOrderModal({ encounterId, consultantId, onClose, onSaved }:
  { encounterId: string; consultantId?: string; onClose: () => void; onSaved: () => void }) {

  const [tests, setTests] = useState<DiagnosticOrderLinePayload[]>([])
  const [query, setQuery] = useState('')
  const [requestedById, setRequestedById] = useState(consultantId ?? '')

  const { data: results = [] } = useQuery({
    queryKey: ['test-search', query],
    queryFn:  () => diagTestSearchApi.search(query),
    enabled:  query.length >= 2,
  })
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'], queryFn: consultantApi.getAll,
  })

  const saveMut = useMutation({
    mutationFn: () => ipDiagnosticApi.add(encounterId, {
      items: tests, requestedById: requestedById || undefined,
    }),
    onSuccess: () => {
      toast({ title: 'Diagnostic order added', variant: 'success' })
      onSaved()
    },
    onError: (e: Error) => toast({ title: 'Failed', description: e.message, variant: 'destructive' }),
  })

  const addTest = (test: DiagnosticOrderLinePayload) => {
    if (!tests.find(t => t.testName === test.testName)) setTests(ts => [...ts, test])
    setQuery('')
  }

  return (
    <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl my-8">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <h3 className="text-base font-bold text-gray-900">Add Diagnostic Order</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
        </div>

        <div className="p-6 flex gap-6">
          <div className="flex-1 min-w-0 space-y-4">
            {/* Requested By */}
            <div className="flex items-center gap-3">
              <label className="text-xs font-medium text-gray-600 shrink-0">Requested By</label>
              <ConsultantSearchInput
                consultants={consultants}
                value={requestedById}
                onChange={setRequestedById}
                className="flex-1"
                size="sm"
              />
            </div>

            {/* Test search */}
            <div className="relative">
              <input value={query} onChange={e => setQuery(e.target.value)}
                placeholder="Search test name (min. 2 chars)…"
                className="w-full px-2.5 py-1.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              {query.length >= 2 && results.length > 0 && (
                <ul className="absolute z-20 top-full left-0 right-0 bg-white border border-gray-300 rounded-lg shadow-md max-h-40 overflow-y-auto">
                  {results.map(t => (
                    <li key={t.id}>
                      <button className="w-full text-left px-3 py-1.5 text-xs hover:bg-[#C25727] hover:text-white transition-colors text-gray-900"
                        onClick={() => addTest({ diagnosticTestId: t.id, testName: t.name, category: t.category })}>
                        <span className="font-medium">{t.name}</span>
                        {t.category && <span className="opacity-75 ml-2">({t.category})</span>}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {tests.length > 0 && (
              <ul className="space-y-1">
                {tests.map((t, i) => (
                  <li key={i} className="flex items-center justify-between px-3 py-1.5 bg-blue-50 rounded-lg text-xs">
                    <span className="font-medium text-blue-800">{t.testName}</span>
                    <button onClick={() => setTests(ts => ts.filter((_, j) => j !== i))} className="text-blue-400 hover:text-red-500">✕</button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <QuickAddPanel
            mode="TEST"
            consultantId={consultantId}
            encounterId={encounterId}
            onAddTest={addTest}
          />
        </div>

        <div className="flex justify-end gap-2 px-6 py-4 border-t border-gray-200">
          <button onClick={onClose}
            className="px-4 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
            CANCEL
          </button>
          <button onClick={() => saveMut.mutate()}
            disabled={saveMut.isPending || tests.length === 0}
            className="px-3 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
            {saveMut.isPending ? 'Saving…' : 'ADD DIAGNOSTIC'}
          </button>
        </div>
      </div>
    </div>
  )
}
