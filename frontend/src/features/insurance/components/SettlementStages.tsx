import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import DatePicker from '../../../components/shared/DatePicker'
import { billingApi } from '../../../services/billing/billingApi'
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
  const [items, setItems] = useState<ChecklistItem[]>(desk.checklist?.checklists ?? [])
  const summary = summariseChecklist(items)

  const update = (idx: number, patch: Partial<ChecklistItem>) =>
    setItems(rows => rows.map((r, i) => (i === idx ? { ...r, ...patch } : r)))

  const addRow = (name = '') =>
    setItems(rows => [...rows, { name, toBeSubmit: 1, submitted: 0, nonSubmission: '' }])

  return (
    <div className="space-y-4">
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
          className="px-3 py-1.5 border border-gray-200 text-xs text-gray-600 rounded-lg hover:bg-gray-50"
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
  const [mode, setMode] = useState<ModeOfDispatch | ''>(desk.modeOfDispatch ?? '')
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
    })
    if (err) return setError(err)
    setError(null)
    onSave({
      modeOfDispatch: mode as ModeOfDispatch,
      ...(mode === 'COURIER' ? { courier: courier as CourierVendor, podNo } : {}),
      ...(mode === 'EMAIL' ? { dispatchMailId: mailId } : {}),
      ...(dispatchDate ? { dispatchDate: new Date(dispatchDate).toISOString() } : {}),
      ...(dispatchedBy ? { dispatchedBy } : {}),
      ...(delay ? { reasonForDelay: delay } : {}),
    })
  }

  return (
    <div className="space-y-4">
      <StageHeader
        title="Dispatch"
        description="How the docket left the hospital, and the proof it did."
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
        <Field label="Mode of dispatch" required>
          <select
            value={mode}
            onChange={e => setMode(e.target.value as ModeOfDispatch | '')}
            className={inputCls}
            aria-label="Mode of dispatch"
          >
            <option value="">Select…</option>
            <option value="COURIER">Courier</option>
            <option value="EMAIL">Email</option>
          </select>
        </Field>
        <Field label="Dispatched on">
          <DatePicker value={dispatchDate} onChange={setDispatchDate} size="sm" />
        </Field>

        {mode === 'COURIER' && (
          <>
            <Field label="Courier" required>
              <select
                value={courier}
                onChange={e => setCourier(e.target.value as CourierVendor | '')}
                className={inputCls}
                aria-label="Courier"
              >
                <option value="">Select…</option>
                {(Object.keys(COURIER_LABELS) as CourierVendor[]).map(c => (
                  <option key={c} value={c}>
                    {COURIER_LABELS[c]}
                  </option>
                ))}
              </select>
            </Field>
            <Field
              label="POD / consignment number"
              required
              hint="The only proof of delivery if the TPA denies receipt."
            >
              <input
                value={podNo}
                onChange={e => setPodNo(e.target.value)}
                className={inputCls}
                aria-label="POD number"
              />
            </Field>
          </>
        )}

        {mode === 'EMAIL' && (
          <Field label="Sent to" required>
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

        <Field label="Dispatched by">
          <input
            value={dispatchedBy}
            onChange={e => setDispatchedBy(e.target.value)}
            placeholder="Staff name"
            className={inputCls}
            aria-label="Dispatched by"
          />
        </Field>
      </div>

      <Field label="Reason for delay" hint="Only if the docket missed the agreed turnaround.">
        <textarea
          value={delay}
          onChange={e => setDelay(e.target.value)}
          rows={2}
          className={inputCls}
          aria-label="Reason for delay"
        />
      </Field>

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
  const [cheques, setCheques] = useState<ChequeReceipt[]>(desk.cheques ?? [])
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
    if (cheques.some(c => !c.chequeNo?.trim())) {
      return setError('Every receipt needs a cheque or UTR number.')
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

      {/* ── Cheques ── */}
      <section className="space-y-2">
        <h4 className="text-xs font-semibold text-gray-700 uppercase tracking-wide">
          Receipts
        </h4>
        <div className="border border-gray-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm" aria-label="Cheque receipts">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-100 text-left text-xs">
                <th className="px-3 py-2 font-semibold text-gray-600">Cheque / UTR</th>
                <th className="px-3 py-2 font-semibold text-gray-600 w-36">Date</th>
                <th className="px-3 py-2 font-semibold text-gray-600">Drawn on</th>
                <th className="px-3 py-2 font-semibold text-gray-600 w-36">Amount</th>
                <th className="w-10" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {cheques.map((c, idx) => (
                <tr key={c.id ?? idx}>
                  <td className="px-3 py-2">
                    <input
                      value={c.chequeNo}
                      onChange={e => updateCheque(idx, { chequeNo: e.target.value })}
                      className="w-full px-2 py-1 border border-gray-200 rounded text-sm"
                      aria-label={`Receipt ${idx + 1} number`}
                    />
                  </td>
                  <td className="px-3 py-2">
                    <DatePicker
                      value={c.chequeDate ?? ''}
                      onChange={v => updateCheque(idx, { chequeDate: v })}
                      size="xs"
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
              setCheques(rows => [...rows, { chequeNo: '', amount: 0, chequeDate: null }])
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
