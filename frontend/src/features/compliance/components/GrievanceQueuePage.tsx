import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Clock, Loader2, Plus } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { grievanceApi } from '../../../services/compliance/complianceApi'
import type { Grievance, GrievanceState } from '../../../types/compliance'

const STATES: { value: GrievanceState; label: string }[] = [
  { value: 'RECEIVED', label: 'New' },
  { value: 'ACKNOWLEDGED', label: 'Acknowledged' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'WITHDRAWN', label: 'Withdrawn' },
]

const daysUntil = (iso: string) =>
  Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000)

/**
 * Grievance redressal — WO-027.
 *
 * <p>The Act asks for an <em>effective</em> mechanism, not a reachable one, and
 * the difference shows up in this screen. Acknowledgement is a separate,
 * prominent action rather than an implicit side effect of opening the record,
 * because being heard and being answered are different things and the gap
 * between them is where a complainant decides to go to the Board instead.
 *
 * <p>Both clocks are shown: the internal 30-day target and the 90-day statutory
 * ceiling. Showing only the ceiling is how a ceiling becomes a norm.
 */
export default function GrievanceQueuePage() {
  const queryClient = useQueryClient()
  const [state, setState] = useState<GrievanceState>('RECEIVED')
  const [raising, setRaising] = useState(false)
  const [resolving, setResolving] = useState<Grievance | null>(null)
  const [managingContact, setManagingContact] = useState(false)

  const { data: grievances = [], isLoading } = useQuery({
    queryKey: ['grievances', state],
    queryFn: () => grievanceApi.queue(state),
  })

  const { data: contact, isError: contactMissing } = useQuery({
    queryKey: ['compliance-contact'],
    queryFn: grievanceApi.currentContact,
    retry: false,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['grievances'] })

  const acknowledge = useMutation({
    mutationFn: (id: string) => grievanceApi.acknowledge(id),
    onSuccess: () => {
      toast({ title: 'Acknowledged. Tell the complainant it is being looked at.' })
      invalidate()
    },
  })

  const resolve = useMutation({
    mutationFn: ({ id, resolution }: { id: string; resolution: string }) =>
      grievanceApi.resolve(id, resolution),
    onSuccess: () => {
      toast({ title: 'Resolved' })
      setResolving(null)
      invalidate()
    },
    onError: (e: Error) => toast({ title: 'Could not resolve', description: e.message }),
  })

  return (
    <div className="space-y-4 p-6">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Grievances</h1>
          <p className="text-sm text-slate-600">
            Data protection complaints from patients. Acknowledge within three
            days; resolve within thirty.
          </p>
        </div>
        <div className="flex shrink-0 gap-2">
          <button
            onClick={() => setManagingContact(true)}
            className="rounded-md border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50"
          >
            Contact point
          </button>
          <button
            onClick={() => setRaising(true)}
            className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Plus className="h-4 w-4" />
            Record a complaint
          </button>
        </div>
      </header>

      {/* s. 8(9) requires a published contact. Until one exists the obligation is
          unmet, and the public endpoint returns 404 — so this is not cosmetic. */}
      {contactMissing && (
        <div className="flex items-start gap-3 rounded-md border border-red-300 bg-red-50 p-3">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
          <div className="text-sm text-red-900">
            <p className="font-medium">No data protection contact is published.</p>
            <p>
              Section 8(9) requires one. Until it is set, patients have no
              published route to complain and the public contact page returns
              nothing.
            </p>
          </div>
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        {STATES.map(s => (
          <button
            key={s.value}
            onClick={() => setState(s.value)}
            className={cn(
              'rounded-full px-3 py-1 text-sm',
              state === s.value
                ? 'bg-blue-600 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200',
            )}
          >
            {s.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 p-8 text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      ) : grievances.length === 0 ? (
        <p className="rounded-md border border-slate-200 bg-slate-50 p-8 text-center text-sm text-slate-500">
          Nothing in this state.
        </p>
      ) : (
        <div className="space-y-3">
          {grievances.map(g => {
            const dueIn = daysUntil(g.dueAt)
            const pastTarget = new Date(g.targetAt).getTime() < Date.now()
            const overdue = dueIn < 0

            return (
              <div
                key={g.id}
                className={cn(
                  'rounded-md border p-4',
                  overdue ? 'border-red-300 bg-red-50' : 'border-slate-200',
                )}
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-sm text-slate-700">
                        {g.grievanceRef}
                      </span>
                      <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
                        {g.category.replace(/_/g, ' ').toLowerCase()}
                      </span>
                      <span className="text-xs text-slate-500">via {g.channel}</span>
                      {g.escalatedToBoard && (
                        <span className="rounded bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
                          Escalated to Board
                        </span>
                      )}
                    </div>

                    <p className="mt-1 text-sm text-slate-800">{g.subject}</p>

                    <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
                      {!g.acknowledgedAt && (
                        <span className="font-medium text-amber-700">
                          Not yet acknowledged
                        </span>
                      )}
                      <span
                        className={cn(
                          'inline-flex items-center gap-1',
                          overdue
                            ? 'font-medium text-red-700'
                            : pastTarget
                              ? 'text-amber-700'
                              : 'text-slate-600',
                        )}
                      >
                        <Clock className="h-3.5 w-3.5" />
                        {overdue
                          ? `${Math.abs(dueIn)} days past the statutory deadline`
                          : pastTarget
                            ? `Past internal target — ${dueIn} days to the deadline`
                            : `${dueIn} days to the deadline`}
                      </span>
                    </div>
                  </div>

                  <div className="flex shrink-0 flex-col gap-2">
                    {!g.acknowledgedAt && (
                      <button
                        onClick={() => acknowledge.mutate(g.id)}
                        className="rounded bg-amber-600 px-2 py-1 text-xs font-medium text-white hover:bg-amber-700"
                      >
                        Acknowledge
                      </button>
                    )}
                    {['RECEIVED', 'ACKNOWLEDGED', 'IN_PROGRESS'].includes(g.state) && (
                      <button
                        onClick={() => setResolving(g)}
                        className="rounded border border-slate-300 px-2 py-1 text-xs hover:bg-slate-50"
                      >
                        Resolve
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {resolving && (
        <ResolveModal
          grievance={resolving}
          onClose={() => setResolving(null)}
          onResolve={resolution => resolve.mutate({ id: resolving.id, resolution })}
          submitting={resolve.isPending}
        />
      )}

      {raising && <RaiseGrievanceModal onClose={() => setRaising(false)} onDone={invalidate} />}

      {managingContact && (
        <ContactModal
          current={contact}
          onClose={() => setManagingContact(false)}
          onDone={() => {
            queryClient.invalidateQueries({ queryKey: ['compliance-contact'] })
            setManagingContact(false)
          }}
        />
      )}
    </div>
  )
}

/**
 * Resolution requires text, mirroring the server and the database constraint.
 * A status change dressed up as an answer is, from the complainant's side,
 * indistinguishable from being ignored.
 */
function ResolveModal({
  grievance, onClose, onResolve, submitting,
}: {
  grievance: Grievance
  onClose: () => void
  onResolve: (resolution: string) => void
  submitting: boolean
}) {
  const [resolution, setResolution] = useState('')

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="lg"
      title={`Resolve ${grievance.grievanceRef}`}
      description="What you write here is what the complainant will be told."
    >
      <textarea
        value={resolution}
        onChange={e => setResolution(e.target.value)}
        rows={8}
        placeholder="What was decided, and why."
        className="w-full rounded-md border border-slate-300 p-3 text-sm"
      />
      <div className="mt-3 flex justify-end gap-2">
        <button onClick={onClose} className="rounded-md border border-slate-300 px-4 py-2 text-sm">
          Cancel
        </button>
        <button
          disabled={!resolution.trim() || submitting}
          onClick={() => onResolve(resolution.trim())}
          className={cn(
            'inline-flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium text-white',
            resolution.trim() && !submitting
              ? 'bg-blue-600 hover:bg-blue-700'
              : 'cursor-not-allowed bg-slate-300',
          )}
        >
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          Resolve
        </button>
      </div>
    </Modal>
  )
}

/**
 * Intake. Either a patient id or contact details, not necessarily both — someone
 * complaining that you hold their data wrongly may not match a record the way
 * you expect, and refusing to log that complaint would be a tidy way of never
 * recording the inconvenient ones.
 */
function RaiseGrievanceModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [patientId, setPatientId] = useState('')
  const [contact, setContact] = useState('')
  const [category, setCategory] = useState('OTHER')
  const [channel, setChannel] = useState('IN_PERSON')
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')

  const raise = useMutation({
    mutationFn: grievanceApi.raise,
    onSuccess: g => {
      toast({ title: `Complaint ${g.grievanceRef} recorded` })
      onDone()
      onClose()
    },
    onError: (e: Error) => toast({ title: 'Could not record', description: e.message }),
  })

  const reachable = patientId.trim() || contact.trim()

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="lg"
      title="Record a complaint"
      description="Log it even if you cannot match the person to a patient record."
    >
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="text-slate-700">Patient ID (if known)</span>
            <input
              value={patientId}
              onChange={e => setPatientId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-xs"
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-700">Or how to reach them</span>
            <input
              value={contact}
              onChange={e => setContact(e.target.value)}
              placeholder="Phone or email"
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
        </div>

        {!reachable && (
          <p className="text-xs text-amber-700">
            One of the two is needed — otherwise there is no way to tell them what
            was decided.
          </p>
        )}

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="text-slate-700">About</span>
            <select
              value={category}
              onChange={e => setCategory(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            >
              {['CONSENT', 'ACCESS_REQUEST', 'CORRECTION', 'ERASURE', 'DATA_ACCURACY',
                'UNAUTHORISED_USE', 'SERVICE', 'OTHER'].map(c => (
                <option key={c} value={c}>{c.replace(/_/g, ' ').toLowerCase()}</option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="text-slate-700">Received via</span>
            <select
              value={channel}
              onChange={e => setChannel(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            >
              {['IN_PERSON', 'PHONE', 'EMAIL', 'PORTAL', 'POST', 'WHATSAPP'].map(c => (
                <option key={c} value={c}>{c.replace(/_/g, ' ').toLowerCase()}</option>
              ))}
            </select>
          </label>
        </div>

        <label className="block text-sm">
          <span className="text-slate-700">Summary</span>
          <input
            value={subject}
            onChange={e => setSubject(e.target.value)}
            maxLength={200}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
          />
        </label>

        <label className="block text-sm">
          <span className="text-slate-700">What they said</span>
          <textarea
            value={body}
            onChange={e => setBody(e.target.value)}
            rows={5}
            placeholder="In their own words where possible."
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
          />
        </label>

        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="rounded-md border border-slate-300 px-4 py-2 text-sm">
            Cancel
          </button>
          <button
            disabled={!reachable || !subject.trim() || raise.isPending}
            onClick={() =>
              raise.mutate({
                patientId: patientId.trim() || undefined,
                complainantContact: contact.trim() || undefined,
                category,
                channel,
                subject: subject.trim(),
                body: body.trim() || undefined,
              })
            }
            className={cn(
              'inline-flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium text-white',
              reachable && subject.trim() && !raise.isPending
                ? 'bg-blue-600 hover:bg-blue-700'
                : 'cursor-not-allowed bg-slate-300',
            )}
          >
            {raise.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Record
          </button>
        </div>
      </div>
    </Modal>
  )
}

/** The contact published under s. 8(9), and the DPO under Rule 13 if applicable. */
function ContactModal({
  current, onClose, onDone,
}: {
  current?: { displayName: string; designation?: string; email: string; phone?: string
             postalAddress?: string; isDpo: boolean; basedInIndia: boolean }
  onClose: () => void
  onDone: () => void
}) {
  const [displayName, setDisplayName] = useState(current?.displayName ?? '')
  const [designation, setDesignation] = useState(current?.designation ?? '')
  const [email, setEmail] = useState(current?.email ?? '')
  const [phone, setPhone] = useState(current?.phone ?? '')
  const [postalAddress, setPostalAddress] = useState(current?.postalAddress ?? '')
  const [isDpo, setIsDpo] = useState(current?.isDpo ?? false)
  const [basedInIndia, setBasedInIndia] = useState(current?.basedInIndia ?? true)

  const publish = useMutation({
    mutationFn: grievanceApi.publishContact,
    onSuccess: () => {
      toast({ title: 'Contact published' })
      onDone()
    },
    onError: (e: Error) => toast({ title: 'Could not publish', description: e.message }),
  })

  const dpoOutsideIndia = isDpo && !basedInIndia

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="lg"
      title="Data protection contact"
      description="Published publicly. Patients use this to raise complaints."
    >
      <div className="space-y-3">
        <label className="block text-sm">
          <span className="text-slate-700">Name or role</span>
          <input
            value={displayName}
            onChange={e => setDisplayName(e.target.value)}
            placeholder="A role name survives staff turnover better than a person's"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="text-slate-700">Designation</span>
            <input
              value={designation}
              onChange={e => setDesignation(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-700">Email</span>
            <input
              value={email}
              onChange={e => setEmail(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="text-slate-700">Phone</span>
            <input
              value={phone}
              onChange={e => setPhone(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
          <label className="block text-sm">
            <span className="text-slate-700">Postal address</span>
            <input
              value={postalAddress}
              onChange={e => setPostalAddress(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
        </div>

        <label className="flex cursor-pointer items-start gap-3">
          <input
            type="checkbox"
            checked={isDpo}
            onChange={e => setIsDpo(e.target.checked)}
            className="mt-1 h-4 w-4 rounded border-slate-300"
          />
          <span className="text-sm text-slate-800">
            This person is our Data Protection Officer.
            <span className="block text-xs text-slate-500">
              Only tick this if the hospital has determined it is a Significant
              Data Fiduciary. It is a legal claim with obligations attached, not
              a job title.
            </span>
          </span>
        </label>

        <label className="flex cursor-pointer items-center gap-3">
          <input
            type="checkbox"
            checked={basedInIndia}
            onChange={e => setBasedInIndia(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300"
          />
          <span className="text-sm text-slate-800">Based in India</span>
        </label>

        {dpoOutsideIndia && (
          <p className="text-sm text-red-600">
            Rule 13 requires a Significant Data Fiduciary's DPO to be based in
            India.
          </p>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="rounded-md border border-slate-300 px-4 py-2 text-sm">
            Cancel
          </button>
          <button
            disabled={!displayName.trim() || !email.trim() || dpoOutsideIndia || publish.isPending}
            onClick={() =>
              publish.mutate({
                displayName: displayName.trim(),
                designation: designation.trim() || undefined,
                email: email.trim(),
                phone: phone.trim() || undefined,
                postalAddress: postalAddress.trim() || undefined,
                isDpo,
                basedInIndia,
              })
            }
            className={cn(
              'inline-flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium text-white',
              displayName.trim() && email.trim() && !dpoOutsideIndia && !publish.isPending
                ? 'bg-blue-600 hover:bg-blue-700'
                : 'cursor-not-allowed bg-slate-300',
            )}
          >
            {publish.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Publish
          </button>
        </div>
      </div>
    </Modal>
  )
}
