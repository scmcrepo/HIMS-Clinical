import { useState } from 'react'
import DatePicker from '../../../components/shared/DatePicker'
import { PrintButton } from '../../../components/shared/PrintButton'
import {
  formatPaise,
  validateCommunication,
  validateDecision,
  type InsuranceDesk,
  type ModeOfCommunication,
  type TpaDecision,
} from '../insuranceDesk'
import type {
  InsurancePreAuthType,
  SubmitEnhancementApprovalCmd,
  SubmitEnhancementCmd,
} from '../../../services/insurance/insuranceApi'
import { AmountInput, Banner, Field, SaveBar, StageHeader, inputCls } from './formPrimitives'

// ── Stage 3 ─────────────────────────────────────────────────────────────────

export function EnhancementStageForm({
  desk,
  onSave,
  saving,
  onGoToBillLink,
}: {
  desk: InsuranceDesk
  onSave: (cmd: SubmitEnhancementCmd) => void
  saving: boolean
  onGoToBillLink: () => void
}) {
  const [type, setType] = useState<InsurancePreAuthType | ''>(
    (desk.enhancementType as InsurancePreAuthType) ?? '',
  )
  const [appliedDate, setAppliedDate] = useState(
    desk.enhancementAppliedDate?.slice(0, 10) ?? new Date().toISOString().slice(0, 10),
  )
  const [appliedTime, setAppliedTime] = useState(() => {
    if (desk.enhancementAppliedDate) {
      const d = new Date(desk.enhancementAppliedDate)
      return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    }
    const d = new Date()
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  })
  const [amount, setAmount] = useState<number | null>(desk.enhancementRequestedAmount)
  const [mode, setMode] = useState<ModeOfCommunication | ''>(
    desk.enhancementCommunicationToTpa || 'MAIL',
  )
  const [faxNo, setFaxNo] = useState(desk.enhancementFaxNo ?? '')
  const [mailId, setMailId] = useState(desk.enhancementMailId ?? '')
  const [reason, setReason] = useState(desk.reasonForEnhancement ?? '')
  const [error, setError] = useState<string | null>(null)

  // The server enforces this too and returns INSURANCE_BILL_NOT_LINKED. Showing
  // it here turns a failed round trip into a one-click fix.
  if (!desk.billLinked) {
    return (
      <div className="space-y-4">
        <StageHeader
          title="Enhancement request"
          description="Ask the TPA to raise the sanctioned limit mid-stay."
        />
        <Banner tone="danger">
          This claim has no credit bill linked yet. An enhancement asks the TPA for more money
          against charges that have to be evidenced, so the bill has to exist first.
        </Banner>
        <button
          onClick={onGoToBillLink}
          className="px-4 py-2 bg-neutral-600 text-white text-sm font-semibold rounded-lg hover:bg-neutral-700 transition-colors"
        >
          Link a bill
        </button>
      </div>
    )
  }

  const submit = () => {
    if (amount == null || amount <= 0) return setError('Enter the revised amount requested.')
    if (!reason.trim()) return setError('Record why the enhancement is needed.')
    const err = validateCommunication(mode || null, faxNo, mailId)
    if (err) return setError(err)
    setError(null)
    let appliedIso: string | undefined = undefined
    if (appliedDate) {
      const [hours, minutes] = (appliedTime || '00:00').split(':')
      const d = new Date(appliedDate)
      d.setHours(parseInt(hours || '0', 10), parseInt(minutes || '0', 10), 0, 0)
      appliedIso = d.toISOString()
    }
    onSave({
      ...(type ? { enhancementType: type } : {}),
      ...(appliedIso ? { appliedDate: appliedIso } : {}),
      requestedAmount: amount,
      communicationToTpa: mode as ModeOfCommunication,
      ...(mode === 'FAX' ? { faxNo } : {}),
      ...(mode === 'MAIL' ? { mailId } : {}),
      reasonForEnhancement: reason.trim(),
    })
  }

  return (
    <div className="space-y-4">
      <StageHeader
        title="Enhancement request"
        description="Ask the TPA to raise the sanctioned limit mid-stay."
        savedAt={desk.stageTimestamps.enhancement}
        action={
          desk.stageTimestamps.enhancement ? (
            <PrintButton
              templateType="ENHANCEMENT_REQUEST"
              params={{ id: desk.id }}
              variant="outline"
              label="Print request"
            />
          ) : undefined
        }
      />

      {desk.preauthApprovedLimit != null && (
        <p className="text-xs text-gray-500">
          Originally sanctioned: {formatPaise(desk.preauthApprovedLimit)}
        </p>
      )}

      <div className="grid grid-cols-2 gap-4">
        <Field label="Revised amount requested" required>
          <AmountInput
            valuePaise={amount}
            onChangePaise={setAmount}
            ariaLabel="Revised amount requested"
          />
        </Field>
        <Field label="Request type">
          <select
            value={type}
            onChange={e => setType(e.target.value as InsurancePreAuthType | '')}
            className={inputCls}
            aria-label="Request type"
          >
            <option value="">Select…</option>
            <option value="PLANNED">Planned</option>
            <option value="EMERGENCY">Emergency</option>
            <option value="DAY_CARE">Day care</option>
            <option value="MATERNITY">Maternity</option>
          </select>
        </Field>
        <Field label="Sent to TPA by" required>
          <select
            value={mode}
            onChange={e => setMode(e.target.value as ModeOfCommunication | '')}
            className={inputCls}
            aria-label="Sent to TPA by"
          >
            <option value="">Select…</option>
            <option value="MAIL">Mail</option>
          </select>
        </Field>
        {mode === 'FAX' && (
          <Field label="TPA fax number" required>
            <input
              value={faxNo}
              onChange={e => setFaxNo(e.target.value)}
              className={inputCls}
              aria-label="TPA fax number"
            />
          </Field>
        )}
        {mode === 'MAIL' && (
          <Field label="TPA mail id" required>
            <input
              type="email"
              value={mailId}
              onChange={e => setMailId(e.target.value)}
              className={inputCls}
              aria-label="TPA mail id"
            />
          </Field>
        )}
        <Field label="Sent on">
          <div className="flex items-center gap-2">
            <div className="flex-1">
              <DatePicker value={appliedDate} onChange={setAppliedDate} size="sm" />
            </div>
            <input
              type="time"
              value={appliedTime}
              onChange={e => setAppliedTime(e.target.value)}
              className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm bg-white text-gray-900 focus:outline-none focus:ring-2 focus:ring-neutral-900 focus:border-transparent transition-all shadow-sm"
              aria-label="Sent time"
            />
          </div>
        </Field>
      </div>

      <Field
        label="Reason for enhancement"
        required
        hint="Stored encrypted. An unexplained request will be queried by the TPA."
      >
        <textarea
          value={reason}
          onChange={e => setReason(e.target.value)}
          rows={3}
          placeholder="e.g. Extended ICU stay following post-operative sepsis"
          className={inputCls}
          aria-label="Reason for enhancement"
        />
      </Field>

      <SaveBar onSave={submit} saving={saving} error={error} label="Save request" />
    </div>
  )
}

// ── Stage 4 ─────────────────────────────────────────────────────────────────

export function EnhancementApprovalStageForm({
  desk,
  onSave,
  saving,
}: {
  desk: InsuranceDesk
  onSave: (cmd: SubmitEnhancementApprovalCmd) => void
  saving: boolean
}) {
  const [decision, setDecision] = useState<TpaDecision | ''>(desk.enhancementApprovalStatus ?? '')
  const [decidedOn, setDecidedOn] = useState(
    desk.enhancementDateOfApproval?.slice(0, 10) ?? new Date().toISOString().slice(0, 10),
  )
  const [decidedTime, setDecidedTime] = useState(() => {
    if (desk.enhancementDateOfApproval) {
      const d = new Date(desk.enhancementDateOfApproval)
      return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    }
    const d = new Date()
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  })
  const [mode, setMode] = useState<ModeOfCommunication | ''>(
    desk.enhancementCommunicationByTpa || 'MAIL',
  )
  const [limit, setLimit] = useState<number | null>(desk.enhancementApprovedLimit)
  const [reason, setReason] = useState(desk.enhancementRejectionReason ?? '')
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    const err = validateDecision(decision || null, limit, reason)
    if (err) return setError(err)
    setError(null)
    let decidedIso: string | undefined = undefined
    if (decidedOn) {
      const [hours, minutes] = (decidedTime || '00:00').split(':')
      const d = new Date(decidedOn)
      d.setHours(parseInt(hours || '0', 10), parseInt(minutes || '0', 10), 0, 0)
      decidedIso = d.toISOString()
    }
    onSave({
      approvalStatus: decision as TpaDecision,
      ...(decidedIso ? { dateOfApproval: decidedIso } : {}),
      ...(mode ? { communicationByTpa: mode } : {}),
      ...(decision === 'APPROVED' && limit != null ? { approvedLimit: limit } : {}),
      ...(decision === 'REJECTED' ? { rejectionReason: reason } : {}),
    })
  }

  return (
    <div className="space-y-4">
      <StageHeader
        title="Enhancement decision"
        description="What the TPA said about the revised limit."
        savedAt={desk.stageTimestamps.enhancementApproval}
      />

      {desk.enhancementApprovalStatus === 'REJECTED' && (
        <Banner tone="warning">
          The enhancement was declined, but the claim still proceeds for the originally sanctioned{' '}
          {formatPaise(desk.preauthApprovedLimit)}. The balance is recoverable from the patient
          under the signed letter of acceptance.
        </Banner>
      )}

      <div className="grid grid-cols-2 gap-4">
        <Field label="Decision" required>
          <select
            value={decision}
            onChange={e => setDecision(e.target.value as TpaDecision | '')}
            className={inputCls}
            aria-label="Enhancement decision"
          >
            <option value="">Awaiting reply</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </Field>
        <Field label="Decided on">
          <div className="flex items-center gap-2">
            <div className="flex-1">
              <DatePicker value={decidedOn} onChange={setDecidedOn} size="sm" />
            </div>
            <input
              type="time"
              value={decidedTime}
              onChange={e => setDecidedTime(e.target.value)}
              className="px-3 py-1.5 border border-gray-200 rounded-lg text-sm bg-white text-gray-900 focus:outline-none focus:ring-2 focus:ring-neutral-900 focus:border-transparent transition-all shadow-sm"
              aria-label="Decided time"
            />
          </div>
        </Field>
        {decision === 'APPROVED' && (
          <Field
            label="Revised limit sanctioned"
            required
            hint="The original sanction is kept separately, not overwritten."
          >
            <AmountInput
              valuePaise={limit}
              onChangePaise={setLimit}
              ariaLabel="Revised limit sanctioned"
            />
          </Field>
        )}
        <Field label="Received from TPA by">
          <select
            value={mode}
            onChange={e => setMode(e.target.value as ModeOfCommunication | '')}
            className={inputCls}
            aria-label="Received from TPA by"
          >
            <option value="">Select…</option>
            <option value="FAX">Fax</option>
            <option value="MAIL">Mail</option>
          </select>
        </Field>
      </div>

      {decision === 'REJECTED' && (
        <Field label="Reason for rejection" required hint="Stored encrypted.">
          <textarea
            value={reason}
            onChange={e => setReason(e.target.value)}
            rows={3}
            className={inputCls}
            aria-label="Reason for rejection"
          />
        </Field>
      )}

      <SaveBar onSave={submit} saving={saving} error={error} label="Save decision" />
    </div>
  )
}
