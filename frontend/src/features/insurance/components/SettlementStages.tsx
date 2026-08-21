import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import DatePicker from '../../../components/shared/DatePicker'
import { billingApi } from '../../../services/billing/billingApi'
import { attachmentApi, type Attachment } from '../../../services/attachment/attachmentApi'
import { toast } from '../../../hooks/useToast'
import {
  COURIER_LABELS,
  formatPaise,
  summariseChecklist,
  totalChequeAmount,
  validateDispatch,
  type ChecklistItem,
  type ChequeReceipt,
  type CourierVendor,
  type InsuranceDesk,
  type ModeOfDispatch,
} from '../insuranceDesk'
import type {
  SubmitDisallowanceCmd,
  SubmitDispatchCmd,
} from '../../../services/insurance/insuranceApi'
import { AmountInput, Banner, Field, SaveBar, StageHeader, inputCls } from './formPrimitives'

/**
 * The documents a TPA asks for on almost every claim. Offered as a starting
 * point so the clerk edits a list rather than types one — a blank grid gets
 * filled with three items and the missing fourth is what delays the claim.
 */
const COMMON_DOCUMENTS = [
  'Discharge Summary',
  'Final Itemised Bill',
  'Payment Receipts',
  'Investigation Reports',
  'Pharmacy Receipts',
  'Indoor Case Papers',
  'Implant Sticker / Invoice',
  'ID Proof',
]

// ── Stage 5 ─────────────────────────────────────────────────────────────────

export function ChecklistStageForm({
  desk,
  onSave,
  saving,
}: {
  desk: InsuranceDesk
  onSave: (items: ChecklistItem[]) => void
  saving: boolean
}) {
  const qc = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [items, setItems] = useState<ChecklistItem[]>(desk.checklist?.checklists ?? [])
  const summary = summariseChecklist(items)

  const { data: attachments = [], isLoading: loadingAttachments } = useQuery<Attachment[]>({
    queryKey: ['attachments', 'category', 'checkListEntry', desk.encounterId, desk.patientId],
    queryFn: () =>
      attachmentApi.getByCategory(
        'checkListEntry',
        desk.encounterId ?? undefined,
        desk.patientId ?? undefined,
      ),
  })

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      return attachmentApi.upload(
        file,
        'INSURANCE',
        desk.encounterId ?? undefined,
        desk.patientId ?? undefined,
        undefined,
        'checkListEntry',
      )
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['attachments', 'category', 'checkListEntry'] })
      toast({ title: 'Checklist attachment uploaded', variant: 'success' })
      if (fileInputRef.current) fileInputRef.current.value = ''
    },
    onError: (e: Error) => {
      toast({ title: 'Upload failed', description: e.message, variant: 'destructive' })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => attachmentApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['attachments', 'category', 'checkListEntry'] })
      toast({ title: 'Attachment deleted', variant: 'success' })
    },
    onError: (e: Error) => {
      toast({ title: 'Delete failed', description: e.message, variant: 'destructive' })
    },
  })

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      uploadMutation.mutate(file)
    }
  }

  const update = (idx: number, patch: Partial<ChecklistItem>) =>
    setItems(rows => rows.map((r, i) => (i === idx ? { ...r, ...patch } : r)))

  const addRow = (name = '') =>
    setItems(rows => [...rows, { name, toBeSubmit: 1, submitted: 0, nonSubmission: '' }])

  return (
    <div className="space-y-5">
      <StageHeader
        title="Pre-dispatch check-list"
        description="What the docket should contain, and what is actually in it."
        savedAt={desk.stageTimestamps.checkList}
      />

      {summary.shortfallItems > 0 && (
        <Banner tone="warning">
          {summary.shortfallItems} of {summary.total} document
          {summary.total === 1 ? '' : 's'} still short: {summary.pending.join(', ')}.
        </Banner>
      )}

      <div className="border border-gray-200 rounded-lg overflow-hidden">
        <table className="w-full text-sm" aria-label="Document check-list">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
              <th className="px-3 py-2 font-semibold text-gray-600">Document</th>
              <th className="px-3 py-2 font-semibold text-gray-600 w-24">Expected</th>
              <th className="px-3 py-2 font-semibold text-gray-600 w-24">Enclosed</th>
              <th className="px-3 py-2 font-semibold text-gray-600">If short, why</th>
              <th className="w-10" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {items.map((row, idx) => {
              const short = (row.submitted ?? 0) < (row.toBeSubmit ?? 0)
              return (
                <tr key={idx} className={short ? 'bg-amber-50/40' : undefined}>
                  <td className="px-3 py-2">
                    <input
                      value={row.name}
                      onChange={e => update(idx, { name: e.target.value })}
                      className="w-full px-2 py-1 border border-gray-200 rounded text-sm"
                      aria-label={`Document ${idx + 1} name`}
                    />
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="number"
                      min={0}
                      value={row.toBeSubmit}
                      onChange={e => update(idx, { toBeSubmit: Number(e.target.value) })}
                      className="w-full px-2 py-1 border border-gray-200 rounded text-sm"
                      aria-label={`${row.name || 'Document'} expected count`}
                    />
                  </td>
                  <td className="px-3 py-2">
                    <input
                      type="number"
                      min={0}
                      value={row.submitted}
                      onChange={e => update(idx, { submitted: Number(e.target.value) })}
                      className="w-full px-2 py-1 border border-gray-200 rounded text-sm"
                      aria-label={`${row.name || 'Document'} enclosed count`}
                    />
                  </td>
                  <td className="px-3 py-2">
                    <input
                      value={row.nonSubmission ?? ''}
                      onChange={e => update(idx, { nonSubmission: e.target.value })}
                      placeholder={short ? 'e.g. lost by attender' : ''}
                      disabled={!short}
                      className="w-full px-2 py-1 border border-gray-200 rounded text-sm disabled:bg-gray-50"
                      aria-label={`${row.name || 'Document'} shortfall reason`}
                    />
                  </td>
                  <td className="px-3 py-2 text-center">
                    <button
                      onClick={() => setItems(rows => rows.filter((_, i) => i !== idx))}
                      className="text-gray-400 hover:text-red-600 text-sm"
                      aria-label={`Remove ${row.name || 'document'}`}
                    >
                      ×
                    </button>
                  </td>
                </tr>
              )
            })}
            {items.length === 0 && (
              <tr>
                <td colSpan={5} className="px-3 py-8 text-center text-gray-400 text-sm">
                  No documents listed yet. Add the ones this TPA asks for.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          onClick={() => addRow()}
          className="px-3 py-1.5 border border-gray-200 text-xs text-gray-600 rounded-lg hover:bg-gray-50 font-medium"
        >
          + Add document
        </button>
        {COMMON_DOCUMENTS.filter(d => !items.some(i => i.name === d)).map(d => (
          <button
            key={d}
            onClick={() => addRow(d)}
            className="px-3 py-1.5 border border-dashed border-gray-300 text-xs text-gray-500 rounded-lg hover:bg-gray-50"
          >
            + {d}
          </button>
        ))}
      </div>

      {/* ── Document Upload / Attachment Section (Matching SCMC) ── */}
      <div className="border-t border-gray-200 pt-4 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <svg
              className="w-4 h-4 text-gray-500"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13"
              />
            </svg>
            <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wide">
              Signed Check-list Attachment / Docket
            </h4>
          </div>
          <div>
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileUpload}
              className="hidden"
              accept=".pdf,.png,.jpg,.jpeg,.doc,.docx"
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploadMutation.isPending}
              className="px-3 py-1.5 bg-gray-100 hover:bg-gray-200 text-gray-700 border border-gray-300 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-colors disabled:opacity-50"
            >
              <svg className="w-3.5 h-3.5 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4" />
              </svg>
              {uploadMutation.isPending ? 'Uploading…' : 'Attach File'}
            </button>
          </div>
        </div>

        {loadingAttachments && <p className="text-xs text-gray-400">Loading attachments…</p>}

        {attachments.length > 0 ? (
          <div className="border border-gray-200 rounded-lg overflow-hidden bg-white">
            <table className="w-full text-xs" aria-label="Checklist attachments">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-100 text-left text-gray-500">
                  <th className="px-3 py-2 font-medium">File Name</th>
                  <th className="px-3 py-2 font-medium w-36">Uploaded</th>
                  <th className="px-3 py-2 font-medium text-right w-28">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {attachments.map(att => (
                  <tr key={att.id} className="hover:bg-gray-50">
                    <td className="px-3 py-2 text-gray-800 font-medium flex items-center gap-2">
                      <svg className="w-4 h-4 text-blue-500 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                      </svg>
                      <span className="truncate max-w-sm" title={att.fileName}>
                        {att.fileName}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-gray-500">
                      {att.createdAt ? new Date(att.createdAt).toLocaleDateString() : '—'}
                    </td>
                    <td className="px-3 py-2 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <a
                          href={attachmentApi.getDownloadUrl(att.id)}
                          target="_blank"
                          rel="noreferrer"
                          className="p-1 text-blue-600 hover:text-blue-800 hover:bg-blue-50 rounded"
                          title="Download attachment"
                        >
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                          </svg>
                        </a>
                        <button
                          type="button"
                          onClick={() => {
                            if (window.confirm(`Delete ${att.fileName}?`)) {
                              deleteMutation.mutate(att.id)
                            }
                          }}
                          disabled={deleteMutation.isPending}
                          className="p-1 text-red-500 hover:text-red-700 hover:bg-red-50 rounded"
                          title="Delete attachment"
                        >
                          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div
            onClick={() => fileInputRef.current?.click()}
            className="border-2 border-dashed border-gray-200 hover:border-gray-300 rounded-lg p-4 text-center cursor-pointer bg-gray-50/50 hover:bg-gray-50 transition-colors"
          >
            <p className="text-xs text-gray-500 font-medium">
              No checklist attachment found. Click here to attach signed checklist document.
            </p>
          </div>
        )}
      </div>

      <SaveBar
        onSave={() => onSave(items)}
        saving={saving}
        disabled={items.some(i => !i.name.trim())}
        error={items.some(i => !i.name.trim()) ? 'Every row needs a document name.' : null}
        label="Save check-list"
      />
    </div>
  )
}

// ── Stage 6 ─────────────────────────────────────────────────────────────────

export function DispatchStageForm({
  desk,
  onSave,
  saving,
}: {
  desk: InsuranceDesk
  onSave: (cmd: SubmitDispatchCmd) => void
  saving: boolean
}) {
  const [mode, setMode] = useState<ModeOfDispatch | ''>(desk.modeOfDispatch ?? 'COURIER')
  const [courier, setCourier] = useState<CourierVendor | ''>(desk.courier ?? '')
  const [podNo, setPodNo] = useState(desk.podNo ?? '')
  const [mailId, setMailId] = useState(desk.dispatchMailId ?? '')
  const [dispatchDate, setDispatchDate] = useState(
    desk.dispatchDate?.slice(0, 10) ?? new Date().toISOString().slice(0, 10),
  )
  const [dispatchedBy, setDispatchedBy] = useState(desk.dispatchedBy ?? '')
  const [delay, setDelay] = useState(desk.reasonForDelay ?? '')
  const [error, setError] = useState<string | null>(null)

  const shortfall = summariseChecklist(desk.checklist?.checklists ?? [])

  const submit = () => {
    const err = validateDispatch({
      modeOfDispatch: mode || null,
      courier: courier || null,
      podNo,
      dispatchMailId: mailId,
      dispatchedBy,
      reasonForDelay: delay,
    })
    if (err) return setError(err)
    setError(null)
    onSave({
      modeOfDispatch: mode as ModeOfDispatch,
      ...(mode === 'COURIER' ? { courier: courier as CourierVendor, podNo: podNo.trim() } : {}),
      ...(mode === 'EMAIL' ? { dispatchMailId: mailId.trim() } : {}),
      ...(dispatchDate ? { dispatchDate: new Date(dispatchDate).toISOString() } : {}),
      ...(dispatchedBy ? { dispatchedBy: dispatchedBy.trim() } : {}),
      ...(delay ? { reasonForDelay: delay.trim() } : {}),
    })
  }

  return (
    <div className="space-y-4">
      <StageHeader
        title="Dispatch Entry"
        description="How the docket left the hospital, POD consignment number, and tracking details."
        savedAt={desk.stageTimestamps.dispatch}
      />

      {/* A warning, not a block. Sometimes a TPA accepts a partial docket and
          the missing page follows — the desk knows, and the system should not
          overrule it. */}
      {shortfall.shortfallItems > 0 && (
        <Banner tone="warning">
          The check-list is still short on {shortfall.pending.join(', ')}. Dispatching now usually
          earns a query from the TPA.
        </Banner>
      )}

      <div className="grid grid-cols-2 gap-4">
        {/* Row 1: Date of Dispatch and Mode of Dispatch */}
        <Field label="Date of Dispatch" required>
          <DatePicker value={dispatchDate} onChange={setDispatchDate} size="sm" />
        </Field>

        <Field label="Mode of Dispatch" required>
          <select
            value={mode}
            onChange={e => setMode(e.target.value as ModeOfDispatch | '')}
            className={inputCls}
            aria-label="Mode of dispatch"
          >
            <option value="COURIER">Courier</option>
            <option value="EMAIL">Email</option>
          </select>
        </Field>

        {/* Row 2: POD NO and Courier/Email details */}
        <Field
          label="POD NO"
          required={mode === 'COURIER'}
          hint="Consignment tracking number — proof of delivery."
        >
          <input
            value={podNo}
            onChange={e => setPodNo(e.target.value)}
            placeholder="Enter the Pod No"
            className={inputCls}
            aria-label="POD number"
          />
        </Field>

        {mode === 'COURIER' ? (
          <Field label="Courier Name" required>
            <select
              value={courier}
              onChange={e => setCourier(e.target.value as CourierVendor | '')}
              className={inputCls}
              aria-label="Courier"
            >
              <option value="">Select Courier…</option>
              {(Object.keys(COURIER_LABELS) as CourierVendor[]).map(c => (
                <option key={c} value={c}>
                  {COURIER_LABELS[c]}
                </option>
              ))}
            </select>
          </Field>
        ) : (
          <Field label="Dispatch Mail ID" required>
            <input
              type="email"
              value={mailId}
              onChange={e => setMailId(e.target.value)}
              placeholder="claims@tpa.example"
              className={inputCls}
              aria-label="Destination mail id"
            />
          </Field>
        )}

        {/* Row 3: Dispatched By and Reason for Delay */}
        <Field label="Dispatched By" required>
          <input
            value={dispatchedBy}
            onChange={e => setDispatchedBy(e.target.value)}
            placeholder="Enter the Dispatched By"
            className={inputCls}
            aria-label="Dispatched by"
          />
        </Field>

        <Field label="Reason For Delay">
          <input
            value={delay}
            onChange={e => setDelay(e.target.value)}
            placeholder="Reason for delay (if any)"
            className={inputCls}
            aria-label="Reason for delay"
          />
        </Field>
      </div>

      <SaveBar onSave={submit} saving={saving} error={error} label="Record dispatch" />
    </div>
  )
}

// ── Stage 7 ─────────────────────────────────────────────────────────────────

export function DisallowanceStageForm({
  desk,
  onSave,
  saving,
}: {
  desk: InsuranceDesk
  onSave: (cmd: SubmitDisallowanceCmd) => void
  saving: boolean
}) {
  const todayStr = () => new Date().toISOString().split('T')[0]

  const [paymentType, setPaymentType] = useState<'CHEQUE' | 'FUND_TRANSFER'>('CHEQUE')
  const [cheques, setCheques] = useState<ChequeReceipt[]>(() => {
    const existing = desk.cheques ?? []
    return existing.map(c => ({
      ...c,
      chequeDate: c.chequeDate || todayStr(),
    }))
  })
  const [deductions, setDeductions] = useState<Record<string, number>>({})
  const [error, setError] = useState<string | null>(null)

  // The bill's charge lines are what deductions are keyed against, so they have
  // to be fetched — the desk payload carries the claim, not the bill.
  const { data: bill, isLoading: billLoading } = useQuery({
    queryKey: ['bill', desk.billId],
    queryFn: () => billingApi.getBillById(desk.billId!),
    enabled: Boolean(desk.billId),
  })

  const totalReceived = totalChequeAmount(cheques)
  const totalDisallowed = Object.values(deductions).reduce((a, b) => a + (b || 0), 0)

  const updateCheque = (idx: number, patch: Partial<ChequeReceipt>) =>
    setCheques(rows => rows.map((r, i) => (i === idx ? { ...r, ...patch } : r)))

  const submit = () => {
    if (paymentType === 'CHEQUE' && cheques.some(c => !c.chequeNo?.trim())) {
      return setError('Every receipt needs a cheque or UTR number.')
    }
    if (
      paymentType === 'FUND_TRANSFER' &&
      cheques.some(c => !(c.accountNo?.trim() || c.chequeNo?.trim()))
    ) {
      return setError('Every receipt needs an account number.')
    }
    if (cheques.some(c => !c.amount || c.amount <= 0)) {
      return setError('Every receipt needs an amount above zero.')
    }
    setError(null)
    onSave({
      cheques,
      disallowances: Object.entries(deductions).map(([chargeLineItemId, disallowedAmount]) => ({
        chargeLineItemId,
        disallowedAmount,
      })),
    })
  }

  return (
    <div className="space-y-5">
      <StageHeader
        title="Settlement"
        description="What the TPA paid, and what it refused to pay."
        savedAt={desk.stageTimestamps.disallowance}
      />

      {/* ── Cheques / Receipts ── */}
      <section className="space-y-3">
        <div className="max-w-xs">
          <Field label="Payment Mode">
            <select
              value={paymentType}
              onChange={e => setPaymentType(e.target.value as 'CHEQUE' | 'FUND_TRANSFER')}
              className={inputCls}
              aria-label="Payment Mode"
            >
              <option value="CHEQUE">Cheque</option>
              <option value="FUND_TRANSFER">Fund Transfer</option>
            </select>
          </Field>
        </div>

        <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wide">
          Receipts
        </h4>

        <div className="border border-gray-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm" aria-label="Cheque receipts">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                <th className="px-3 py-2 font-semibold text-gray-600">
                  {paymentType === 'CHEQUE' ? 'Cheque / UTR' : 'Account Number'}
                </th>
                <th className="px-3 py-2 font-semibold text-gray-600 w-44">Date</th>
                <th className="px-3 py-2 font-semibold text-gray-600">Drawn on</th>
                <th className="px-3 py-2 font-semibold text-gray-600 w-36">Amount</th>
                <th className="w-10" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {cheques.map((c, idx) => (
                <tr key={c.id ?? idx}>
                  <td className="px-3 py-2">
                    {paymentType === 'CHEQUE' ? (
                      <input
                        value={c.chequeNo}
                        onChange={e => updateCheque(idx, { chequeNo: e.target.value })}
                        placeholder="Cheque / UTR"
                        className="w-full px-2 py-1 border border-gray-200 rounded text-sm"
                        aria-label={`Receipt ${idx + 1} number`}
                      />
                    ) : (
                      <input
                        value={c.accountNo ?? c.chequeNo}
                        onChange={e =>
                          updateCheque(idx, {
                            accountNo: e.target.value,
                            chequeNo: e.target.value,
                          })
                        }
                        placeholder="Account Number"
                        className="w-full px-2 py-1 border border-gray-200 rounded text-sm"
                        aria-label={`Receipt ${idx + 1} account number`}
                      />
                    )}
                  </td>
                  <td className="px-3 py-2">
                    <DatePicker
                      value={c.chequeDate || todayStr()}
                      onChange={v => updateCheque(idx, { chequeDate: v })}
                      size="sm"
                    />
                  </td>
                  <td className="px-3 py-2">
                    <input
                      value={c.drawnOn ?? ''}
                      onChange={e => updateCheque(idx, { drawnOn: e.target.value })}
                      placeholder="Bank"
                      className="w-full px-2 py-1 border border-gray-200 rounded text-sm"
                      aria-label={`Receipt ${idx + 1} bank`}
                    />
                  </td>
                  <td className="px-3 py-2">
                    <AmountInput
                      valuePaise={c.amount ?? null}
                      onChangePaise={v => updateCheque(idx, { amount: v ?? 0 })}
                      ariaLabel={`Receipt ${idx + 1} amount`}
                    />
                  </td>
                  <td className="px-3 py-2 text-center">
                    <button
                      onClick={() => setCheques(rows => rows.filter((_, i) => i !== idx))}
                      className="text-gray-400 hover:text-red-600"
                      aria-label={`Remove receipt ${idx + 1}`}
                    >
                      ×
                    </button>
                  </td>
                </tr>
              ))}
              {cheques.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-gray-400 text-sm">
                    No payment recorded yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="flex items-center justify-between">
          <button
            onClick={() =>
              setCheques(rows => [
                ...rows,
                { chequeNo: '', amount: 0, chequeDate: todayStr() },
              ])
            }
            className="px-3 py-1.5 border border-gray-200 text-xs text-gray-600 rounded-lg hover:bg-gray-50"
          >
            + Add receipt
          </button>
          <p className="text-xs text-gray-600">
            Received: <span className="font-semibold">{formatPaise(totalReceived)}</span>
          </p>
        </div>
        {/* Removing a row deletes it server-side, because the grid submits the
            whole list. Saying so beats a surprise. */}
        {cheques.length !== (desk.cheques?.length ?? 0) && (
          <p className="text-[11px] text-gray-400">
            Saving replaces the stored receipts with this list.
          </p>
        )}
      </section>

      {/* ── Deductions ── */}
      <section className="space-y-2">
        <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wide">
          Disallowed charges
        </h4>

        {!desk.billLinked && (
          <Banner tone="info">
            No bill is linked, so there are no charges to deduct against. Receipts can still be
            recorded above.
          </Banner>
        )}

        {desk.billLinked && billLoading && <p className="text-xs text-gray-400">Loading charges…</p>}

        {desk.billLinked && bill && (
          <div className="border border-gray-200 rounded-lg overflow-hidden max-h-72 overflow-y-auto">
            <table className="w-full text-sm" aria-label="Bill charges">
              <thead className="sticky top-0">
                <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                  <th className="px-3 py-2 font-semibold text-gray-600">Charge</th>
                  <th className="px-3 py-2 font-semibold text-gray-600 text-right w-32">Billed</th>
                  <th className="px-3 py-2 font-semibold text-gray-600 w-40">Disallowed</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {(bill.chargeLineItems ?? []).map(line => {
                  const billed = (line.amount ?? 0) - (line.discountAmount ?? 0)
                  const current = deductions[line.id] ?? line.disallowedAmount ?? 0
                  return (
                    <tr key={line.id}>
                      <td className="px-3 py-2 text-gray-800">{line.itemName ?? 'Charge'}</td>
                      <td className="px-3 py-2 text-right text-gray-600">{formatPaise(billed)}</td>
                      <td className="px-3 py-2">
                        <AmountInput
                          valuePaise={current}
                          onChangePaise={v =>
                            setDeductions(d => ({ ...d, [line.id]: v ?? 0 }))
                          }
                          ariaLabel={`Disallowed on ${line.itemName ?? 'charge'}`}
                        />
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {totalDisallowed > 0 && (
          <p className="text-xs text-gray-600">
            Total disallowed:{' '}
            <span className="font-semibold text-red-700">{formatPaise(totalDisallowed)}</span>{' '}
            — recoverable from the patient under the letter of acceptance.
          </p>
        )}
      </section>

      <SaveBar onSave={submit} saving={saving} error={error} label="Record settlement" />
    </div>
  )
}
