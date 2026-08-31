import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Eye, Loader2, ShieldOff } from 'lucide-react'

import { Modal } from '../../../components/ui/Modal'
import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { retentionApi } from '../../../services/compliance/complianceApi'
import type { RetentionPolicy, RetentionRunItem } from '../../../types/compliance'

/**
 * Retention policy administration — WO-025.
 *
 * <p>This screen arms the only job in the system that destroys patient records
 * on a schedule, unattended. It is built to make that fact hard to miss rather
 * than convenient: arming requires typing the store name, the preview is the
 * prominent action, and a policy in dry-run is shown as inactive rather than as
 * a subtle badge.
 *
 * <p>There is no "run now". The server has no such endpoint either — preview
 * forces dry-run regardless of configuration. Destruction happens on the nightly
 * schedule, after someone read a preview.
 */
export default function RetentionPolicyPage() {
  const queryClient = useQueryClient()
  const [arming, setArming] = useState<RetentionPolicy | null>(null)
  const [previewItems, setPreviewItems] = useState<RetentionRunItem[] | null>(null)

  const { data: policies = [], isLoading } = useQuery({
    queryKey: ['retention-policies'],
    queryFn: retentionApi.policies,
  })

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['retention-policies'] })

  const preview = useMutation({
    mutationFn: retentionApi.preview,
    onSuccess: async run => {
      setPreviewItems(await retentionApi.runDetail(run.id))
    },
    onError: (e: Error) => toast({ title: 'Preview failed', description: e.message }),
  })

  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Record<string, unknown> }) =>
      retentionApi.update(id, body),
    onSuccess: p => {
      toast({
        title: p.enabled && !p.dryRun
          ? `${p.targetStore} is armed and will delete on the next run`
          : 'Policy updated',
      })
      setArming(null)
      invalidate()
    },
    onError: (e: Error) => toast({ title: 'Could not update', description: e.message }),
  })

  const liveCount = policies.filter(p => p.enabled && !p.dryRun).length

  return (
    <div className="space-y-4 p-6">
      <header className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Data retention</h1>
          <p className="text-sm text-slate-600">
            When each kind of data stops being kept. Section 8(7) requires
            personal data to be erased once its purpose is served.
          </p>
        </div>
        <button
          onClick={() => preview.mutate()}
          disabled={preview.isPending}
          className="inline-flex shrink-0 items-center gap-2 rounded-md border border-slate-300 px-3 py-2 text-sm hover:bg-slate-50"
        >
          {preview.isPending ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Eye className="h-4 w-4" />
          )}
          Preview — changes nothing
        </button>
      </header>

      {/* Building the engine did not fix the risk; arming it will. Until then
          storage limitation is still unmet, and that should be visible. */}
      {liveCount === 0 && (
        <div className="flex items-start gap-3 rounded-md border border-amber-200 bg-amber-50 p-3">
          <ShieldOff className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
          <div className="text-sm text-amber-900">
            <p className="font-medium">No policy is active. Nothing is being deleted.</p>
            <p>
              Every policy is in preview mode. Storage limitation under s. 8(7) is
              not yet in effect. Run a preview, have the periods approved, then
              arm one policy at a time.
            </p>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center gap-2 p-8 text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading…
        </div>
      ) : (
        <div className="space-y-3">
          {policies.map(p => {
            const live = p.enabled && !p.dryRun
            return (
              <div
                key={p.id}
                className={cn(
                  'rounded-md border p-4',
                  live ? 'border-red-300 bg-red-50' : 'border-slate-200',
                )}
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-sm text-slate-800">
                        {p.targetStore}
                      </span>
                      <span
                        className={cn(
                          'rounded px-2 py-0.5 text-xs font-medium',
                          p.action === 'DELETE'
                            ? 'bg-red-100 text-red-800'
                            : 'bg-amber-100 text-amber-800',
                        )}
                      >
                        {p.action}
                      </span>
                      <span
                        className={cn(
                          'rounded px-2 py-0.5 text-xs font-medium',
                          live
                            ? 'bg-red-600 text-white'
                            : 'bg-slate-200 text-slate-700',
                        )}
                      >
                        {live ? 'ACTIVE — deletes data' : 'Preview only'}
                      </span>
                    </div>

                    <p className="mt-1 text-sm text-slate-700">
                      After <strong>{p.retentionDays} days</strong>, measured from{' '}
                      <code className="text-xs">{p.dateColumn}</code>
                      {p.action === 'ANONYMISE' && (
                        <> — clears <code className="text-xs">{p.anonymiseColumn}</code></>
                      )}
                    </p>

                    <p className="mt-1 text-xs text-slate-600">{p.justification}</p>
                    {p.statutoryBasis && (
                      <p className="mt-0.5 text-xs text-slate-500">
                        Basis: {p.statutoryBasis}
                      </p>
                    )}
                    {p.lastRunAt && (
                      <p className="mt-1 text-xs text-slate-500">
                        Last run {new Date(p.lastRunAt).toLocaleString()} —{' '}
                        {p.lastRunAffected ?? 0} row(s)
                      </p>
                    )}
                  </div>

                  <div className="flex shrink-0 flex-col gap-2">
                    {live ? (
                      <button
                        onClick={() =>
                          update.mutate({ id: p.id, body: { dryRun: true } })
                        }
                        className="rounded border border-slate-300 px-2 py-1 text-xs hover:bg-white"
                      >
                        Return to preview
                      </button>
                    ) : (
                      <button
                        onClick={() => setArming(p)}
                        className="rounded bg-red-600 px-2 py-1 text-xs font-medium text-white hover:bg-red-700"
                      >
                        Arm
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {previewItems && (
        <Modal
          isOpen
          onClose={() => setPreviewItems(null)}
          size="2xl"
          title="Preview — nothing was changed"
          description="What each policy would affect if it were armed today."
        >
          {previewItems.length === 0 ? (
            <p className="p-4 text-sm text-slate-500">
              No enabled policies to evaluate. Enable a policy first — it stays in
              preview until you arm it separately.
            </p>
          ) : (
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2">Store</th>
                  <th className="px-3 py-2">Action</th>
                  <th className="px-3 py-2">Cutoff</th>
                  <th className="px-3 py-2">Would affect</th>
                  <th className="px-3 py-2">Note</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {previewItems.map(i => (
                  <tr key={i.id} className={i.outcome === 'SKIPPED' ? 'bg-amber-50' : ''}>
                    <td className="px-3 py-2 font-mono text-xs">{i.targetStore}</td>
                    <td className="px-3 py-2">{i.action}</td>
                    <td className="px-3 py-2 text-xs text-slate-600">
                      {new Date(i.cutoffAt).toLocaleDateString()}
                    </td>
                    <td className="px-3 py-2 font-medium">{i.rowsMatched}</td>
                    <td className="px-3 py-2 text-xs text-slate-500">{i.detail ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p className="mt-3 text-xs text-slate-500">
            A count far larger than you expect usually means the policy is
            measuring from the wrong date column.
          </p>
        </Modal>
      )}

      {arming && (
        <ArmModal
          policy={arming}
          onClose={() => setArming(null)}
          onArm={() =>
            update.mutate({ id: arming.id, body: { enabled: true, dryRun: false } })
          }
          submitting={update.isPending}
        />
      )}
    </div>
  )
}

/**
 * Arming confirmation.
 *
 * <p>Requires typing the store name. That is friction on purpose: this is the
 * single action in the application that causes patient records to be destroyed
 * on a schedule with nobody watching, and it should not be reachable by a
 * misplaced click.
 */
function ArmModal({
  policy, onClose, onArm, submitting,
}: {
  policy: RetentionPolicy
  onClose: () => void
  onArm: () => void
  submitting: boolean
}) {
  const [typed, setTyped] = useState('')
  const confirmed = typed.trim() === policy.targetStore

  return (
    <Modal
      isOpen
      onClose={onClose}
      size="lg"
      title={`Arm retention for ${policy.targetStore}?`}
      description="This takes effect on the next nightly run."
    >
      <div className="space-y-3">
        <div className="flex items-start gap-3 rounded-md border border-red-300 bg-red-50 p-3">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
          <div className="text-sm text-red-900">
            <p className="font-medium">
              Rows older than {policy.retentionDays} days will be{' '}
              {policy.action === 'DELETE' ? 'permanently deleted' : 'anonymised'}.
            </p>
            <p>
              {policy.action === 'DELETE'
                ? 'Deleted rows cannot be recovered.'
                : `The ${policy.anonymiseColumn} link will be cleared and cannot be restored.`}{' '}
              Up to {policy.maxRowsPerRun} rows per run.
            </p>
          </div>
        </div>

        <p className="text-sm text-slate-700">
          Run a preview first if you have not. Confirm the counts look like what
          you expect before arming.
        </p>

        <label className="block text-sm">
          <span className="text-slate-700">
            Type <code className="font-mono">{policy.targetStore}</code> to confirm
          </span>
          <input
            value={typed}
            onChange={e => setTyped(e.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm"
            autoComplete="off"
          />
        </label>

        <div className="flex justify-end gap-2 pt-2">
          <button onClick={onClose} className="rounded-md border border-slate-300 px-4 py-2 text-sm">
            Cancel
          </button>
          <button
            disabled={!confirmed || submitting}
            onClick={onArm}
            className={cn(
              'inline-flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium text-white',
              confirmed && !submitting
                ? 'bg-red-600 hover:bg-red-700'
                : 'cursor-not-allowed bg-slate-300',
            )}
          >
            {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
            Arm this policy
          </button>
        </div>
      </div>
    </Modal>
  )
}
