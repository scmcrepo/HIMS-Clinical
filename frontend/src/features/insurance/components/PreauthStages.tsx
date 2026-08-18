import { useState } from 'react'
import DatePicker from '../../../components/shared/DatePicker'
import { PrintButton } from '../../../components/shared/PrintButton'
import {
  daysUntilCardExpiry,
  formatPaise,
  validateCommunication,
  validateDecision,
  type InsuranceDesk,
  type ModeOfCommunication,
  type TpaDecision,
} from '../insuranceDesk'
import type {
  SubmitPreauthCmd,
  SubmitPreauthApprovalCmd,
} from '../../../services/insurance/insuranceApi'
import type { InsurancePreAuthType } from '../../../services/insurance/insuranceApi'
import { AmountInput, Banner, Field, SaveBar, StageHeader, inputCls } from './formPrimitives'

const PREAUTH_TYPES: Array<{ value: InsurancePreAuthType; label: string }> = [
  { value: 'PLANNED', label: 'Planned admission' },
  { value: 'EMERGENCY', label: 'Emergency' },
  { value: 'DAY_CARE', label: 'Day care' },
  { value: 'OPD', label: 'OPD' },
  { value: 'MATERNITY', label: 'Maternity' },
]

/** Fax number / mail id pair, shown according to the chosen mode. */
function CommunicationFields({
  mode,
  faxNo,
  mailId,
  onMode,
  onFax,
  onMail,
  label,
}: {
  mode: ModeOfCommunication | ''
  faxNo: string
  mailId: string
  onMode: (m: ModeOfCommunication | '') => void
  onFax: (v: string) => void
  onMail: (v: string) => void
  label: string
}) {
  return (
    <>
      <Field label={label} required>
        <select
          value={mode}
          onChange={e => onMode(e.target.value as ModeOfCommunication | '')}
          className={inputCls}
          aria-label={label}
        >
          <option value="">Select…</option>
          <option value="FAX">Fax</option>
          <option value="MAIL">Mail</option>
        </select>
      </Field>
      {/* Only the field the mode needs is shown. Rendering both invites a clerk
          to fill the wrong one, and the server clears whichever does not apply. */}
      {mode === 'FAX' && (
        <Field label="TPA fax number" required>
          <input
            value={faxNo}
            onChange={e => onFax(e.target.value)}
            placeholder="044-2345 6789"
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
            onChange={e => onMail(e.target.value)}
            placeholder="claims@tpa.example"
            className={inputCls}
            aria-label="TPA mail id"
          />
        </Field>
      )}
    </>
  )
}

// ── Stage 1 ─────────────────────────────────────────────────────────────────

export function PreauthStageForm({
  desk,
  onSave,
  saving,
}: {
  desk: InsuranceDesk
  onSave: (cmd: SubmitPreauthCmd) => void
  saving: boolean
}) {
  const [cardValidity, setCardValidity] = useState(desk.cardValidity?.slice(0, 10) ?? '')
  const [policyNumber, setPolicyNumber] = useState(desk.policyNumber ?? '')
  const [preAuthType, setPreAuthType] = useState<InsurancePreAuthType | ''>(
    (desk.preAuthType as InsurancePreAuthType) ?? '',
  )
  const [mode, setMode] = useState<ModeOfCommunication | ''>(desk.preauthCommunicationToTpa ?? '')
  const [faxNo, setFaxNo] = useState(desk.preauthFaxNo ?? '')
  const [mailId, setMailId] = useState(desk.preauthMailId ?? '')
  const [appliedDate, setAppliedDate] = useState(
    desk.preauthAppliedDate?.slice(0, 10) ?? new Date().toISOString().slice(0, 10),
  )
  const [amount, setAmount] = useState<number | null>(desk.preauthRequestedAmount)
  const [error, setError] = useState<string | null>(null)

  const daysLeft = daysUntilCardExpiry(cardValidity || null)

  const submit = () => {
    const err = validateCommunication(mode || null, faxNo, mailId)
    if (err) return setError(err)
    setError(null)
    onSave({
      ...(policyNumber ? { policyNumber } : {}),
      ...(cardValidity ? { cardValidity } : {}),
      ...(preAuthType ? { preAuthType } : {}),
      communicationToTpa: mode as ModeOfCommunication,
      ...(mode === 'FAX' ? { faxNo } : {}),
      ...(mode === 'MAIL' ? { mailId } : {}),
      ...(appliedDate ? { appliedDate: new Date(appliedDate).toISOString() } : {}),
      ...(amount != null ? { requestedAmount: amount } : {}),
    })
  }

  return (
    <div className="space-y-4">
      <StageHeader
        title="Pre-authorisation request"
        description="What was sent to the TPA, and where it was sent."
        savedAt={desk.stageTimestamps.preauth}
      />

      {/* Recorded, never blocked — the desk must be able to capture what the
          patient actually presented. */}
      {daysLeft !== null && daysLeft < 0 && (
        <Banner tone="warning">
          This health card lapsed {Math.abs(daysLeft)} day{Math.abs(daysLeft) === 1 ? '' : 's'} ago.
          Confirm cover with the TPA before treating this as a cashless admission.
        </Banner>
      )}
      {daysLeft !== null && daysLeft >= 0 && daysLeft <= 7 && (
        <Banner tone="warning">
          This health card lapses in {daysLeft} day{daysLeft === 1 ? '' : 's'}.
        </Banner>
      )}

      <div className="grid grid-cols-2 gap-4">
        <Field label="Policy number">
          <input
            value={policyNumber}
            onChange={e => setPolicyNumber(e.target.value)}
            className={inputCls}
            aria-label="Policy number"
          />
        </Field>
        <Field label="Card valid until" hint="Leave blank if the card shows no expiry.">
          <DatePicker value={cardValidity} onChange={setCardValidity} size="sm" />
        </Field>
        <Field label="Pre-auth type">
          <select
            value={preAuthType}
            onChange={e => setPreAuthType(e.target.value as InsurancePreAuthType | '')}
            className={inputCls}
            aria-label="Pre-auth type"
          >
            <option value="">Select…</option>
            {PREAUTH_TYPES.map(t => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Amount requested" hint="Estimated hospitalisation cost.">
          <AmountInput
            valuePaise={amount}
            onChangePaise={setAmount}
            ariaLabel="Amount requested"
          />
        </Field>
        <CommunicationFields
          label="Sent to TPA by"
          mode={mode}
          faxNo={faxNo}
          mailId={mailId}
          onMode={setMode}
          onFax={setFaxNo}
          onMail={setMailId}
        />
        <Field label="Sent on">
          <DatePicker value={appliedDate} onChange={setAppliedDate} size="sm" />
        </Field>
      </div>

      <SaveBar onSave={submit} saving={saving} error={error} label="Save request" />
    </div>
  )
}

// ── Stage 2 ─────────────────────────────────────────────────────────────────

export function PreauthApprovalStageForm({
  desk,
  onSave,
  saving,
}: {
  desk: InsuranceDesk
  onSave: (cmd: SubmitPreauthApprovalCmd) => void
  saving: boolean
}) {
  const [claimNo, setClaimNo] = useState(desk.claimNo ?? '')
  const [decision, setDecision] = useState<TpaDecision | ''>(desk.preauthApprovalStatus ?? '')
  const [decidedOn, setDecidedOn] = useState(
    desk.preauthDateOfApproval?.slice(0, 10) ?? new Date().toISOString().slice(0, 10),
  )
  const [mode, setMode] = useState<ModeOfCommunication | ''>(desk.preauthCommunicationByTpa ?? '')
  const [faxNo, setFaxNo] = useState(desk.preauthApproveFaxNo ?? '')
  const [mailId, setMailId] = useState(desk.preauthApproveMailId ?? '')
  const [limit, setLimit] = useState<number | null>(desk.preauthApprovedLimit)
  const [reason, setReason] = useState(desk.preauthRejectionReason ?? '')
  const [error, setError] = useState<string | null>(null)

  const submit = () => {
    if (!claimNo.trim()) return setError('Enter the TPA claim number.')
    const err = validateDecision(decision || null, limit, reason)
    if (err) return setError(err)
    setError(null)
    onSave({
      claimNo: claimNo.trim(),
      approvalStatus: decision as TpaDecision,
      ...(decidedOn ? { dateOfApproval: new Date(decidedOn).toISOString() } : {}),
      ...(mode ? { communicationByTpa: mode } : {}),
      ...(mode === 'FAX' ? { approveFaxNo: faxNo } : {}),
      ...(mode === 'MAIL' ? { approveMailId: mailId } : {}),
      ...(decision === 'APPROVED' && limit != null ? { approvedLimit: limit } : {}),
      ...(decision === 'REJECTED' ? { rejectionReason: reason } : {}),
    })
  }

  return (
    <div className="space-y-4">
      <StageHeader
        title="TPA decision"
        description="What the TPA sent back, and the docket number it filed the claim under."
        savedAt={desk.stageTimestamps.preauthApproval}
        action={
          desk.preauthApprovalStatus === 'APPROVED' ? (
            <PrintButton
              templateType="LETTER_ACCEPTANCE"
              params={{ id: desk.id }}
              variant="outline"
              label="Print letter of acceptance"
            />
          ) : undefined
        }
      />

      {desk.preauthApprovalStatus === 'APPROVED' && (
        <Banner tone="info">
          A sanction is not a guarantee of payment. Have the patient or attender sign the letter of
          acceptance so any amount disallowed at settlement is recoverable.
        </Banner>
      )}

      <div className="grid grid-cols-2 gap-4">
        <Field label="TPA claim number" required hint="The docket number the TPA filed this under.">
          <input
            value={claimNo}
            onChange={e => setClaimNo(e.target.value)}
            className={inputCls}
            aria-label="TPA claim number"
          />
        </Field>
        <Field label="Decision" required>
          <select
            value={decision}
            onChange={e => setDecision(e.target.value as TpaDecision | '')}
            className={inputCls}
            aria-label="TPA decision"
          >
            <option value="">Awaiting reply</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </Field>
        <Field label="Decided on">
          <DatePicker value={decidedOn} onChange={setDecidedOn} size="sm" />
        </Field>
        {decision === 'APPROVED' && (
          <Field label="Amount sanctioned" required>
            <AmountInput valuePaise={limit} onChangePaise={setLimit} ariaLabel="Amount sanctioned" />
          </Field>
        )}
        <CommunicationFields
          label="Received from TPA by"
          mode={mode}
          faxNo={faxNo}
          mailId={mailId}
          onMode={setMode}
          onFax={setFaxNo}
          onMail={setMailId}
        />
      </div>

      {decision === 'REJECTED' && (
        <Field
          label="Reason for rejection"
          required
          hint="Stored encrypted — this often contains clinical detail."
        >
          <textarea
            value={reason}
            onChange={e => setReason(e.target.value)}
            rows={3}
            className={inputCls}
            aria-label="Reason for rejection"
          />
        </Field>
      )}

      {desk.effectiveApprovedLimit != null && (
        <p className="text-xs text-gray-500">
          Limit currently in force: {formatPaise(desk.effectiveApprovedLimit)}
        </p>
      )}

      <SaveBar onSave={submit} saving={saving} error={error} label="Save decision" />
    </div>
  )
}
