import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, Download, FileText, Loader2, Lock, Plus } from 'lucide-react'

import { toast } from '../../../hooks/useToast'
import { cn } from '../../../lib/utils'
import { abdmApi } from '../../../services/abdm/abdmApi'
import ConsentRequestModal from './ConsentRequestModal'
import { HI_TYPES, groupByType, sortByRecordDate, type ExternalRecord } from '../types'

interface Props {
  patientId: string
  encounterId?: string
  /** Present when the viewer is mounted inside a case sheet that can receive imports. */
  caseSheetId?: string
}

/**
 * External health records viewer — Screen 3.2.
 *
 * <p>Records arrive here only while the consent that admitted them is still
 * live; the server filters expired and revoked artifacts out. An empty list
 * therefore means either no consent or an ended one, and the empty state says so
 * rather than implying the patient has no history.
 *
 * <p>Payloads are fetched one at a time, on open. The index deliberately carries
 * no clinical content: each open is separately audited server-side, and a bulk
 * fetch would both leak more than needed and bypass that record.
 */
export default function ExternalRecordsViewer({ patientId, encounterId, caseSheetId }: Props) {
  const queryClient = useQueryClient()
  const [requesting, setRequesting] = useState(false)
  const [openId, setOpenId] = useState<string | null>(null)

  const { data: records = [], isLoading } = useQuery({
    queryKey: ['abdm', 'records', patientId],
    queryFn: () => abdmApi.listRecords(patientId),
  })

  const { data: opened, isFetching: opening } = useQuery({
    queryKey: ['abdm', 'record', openId],
    queryFn: () => abdmApi.openRecord(openId!),
    enabled: !!openId,
  })

  const importOp = useMutation({
    mutationFn: (recordId: string) => abdmApi.importRecord(recordId, caseSheetId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['abdm', 'records', patientId] })
      toast({ title: 'Copied into the case sheet', variant: 'success' })
    },
    onError: () => toast({ title: 'Could not import the record', variant: 'destructive' }),
  })

  const grouped = groupByType(sortByRecordDate(records))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-neutral-900">Records from other providers</h3>
          <p className="mt-0.5 text-xs text-neutral-500">
            Retrieved through ABDM with the patient's consent.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setRequesting(true)}
          className="inline-flex items-center gap-1.5 rounded-lg border border-neutral-300 px-3 py-1.5 text-sm font-medium hover:bg-neutral-50"
        >
          <Plus size={14} />
          Request records
        </button>
      </div>

      {isLoading ? (
        <p className="text-sm text-neutral-500">Loading records…</p>
      ) : records.length === 0 ? (
        <div className="rounded-xl border border-dashed border-neutral-300 p-6 text-center">
          <Lock size={18} className="mx-auto text-neutral-400" />
          <p className="mt-2 text-sm font-medium text-neutral-700">No records available</p>
          <p className="mt-1 text-sm text-neutral-500">
            Either no consent has been granted, or a previous consent has expired or been
            withdrawn. This does not mean the patient has no history elsewhere.
          </p>
        </div>
      ) : (
        Object.entries(grouped).map(([label, items]) => (
          <section key={label}>
            <h4 className="text-xs font-medium uppercase tracking-wide text-neutral-500">
              {label} ({items.length})
            </h4>
            <ul className="mt-2 space-y-2">
              {items.map((r) => (
                <RecordRow
                  key={r.id}
                  record={r}
                  expanded={openId === r.id}
                  loading={opening && openId === r.id}
                  payload={openId === r.id ? opened?.payload : undefined}
                  canImport={!!caseSheetId && !r.imported}
                  importing={importOp.isPending}
                  onToggle={() => setOpenId(openId === r.id ? null : r.id)}
                  onImport={() => importOp.mutate(r.id)}
                />
              ))}
            </ul>
          </section>
        ))
      )}

      {requesting && (
        <ConsentRequestModal
          patientId={patientId}
          encounterId={encounterId}
          onClose={() => setRequesting(false)}
        />
      )}
    </div>
  )
}

function RecordRow({
  record,
  expanded,
  loading,
  payload,
  canImport,
  importing,
  onToggle,
  onImport,
}: {
  record: ExternalRecord
  expanded: boolean
  loading: boolean
  payload?: string
  canImport: boolean
  importing: boolean
  onToggle: () => void
  onImport: () => void
}) {
  return (
    <li className="rounded-xl border border-neutral-200">
      <div className="flex items-start justify-between gap-3 p-3">
        <button
          type="button"
          onClick={onToggle}
          aria-expanded={expanded}
          className="min-w-0 flex-1 text-left"
        >
          <p className="flex items-center gap-2 text-sm font-medium text-neutral-900">
            <FileText size={14} className="shrink-0 text-neutral-400" />
            {record.displayTitle ?? HI_TYPES[record.hiType] ?? record.hiType}
          </p>
          <p className="mt-0.5 flex items-center gap-1.5 text-xs text-neutral-500">
            <Building2 size={12} />
            {record.sourceHipName ?? 'Unnamed provider'}
            {record.recordDate && (
              <> · {new Date(record.recordDate).toLocaleDateString('en-IN')}</>
            )}
          </p>
        </button>

        {record.imported ? (
          <span className="shrink-0 text-xs font-medium text-emerald-700">In case sheet</span>
        ) : (
          <button
            type="button"
            onClick={onImport}
            disabled={!canImport || importing}
            title={canImport ? undefined : 'Open a case sheet to import into'}
            className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-neutral-300 px-2.5 py-1 text-xs font-medium hover:bg-neutral-50 disabled:opacity-40"
          >
            <Download size={12} />
            Import
          </button>
        )}
      </div>

      {expanded && (
        <div className="border-t border-neutral-100 p-3">
          {loading ? (
            <p className="flex items-center gap-2 text-sm text-neutral-500">
              <Loader2 size={14} className="animate-spin" />
              Opening…
            </p>
          ) : (
            <pre
              className={cn(
                'max-h-72 overflow-auto rounded-lg bg-neutral-50 p-3 text-xs',
                'whitespace-pre-wrap break-words text-neutral-700',
              )}
            >
              {payload ?? 'This record could not be opened.'}
            </pre>
          )}
        </div>
      )}
    </li>
  )
}
