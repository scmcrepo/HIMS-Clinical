/**
 * Mapping the doctor's real slots onto the words people type.
 *
 * A slot in this system is a window with a capacity — 09:00-13:00, twelve patients — not a
 * point in time. So "morning" does not pick a moment, it picks whichever window starts
 * inside the morning bracket, and the confirmation talks about token numbers rather than
 * inventing an appointment time the clinic does not actually keep.
 */

import { SESSION_WORDS } from './vocab'
import type { SessionRef } from './parse'
import type { AppointmentSlot } from '../../../types/appointment'
import type { SessionOption } from './suggest'

export interface SlotOption extends SessionOption {
  slotId: string
  fromTime: string
  toTime: string
  availableCount: number
  maxPatients: number
}

const hourOf = (time: string): number => Number(time.split(':')[0] ?? '0')

export function formatTime(time: string | null | undefined): string {
  if (!time) return '—'
  const [rawHour, rawMinute] = time.split(':')
  const hour = Number(rawHour)
  const minute = Number(rawMinute ?? '0')
  const suffix = hour < 12 ? 'am' : 'pm'
  const display = ((hour + 11) % 12) + 1
  return minute ? `${display}:${String(minute).padStart(2, '0')}${suffix}` : `${display}${suffix}`
}

const bucketFor = (time: string) => {
  const hour = hourOf(time)
  return SESSION_WORDS.find(w => hour >= w.fromHour && hour < w.toHour) ?? null
}

/**
 * Builds the pickable sessions for a day.
 *
 * Where a bracket holds a single window the typed word is enough — "morning" is
 * unambiguous. Where a doctor sits twice in one bracket the option offers its start time
 * instead, which the parser reads back as a time. Every option therefore round-trips: the
 * box never suggests text it cannot then understand.
 */
export function slotOptions(slots: AppointmentSlot[], isToday: boolean, now: Date): SlotOption[] {
  const nowTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:00`
  const usable = slots.filter(slot => !isToday || slot.toTime > nowTime)

  const perBucket = new Map<string, number>()
  usable.forEach(slot => {
    const bucket = bucketFor(slot.fromTime)?.canonical ?? 'other'
    perBucket.set(bucket, (perBucket.get(bucket) ?? 0) + 1)
  })

  return usable.map(slot => {
    const bucket = bucketFor(slot.fromTime)
    const alone = bucket !== null && perBucket.get(bucket.canonical) === 1
    const window = `${formatTime(slot.fromTime)}–${formatTime(slot.toTime)}`
    const full = slot.availableCount <= 0 || !slot.isAvailable

    return {
      slotId: slot.slotId,
      insert: alone && bucket ? bucket.canonical : formatTime(slot.fromTime).replace(/[:\s]/g, ''),
      label: bucket ? `${cap(bucket.canonical)} · ${window}` : window,
      hint: full ? 'full' : `${slot.availableCount} of ${slot.maxPatients} open`,
      full,
      fromTime: slot.fromTime,
      toTime: slot.toTime,
      availableCount: slot.availableCount,
      maxPatients: slot.maxPatients,
    }
  })
}

const cap = (value: string) => value.charAt(0).toUpperCase() + value.slice(1)

export interface SlotResolution {
  slot: SlotOption | null
  /** Several windows answer to what was typed, so the box has to ask. */
  ambiguous: boolean
}

/**
 * Picks the window a parsed session refers to.
 *
 * With nothing typed and only one window open, that window is the answer — which is what
 * makes a bare phone number a complete booking on most days. Two windows and no word
 * typed is the one case the box refuses to guess: choosing morning when someone meant
 * evening is worse than asking.
 */
export function resolveSlot(options: SlotOption[], session: SessionRef | null): SlotResolution {
  const open = options.filter(option => !option.full)

  if (!session) {
    if (open.length === 1) return { slot: open[0], ambiguous: false }
    return { slot: null, ambiguous: open.length > 1 }
  }

  const matches = session.kind === 'named'
    ? options.filter(option => {
        const hour = hourOf(option.fromTime)
        return hour >= session.fromHour && hour < session.toHour
      })
    : options.filter(option => {
        const from = hourOf(option.fromTime)
        const to = hourOf(option.toTime)
        return session.hour >= from && session.hour < Math.max(to, from + 1)
      })

  const openMatches = matches.filter(option => !option.full)
  if (openMatches.length === 1) return { slot: openMatches[0], ambiguous: false }
  if (openMatches.length > 1) return { slot: null, ambiguous: true }
  // Every window in the bracket is full: surface it rather than silently sliding
  // the patient into a different part of the day.
  if (matches.length > 0) return { slot: matches[0], ambiguous: false }
  return { slot: null, ambiguous: false }
}
