import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Check, Loader2, X } from 'lucide-react'

import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { consentApi } from '../../../services/compliance/complianceApi'
import type { ConsentPurpose, PurposeStatus } from '../../../types/compliance'

interface Props {
  patientId: string
  /** Read-only when the user holds CONSENT_VIEW but not CONSENT_MANAGE. */
  canManage?: boolean
}

/**
 * What this patient has agreed to, and the means to change it — WO-023.
 *
 * <p>Withdrawal is one click with no confirmation dialog. That is deliberate:
 * consent harder to withdraw than to give is not freely given, and a
 * "are you sure?" step on withdrawal but not on granting would be exactly that
 * asymmetry. Granting, by contrast, requires reading the notice aloud first.
 *
 * <p>Records written by the pre-V205 self-granting defect surface here as
 * "needs re-consent" rather than being hidden or shown as valid consent. An
 * operator should be able to see why the system is asking a patient again.
 */
export default function PatientConsentPanel({ patientId, canManage = false }: Props) {
  const queryClient = useQueryClient()
  const [expanded, setExpanded] = useState<ConsentPurpose | null>(null)

  const { data: statuses = [], isLoading } = useQuery({
    queryKey: ['consent-status', patientId],
    queryFn: () => consentApi.statusFor(patientId),
  })

  const { data: history = [] } = useQuery({
    queryKey: ['consent-history', patientId],
    queryFn: () => consentApi.historyFor(patientId),
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['consent-status', patientId] })
    queryClient.invalidateQueries({ queryKey: ['consent-history', patientId] })
  }

  const withdraw = useMutation({
    mutationFn: (purpose: ConsentPurpose) => consentApi.withdraw(patientId, purpose),
    onSuccess: () => {
      toast({ title: 'Consent withdrawn' })
      invalidate()
    },
    onError: (e: Error) =>
      toast({ title: 'Could not withdraw', description: e.message }),
  })

  const grant = useMutation({
    mutationFn: (s: PurposeStatus) =>
      consentApi.grant(patientId, {
        purpose: s.purpose,
        noticeVersion: s.noticeVersion ?? 'v1.0',
        noticeLanguage: s.noticeLanguage ?? 'en',
        captureChannel: 'IN_PERSON',
        patientAgreed: true,
        minor: false,
        guardianVerified: false,
      }),
    onSuccess: () => {
      toast({ title: 'Consent recorded' })
      setExpanded(null)
      invalidate()
    },
    onError: (e: Error) => toast({ title: 'Could not record', description: e.message }),
  })

  // A grant the pre-V205 system manufactured. Live in the table, ignored by the
  // gate, and the reason the patient is being asked again.
  const inferred = history.filter(
    h => h.state === 'GRANTED' && h.provenance === 'SYSTEM_INFERRED',
  )

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 p-6 text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading consent…
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <header>
        <h2 className="text-base font-semibold text-slate-900">Consent</h2>
        <p className="text-sm text-slate-600">
          What this patient has agreed to. Read the notice aloud before recording
          consent.
        </p>
      </header>

      {inferred.length > 0 && (
        <div className="flex items-start gap-3 rounded-md border border-amber-200 bg-amber-50 p-3">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
          <p className="text-sm text-amber-900">
            {inferred.length} consent record(s) for this patient were created
            automatically by an earlier version of the system and were never
            actually agreed to. They do not permit anything. Please re-ask.
          </p>
        </div>
      )}

      <div className="divide-y divide-slate-100 rounded-md border border-slate-200">
        {statuses.map(s => (
          <div key={s.purpose} className="p-3">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-slate-900">
                    {s.summary}
                  </span>
                  {s.requiredForCare && (
                    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
                      Required for care
                    </span>
                  )}
                </div>

                <div className="mt-1 flex items-center gap-2 text-xs">
                  {s.granted ? (
                    <span className="inline-flex items-center gap-1 text-emerald-700">
                      <Check className="h-3.5 w-3.5" /> Consented
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-slate-500">
                      <X className="h-3.5 w-3.5" /> Not consented
                    </span>
                  )}
                  {s.noticeIsDraft && (
                    <span className="text-amber-600">Placeholder notice text</span>
                  )}
                  {s.noticeMissing && (
                    <span className="text-red-600">No notice on file</span>
                  )}
                </div>
              </div>

              {canManage && (
                <div className="shrink-0">
                  {s.granted ? (
                    // One click, no confirmation. See the class docstring.
                    <button
                      onClick={() => withdraw.mutate(s.purpose)}
                      disabled={withdraw.isPending}
                      className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                    >
                      Withdraw
                    </button>
                  ) : (
                    <button
                      onClick={() =>
                        setExpanded(expanded === s.purpose ? null : s.purpose)
                      }
                      disabled={s.noticeMissing}
                      className="rounded bg-blue-600 px-2 py-1 text-xs font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                    >
                      Record consent
                    </button>
                  )}
                </div>
              )}
            </div>

            {expanded === s.purpose && s.noticeText && (
              <div className="mt-3 rounded-md border border-slate-200 bg-slate-50 p-3">
                <p className="mb-2 text-xs text-slate-500">
                  {(s.noticeLanguage ?? 'en').toUpperCase()} · version{' '}
                  {s.noticeVersion}
                </p>
                <p className="whitespace-pre-wrap text-sm text-slate-800">
                  {s.noticeText}
                </p>
                <div className="mt-3 flex justify-end gap-2">
                  <button
                    onClick={() => setExpanded(null)}
                    className="rounded border border-slate-300 px-3 py-1 text-xs"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={() => grant.mutate(s)}
                    disabled={grant.isPending}
                    className={cn(
                      'inline-flex items-center gap-1 rounded bg-blue-600 px-3 py-1 text-xs font-medium text-white hover:bg-blue-700',
                      grant.isPending && 'opacity-60',
                    )}
                  >
                    {grant.isPending && <Loader2 className="h-3 w-3 animate-spin" />}
                    I read this and the patient agreed
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
