import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BadgeCheck, ShieldAlert, Loader2 } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { abhaApi } from '../../../services/abha/abhaApi'
import {
  CHANNEL_LABELS,
  type AbhaLinkage,
  type OtpChannel,
  activeLinkage,
  canStartEnrolment,
  failureMessage,
  validateOtp,
  validateStartRequest,
} from '../types'

interface Props {
  patientId: string
  patientName?: string
  onClose: () => void
  onLinked?: (linkage: AbhaLinkage) => void
}

/**
 * ABHA verification & creation — Screen 1.1.
 *
 * <p>Two steps in one dialog: choose a channel and send an OTP, then verify it.
 * The step is derived from whether an enrolment is in flight rather than held in
 * its own state, so the dialog cannot show the OTP field for an enrolment that
 * was never started.
 *
 * <p>The Aadhaar or mobile number the desk types is held only for the duration
 * of the first request and is cleared as soon as the OTP is sent. It is never
 * put in a query key, because query keys are cached and inspectable.
 */
export default function AbhaVerificationModal({
  patientId,
  patientName,
  onClose,
  onLinked,
}: Props) {
  const queryClient = useQueryClient()

  const [channel, setChannel] = useState<OtpChannel>('AADHAAR')
  const [loginId, setLoginId] = useState('')
  const [otp, setOtp] = useState('')
  const [errors, setErrors] = useState<string[]>([])
  const [pending, setPending] = useState<AbhaLinkage | null>(null)

  const { data: history = [], isLoading } = useQuery({
    queryKey: ['abha', 'history', patientId],
    queryFn: () => abhaApi.historyFor(patientId),
  })

  const linked = activeLinkage(history)
  const mayEnrol = canStartEnrolment(history)

  const startOp = useMutation({
    mutationFn: () => abhaApi.start({ patientId, channel, loginId: loginId.replace(/\s/g, '') }),
    onSuccess: (linkage) => {
      setPending(linkage)
      // Clear the identifier the moment it is no longer needed.
      setLoginId('')
      setErrors([])
      toast({ title: 'OTP sent to the patient', variant: 'success' })
    },
    onError: () => setErrors(['Could not send the OTP. Check the number and try again.']),
  })

  const verifyOp = useMutation({
    mutationFn: () => abhaApi.verify(pending!.id, { otp }),
    onSuccess: (linkage) => {
      queryClient.invalidateQueries({ queryKey: ['abha', 'history', patientId] })
      queryClient.invalidateQueries({ queryKey: ['abha', 'linked', patientId] })
      toast({ title: 'ABHA verified and linked', variant: 'success' })
      onLinked?.(linkage)
      onClose()
    },
    onError: () => setErrors([failureMessage(null)]),
  })

  function handleSend() {
    const result = validateStartRequest({ patientId, channel, loginId })
    setErrors(result.errors)
    if (result.valid) startOp.mutate()
  }

  function handleVerify() {
    const result = validateOtp(otp)
    setErrors(result.errors)
    if (result.valid) verifyOp.mutate()
  }

  return (
    <Modal isOpen onClose={onClose} title="Verify or create ABHA" size="lg">
      <div className="px-6 pt-6 pb-2">
        <h2 className="text-lg font-semibold text-neutral-900">Verify or create ABHA</h2>
        <p className="mt-1 text-sm text-neutral-500">
          {patientName
            ? `Link a national health ID to ${patientName}.`
            : 'Link a national health ID to this patient.'}
        </p>
      </div>

      <div className="px-6 pb-6 space-y-5 overflow-y-auto">
        {isLoading && (
          <div className="flex items-center gap-2 text-sm text-neutral-500">
            <Loader2 size={15} className="animate-spin" />
            Checking existing ABHA…
          </div>
        )}

        {linked && (
          <div className="flex items-start gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4">
            <BadgeCheck size={18} className="mt-0.5 shrink-0 text-emerald-600" />
            <div className="text-sm">
              <p className="font-medium text-emerald-900">ABHA already linked</p>
              <p className="mt-0.5 text-emerald-700">
                {linked.abhaNumberMasked}
                {linked.abhaAddress ? ` · ${linked.abhaAddress}` : ''}
              </p>
              <p className="mt-1 text-emerald-700">
                A patient may hold only one ABHA. To replace it, unlink the current one first.
              </p>
            </div>
          </div>
        )}

        {!linked && !pending && (
          <>
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium text-neutral-700">Send the OTP using</legend>
              {(['AADHAAR', 'MOBILE'] as OtpChannel[]).map((c) => (
                <label
                  key={c}
                  className={cn(
                    'flex cursor-pointer items-center gap-3 rounded-lg border p-3 text-sm transition-colors',
                    channel === c
                      ? 'border-neutral-900 bg-neutral-50 text-neutral-900'
                      : 'border-neutral-200 hover:bg-neutral-50',
                  )}
                >
                  <input
                    type="radio"
                    name="abha-channel"
                    checked={channel === c}
                    onChange={() => {
                      setChannel(c)
                      setLoginId('')
                      setErrors([])
                    }}
                    className="accent-neutral-900"
                  />
                  {CHANNEL_LABELS[c]}
                </label>
              ))}
            </fieldset>

            <div>
              <label
                htmlFor="abha-login-id"
                className="block text-sm font-medium text-neutral-700"
              >
                {channel === 'AADHAAR' ? 'Aadhaar number' : 'Mobile number'}
              </label>
              <input
                id="abha-login-id"
                inputMode="numeric"
                autoComplete="off"
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                placeholder={channel === 'AADHAAR' ? '1234 5678 9012' : '9876543210'}
                className="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
              />
              <p className="mt-1.5 text-xs text-neutral-500">
                Used once to reach ABDM. It is not stored in the hospital record.
              </p>
            </div>
          </>
        )}

        {pending && (
          <div>
            <label htmlFor="abha-otp" className="block text-sm font-medium text-neutral-700">
              OTP sent to the patient
            </label>
            <input
              id="abha-otp"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              placeholder="000000"
              className="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm tracking-widest focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
            />
            <p className="mt-1.5 text-xs text-neutral-500">
              ABDM limits how many times an OTP can be requested for one number.
            </p>
          </div>
        )}

        {errors.length > 0 && (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          >
            <ShieldAlert size={16} className="mt-0.5 shrink-0" />
            <ul className="space-y-0.5">
              {errors.map((e) => (
                <li key={e}>{e}</li>
              ))}
            </ul>
          </div>
        )}
      </div>

      <div className="flex justify-end gap-2 border-t border-neutral-100 px-6 py-4">
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg px-4 py-2 text-sm font-medium text-neutral-600 hover:bg-neutral-50"
        >
          Close
        </button>

        {!linked && !pending && (
          <button
            type="button"
            onClick={handleSend}
            disabled={!mayEnrol || startOp.isPending}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
          >
            {startOp.isPending && <Loader2 size={15} className="animate-spin" />}
            Send OTP
          </button>
        )}

        {pending && (
          <button
            type="button"
            onClick={handleVerify}
            disabled={verifyOp.isPending}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
          >
            {verifyOp.isPending && <Loader2 size={15} className="animate-spin" />}
            Verify and link
          </button>
        )}
      </div>
    </Modal>
  )
}
