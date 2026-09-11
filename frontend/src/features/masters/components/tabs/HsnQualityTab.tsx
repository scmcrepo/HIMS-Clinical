import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../../hooks/useToast'
import { Section, Table, LoadingSection } from '../MasterSharedUI'
import { hsnQualityApi, type HsnCodeRow } from '../../../../services/masters/masterApi'

/**
 * HsnQualityTab — find and correct malformed HSN codes in the item master.
 *
 * GST groups outward supplies by HSN at 4, 6 or 8 digits, so a code of any other
 * shape cannot be reported and lands in the GST report's "Unclassified" bucket.
 *
 * The screen leads with the distinct-code count rather than the affected-item count,
 * because that is the number that says how much work this actually is: codes repeat
 * heavily across an item master, so a few dozen decisions usually clear thousands of
 * items. Suggestions are offered where stripping leading zeros lands on a valid
 * length, and never applied on their own — deciding a product's real HSN is a call
 * with tax consequences, so it stays with the pharmacy team.
 */
export default function HsnQualityTab() {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<string | null>(null)
  const [draft, setDraft] = useState('')

  const { data: profile, isLoading } = useQuery({
    queryKey: ['hsnQuality'],
    queryFn: hsnQualityApi.profile,
  })

  const { data: affectedItems = [] } = useQuery({
    queryKey: ['hsnQualityItems', editing],
    queryFn: () => hsnQualityApi.itemsForCode(editing!),
    enabled: !!editing,
  })

  const remap = useMutation({
    mutationFn: ({ from, to }: { from: string; to: string }) => hsnQualityApi.remap(from, to),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['hsnQuality'] })
      qc.invalidateQueries({ queryKey: ['items'] })
      setEditing(null)
      setDraft('')
      toast({
        title: `${res.itemsUpdated} ${res.itemsUpdated === 1 ? 'item' : 'items'} moved to HSN ${res.to}`,
        variant: 'success',
      })
    },
    onError: (e: any) =>
      toast({
        title: 'Could not update',
        description: e?.response?.data?.message || e.message,
        variant: 'destructive',
      }),
  })

  if (isLoading) return <LoadingSection />

  const p = profile
  const clean = !p || p.itemsInvalid === 0

  const startEdit = (row: HsnCodeRow) => {
    setEditing(row.hsnCode)
    setDraft(row.suggestion ?? '')
  }

  const Stat = ({ label, value, tone }: { label: string; value: string | number; tone?: 'bad' | 'good' }) => (
    <div>
      <div className="text-xs font-semibold text-gray-500 mb-1">{label}</div>
      <div
        className={
          'text-2xl font-bold tabular-nums ' +
          (tone === 'bad' ? 'text-amber-700' : tone === 'good' ? 'text-emerald-700' : 'text-gray-800')
        }
      >
        {value}
      </div>
    </div>
  )

  return (
    <Section
      title="HSN Data Quality"
      description="Codes must be 4, 6 or 8 digits to appear in the GST HSN summary"
    >
      {p && (
        <div className="flex flex-wrap gap-x-12 gap-y-4 bg-gray-50 border border-gray-200 p-4 rounded-lg mb-5">
          <Stat label="Codes to fix" value={p.distinctInvalidCodes} tone={p.distinctInvalidCodes ? 'bad' : 'good'} />
          <Stat label="Items affected" value={p.itemsInvalid} />
          <Stat label="Suggestions ready" value={p.suggestionsAvailable} />
          <Stat label="Items already valid" value={p.itemsValid} tone="good" />
          <Stat label="No code recorded" value={p.itemsBlank} />
        </div>
      )}

      {clean ? (
        <div className="flex items-center bg-emerald-50 border border-emerald-200 text-emerald-800 px-4 py-3 rounded-lg text-sm">
          <svg className="w-4 h-4 mr-2 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7" />
          </svg>
          Every HSN code in the item master is reportable. Nothing will land in the Unclassified bucket.
        </div>
      ) : (
        <>
          <p className="text-sm text-gray-600 mb-4 max-w-2xl">
            Fixing the codes at the top clears the most items. A suggestion appears only where
            removing leading zeros lands on a valid length — everything else needs the product
            looked up, because a wrong HSN misstates tax on every future sale.
          </p>

          <Table headers={['HSN Code', 'Items', 'Why it fails', 'Correct to', '']}>
            {p!.invalidCodes.map(row => {
                const isEditing = editing === row.hsnCode
                const digits = /^\d+$/.test(row.hsnCode)
                const reason = !digits
                  ? 'Contains non-digits'
                  : `${row.hsnCode.length} digits — needs 4, 6 or 8`

                return (
                  <tr key={row.hsnCode} className={isEditing ? 'bg-neutral-50' : ''}>
                    <td className="px-4 py-3 font-mono text-sm text-gray-800">{row.hsnCode}</td>
                    <td className="px-4 py-3 text-sm text-gray-700 tabular-nums">{row.itemCount}</td>
                    <td className="px-4 py-3 text-sm text-gray-500">{reason}</td>
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <div className="flex items-center gap-2">
                          <input
                            autoFocus
                            value={draft}
                            onChange={e => setDraft(e.target.value.replace(/\D/g, ''))}
                            placeholder="4, 6 or 8 digits"
                            className="w-40 px-3 py-1.5 border border-gray-300 rounded-lg text-sm font-mono focus:outline-none focus:ring-2 focus:ring-neutral-500"
                          />
                          {row.suggestion && draft !== row.suggestion && (
                            <button
                              type="button"
                              onClick={() => setDraft(row.suggestion!)}
                              className="text-xs text-neutral-600 underline hover:text-neutral-800"
                            >
                              use {row.suggestion}
                            </button>
                          )}
                        </div>
                      ) : row.suggestion ? (
                        <span className="text-sm text-gray-500">
                          suggested <span className="font-mono text-gray-800">{row.suggestion}</span>
                        </span>
                      ) : (
                        <span className="text-sm text-gray-400">needs lookup</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap">
                      {isEditing ? (
                        <div className="flex items-center gap-2 justify-end">
                          <button
                            type="button"
                            onClick={() => { setEditing(null); setDraft('') }}
                            className="px-3 py-1.5 text-xs font-semibold text-gray-600 hover:text-gray-800"
                          >
                            Cancel
                          </button>
                          <button
                            type="button"
                            disabled={!/^(\d{4}|\d{6}|\d{8})$/.test(draft) || remap.isPending}
                            onClick={() => remap.mutate({ from: row.hsnCode, to: draft })}
                            className="px-3 py-1.5 text-xs font-semibold text-white bg-neutral-600 rounded-lg hover:bg-neutral-700 disabled:opacity-40 disabled:cursor-not-allowed"
                          >
                            {remap.isPending ? 'Applying…' : `Apply to ${row.itemCount}`}
                          </button>
                        </div>
                      ) : (
                        <button
                          type="button"
                          onClick={() => startEdit(row)}
                          className="px-3 py-1.5 text-xs font-semibold text-neutral-700 border border-gray-300 rounded-lg hover:bg-gray-50"
                        >
                          Fix
                        </button>
                      )}
                    </td>
                  </tr>
                )
            })}
          </Table>

          {editing && affectedItems.length > 0 && (
            <div className="mt-4 border border-gray-200 rounded-lg overflow-hidden">
              <div className="px-4 py-2.5 bg-gray-50 border-b border-gray-200 text-xs font-semibold text-gray-600">
                Items currently on {editing} — check these belong together before applying
              </div>
              <ul className="max-h-56 overflow-y-auto divide-y divide-gray-100">
                {affectedItems.map(item => (
                  <li key={item.id} className="px-4 py-2 text-sm text-gray-700 flex justify-between gap-4">
                    <span className="truncate">{item.name}</span>
                    <span className="text-gray-400 tabular-nums shrink-0">{item.taxRate ?? 0}%</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </Section>
  )
}
