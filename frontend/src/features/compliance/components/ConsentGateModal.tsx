import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ShieldAlert, Loader2, Languages } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { cn } from '../../../lib/utils'
import { consentApi } from '../../../services/compliance/complianceApi'
import type {
  ConsentAttestation,
  ConsentRequiredPayload,
} from '../../../types/compliance'

/**
 * Languages the notice registry is seeded for (V211 English, V217 Tamil and
 * Hindi). Not every hospital will have every language on file, so a miss is
 * surfaced as an error rather than falling back to English — silently showing
 * English to a patient who asked for Tamil is exactly the failure this exists
 * to prevent.
 */
const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'ta', label: 'தமிழ்' },
  { code: 'hi', label: 'हिन्दी' },
]

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

  // WO-023 / E-005. V217 seeds Tamil and Hindi notices; without a way to ask for
  // them they would sit in the table unread, which is the same as not having
  // them. A notice the patient cannot read is not notice.
  const [language, setLanguage] = useState(payload.noticeLanguage ?? 'en')

  // Fetched, never translated in the browser. The hash stored against the
  // consent record is computed server-side over the server's copy of this exact
  // language and version, so text assembled here would drift from what the
  // record claims was shown.
  const translated = useQuery({
    queryKey: ['consent-notice', payload.purpose, language],
    queryFn: () => consentApi.notice(payload.purpose, language),
    enabled: language !== (payload.noticeLanguage ?? 'en'),
    retry: false,
  })

  const showingOriginal = language === (payload.noticeLanguage ?? 'en')
  const noticeText = showingOriginal ? payload.noticeText : translated.data?.bodyText
  const noticeVersion = showingOriginal
    ? payload.noticeVersion ?? 'v1.0'
    : translated.data?.version

  // Switching language mid-read invalidates the agreement: the patient has not
  // read the text now on screen. Cheaper to re-tick than to record consent
  // against something nobody read.
  const changeLanguage = (next: string) => {
    if (next === language) return
    setLanguage(next)
    setAgreed(false)
  }

  // A minor's consent needs verified guardian approval; the server refuses the
  // grant otherwise. Blocking here too means the desk sees why before it submits
  // rather than after.
  const guardianMissing = minor && !guardianVerified
  const canSubmit = agreed && !guardianMissing && !submitting && !!noticeText

  const handleSubmit = () => {
    if (!canSubmit) return
    onAttest({
      // The language and version actually on screen, not the ones the 409
      // arrived with. Recording 'en' after showing Tamil would make the stored
      // hash disagree with what the patient read.
      noticeVersion: noticeVersion ?? 'v1.0',
      noticeLanguage: language,
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
          ? `Read this notice to ${patientName} before continuing.`
          : 'Read this notice to the patient before continuing.'
      }
    >
      <div className="flex flex-col max-h-[85vh]">
        {/* Modal Header */}
        <div className="px-6 pt-6 pb-4 border-b border-slate-100 pr-12 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-amber-50 border border-amber-200 flex items-center justify-center text-amber-600 shrink-0">
              <ShieldAlert className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 leading-tight">Patient Consent Required</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                {patientName
                  ? `Read this notice to ${patientName} before continuing.`
                  : 'Read this notice to the patient before continuing.'}
              </p>
            </div>
          </div>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          <div className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50/80 p-3.5">
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

          <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
            <div className="mb-2.5 flex flex-wrap items-center gap-2 text-xs text-slate-500">
              <Languages className="h-3.5 w-3.5" />
              <span>version {noticeVersion ?? '—'}</span>
              <span className="text-slate-300">|</span>
              {LANGUAGES.map(l => (
                <button
                  key={l.code}
                  type="button"
                  onClick={() => changeLanguage(l.code)}
                  className={cn(
                    'rounded-md px-2 py-0.5 text-xs font-medium transition-colors cursor-pointer',
                    language === l.code
                      ? 'bg-slate-800 text-white'
                      : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200',
                  )}
                >
                  {l.label}
                </button>
              ))}
            </div>

            {translated.isFetching ? (
              <p className="text-sm text-slate-500 py-2">Loading the notice…</p>
            ) : translated.isError ? (
              <p className="text-sm font-medium text-red-600 py-2">
                This hospital has no notice on file in this language. Switch back to
                a language it does have, or contact your administrator — consent
                cannot be captured against a notice that does not exist.
              </p>
            ) : (
              <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-800">
                {noticeText ??
                  'No notice text is on file for this purpose. Do not proceed — ' +
                    'contact your administrator, as consent cannot be recorded ' +
                    'against a notice this hospital cannot produce.'}
              </p>
            )}
          </div>

          {!noticeText && !translated.isFetching && !translated.isError && (
            <p className="text-sm font-medium text-red-600">
              Consent cannot be captured without notice text. This is a
              configuration problem, not something to work around.
            </p>
          )}

          <div className="space-y-3 rounded-xl border border-slate-200 p-4 bg-white">
            <label className="flex cursor-pointer items-start gap-3">
              <input
                type="checkbox"
                className="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600"
                checked={agreed}
                disabled={!payload.noticeText}
                onChange={e => setAgreed(e.target.checked)}
              />
              <span className="text-sm text-slate-800 font-medium">
                I read this notice to the patient and they agreed.
              </span>
            </label>

            <label className="flex cursor-pointer items-start gap-3">
              <input
                type="checkbox"
                className="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600"
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
                  className="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600"
                  checked={guardianVerified}
                  onChange={e => setGuardianVerified(e.target.checked)}
                />
                <span className="text-sm text-slate-800">
                  I verified the guardian's identity and they gave consent.
                </span>
              </label>
            )}

            {guardianMissing && (
              <p className="ml-7 text-sm text-red-600 font-medium">
                A minor's consent requires verified guardian approval.
              </p>
            )}
          </div>

          <p className="text-xs text-slate-500">
            This will be recorded against your user account, with the notice version
            and the time.
          </p>
        </div>

        {/* Modal Footer */}
        <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end gap-2.5 shrink-0">
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50 transition-colors cursor-pointer"
          >
            Patient declined
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!canSubmit}
            className={cn(
              'inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white transition-colors cursor-pointer shadow-2xs',
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
