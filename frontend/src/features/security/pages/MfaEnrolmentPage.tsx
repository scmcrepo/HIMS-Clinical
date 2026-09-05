import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { QRCodeSVG } from 'qrcode.react'
import { ShieldCheck, ShieldAlert, Copy, Check, AlertTriangle } from 'lucide-react'
import { mfaApi } from '../../../services/security/mfaApi'
import type { MfaEnrolment } from '../../../services/security/mfaApi'

/**
 * Set up two-step verification for your own account (WO-029 / U-002).
 *
 * <p>Self-service and unpermissioned by design: a user may always improve the
 * security of their own account. Clearing someone else's second factor is the
 * separately permissioned path and does not live here.
 *
 * <p>Three states — not enrolled, showing the QR, showing the recovery codes.
 * The last one is the reason this is a page rather than a modal: the codes
 * appear exactly once and the user has to be able to write them down without a
 * stray click dismissing them.
 */
export default function MfaEnrolmentPage() {
  const [secret, setSecret] = useState<string | null>(null)
  const [uri, setUri] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)
  const [copied, setCopied] = useState(false)
  const [acknowledged, setAcknowledged] = useState(false)

  const status = useQuery({ queryKey: ['mfa-status'], queryFn: mfaApi.status, retry: false })

  const begin = useMutation({
    mutationFn: mfaApi.enrol,
    onSuccess: (e: MfaEnrolment) => {
      setSecret(e.secret)
      setUri(e.provisioningUri)
      setError('')
    },
    onError: (err: any) =>
      setError(err?.response?.data?.message || 'Could not start enrolment.'),
  })

  const confirm = useMutation({
    mutationFn: () => mfaApi.confirm(code),
    onSuccess: (codes: string[]) => {
      setRecoveryCodes(codes)
      // Drop the secret from component state the moment it is no longer needed.
      // It stays in the user's authenticator app and in an encrypted column;
      // there is no reason for it to outlive this step in a browser.
      setSecret(null)
      setUri(null)
      setCode('')
    },
    onError: (err: any) =>
      setError(err?.response?.data?.message || 'That code was not accepted.'),
  })

  const copyCodes = () => {
    if (!recoveryCodes) return
    navigator.clipboard.writeText(recoveryCodes.join('\n'))
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  // ── Recovery codes: shown once, and this is that once ────────────────────
  if (recoveryCodes) {
    return (
      <div className="mx-auto max-w-2xl p-6">
        <div className="mb-6 flex items-start gap-3">
          <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-green-50">
            <ShieldCheck className="h-5 w-5 text-green-600" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-neutral-900">
              Two-step verification is on
            </h1>
            <p className="mt-1 text-sm text-neutral-500">
              Save these recovery codes somewhere safe and offline.
            </p>
          </div>
        </div>

        <div className="mb-4 flex items-start gap-2.5 rounded-lg bg-amber-50 px-4 py-3">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
          <p className="text-sm text-amber-800">
            This is the only time these will be shown. They are stored as hashes,
            so nobody — including your administrator — can retrieve them later.
            Each one works once, and any one of them signs you in if you lose your
            phone.
          </p>
        </div>

        <div className="mb-4 grid grid-cols-2 gap-2 rounded-lg border border-neutral-200 bg-neutral-50 p-4 font-mono text-sm">
          {recoveryCodes.map(c => (
            <div key={c} className="tracking-wider text-neutral-800">{c}</div>
          ))}
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={copyCodes}
            className="inline-flex items-center gap-2 rounded-lg border border-neutral-200 px-3.5 py-2 text-sm font-medium text-neutral-700 hover:bg-neutral-50"
          >
            {copied ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4" />}
            {copied ? 'Copied' : 'Copy all'}
          </button>

          <label className="flex items-center gap-2 text-sm text-neutral-600">
            <input
              type="checkbox"
              checked={acknowledged}
              onChange={e => setAcknowledged(e.target.checked)}
              className="rounded border-neutral-300"
            />
            I have saved these codes
          </label>
        </div>

        {/* The checkbox gates nothing technical — it exists so that leaving this
            page is a deliberate act rather than a reflex, because after it the
            codes are gone. */}
        {acknowledged && (
          <p className="mt-4 text-sm text-neutral-500">
            You can close this page. You will be asked for a code from your
            authenticator app the next time you sign in.
          </p>
        )}
      </div>
    )
  }

  // ── Scan and confirm ────────────────────────────────────────────────────
  if (secret && uri) {
    return (
      <div className="mx-auto max-w-2xl p-6">
        <h1 className="text-xl font-semibold text-neutral-900">
          Scan this with your authenticator app
        </h1>
        <p className="mt-1 text-sm text-neutral-500">
          Google Authenticator, Authy, 1Password or any other TOTP app will work.
        </p>

        <div className="mt-6 flex flex-col items-center gap-4 rounded-lg border border-neutral-200 p-6 sm:flex-row sm:items-start">
          <div className="rounded-lg bg-white p-3 ring-1 ring-neutral-100">
            <QRCodeSVG value={uri} size={168} />
          </div>

          <div className="flex-1">
            <p className="text-xs font-semibold text-neutral-600">
              Or enter this key by hand
            </p>
            <code className="mt-1.5 block break-all rounded bg-neutral-50 px-3 py-2 font-mono text-sm tracking-wider text-neutral-800">
              {secret}
            </code>
            <p className="mt-2 text-xs text-neutral-400">
              Useful when a camera will not focus. Case does not matter and spaces
              are ignored.
            </p>
          </div>
        </div>

        <div className="mt-6">
          <label className="block text-xs font-semibold text-neutral-600 mb-1.5">
            Enter the 6-digit code your app is showing
          </label>
          <input
            value={code}
            onChange={e => setCode(e.target.value)}
            autoFocus
            inputMode="numeric"
            placeholder="123456"
            className="w-48 rounded-lg border border-neutral-200 px-3.5 py-2.5 text-center text-lg font-semibold tracking-[0.3em] focus:border-neutral-900 focus:ring-2 focus:ring-neutral-200 focus:outline-none placeholder:tracking-normal placeholder:font-normal placeholder:text-neutral-300"
          />
          <p className="mt-1.5 text-xs text-neutral-400">
            Confirming proves the app and the server agree. Until you do, this is
            not switched on — so a half-finished setup cannot lock you out.
          </p>
        </div>

        {error && (
          <p className="mt-4 rounded-lg bg-red-50 px-3.5 py-2.5 text-sm text-red-600">{error}</p>
        )}

        <button
          onClick={() => { setError(''); confirm.mutate() }}
          disabled={confirm.isPending || code.trim().length < 6}
          className="mt-5 rounded-lg bg-neutral-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-neutral-800 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {confirm.isPending ? 'Checking…' : 'Confirm and turn on'}
        </button>
      </div>
    )
  }

  // ── Landing ─────────────────────────────────────────────────────────────
  const mode = status.data?.mode
  const alreadyOn = status.data?.confirmed

  return (
    <div className="mx-auto max-w-2xl p-6">
      <div className="mb-6 flex items-start gap-3">
        <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-neutral-100">
          {alreadyOn
            ? <ShieldCheck className="h-5 w-5 text-green-600" />
            : <ShieldAlert className="h-5 w-5 text-neutral-500" />}
        </div>
        <div>
          <h1 className="text-xl font-semibold text-neutral-900">Two-step verification</h1>
          <p className="mt-1 text-sm text-neutral-500">
            A code from your phone, on top of your password.
          </p>
        </div>
      </div>

      {status.isLoading && <p className="text-sm text-neutral-500">Loading…</p>}

      {alreadyOn && (
        <div className="rounded-lg border border-neutral-200 p-4">
          <p className="text-sm text-neutral-700">
            This account is protected. You have{' '}
            <strong>{status.data?.recoveryCodesRemaining}</strong> unused recovery
            {status.data?.recoveryCodesRemaining === 1 ? ' code' : ' codes'} left.
          </p>
          <p className="mt-2 text-sm text-neutral-500">
            To move this to a new device, an administrator has to reset it first.
            Changing a working second factor without proving control of the old one
            is how accounts get taken over.
          </p>
        </div>
      )}

      {/* Enrolment is refused server-side while the mode is OFF, so the button is
          not offered. A credential nothing checks is worse than none: it looks
          like protection on a status page and provides none. */}
      {!alreadyOn && mode === 'OFF' && (
        <p className="rounded-lg bg-neutral-50 px-4 py-3 text-sm text-neutral-600">
          Two-step verification is not switched on for this deployment yet. There
          is nothing to set up until an administrator enables it.
        </p>
      )}

      {!alreadyOn && mode && mode !== 'OFF' && (
        <>
          {status.data?.privileged && mode === 'REQUIRED' && (
            <p className="mb-4 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800">
              This account has hospital-wide access, so a second factor is required
              before you can sign in again.
            </p>
          )}
          {error && (
            <p className="mb-4 rounded-lg bg-red-50 px-3.5 py-2.5 text-sm text-red-600">{error}</p>
          )}
          <button
            onClick={() => begin.mutate()}
            disabled={begin.isPending}
            className="rounded-lg bg-neutral-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-neutral-800 disabled:opacity-50"
          >
            {begin.isPending ? 'Starting…' : 'Set up two-step verification'}
          </button>
        </>
      )}
    </div>
  )
}
