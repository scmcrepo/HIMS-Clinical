import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, X } from 'lucide-react'

import api from '../../../lib/axios'
import { cn } from '../../../lib/utils'
import type { ApiResponse } from '../../../types/api'

export interface Icd10Result {
  code: string
  title: string
  chapter: string | null
}

interface Props {
  value: { code: string; title: string } | null
  onChange: (selection: { code: string; title: string } | null) => void
}

/**
 * ICD-10 diagnosis search — Screen 4.1.
 *
 * <p>Search only; no free-text entry. A pre-auth carrying an invented or
 * mistyped code is rejected by the payer days later, with the patient already
 * admitted, so the field will not accept anything the catalogue does not
 * contain.
 *
 * <p>If the catalogue has not been loaded the field says so explicitly rather
 * than showing an empty dropdown. "No matches" and "no data loaded" send a
 * clinician to very different places.
 */
export default function Icd10SearchField({ value, onChange }: Props) {
  const [term, setTerm] = useState('')
  const [debounced, setDebounced] = useState('')
  const [open, setOpen] = useState(false)

  // Debounced: the catalogue is large and a keystroke-per-query search is both
  // slow for the clinician and needless load on the database.
  useEffect(() => {
    const t = setTimeout(() => setDebounced(term.trim()), 250)
    return () => clearTimeout(t)
  }, [term])

  const enabled = debounced.length >= 2

  const { data: results = [], isFetching } = useQuery({
    queryKey: ['icd10', debounced],
    queryFn: () =>
      api
        .get<ApiResponse<Icd10Result[]>>('/catalog/icd10/search', { params: { q: debounced } })
        .then(r => r.data.data ?? []),
    enabled,
  })

  const showEmpty = useMemo(
    () => enabled && !isFetching && results.length === 0,
    [enabled, isFetching, results.length],
  )

  if (value) {
    return (
      <div className="flex items-start justify-between gap-3 rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2">
        <div className="min-w-0 text-sm">
          <p className="font-medium text-neutral-900">{value.code}</p>
          <p className="mt-0.5 text-neutral-600">{value.title}</p>
        </div>
        <button
          type="button"
          aria-label="Clear diagnosis"
          onClick={() => {
            onChange(null)
            setTerm('')
          }}
          className="shrink-0 text-neutral-400 hover:text-neutral-700"
        >
          <X size={15} />
        </button>
      </div>
    )
  }

  return (
    <div className="relative">
      <div className="relative">
        <Search
          size={15}
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400"
        />
        <input
          type="text"
          value={term}
          onChange={(e) => {
            setTerm(e.target.value)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          placeholder="Search by code or diagnosis, e.g. I21 or myocardial"
          aria-label="ICD-10 diagnosis"
          className="w-full rounded-lg border border-neutral-200 py-2 pl-9 pr-3 text-sm focus:border-neutral-400 focus:outline-none focus:ring-2 focus:ring-neutral-500"
        />
      </div>

      {open && enabled && (
        <div className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-lg border border-neutral-200 bg-white shadow-lg">
          {isFetching && <p className="px-3 py-2 text-sm text-neutral-500">Searching…</p>}

          {showEmpty && (
            <p className="px-3 py-3 text-sm text-neutral-500">
              No matching diagnosis. If no search returns results, the ICD-10 catalogue may not
              be loaded yet — ask an administrator to import the official release.
            </p>
          )}

          {results.map((r) => (
            <button
              key={r.code}
              type="button"
              onClick={() => {
                onChange({ code: r.code, title: r.title })
                setOpen(false)
              }}
              className={cn(
                'block w-full px-3 py-2 text-left text-sm hover:bg-neutral-50',
                'border-b border-neutral-100 last:border-b-0',
              )}
            >
              <span className="font-medium text-neutral-900">{r.code}</span>
              <span className="ml-2 text-neutral-600">{r.title}</span>
            </button>
          ))}
        </div>
      )}

      {term.trim().length === 1 && (
        <p className="mt-1 text-xs text-neutral-500">Type at least two characters.</p>
      )}
    </div>
  )
}
