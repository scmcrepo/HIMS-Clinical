/**
 * Matching typed fragments against the doctor list, and the small amount of memory that
 * makes the box get better with use.
 *
 * There is no model here and no server round trip. The consultant list is already in the
 * browser (the appointment screens load it for their own dropdowns), it is a few dozen
 * rows, and matching a token against it exhaustively costs nothing. That is the whole
 * reason a deterministic approach works for this problem: the input is not free English,
 * it is a token that has to be one of about fifty known things.
 */

import type { Consultant } from '../../../services/consultant/consultantApi'
import { editDistance, phoneticKey } from './vocab'

export type MatchKind = 'alias' | 'prefix' | 'substring' | 'phonetic' | 'fuzzy'

export interface ConsultantMatch {
  consultant: Consultant
  kind: MatchKind
  score: number
}

/**
 * One searchable spelling of a doctor, and how much a hit on it is worth.
 *
 * The weights encode which way people actually type. A surname beats a given name; the
 * full name beats either, so "priya nair" resolves as one doctor instead of splitting into
 * a patient called Priya and a Dr. Nair; a speciality is the weakest, because matching
 * "cardio" is a useful shortcut but a poor reason to override a name.
 */
export interface IndexKey {
  value: string
  folded: string
  weight: number
}

export interface ConsultantEntry {
  consultant: Consultant
  keys: IndexKey[]
}

const isInactive = (c: Consultant) =>
  (c.status as unknown) === 'INACTIVE' || (c.status as unknown) === 'DELETED' || (c.status as unknown) === 0

const KEY_WEIGHT = { fullName: 320, lastName: 300, firstName: 260, speciality: 200 }

export function buildConsultantIndex(consultants: Consultant[]): ConsultantEntry[] {
  return consultants.filter(c => !isInactive(c)).map(c => {
    const speciality = c.specialisation || c.qualification || ''
    const raw: { value: string; weight: number }[] = [
      { value: `${c.firstName} ${c.lastName}`, weight: KEY_WEIGHT.fullName },
      { value: c.lastName, weight: KEY_WEIGHT.lastName },
      { value: c.firstName, weight: KEY_WEIGHT.firstName },
      ...speciality.split(/[\s,/&-]+/).filter(Boolean)
        .map(word => ({ value: word, weight: KEY_WEIGHT.speciality })),
    ]

    const keys: IndexKey[] = raw
      .filter(k => Boolean(k.value))
      .map(k => ({ value: k.value.toLowerCase(), folded: phoneticKey(k.value), weight: k.weight }))

    return { consultant: c, keys }
  })
}

/**
 * Scores every doctor against a fragment, best first.
 *
 * Prefix beats everything below it because that is what typing feels like: "sha" must
 * offer Sharma before Shanmugam, and both before someone whose speciality merely contains
 * the letters. Phonetic and fuzzy sit at the bottom as a safety net, never as a reason to
 * outrank something the user literally typed the start of.
 */
export function matchConsultants(
  fragment: string,
  index: ConsultantEntry[],
  aliases: Record<string, string> = {},
): ConsultantMatch[] {
  const q = fragment.trim().toLowerCase()
  if (!q) return []

  const aliasTarget = aliases[q]
  const folded = phoneticKey(q)
  const out: ConsultantMatch[] = []

  for (const entry of index) {
    if (aliasTarget && entry.consultant.id === aliasTarget) {
      out.push({ consultant: entry.consultant, kind: 'alias', score: 1000 })
      continue
    }

    let best = 0
    let kind: MatchKind = 'fuzzy'

    for (const key of entry.keys) {
      let score = 0
      let how: MatchKind = 'fuzzy'

      if (key.value.startsWith(q)) {
        // A shorter key means the fragment covers more of it: "sharma" for "sharma"
        // should beat "sharma" for "sharmaanand".
        score = key.weight + Math.max(0, 40 - (key.value.length - q.length))
        how = 'prefix'
      } else if (q.length >= 3 && key.value.includes(q)) {
        score = 120
        how = 'substring'
      } else if (q.length >= 3 && folded && key.folded === folded) {
        score = 90
        how = 'phonetic'
      } else if (q.length >= 4) {
        const distance = editDistance(q, key.value, 2)
        if (distance <= 2) {
          score = distance === 1 ? 70 : 50
          how = 'fuzzy'
        }
      }

      if (score > best) { best = score; kind = how }
    }

    if (best > 0) out.push({ consultant: entry.consultant, kind, score: best })
  }

  return out.sort((a, b) => b.score - a.score)
}

// ── remembered corrections ──────────────────────────────────────────────────

/**
 * Two small tables in localStorage, and the only thing resembling learning in here.
 *
 * When someone types "rk", gets the wrong doctor, and fixes the chip, the alias is kept.
 * Next time "rk" resolves first try. A frequency counter does the rest of the work: after
 * a fortnight the three doctors a given receptionist actually books rank above the other
 * forty-seven. Both are plain lookup tables — nothing is trained, and nothing leaves the
 * machine.
 *
 * Per user and per device on purpose. Reception desks and consultant desks book very
 * different people, and one shared table would average them into uselessness.
 */
const ALIAS_KEY = 'hms.quickbook.aliases'
const FREQUENCY_KEY = 'hms.quickbook.frequency'

function readMap<T>(key: string): Record<string, T> {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return {}
    const parsed: unknown = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, T>) : {}
  } catch {
    return {}
  }
}

function writeMap(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    /* private mode or a full quota — the box works, it just stops remembering */
  }
}

export const readAliases = (): Record<string, string> => readMap<string>(ALIAS_KEY)

/** Remembers that `fragment` meant this doctor. Fragments of one character are ignored. */
export function rememberAlias(fragment: string, consultantId: string): void {
  const key = fragment.trim().toLowerCase()
  if (key.length < 2 || /^\d+$/.test(key)) return
  writeMap(ALIAS_KEY, { ...readAliases(), [key]: consultantId })
}

export const readFrequencies = (): Record<string, number> => readMap<number>(FREQUENCY_KEY)

export function recordBooking(consultantId: string): void {
  const frequencies = readFrequencies()
  writeMap(FREQUENCY_KEY, { ...frequencies, [consultantId]: (frequencies[consultantId] ?? 0) + 1 })
}
