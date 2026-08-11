import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, ShieldAlert, ShieldCheck } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { abdmApi } from '../../../services/abdm/abdmApi'
import {
  CONSENT_STATE_LABELS,
  HI_TYPES,
  PURPOSE_CODES,
  type ConsentFormState,
  type HiType,
  type PurposeCode,
  displayState,
  hiTypeLabels,
  validateConsentForm,
} from '../types'

interface Props {
  patientId: string
  encounterId?: string
  onClose: () => void
}

/** Thirty days out, as a starting point the clinician can change. */
function defaultExpiry(): string {
  const d = new Date()
  d.setDate(d.getDate() + 30)
  return d.toISOString().slice(0, 10)
}

/**
 * ABDM consent request — Screen 3.1.
 *
 * <p>Asks the patient, through ABDM's Consent Manager, for permission to read
 * records held by other providers. The expiry is presented as an explicit field
 * with a visible default rather than being applied silently: it decides how long
 * this hospital may hold another provider's clinical data, which is not a
 * decision to make on a clinician's behalf without showing it to them.
 */
export default function ConsentRequestModal({ patientId, encounterId, onClose }: Props) {
  const queryClient = useQueryClient()

  const [form, setForm] = useState<ConsentFormState>({
    purposeCode: 'CAREMGT',
    hiTypes: ['OPConsultation', 'DiagnosticReport'],
    dateRangeFrom: '',
    dateRangeTo: new Date().toISOString().slice(0, 10),
    expiresAt: defaultExpiry(),
  })

  const check = validateConsentForm(form)

  const { data: existing = [] } = useQuery({
    queryKey: ['abdm', 'consent-requests', patientId],
    queryFn: () => abdmApi.consentRequestsFor(patientId),
  })

  const requestOp = useMutation({
    mutationFn: () =>
      abdmApi.requestConsent({
        patientId,
        encounterId,
        purposeCode: form.purposeCode as PurposeCode,
        hiTypes: form.hiTypes,
        dateRangeFrom: form.dateRangeFrom,
        dateRangeTo: form.dateRangeTo,
        // The server wants an instant; the field collects a date.
        expiresAt: new Date(`${form.expiresAt}T23:59:59Z`).toISOString(),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['abdm'] })
      toast({ title: 'Consent request sent to the patient', variant: 'success' })
      onClose()
    },
    onError: () => toast({ title: 'Could not send the consent request', variant: 'destructive' }),
  })

  function toggleType(t: HiType) {
    setForm((f) => ({
      ...f,
      hiTypes: f.hiTypes.includes(t) ? f.hiTypes.filter((x) => x !== t) : [...f.hiTypes, t],
    }))
  }

  return (
    <Modal isOpen onClose={onClose} title="Request access to health records" size="lg">
      <div className="px-6 pt-6">
        <h2 className="text-lg font-semibold text-neutral-900">Request health records</h2>
        <p className="mt-1 text-sm text-neutral-500">
          The patient approves this on their phone. Nothing is retrieved until they do.
        </p>
      </div>

      <div className="max-h-[60vh] space-y-5 overflow-y-auto px-6 py-5">
        {existing.length > 0 && (
          <ul className="space-y-1.5 rounded-lg border border-neutral-200 p-3 text-sm">
            {existing.slice(0, 3).map((r) => {
              const state = displayState(r)
              return (
                <li key={r.id} className="flex items-center justify-between gap-3">
                  <span className="text-neutral-600">{hiTypeLabels(r.hiTypes).join(', ')}</span>
                  <span
                    className={cn(
                      'shrink-0 rounded-full px-2 py-0.5 text-xs font-medium',
                      state === 'GRANTED'
                        ? 'bg-emerald-50 text-emerald-700'
                        : 'bg-neutral-100 text-neutral-600',
                    )}
                  >
                    {CONSENT_STATE_LABELS[state]}
                  </span>
                </li>
              )
            })}
          </ul>
        )}

        <div>
          <label htmlFor="purpose" className="block text-sm font-medium text-neutral-700">
            Why are the records needed?
          </label>
          <select
            id="purpose"
            value={form.purposeCode}
            onChange={(e) => setForm({ ...form, purposeCode: e.target.value as PurposeCode })}
            className="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
          >
            {Object.entries(PURPOSE_CODES).map(([code, label]) => (
              <option key={code} value={code}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <fieldset>
          <legend className="text-sm font-medium text-neutral-700">Record types</legend>
          <div className="mt-2 grid gap-1.5 sm:grid-cols-2">
            {Object.entries(HI_TYPES).map(([key, label]) => (
              <label
                key={key}
                className="flex cursor-pointer items-center gap-2 rounded-lg border border-neutral-200 px-3 py-2 text-sm hover:bg-neutral-50"
              >
                <input
                  type="checkbox"
                  checked={form.hiTypes.includes(key as HiType)}
                  onChange={() => toggleType(key as HiType)}
                  className="accent-neutral-900"
                />
                {label}
              </label>
            ))}
          </div>
          <p className="mt-1.5 text-xs text-neutral-500">
            Ask only for what is needed. Patients decline broad requests more often.
          </p>
        </fieldset>

        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="from" className="block text-sm font-medium text-neutral-700">
              Records from
            </label>
            <input
              id="from"
              type="date"
              value={form.dateRangeFrom}
              onChange={(e) => setForm({ ...form, dateRangeFrom: e.target.value })}
              className="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label htmlFor="to" className="block text-sm font-medium text-neutral-700">
              Records to
            </label>
            <input
              id="to"
              type="date"
              value={form.dateRangeTo}
              onChange={(e) => setForm({ ...form, dateRangeTo: e.target.value })}
              className="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm"
            />
          </div>
        </div>

        <div>
          <label htmlFor="expires" className="block text-sm font-medium text-neutral-700">
            Our access expires on
          </label>
          <input
            id="expires"
            type="date"
            value={form.expiresAt}
            onChange={(e) => setForm({ ...form, expiresAt: e.target.value })}
            className="mt-1 w-56 rounded-lg border border-neutral-200 px-3 py-2 text-sm"
          />
          <p className="mt-1.5 text-xs text-neutral-500">
            After this date the records stop being viewable here, automatically.
          </p>
        </div>

        {!check.valid && form.dateRangeFrom !== '' && (
          <ul
            role="alert"
            className="space-y-1 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"
          >
            {check.errors.map((e) => (
              <li key={e} className="flex items-start gap-2">
                <ShieldAlert size={15} className="mt-0.5 shrink-0" />
                {e}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="flex justify-end gap-2 border-t border-neutral-100 px-6 py-4">
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg px-4 py-2 text-sm font-medium text-neutral-600 hover:bg-neutral-50"
        >
          Cancel
        </button>
        <button
          type="button"
          disabled={!check.valid || requestOp.isPending}
          onClick={() => requestOp.mutate()}
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
        >
          {requestOp.isPending ? (
            <Loader2 size={15} className="animate-spin" />
          ) : (
            <ShieldCheck size={15} />
          )}
          Send to patient
        </button>
      </div>
    </Modal>
  )
}
