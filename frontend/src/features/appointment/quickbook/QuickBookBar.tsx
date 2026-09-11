import { useEffect, useMemo, useRef, useState } from 'react'
import type { KeyboardEvent as ReactKeyboardEvent } from 'react'
import { format, isSameDay } from 'date-fns'
import { useQuery } from '@tanstack/react-query'
import { CornerDownLeft, Loader2, UserPlus } from 'lucide-react'

import type { Consultant } from '../../../services/consultant/consultantApi'
import type { Patient } from '../../../types/patient'
import { patientApi } from '../../../services/patient/patientApi'
import { useAvailabilityCheck, useAppointmentMutations } from '../../../hooks/appointment/useAppointment'
import { cn } from '../../../lib/utils'

import { buildConsultantIndex, readAliases, readFrequencies, recordBooking, rememberAlias } from './match'
import { parseBooking } from './parse'
import { applySuggestion, currentToken, ghostFor, suggest, type Suggestion } from './suggest'
import { resolveSlot, slotOptions, type SlotOption } from './sessions'

interface Props {
  consultants: Consultant[]
  /** Doctors with appointments on the day in view. Free evidence of who is sitting. */
  activeConsultantIds: Set<string>
  /** The doctor the page is already filtered to, used when none is typed. */
  defaultConsultantId?: string
  /**
   * A line handed in from elsewhere — the day board, today. The nonce is what makes
   * picking the same session twice register the second time.
   */
  prefill?: { text: string; nonce: number } | null
  onBooked?: () => void
}

/** Debounces a value. Name lookups decrypt server-side, so they must not fire per keystroke. */
function useDebounced<T>(value: T, delay: number): T {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return settled
}

/**
 * One line of text instead of an eight-field form.
 *
 * The box parses as you type and never books from the text directly: it fills a draft,
 * shown as four chips, and a human presses Enter. That ordering is the whole safety
 * argument — a misparse costs one keystroke to fix rather than producing a wrong
 * appointment, ambiguity becomes a question instead of a guess, and the booking rules the
 * backend already enforces get to render as advice *before* anyone commits rather than as
 * a validation error afterwards.
 */
export function QuickBookBar({ consultants, activeConsultantIds, defaultConsultantId, prefill, onBooked }: Props) {
  const [text, setText] = useState('')
  const [caret, setCaret] = useState(0)
  const [open, setOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)
  // Enter means "book" unless the user has actually walked into the list with the arrow
  // keys. Without this, a visible dropdown quietly steals every Enter and the fastest path
  // through the box — type, press Enter — stops working.
  const [navigated, setNavigated] = useState(false)
  const [slotOverride, setSlotOverride] = useState<string | null>(null)
  const [patientOverride, setPatientOverride] = useState<Patient | null>(null)
  const [memoryVersion, setMemoryVersion] = useState(0)

  const inputRef = useRef<HTMLInputElement>(null)
  const mirrorRef = useRef<HTMLDivElement>(null)

  const today = useMemo(() => new Date(), [])
  const index = useMemo(() => buildConsultantIndex(consultants), [consultants])
  const aliases = useMemo(() => readAliases(), [memoryVersion])
  const frequencies = useMemo(() => readFrequencies(), [memoryVersion])

  const parsed = useMemo(
    () => parseBooking(text, index, aliases, today),
    [text, index, aliases, today])

  // ── resolution ────────────────────────────────────────────────────────────

  const patientKey = parsed.phone ?? parsed.patientNumber ?? (parsed.name && parsed.name.length >= 3 ? parsed.name : null)
  const debouncedKey = useDebounced(patientKey ?? '', parsed.phone ? 150 : 400)

  const { data: patientPage, isFetching: findingPatient } = useQuery({
    queryKey: ['quickbook', 'patient', debouncedKey],
    queryFn: () => patientApi.search(debouncedKey, 0, 5),
    enabled: debouncedKey.length >= 3,
    staleTime: 30_000,
  })

  const patientMatches = debouncedKey === (patientKey ?? '') ? (patientPage?.content ?? []) : []
  const patient = patientOverride ?? (patientMatches.length === 1 ? patientMatches[0] : null)

  // The patient's usual doctor is a better default than nothing, and it is the difference
  // between typing a phone number and typing a phone number plus a name.
  const consultantId = parsed.consultant?.id ?? defaultConsultantId ?? patient?.primaryProviderId ?? undefined
  const consultant = consultants.find(c => c.id === consultantId) ?? null

  const date = parsed.date ?? today
  const dateStr = format(date, 'yyyy-MM-dd')

  const { data: availability, isFetching: checkingAvailability } =
    useAvailabilityCheck(consultantId, dateStr)

  const options = useMemo(
    () => slotOptions(availability?.slots ?? [], isSameDay(date, today), new Date()),
    [availability, date, today])

  const resolution = useMemo(() => resolveSlot(options, parsed.session), [options, parsed.session])
  const slot: SlotOption | null =
    (slotOverride ? options.find(o => o.slotId === slotOverride) ?? null : null) ?? resolution.slot

  // ── suggestions ───────────────────────────────────────────────────────────

  const suggestions = useMemo(
    () => suggest(text, caret, {
      index, aliases, frequencies, parsed, activeConsultantIds,
      sessions: options,
    }),
    [text, caret, index, aliases, frequencies, parsed, activeConsultantIds, options])

  const ghost = ghostFor(text, caret, suggestions[0])
  const showList = open && suggestions.length > 0

  useEffect(() => { setHighlight(0); setNavigated(false) }, [text])

  // A prefill lands as ordinary text and is parsed like anything typed, so the board
  // cannot select something the chips would then describe differently.
  useEffect(() => {
    if (!prefill) return
    const line = `${prefill.text} `
    setText(line)
    setCaret(line.length)
    setOpen(false)
    requestAnimationFrame(() => {
      inputRef.current?.focus()
      inputRef.current?.setSelectionRange(line.length, line.length)
    })
    // Keyed on the nonce alone: re-running whenever the text happens to match would
    // fight the user as they edit what the board handed them.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [prefill?.nonce])

  // Anything typed invalidates a hand-picked slot or patient — otherwise a correction to
  // the doctor silently keeps the previous doctor's slot.
  useEffect(() => { setSlotOverride(null); setPatientOverride(null) }, [text])

  // ── preflight ─────────────────────────────────────────────────────────────

  const walkIn = !patient && parsed.name !== null && parsed.phone !== null
  const blocker = useMemo(() => {
    if (!consultantId) return null
    if (availability?.reason === 'ON_LEAVE') {
      return `${label(consultant)} is on leave on ${format(date, 'EEE d MMM')}.`
    }
    if (availability?.reason === 'NO_SLOTS') {
      return `${label(consultant)} does not sit on ${format(date, 'EEEE')}s.`
    }
    if (patient?.isInpatient) {
      return `${patient.fullName} is currently admitted — discharge before booking an appointment.`
    }
    if (slot?.full) {
      const alternative = options.find(o => !o.full)
      return `That session is full (${slot.maxPatients}/${slot.maxPatients}).`
        + (alternative ? ` ${alternative.label} has ${alternative.availableCount} open.` : '')
    }
    return null
  }, [consultantId, consultant, availability, patient, slot, options, date])

  const missing = !consultantId ? 'doctor'
    : !slot ? 'session'
    : !patient && !walkIn ? 'patient'
    : null

  const ready = !blocker && missing === null && (patient !== null || walkIn)

  // ── actions ───────────────────────────────────────────────────────────────

  const mutations = useAppointmentMutations()

  const accept = (suggestion: Suggestion) => {
    const next = applySuggestion(text, caret, suggestion)
    setText(next.text)
    setCaret(next.caret)
    setOpen(false)
    // "sha" -> Dr. Sharma is worth keeping: next time it resolves without the dropdown.
    const typed = currentToken(text, caret).text
    if (suggestion.consultantId && typed) {
      rememberAlias(typed, suggestion.consultantId)
      setMemoryVersion(v => v + 1)
    }
    requestAnimationFrame(() => {
      inputRef.current?.focus()
      inputRef.current?.setSelectionRange(next.caret, next.caret)
    })
  }

  /**
   * Resolving an ambiguous doctor by remembering the correction.
   *
   * There is no separate "chosen doctor" state to set: storing the alias and bumping the
   * memory version reparses the same text, and this time the fragment resolves outright.
   * The correction and the selection are the same act, so the box cannot end up displaying
   * one doctor while holding another.
   */
  const chooseConsultant = (chosen: Consultant) => {
    if (parsed.consultantFragment) {
      rememberAlias(parsed.consultantFragment, chosen.id)
      setMemoryVersion(v => v + 1)
    }
    inputRef.current?.focus()
  }

  const book = () => {
    if (!ready || !slot || !consultantId) return
    mutations.book.mutate({
      patientId: patient?.id,
      providerId: consultantId,
      slotId: slot.slotId,
      appointmentDate: dateStr,
      // The walk-in name column accepts letters and spaces only, so "k.ravi" has to
      // arrive as "K Ravi" rather than as a 400 the receptionist cannot act on.
      tempPatientName: patient ? undefined : parsed.name?.replace(/[^a-zA-Z ]+/g, ' ').replace(/\s+/g, ' ').trim() || undefined,
      tempPatientSalutation: patient ? undefined : parsed.salutation ?? undefined,
      tempPatientGender: patient ? undefined : parsed.gender ?? undefined,
      tempPatientPhone: patient ? undefined : parsed.phone ?? undefined,
      tempPatientAge: patient ? undefined : parsed.age ?? undefined,
    }, {
      onSuccess: () => {
        recordBooking(consultantId)
        setMemoryVersion(v => v + 1)
        setText('')
        setCaret(0)
        setOpen(false)
        onBooked?.()
      },
    })
  }

  const onKeyDown = (event: ReactKeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setOpen(true)
      setNavigated(true)
      setHighlight(h => Math.min(h + 1, suggestions.length - 1))
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setNavigated(true)
      setHighlight(h => Math.max(h - 1, 0))
      return
    }
    if (event.key === 'Escape') {
      setOpen(false)
      return
    }
    if ((event.key === 'Tab' || event.key === 'ArrowRight') && ghost) {
      event.preventDefault()
      accept(suggestions[0])
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      if (showList && navigated && suggestions[highlight]) accept(suggestions[highlight])
      else book()
    }
  }

  const sync = (element: HTMLInputElement) => {
    setCaret(element.selectionStart ?? element.value.length)
    if (mirrorRef.current) mirrorRef.current.scrollLeft = element.scrollLeft
  }

  // ── render ────────────────────────────────────────────────────────────────

  return (
    <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4 space-y-3">
      <div className="relative">
        {/* A mirror behind the input paints the grey completion. The typed half is
            invisible but still occupies its exact width, so the ghost lands where the
            caret is without measuring anything. */}
        <div
          ref={mirrorRef}
          aria-hidden="true"
          className="absolute inset-0 px-4 py-3 text-sm whitespace-pre overflow-hidden pointer-events-none rounded-xl border border-transparent"
        >
          <span className="invisible">{text}</span>
          {ghost && <span className="text-gray-400">{ghost}</span>}
        </div>

        <input
          ref={inputRef}
          value={text}
          onChange={e => { setText(e.target.value); setOpen(true); sync(e.target) }}
          onKeyUp={e => sync(e.currentTarget)}
          onClick={e => sync(e.currentTarget)}
          onScroll={e => sync(e.currentTarget)}
          onFocus={() => setOpen(true)}
          onBlur={() => window.setTimeout(() => setOpen(false), 120)}
          onKeyDown={onKeyDown}
          placeholder="9876543210 sharma tomorrow morning"
          aria-label="Book an appointment by typing"
          autoComplete="off"
          spellCheck={false}
          className="relative w-full bg-transparent px-4 py-3 text-sm border border-gray-200 rounded-xl outline-none focus:ring-2 focus:ring-neutral-500 focus:border-neutral-500"
        />

        {/* Options sit directly inside the listbox: an intervening <li> would break the
            parent/child relationship a screen reader relies on to announce them. */}
        {showList && (
          <div
            role="listbox"
            aria-label="Suggestions"
            className="absolute z-20 mt-1 w-full max-w-lg bg-white border border-gray-200 rounded-xl shadow-lg overflow-hidden"
          >
            {suggestions.map((suggestion, i) => (
              <button
                key={`${suggestion.kind}:${suggestion.insert}`}
                type="button"
                role="option"
                aria-selected={i === highlight}
                onMouseDown={e => { e.preventDefault(); accept(suggestion) }}
                onMouseEnter={() => setHighlight(i)}
                className={cn(
                  'w-full flex items-baseline justify-between gap-4 px-4 py-2 text-left text-sm',
                  i === highlight ? 'bg-neutral-50' : 'bg-white',
                )}
              >
                <span className="font-medium text-gray-800">{suggestion.label}</span>
                {suggestion.hint && <span className="text-xs text-gray-500 shrink-0">{suggestion.hint}</span>}
              </button>
            ))}
          </div>
        )}
      </div>

      {text.trim().length > 0 && (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <Chip
              label="Patient"
              value={patient
                ? `${patient.fullName} · ${patient.patientNumber}`
                : walkIn
                  ? `${parsed.salutation ? parsed.salutation + ' ' : ''}${parsed.name} · new${parsed.age ? ` · ${parsed.age}` : ''}`
                  : null}
              pending={findingPatient}
              prompt={parsed.phone && !patient && !findingPatient ? 'not registered — add a name to register' : 'phone or name'}
              tone={patient?.isInpatient ? 'warn' : walkIn ? 'new' : 'set'}
            />
            <Chip
              label="Doctor"
              value={consultant ? label(consultant) : null}
              prompt="which doctor"
              tone="set"
            />
            <Chip
              label="Date"
              value={format(date, 'EEE d MMM')}
              prompt="when"
              tone={parsed.date ? 'set' : 'default'}
            />
            <Chip
              label="Session"
              value={slot ? `${slot.label} · ${slot.hint}` : null}
              pending={checkingAvailability}
              prompt={resolution.ambiguous ? 'pick one below' : 'which session'}
              tone={slot?.full ? 'warn' : 'set'}
            />
          </div>

          {parsed.consultantCandidates.length > 0 && (
            <PickerRow
              title="Which doctor?"
              items={parsed.consultantCandidates.map(c => ({ id: c.id, label: label(c) }))}
              onPick={id => {
                const chosen = parsed.consultantCandidates.find(c => c.id === id)
                if (chosen) chooseConsultant(chosen)
              }}
            />
          )}

          {patientMatches.length > 1 && !patientOverride && (
            <PickerRow
              title="Which patient?"
              items={patientMatches.map(p => ({
                id: p.id,
                label: `${p.fullName} · ${p.patientNumber}${p.contactNumber ? ` · ${p.contactNumber}` : ''}`,
              }))}
              onPick={id => setPatientOverride(patientMatches.find(p => p.id === id) ?? null)}
            />
          )}

          {options.length > 1 && (!slot || resolution.ambiguous) && (
            <PickerRow
              title="Which session?"
              items={options.map(option => ({
                id: option.slotId,
                label: `${option.label} · ${option.hint}`,
                disabled: option.full,
              }))}
              onPick={setSlotOverride}
            />
          )}

          {blocker && (
            <p className="text-xs font-semibold text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
              {blocker}
            </p>
          )}

          <div className="flex items-center justify-between gap-4 pt-1">
            <p className="text-xs text-gray-500">
              {ready
                ? 'Press Enter to book'
                : missing === 'patient' && parsed.phone
                  ? 'Add a name to register this number as a new patient'
                  : missing
                    ? `Still need: ${missing}`
                    : ''}
            </p>
            <button
              type="button"
              onClick={book}
              disabled={!ready || mutations.book.isPending}
              className="inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white bg-neutral-600 rounded-lg hover:bg-neutral-700 disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {mutations.book.isPending
                ? <Loader2 className="h-4 w-4 animate-spin" />
                : walkIn ? <UserPlus className="h-4 w-4" /> : <CornerDownLeft className="h-4 w-4" />}
              {walkIn ? 'Register & Book' : 'Book'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}

const label = (consultant: Consultant | null) =>
  consultant
    ? `${consultant.salutation || 'Dr.'} ${consultant.firstName} ${consultant.lastName}`.replace(/\s+/g, ' ').trim()
    : 'The doctor'

interface ChipProps {
  label: string
  value: string | null
  prompt: string
  pending?: boolean
  tone?: 'set' | 'warn' | 'new' | 'default'
}

function Chip({ label: name, value, prompt, pending, tone = 'default' }: ChipProps) {
  const filled = value !== null
  return (
    <span
      className={cn(
        'inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-xs',
        !filled && 'border-dashed border-gray-300 bg-gray-50 text-gray-400',
        filled && tone === 'warn' && 'border-amber-200 bg-amber-50 text-amber-800',
        filled && tone === 'new' && 'border-teal-200 bg-teal-50 text-teal-800',
        filled && (tone === 'set' || tone === 'default') && 'border-gray-200 bg-white text-gray-800',
      )}
    >
      <span className="font-semibold uppercase tracking-wide text-[10px] text-gray-400">{name}</span>
      {pending
        ? <Loader2 className="h-3 w-3 animate-spin text-gray-400" />
        : <span className="font-medium">{value ?? prompt}</span>}
    </span>
  )
}

interface PickerRowProps {
  title: string
  items: { id: string; label: string; disabled?: boolean }[]
  onPick: (id: string) => void
}

function PickerRow({ title, items, onPick }: PickerRowProps) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-xs font-semibold text-gray-500">{title}</span>
      {items.map(item => (
        <button
          key={item.id}
          type="button"
          disabled={item.disabled}
          onClick={() => onPick(item.id)}
          className="px-3 py-1.5 text-xs font-medium border border-gray-200 rounded-lg bg-white hover:border-neutral-400 hover:bg-neutral-50 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          {item.label}
        </button>
      ))}
    </div>
  )
}
