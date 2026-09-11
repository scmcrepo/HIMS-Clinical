/**
 * The closed vocabularies the quick-book box understands.
 *
 * Everything here is data, deliberately. The parser and the autocomplete both read these
 * tables and nothing else, so the box can never suggest a word the parser would then fail
 * to understand — the usual failure mode when a suggester and a parser are written twice.
 *
 * Adding a hospital's local shorthand, or a local-language synonym, is a row in a list
 * rather than a code change. None are seeded: a word that means "tomorrow" in one language
 * and "yesterday" in another is worse than no word at all, so the choice belongs to whoever
 * knows the clinic.
 */

import { addDays, startOfDay } from 'date-fns'

// ── dates ───────────────────────────────────────────────────────────────────

export interface DateWord {
  /** What the box rewrites the typed token to once accepted. */
  canonical: string
  /** Everything that resolves to it, canonical included. Lower case, no punctuation. */
  words: string[]
  resolve: (today: Date) => Date
}

/** Monday-indexed, matching JS getDay() after shifting Sunday to the end. */
const WEEKDAYS = [
  'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday',
] as const

/** Days until the next occurrence of `target` (0-6, Mon-based). */
function daysUntil(from: Date, target: number, strictlyAfter: boolean): number {
  const current = (from.getDay() + 6) % 7 // JS Sunday=0 -> Mon-based
  let delta = (target - current + 7) % 7
  if (delta === 0 && strictlyAfter) delta = 7
  return delta
}

const RELATIVE_DAYS: DateWord[] = [
  { canonical: 'today',     words: ['today', 'tdy', 'tod'],                     resolve: t => startOfDay(t) },
  { canonical: 'tomorrow',  words: ['tomorrow', 'tmrw', 'tmw', 'tom', '2mrw'],  resolve: t => addDays(startOfDay(t), 1) },
  { canonical: 'day after', words: ['day after', 'dayafter', 'dat', 'overmorrow'], resolve: t => addDays(startOfDay(t), 2) },
]

/**
 * `mon` is the next Monday on or after today; `next mon` is the next one strictly after.
 * The two differ only when today is itself a Monday, which is precisely when someone
 * bothers to type the word "next".
 */
const WEEKDAY_WORDS: DateWord[] = WEEKDAYS.flatMap((day, index) => {
  const short = day.slice(0, 3)
  return [
    {
      canonical: day,
      words: [day, short],
      resolve: (t: Date) => addDays(startOfDay(t), daysUntil(t, index, false)),
    },
    {
      canonical: `next ${day}`,
      words: [`next ${day}`, `next ${short}`, `nxt ${day}`, `nxt ${short}`],
      resolve: (t: Date) => addDays(startOfDay(t), daysUntil(t, index, true)),
    },
  ]
})

export const DATE_WORDS: DateWord[] = [...RELATIVE_DAYS, ...WEEKDAY_WORDS]

/** Longest phrase in the date table, so the tokenizer knows how wide to look. */
export const MAX_DATE_PHRASE_WORDS = DATE_WORDS.reduce(
  (max, w) => Math.max(max, ...w.words.map(s => s.split(' ').length)), 1)

/**
 * The shortest word for a date that this vocabulary can read back.
 *
 * Used when something outside the box — the day board — wants to prefill it. Emitting the
 * word rather than an id keeps a single source of truth: whatever lands in the box is
 * parsed the same way as anything typed, so a prefill cannot disagree with what the chips
 * then show. A date already past carries its year explicitly, because a bare "12/3" is
 * read forwards and would silently land in the wrong one.
 */
export function dateToken(target: Date, today: Date): string {
  const day = 24 * 60 * 60 * 1000
  const diff = Math.round((startOfDay(target).getTime() - startOfDay(today).getTime()) / day)
  if (diff === 0) return 'today'
  if (diff === 1) return 'tomorrow'
  if (diff === 2) return 'day after'
  const numeric = `${target.getDate()}/${target.getMonth() + 1}`
  return diff < 0 ? `${numeric}/${String(target.getFullYear()).slice(-2)}` : numeric
}

// ── sessions ────────────────────────────────────────────────────────────────

/**
 * A session word is a bracket of hours, not a time.
 *
 * Slots in this system are windows with a capacity — `09:00-13:00, max 12` — so "morning"
 * does not select a moment, it selects whichever of the doctor's windows starts inside the
 * bracket. That is also why the confirmation says "token 8 of 12" rather than inventing a
 * precise appointment time the clinic does not actually keep.
 */
export interface SessionWord {
  canonical: string
  words: string[]
  /** Half-open [fromHour, toHour) that a slot's start time must fall inside. */
  fromHour: number
  toHour: number
}

export const SESSION_WORDS: SessionWord[] = [
  { canonical: 'morning',   words: ['morning', 'mrng', 'morn', 'fn', 'forenoon', 'am'], fromHour: 0,  toHour: 12 },
  { canonical: 'afternoon', words: ['afternoon', 'aftrnoon', 'an', 'noon'],             fromHour: 12, toHour: 16 },
  { canonical: 'evening',   words: ['evening', 'evng', 'eve', 'pm'],                    fromHour: 16, toHour: 20 },
  { canonical: 'night',     words: ['night', 'nite'],                                   fromHour: 20, toHour: 24 },
]

// ── noise ───────────────────────────────────────────────────────────────────

/** Words carrying no information here. Consumed silently so they never look like a name. */
export const STOPWORDS = new Set([
  'book', 'booking', 'appointment', 'appt', 'apt', 'slot', 'for', 'with', 'at', 'on',
  'to', 'the', 'a', 'an', 'pls', 'please', 'and', 'give', 'need', 'want', 'see',
])

/** Honorifics stripped before a name or doctor match. */
export const DOCTOR_PREFIXES = new Set(['dr', 'dr.', 'doctor', 'drs'])
export const SALUTATIONS: Record<string, string> = {
  mr: 'Mr', mrs: 'Mrs', ms: 'Ms', miss: 'Ms', master: 'Master', baby: 'Baby',
}

// ── fuzzy keys ──────────────────────────────────────────────────────────────

/**
 * Folds a word to a spelling-insensitive key.
 *
 * Aimed at the way Indian names vary in transliteration rather than at typos in general:
 * Kumar/Kumaar, Shanmugam/Shanmugham, Vishwa/Vishva all have to land on one key. Plain
 * edit distance misses the aspirated-consonant cases entirely, because "gh" -> "g" is one
 * edit but so is every other single-letter change.
 *
 * The key is not meant to be pronounceable or reversible. It only has to be *stable*: the
 * query and the stored name must fold the same way.
 */
export function phoneticKey(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z]/g, '')
    .replace(/([bcdgkpstj])h/g, '$1')  // bh->b, gh->g, kh->k, th->t, ch->c ...
    .replace(/w/g, 'v')
    .replace(/z/g, 's')
    .replace(/q/g, 'k')
    .replace(/x/g, 'ks')
    .replace(/ee/g, 'i')
    .replace(/oo/g, 'u')
    .replace(/(.)\1+/g, '$1')          // kumaar -> kumar, sharrma -> sharma
}

/** Levenshtein, bailing out once the distance exceeds `max`. */
export function editDistance(a: string, b: string, max: number): number {
  if (Math.abs(a.length - b.length) > max) return max + 1
  let previous = Array.from({ length: b.length + 1 }, (_, i) => i)
  for (let i = 1; i <= a.length; i++) {
    const current = [i]
    let best = i
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1
      const value = Math.min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
      current.push(value)
      if (value < best) best = value
    }
    if (best > max) return max + 1
    previous = current
  }
  return previous[b.length]
}
