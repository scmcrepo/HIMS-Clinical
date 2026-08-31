import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Loader2, Plus, ShieldAlert } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { incidentApi } from '../../../services/compliance/complianceApi'
import type { IncidentState, SecurityIncident } from '../../../types/compliance'

const STATES: { value: IncidentState; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'CONTAINED', label: 'Contained' },
  { value: 'NOTIFIED', label: 'Notified' },
  { value: 'CLOSED', label: 'Closed' },
  { value: 'DISMISSED', label: 'Dismissed' },
]

const SEVERITY_STYLE: Record<string, string> = {
  CRITICAL: 'bg-red-600 text-white',
  HIGH: 'bg-red-100 text-red-800',
  MEDIUM: 'bg-amber-100 text-amber-800',
  LOW: 'bg-slate-100 text-slate-700',
}

/**
 * The breach register — WO-026.
 *
 * <p>Until this screen existed the incident API was unreachable by staff, which
 * defeated the point of granting {@code INCIDENT_RAISE} to clinical and
 * reception roles: a near-miss nobody can file is a near-miss nobody learns
 * from. Reporting is therefore the primary action here, not a buried one.
 *
 * <p>The two Rule 7 clocks are shown separately, because the initial intimation
 * and the 72-hour report are missed independently and a single "notified" badge
 * cannot tell an operator which obligation is about to lapse.
 */
export default function IncidentRegisterPage() {
  const queryClient = useQueryClient()
  const [state, setState] = useState<IncidentState>('OPEN')
  const [reporting, setReporting] = useState(false)
  const [noticeFor, setNoticeFor] = useState<SecurityIncident | null>(null)
  const [noticeText, setNoticeText] = useState('')

  const { data: incidents = [], isLoading } = useQuery({
    queryKey: ['incidents', state],
    queryFn: () => incidentApi.queue(state),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['incidents'] })

  const raise = useMutation({
    mutationFn: incidentApi.raise,
    onSuccess: i => {
      toast({ title: `Incident ${i.incidentRef} recorded` })
      setReporting(false)
      invalidate()
    },
    onError: (e: Error) => toast({ title: 'Could not record', description: e.message }),
  })

  const openNotice = async (incident: SecurityIncident) => {
    try {
      setNoticeFor(incident)
      setNoticeText(await incidentApi.draftNotice(incident.id))
    } catch (e) {
      toast({ title: 'Could not draft the notice', description: (e as Error).message })
      setNoticeFor(null)
    }
  }

  return (
    <div className="space-y-4 p-6">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Security incidents</h1>
          <p className="text-sm text-slate-600">
            Personal data breaches and near-misses. If you think something went
            wrong, record it — an over-reported incident costs a conversation.
          </p>
        </div>
        <button
          onClick={() => setReporting(true)}
          className="inline-flex shrink-0 items-center gap-2 rounded-md bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-700"
        >
          <Plus className="h-4 w-4" />
          Report an incident
        </button>
      </header>

      <div className="flex flex-wrap gap-2">
        {STATES.map(s => (
          <button
            key={s.value}
            onClick={() => setState(s.value)}
            className={cn(
              'rounded-full px-3 py-1 text-sm',
              state === s.value
                ? 'bg-slate-800 text-white'
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
      ) : incidents.length === 0 ? (
        <p className="rounded-md border border-slate-200 bg-slate-50 p-8 text-center text-sm text-slate-500">
          Nothing in this state.
        </p>
      ) : (
        <div className="space-y-3">
          {incidents.map(i => (
            <div key={i.id} className="rounded-md border border-slate-200 p-4">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm text-slate-700">
                      {i.incidentRef}
                    </span>
                    <span
                      className={cn(
                        'rounded px-2 py-0.5 text-xs font-medium',
                        SEVERITY_STYLE[i.severity] ?? SEVERITY_STYLE.LOW,
                      )}
                    >
                      {i.severity}
                    </span>
                    <span className="text-xs text-slate-500">{i.category}</span>
                    {i.detectionSource === 'AUTOMATED_DETECTION' && (
                      <span className="rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-800">
                        Auto-detected
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-sm text-slate-800">{i.summary}</p>

                  <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs">
                    <span className="text-slate-500">
                      Detected {new Date(i.detectedAt).toLocaleString()}
                    </span>
                    <span className={i.scopeUncertain ? 'text-amber-700' : 'text-slate-600'}>
                      {i.scopeUncertain
                        ? 'Scope not yet established'
                        : `${i.affectedPrincipalCount} affected`}
                    </span>
                  </div>

                  {/* Two clocks, shown apart. They lapse independently. */}
                  <div className="mt-2 flex flex-wrap gap-2 text-xs">
                    <span
                      className={cn(
                        'rounded px-2 py-0.5',
                        i.boardNotifiedAt
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-red-100 text-red-800',
                      )}
                    >
                      {i.boardNotifiedAt ? 'Board notified' : 'Board NOT notified'}
                    </span>
                    <span
                      className={cn(
                        'rounded px-2 py-0.5',
                        i.boardDetailReportAt
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-amber-100 text-amber-800',
                      )}
                    >
                      {i.boardDetailReportAt ? '72h report filed' : '72h report outstanding'}
                    </span>
                    {i.affectedPrincipalCount > 0 && (
                      <span
                        className={cn(
                          'rounded px-2 py-0.5',
                          i.principalsNotifiedAt
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-red-100 text-red-800',
                        )}
                      >
                        {i.principalsNotifiedAt
                          ? 'People notified'
                          : 'People NOT notified'}
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex shrink-0 flex-col gap-2">
                  {i.affectedPrincipalCount > 0 && (
                    <button
                      onClick={() => openNotice(i)}
                      className="rounded border border-slate-300 px-2 py-1 text-xs hover:bg-slate-50"
                    >
                      Draft notice
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {reporting && (
        <ReportIncidentModal
          onClose={() => setReporting(false)}
          onSubmit={body => raise.mutate(body)}
          submitting={raise.isPending}
        />
      )}

      {noticeFor && (
        <Modal
          isOpen
          onClose={() => setNoticeFor(null)}
          size="2xl"
          title={`Notice for ${noticeFor.incidentRef}`}
          description="Rule 7 requires nature, likely consequences, remedial steps and a contact. Review before sending."
        >
          <textarea
            readOnly
            value={noticeText}
            rows={18}
            className="w-full rounded-md border border-slate-300 p-3 font-mono text-xs"
          />
          <p className="mt-2 text-xs text-slate-500">
            Generated from the incident record. Edit before sending if the
            circumstances need it.
          </p>
        </Modal>
      )}
    </div>
  )
}

interface ReportProps {
  onClose: () => void
  onSubmit: (body: {
    category: string
    severity: string
    summary: string
    detail?: string
    dataCategories?: string
    scopeUncertain: boolean
  }) => void
  submitting: boolean
}

/**
 * Reporting form.
 *
 * <p>{@code scopeUncertain} defaults to true. Scope is almost never known at the
 * moment of discovery, and a form that defaults to "0 affected" produces a
 * confident zero where an honest "not yet known" belongs — which is the number
 * that would then be reported to the Board.
 */
function ReportIncidentModal({ onClose, onSubmit, submitting }: ReportProps) {
  const [category, setCategory] = useState('UNAUTHORISED_ACCESS')
  const [severity, setSeverity] = useState('MEDIUM')
  const [summary, setSummary] = useState('')
  const [detail, setDetail] = useState('')
  const [dataCategories, setDataCategories] = useState('')
  const [scopeUncertain, setScopeUncertain] = useState(true)

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="lg"
      title="Report an incident"
      description="Better reported and dismissed than unreported."
    >
      <div className="space-y-3">
        <div className="flex items-start gap-3 rounded-md border border-amber-200 bg-amber-50 p-3">
          <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
          <p className="text-sm text-amber-900">
            Do not put patient names or contact details in the description. Who
            was affected is recorded separately, by patient id.
          </p>
        </div>

        <label className="block text-sm">
          <span className="text-slate-700">What happened</span>
          <input
            value={summary}
            onChange={e => setSummary(e.target.value)}
            maxLength={500}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            placeholder="A short factual summary"
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="text-slate-700">Category</span>
            <select
              value={category}
              onChange={e => setCategory(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            >
              {['CROSS_TENANT_ACCESS', 'UNAUTHORISED_ACCESS', 'DATA_LOSS',
                'DATA_EXPOSURE', 'CREDENTIAL_COMPROMISE', 'INTEGRITY_COMPROMISE',
                'AVAILABILITY', 'OTHER'].map(c => (
                <option key={c} value={c}>{c.replace(/_/g, ' ').toLowerCase()}</option>
              ))}
            </select>
          </label>

          <label className="block text-sm">
            <span className="text-slate-700">Severity</span>
            <select
              value={severity}
              onChange={e => setSeverity(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            >
              {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(s => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </label>
        </div>

        <label className="block text-sm">
          <span className="text-slate-700">What kind of information was involved</span>
          <input
            value={dataCategories}
            onChange={e => setDataCategories(e.target.value)}
            maxLength={300}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
            placeholder="e.g. names and contact numbers"
          />
        </label>

        <label className="block text-sm">
          <span className="text-slate-700">More detail</span>
          <textarea
            value={detail}
            onChange={e => setDetail(e.target.value)}
            rows={4}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2"
          />
        </label>

        <label className="flex cursor-pointer items-start gap-3">
          <input
            type="checkbox"
            checked={scopeUncertain}
            onChange={e => setScopeUncertain(e.target.checked)}
            className="mt-1 h-4 w-4 rounded border-slate-300"
          />
          <span className="text-sm text-slate-800">
            We do not yet know how many people are affected.
            <span className="block text-xs text-slate-500">
              Leave this ticked unless you are sure. An unknown scope recorded as
              zero looks like a contained incident.
            </span>
          </span>
        </label>

        <div className="flex justify-end gap-2 pt-2">
          <button
            onClick={onClose}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm"
          >
            Cancel
          </button>
          <button
            disabled={!summary.trim() || submitting}
            onClick={() =>
              onSubmit({
                category,
                severity,
                summary: summary.trim(),
                detail: detail.trim() || undefined,
                dataCategories: dataCategories.trim() || undefined,
                scopeUncertain,
              })
            }
            className={cn(
              'inline-flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium text-white',
              summary.trim() && !submitting
                ? 'bg-red-600 hover:bg-red-700'
                : 'cursor-not-allowed bg-slate-300',
            )}
          >
            {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
            <AlertTriangle className="h-4 w-4" />
            Record incident
          </button>
        </div>
      </div>
    </Modal>
  )
}
