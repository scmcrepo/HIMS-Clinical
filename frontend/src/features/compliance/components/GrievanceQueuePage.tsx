import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Clock, Loader2, MessageSquare, Plus, ShieldCheck } from 'lucide-react'

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
      <div className="flex flex-col max-h-[85vh]">
        <div className="px-6 pt-6 pb-4 border-b border-slate-100 pr-12 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-center text-emerald-600 shrink-0">
              <CheckCircle2 className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 leading-tight">Resolve Complaint</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Grievance Ref: <span className="font-mono font-semibold text-slate-700">{grievance.grievanceRef}</span>
              </p>
            </div>
          </div>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Resolution Decision & Communication
            </label>
            <p className="text-xs text-slate-500 mb-2">
              What you write here is the official determination that will be relayed to the complainant.
            </p>
            <textarea
              value={resolution}
              onChange={e => setResolution(e.target.value)}
              rows={8}
              placeholder="Detail what was investigated, what was decided, and any remediation steps taken..."
              className="w-full rounded-xl border border-slate-300 p-3.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-emerald-600 leading-relaxed"
            />
          </div>
        </div>

        <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end gap-2.5 shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={!resolution.trim() || submitting}
            onClick={() => onResolve(resolution.trim())}
            className={cn(
              'inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white transition-colors cursor-pointer shadow-2xs',
              resolution.trim() && !submitting
                ? 'bg-emerald-600 hover:bg-emerald-700'
                : 'cursor-not-allowed bg-slate-300',
            )}
          >
            {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
            <CheckCircle2 className="h-4 w-4" />
            Resolve Grievance
          </button>
        </div>
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
      <div className="flex flex-col max-h-[85vh]">
        <div className="px-6 pt-6 pb-4 border-b border-slate-100 pr-12 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-center text-blue-600 shrink-0">
              <MessageSquare className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 leading-tight">Record a Complaint</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Log under DPDP grievance redressal even without matching patient ID.
              </p>
            </div>
          </div>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Patient ID (if known)</label>
              <input
                value={patientId}
                onChange={e => setPatientId(e.target.value)}
                placeholder="UUID or MRN"
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 font-mono text-xs bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Or how to reach them</label>
              <input
                value={contact}
                onChange={e => setContact(e.target.value)}
                placeholder="Phone or email"
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-xs bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>
          </div>

          {!reachable && (
            <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-2.5">
              One of Patient ID or Contact is required so the resolution can be communicated to the complainant.
            </p>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">About</label>
              <select
                value={category}
                onChange={e => setCategory(e.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-xs bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
              >
                {['CONSENT', 'ACCESS_REQUEST', 'CORRECTION', 'ERASURE', 'DATA_ACCURACY',
                  'UNAUTHORISED_USE', 'SERVICE', 'OTHER'].map(c => (
                  <option key={c} value={c}>{c.replace(/_/g, ' ').toLowerCase()}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Received via</label>
              <select
                value={channel}
                onChange={e => setChannel(e.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-xs bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
              >
                {['IN_PERSON', 'PHONE', 'EMAIL', 'PORTAL', 'POST', 'WHATSAPP'].map(c => (
                  <option key={c} value={c}>{c.replace(/_/g, ' ').toLowerCase()}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Summary</label>
            <input
              value={subject}
              onChange={e => setSubject(e.target.value)}
              maxLength={200}
              placeholder="Brief summary of the issue"
              className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">What they said</label>
            <textarea
              value={body}
              onChange={e => setBody(e.target.value)}
              rows={4}
              placeholder="In their own words where possible..."
              className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-600 leading-relaxed"
            />
          </div>
        </div>

        <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end gap-2.5 shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
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
              'inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white transition-colors cursor-pointer shadow-2xs',
              reachable && subject.trim() && !raise.isPending
                ? 'bg-blue-600 hover:bg-blue-700'
                : 'cursor-not-allowed bg-slate-300',
            )}
          >
            {raise.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Record Grievance
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
      <div className="flex flex-col max-h-[85vh]">
        <div className="px-6 pt-6 pb-4 border-b border-slate-100 pr-12 shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-center text-blue-600 shrink-0">
              <ShieldCheck className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900 leading-tight">Data Protection Contact</h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Published publicly for DPDP s. 8(9) compliance & grievance intake.
              </p>
            </div>
          </div>
        </div>

        <div className="p-6 space-y-4 overflow-y-auto flex-1">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">Name or role</label>
            <input
              value={displayName}
              onChange={e => setDisplayName(e.target.value)}
              placeholder="e.g. Data Protection Officer or Grievance Officer"
              className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
            />
            <p className="text-xs text-slate-400 mt-1">A role title survives staff turnover better than an individual's name.</p>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Designation</label>
              <input
                value={designation}
                onChange={e => setDesignation(e.target.value)}
                placeholder="e.g. Compliance Lead"
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Email <span className="text-red-500">*</span></label>
              <input
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="grievance@hospital.org"
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Phone</label>
              <input
                value={phone}
                onChange={e => setPhone(e.target.value)}
                placeholder="+91 80 1234 5678"
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Postal address</label>
              <input
                value={postalAddress}
                onChange={e => setPostalAddress(e.target.value)}
                placeholder="Hospital Postal Address"
                className="w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-blue-600"
              />
            </div>
          </div>

          <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-3 bg-slate-50/50">
            <input
              type="checkbox"
              checked={isDpo}
              onChange={e => setIsDpo(e.target.checked)}
              className="mt-1 h-4 w-4 rounded border-slate-300 text-blue-600"
            />
            <span className="text-sm text-slate-800">
              This person is our Data Protection Officer (DPO).
              <span className="block text-xs text-slate-500 mt-0.5">
                Only tick this if the hospital has determined it is a Significant
                Data Fiduciary. It carries formal statutory responsibilities.
              </span>
            </span>
          </label>

          <label className="flex cursor-pointer items-center gap-3">
            <input
              type="checkbox"
              checked={basedInIndia}
              onChange={e => setBasedInIndia(e.target.checked)}
              className="h-4 w-4 rounded border-slate-300 text-blue-600"
            />
            <span className="text-sm text-slate-800 font-medium">Based in India</span>
          </label>

          {dpoOutsideIndia && (
            <p className="text-xs font-medium text-red-600 bg-red-50 border border-red-200 rounded-lg p-2.5">
              Rule 13 statutory requirement: a Significant Data Fiduciary's DPO must be based in India.
            </p>
          )}
        </div>

        <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex justify-end gap-2.5 shrink-0">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
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
              'inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white transition-colors cursor-pointer shadow-2xs',
              displayName.trim() && email.trim() && !dpoOutsideIndia && !publish.isPending
                ? 'bg-blue-600 hover:bg-blue-700'
                : 'cursor-not-allowed bg-slate-300',
            )}
          >
            {publish.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
            Publish Contact
          </button>
        </div>
      </div>
    </Modal>
  )
}
