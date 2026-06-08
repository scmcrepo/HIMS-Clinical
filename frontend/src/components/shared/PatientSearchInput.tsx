import { useState, useRef, useEffect } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { usePatientSearch } from '../../hooks/patient/usePatient'
import { encounterApi } from '../../services/encounter/encounterApi'
import type { Patient } from '../../types/patient'
import type { EncounterType, EncounterSummary } from '../../types/encounter'
import { cn } from '../../lib/utils'

interface Props {
  onSelect: (patient: Patient | null, encounterId?: string) => void
  selectedPatient?: Patient | null
  placeholder?: string
  className?: string
  /** When set, searches active encounters of this type instead of the full patient list */
  encounterFilter?: EncounterType
}

export function PatientSearchInput({ onSelect, placeholder = 'Search patient by name or phone…', className, encounterFilter, selectedPatient }: Props) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!selectedPatient) {
      setQuery('')
    } else {
      setQuery(selectedPatient.fullName)
    }
  }, [selectedPatient])

  // Debounce the query
  const [debouncedQuery, setDebouncedQuery] = useState(query)
  useEffect(() => {
    const handler = setTimeout(() => setDebouncedQuery(query), 400)
    return () => clearTimeout(handler)
  }, [query])

  // Standard patient search (used when no encounter filter)
  const patientSearch = usePatientSearch(encounterFilter ? '' : query)

  // Encounter-based search moved to grouped logic below

  useEffect(() => {
    const handler = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  // Map encounter results to patient-like objects for display
  const handleEncounterSelect = (enc: EncounterSummary) => {
    const mappedPatient: Patient = {
      id: enc.patientId,
      patientNumber: enc.patientNumber ?? '',
      salutation: null,
      firstName: enc.patientName?.split(' ')[0] ?? '',
      lastName: enc.patientName?.split(' ').slice(1).join(' ') ?? '',
      fullName: enc.patientName ?? '',
      gender: 'OTHER',
      dateOfBirth: null,
      estimatedDateOfBirth: '',
      age: '',
      contactNumber: enc.patientMobileNumber ?? null,
      email: null,
      bloodGroup: null,
      address: null,
      primaryProviderId: enc.primaryProviderId,
      areaId: null,
      categoryId: null,
      isClinicalTrial: false,
      status: 'ACTIVE',
      activeEncounterId: enc.id,
    }
    onSelect(mappedPatient, enc.id)
    setQuery(enc.patientName ?? '')
    setOpen(false)
  }

  // Group encounters by patientId to avoid duplicates in the search list
  const encounterResults = useQuery({
    queryKey: ['encounter-patient-search', encounterFilter, debouncedQuery],
    queryFn: async () => {
      let results: { content: EncounterSummary[] }
      if (encounterFilter === 'INPATIENT') {
        results = await encounterApi.getActiveInpatients(debouncedQuery || undefined, 0, 50)
      } else {
        results = await encounterApi.getTodayOutpatients(debouncedQuery || undefined, undefined, 0, 50)
      }
      
      // Filter for unique patients, keeping the latest encounter for each
      const uniquePatients: EncounterSummary[] = []
      const seenPatients = new Set<string>()
      
      // Results are usually sorted by date descending from backend
      results.content.forEach(enc => {
        if (!seenPatients.has(enc.patientId)) {
          uniquePatients.push(enc)
          seenPatients.add(enc.patientId)
        }
      })
      
      return uniquePatients.slice(0, 20)
    },
    enabled: !!encounterFilter && debouncedQuery.length >= 2,
    placeholderData: keepPreviousData,
  }).data ?? []
  const useEncounterMode = !!encounterFilter

  return (
    <div ref={ref} className={cn('relative', className)}>
      <div className="relative group">
        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 group-focus-within:text-neutral-500 transition-colors">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </span>
        <input
          type="text" value={query} placeholder={placeholder}
          className="w-full pl-9 pr-4 py-1.5 border border-gray-300 rounded text-sm focus:outline-none focus:ring-1 focus:ring-neutral-500 transition-all"
          onChange={e => {
            const val = e.target.value
            setQuery(val)
            setOpen(true)
            if (val === '') {
              onSelect(null)
            }
          }}
          onFocus={() => query.length >= 2 && setOpen(true)}
          aria-label="Search patients" aria-autocomplete="list" role="combobox" aria-expanded={open}
        />
      </div>

      {/* Encounter-based results */}
      {useEncounterMode && open && encounterResults.length > 0 && (
        <ul role="listbox" className="absolute z-50 w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-60 overflow-auto">
          {encounterResults.map(enc => (
            <li key={enc.id} role="option" aria-selected={false}
              className="px-4 py-2.5 hover:bg-neutral-50 cursor-pointer flex flex-col"
              onMouseDown={() => handleEncounterSelect(enc)}>
              <span className="font-medium text-sm text-gray-900">
                {enc.patientName} <span className="text-neutral-600 ml-1 text-[10px]">{enc.patientNumber}</span>
              </span>
              <span className="text-xs text-gray-500">
                {enc.status} · {enc.providerName ?? 'No provider'}
                {enc.encounterType === 'INPATIENT' && enc.hasBed && ' · Has Bed'}
              </span>
            </li>
          ))}
        </ul>
      )}

      {/* Standard patient results */}
      {!useEncounterMode && open && patientSearch.data?.content && patientSearch.data.content.length > 0 && (
        <ul role="listbox" className="absolute z-50 w-full mt-1 bg-white border border-gray-200 rounded-lg shadow-lg max-h-60 overflow-auto">
          {patientSearch.data.content.map(p => (
            <li key={p.id} role="option" aria-selected={false}
              className="px-4 py-2.5 hover:bg-neutral-50 cursor-pointer flex flex-col"
              onMouseDown={() => { onSelect(p); setQuery(p.fullName); setOpen(false) }}>
              <span className="font-medium text-sm text-gray-900">{p.fullName} <span className="text-neutral-600 ml-1 text-[10px]">{p.patientNumber}</span></span>
              <span className="text-xs text-gray-500">{p.contactNumber ?? 'No contact'} · {p.age}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
