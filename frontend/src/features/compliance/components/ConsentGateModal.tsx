import { useState } from 'react'
import { ShieldAlert, Loader2, Languages } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { cn } from '../../../lib/utils'
import type {
  ConsentAttestation,
  ConsentRequiredPayload,
} from '../../../types/compliance'

interface Props {
  /** The 409 payload the server returned, carrying the notice to display. */
  payload: ConsentRequiredPayload
  patientName?: string
  onClose: () => void
  /** Called with the attestation so the caller can retry its original request. */
  onAttest: (attestation: ConsentAttestation) => void
  submitting?: boolean
}

/**
 * Shown when the server refuses an action for want of patient consent.
 *
 * <p>This dialog is the reason the consent gate can be enforced at all. Before
 * WO-022 three services granted the consent they were about to check, so the
 * gate never failed and no screen like this was needed. Removing that defect
 * without providing this would have left the desk staring at a 409 it could not
 * act on.
 *
 * <p>The checkbox starts unticked and is never defaulted. A pre-ticked consent
 * box is not consent under the DPDP Act — agreement has to be an affirmative act
 * — and defaulting it would recreate the original defect in the interface layer
 * instead of the service layer.
 *
 * <p>The notice text comes from the server, not from a constant in this file.
 * Each hospital supplies its own wording per purpose and language, and the hash
 * stored against the consent record is computed over the server's copy. A notice
 * hard-coded here could drift from the one the record claims was shown.
 */
export default function ConsentGateModal({
  payload,
  patientName,
  onClose,
  onAttest,
  submitting = false,
}: Props) {
  const [agreed, setAgreed] = useState(false)
  const [minor, setMinor] = useState(false)
  const [guardianVerified, setGuardianVerified] = useState(false)

  // A minor's consent needs verified guardian approval; the server refuses the
  // grant otherwise. Blocking here too means the desk sees why before it submits
  // rather than after.
  const guardianMissing = minor && !guardianVerified
  const canSubmit = agreed && !guardianMissing && !submitting

  const handleSubmit = () => {
    if (!canSubmit) return
    onAttest({
      noticeVersion: payload.noticeVersion ?? 'v1.0',
      noticeLanguage: payload.noticeLanguage ?? 'en',
      patientAgreed: true,
      minor,
      guardianVerified,
    })
  }

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="lg"
      title="Patient consent required"
      description={
        patientName
          ? `Read this to ${patientName} before continuing.`
          : 'Read this to the patient before continuing.'
      }
    >
      <div className="space-y-4">
        <div className="flex items-start gap-3 rounded-md border border-amber-200 bg-amber-50 p-3">
          <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
          <p className="text-sm text-amber-900">
            This action cannot proceed until the patient has been shown the notice
            below and has agreed.
            {payload.requiredForCare && (
              <span className="mt-1 block font-medium">
                This consent is required for care. If the patient declines, escalate
                rather than proceeding.
              </span>
            )}
          </p>
        </div>

        <div className="rounded-md border border-slate-200 bg-slate-50 p-4">
          <div className="mb-2 flex items-center gap-2 text-xs text-slate-500">
            <Languages className="h-3.5 w-3.5" />
            <span>
              {(payload.noticeLanguage ?? 'en').toUpperCase()} · version{' '}
              {payload.noticeVersion ?? 'v1.0'}
            </span>
          </div>
          <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">
            {payload.noticeText ??
              'No notice text is on file for this purpose. Do not proceed — ' +
                'contact your administrator, as consent cannot be recorded ' +
                'against a notice this hospital cannot produce.'}
          </p>
        </div>

        {!payload.noticeText && (
          <p className="text-sm font-medium text-red-600">
            Consent cannot be captured without notice text. This is a
            configuration problem, not something to work around.
          </p>
        )}

        <div className="space-y-3">
          <label className="flex cursor-pointer items-start gap-3">
            <input
              type="checkbox"
              className="mt-1 h-4 w-4 rounded border-slate-300"
              checked={agreed}
              disabled={!payload.noticeText}
              onChange={e => setAgreed(e.target.checked)}
            />
            <span className="text-sm text-slate-800">
              I read this notice to the patient and they agreed.
            </span>
          </label>

          <label className="flex cursor-pointer items-start gap-3">
            <input
              type="checkbox"
              className="mt-1 h-4 w-4 rounded border-slate-300"
              checked={minor}
              onChange={e => {
                setMinor(e.target.checked)
                if (!e.target.checked) setGuardianVerified(false)
              }}
            />
            <span className="text-sm text-slate-800">The patient is a minor.</span>
          </label>

          {minor && (
            <label className="ml-7 flex cursor-pointer items-start gap-3">
              <input
                type="checkbox"
                className="mt-1 h-4 w-4 rounded border-slate-300"
                checked={guardianVerified}
                onChange={e => setGuardianVerified(e.target.checked)}
              />
              <span className="text-sm text-slate-800">
                I verified the guardian's identity and they gave consent.
              </span>
            </label>
          )}

          {guardianMissing && (
            <p className="ml-7 text-sm text-red-600">
              A minor's consent requires verified guardian approval.
            </p>
          )}
        </div>

        <p className="text-xs text-slate-500">
          This will be recorded against your user account, with the notice version
          and the time.
        </p>

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            Patient declined
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!canSubmit}
            className={cn(
              'inline-flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium text-white',
              canSubmit
                ? 'bg-blue-600 hover:bg-blue-700'
                : 'cursor-not-allowed bg-slate-300',
            )}
          >
            {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
            Record consent and continue
          </button>
        </div>
      </div>
    </Modal>
  )
}
