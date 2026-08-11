import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link2, Loader2, RefreshCw, ShieldAlert } from 'lucide-react'

import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { policyApi } from '../../../services/policy/policyApi'
import { POLICY_TYPE_LABELS, type DiscoveredPolicy } from '../types'

interface Props {
  patientId: string
  /** Where a linked policy lands. Absent until an insurance record exists. */
  insuranceId?: string
}

/**
 * Policy search & digital retrieval — Screen 1.2.
 *
 * <p>Three steps, because the middle one is not optional: send the patient an
 * OTP, confirm it, then read what the registry returned. Asking the national
 * registry which insurers a person holds policies with is a disclosure about
 * their financial affairs, and walking into a hospital is not consent to it.
 *
 * <p>Results are polled rather than awaited. NHCX acknowledges immediately and
 * the registry answers on a callback, so there is a real interval where the
 * request is in flight and the list is legitimately empty.
 */
export default function PolicyDiscoveryPanel({ patientId, insuranceId }: Props) {
  const queryClient = useQueryClient()

  const [identifier, setIdentifier] = useState('')
  const [otp, setOtp] = useState('')
  const [correlationId, setCorrelationId] = useState<string | null>(null)
  const [awaitingRegistry, setAwaitingRegistry] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { data: policies = [], isFetching } = useQuery({
    queryKey: ['policy', 'discovered', patientId],
    queryFn: () => policyApi.discoveredFor(patientId),
    // Poll only while a request is genuinely outstanding.
    refetchInterval: awaitingRegistry ? 4000 : false,
  })

  const otpOp = useMutation({
    mutationFn: () => policyApi.requestOtp(patientId, identifier.trim()),
    onSuccess: (id) => {
      setCorrelationId(id)
      setIdentifier('')
      setError(null)
      toast({ title: 'OTP sent to the patient', variant: 'success' })
    },
    onError: () => setError('Could not reach the registry. Try again in a moment.'),
  })

  const confirmOp = useMutation({
    mutationFn: () => policyApi.confirmDiscovery(patientId, correlationId!, otp.trim()),
    onSuccess: () => {
      setAwaitingRegistry(true)
      setOtp('')
      setError(null)
      toast({ title: 'Looking up policies…', variant: 'success' })
      // Stop polling after a reasonable wait rather than forever.
      setTimeout(() => setAwaitingRegistry(false), 60_000)
    },
    onError: () => setError('That OTP was not accepted.'),
  })

  const linkOp = useMutation({
    mutationFn: (discoveredId: string) => policyApi.link(discoveredId, insuranceId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policy', 'discovered', patientId] })
      toast({ title: 'Policy linked to this encounter', variant: 'success' })
    },
    onError: () => toast({ title: 'Could not link the policy', variant: 'destructive' }),
  })

  return (
    <div className="space-y-4">
      {!correlationId && (
        <div className="rounded-xl border border-neutral-200 p-4">
          <label htmlFor="policy-identifier" className="block text-sm font-medium text-neutral-700">
            ABHA address or mobile number
          </label>
          <div className="mt-1 flex gap-2">
            <input
              id="policy-identifier"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="ravi@abdm or 9876543210"
              className="flex-1 rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
            />
            <button
              type="button"
              onClick={() => otpOp.mutate()}
              disabled={!identifier.trim() || otpOp.isPending}
              className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
            >
              {otpOp.isPending && <Loader2 size={15} className="animate-spin" />}
              Send OTP
            </button>
          </div>
          <p className="mt-1.5 text-xs text-neutral-500">
            The patient authorises the lookup. Their policies are not disclosed without it.
          </p>
        </div>
      )}

      {correlationId && !awaitingRegistry && policies.length === 0 && (
        <div className="rounded-xl border border-neutral-200 p-4">
          <label htmlFor="policy-otp" className="block text-sm font-medium text-neutral-700">
            OTP sent to the patient
          </label>
          <div className="mt-1 flex gap-2">
            <input
              id="policy-otp"
              inputMode="numeric"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              placeholder="000000"
              className="flex-1 rounded-lg border border-neutral-200 px-3 py-2 text-sm tracking-widest focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
            />
            <button
              type="button"
              onClick={() => confirmOp.mutate()}
              disabled={!otp.trim() || confirmOp.isPending}
              className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
            >
              {confirmOp.isPending && <Loader2 size={15} className="animate-spin" />}
              Find policies
            </button>
          </div>
        </div>
      )}

      {awaitingRegistry && (
        <div className="flex items-center gap-2 rounded-xl border border-neutral-200 bg-neutral-50 p-4 text-sm text-neutral-600">
          <RefreshCw size={15} className="animate-spin" />
          Waiting for the registry. This usually takes a few seconds.
        </div>
      )}

      {error && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"
        >
          <ShieldAlert size={16} className="mt-0.5 shrink-0" />
          {error}
        </div>
      )}

      {policies.length > 0 && (
        <ul className="space-y-2">
          {policies.map((p) => (
            <PolicyRow
              key={p.id}
              policy={p}
              canLink={!!insuranceId && !p.linked}
              linking={linkOp.isPending}
              onLink={() => linkOp.mutate(p.id)}
            />
          ))}
        </ul>
      )}

      {!isFetching && !awaitingRegistry && correlationId && policies.length === 0 && (
        <p className="text-sm text-neutral-500">
          No policies returned. Register the policy manually if the patient has the card.
        </p>
      )}
    </div>
  )
}

function PolicyRow({
  policy,
  canLink,
  linking,
  onLink,
}: {
  policy: DiscoveredPolicy
  canLink: boolean
  linking: boolean
  onLink: () => void
}) {
  return (
    <li
      className={cn(
        'flex items-start justify-between gap-4 rounded-xl border p-4',
        policy.linked ? 'border-emerald-200 bg-emerald-50' : 'border-neutral-200',
      )}
    >
      <div className="min-w-0 text-sm">
        <p className="font-medium text-neutral-900">{policy.payerName ?? 'Unnamed insurer'}</p>
        <p className="mt-0.5 text-neutral-600">
          {policy.policyNumberMasked ?? '—'}
          {policy.tpaName ? ` · TPA ${policy.tpaName}` : ''}
        </p>
        <p className="mt-0.5 text-neutral-500">
          {policy.policyType ? POLICY_TYPE_LABELS[policy.policyType] : 'Type not stated'}
          {policy.policyEndDate ? ` · valid to ${policy.policyEndDate}` : ''}
        </p>
        {/* On a family floater the primary insured is often not the patient. */}
        {policy.relationship && policy.relationship !== 'SELF' && (
          <p className="mt-0.5 text-neutral-500">
            Primary insured: {policy.primaryInsuredName ?? '—'} ({policy.relationship.toLowerCase()})
          </p>
        )}
      </div>

      {policy.linked ? (
        <span className="shrink-0 text-xs font-medium text-emerald-700">Linked</span>
      ) : (
        <button
          type="button"
          onClick={onLink}
          disabled={!canLink || linking}
          title={canLink ? undefined : 'Create the insurance record first'}
          className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-neutral-300 px-3 py-1.5 text-xs font-medium text-neutral-700 hover:bg-neutral-50 disabled:opacity-40"
        >
          <Link2 size={13} />
          Link
        </button>
      )}
    </li>
  )
}
