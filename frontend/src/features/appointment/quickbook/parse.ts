/**
 * Turns a typed line into the four things a booking needs.
 *
 * The approach is bucketing, not grammar. Rather than parse a sentence, every token is
 * offered to each closed set in turn — is this ten digits? is this a date word? is this one
 * of our fifty doctors? — most unambiguous first, each match consuming what it used.
 *
 * The consequence is that word order stops mattering. "tmrw sharma 9876543210" and
 * "9876543210 with dr sharma tomorrow" produce identical results, because nothing here
 * depends on syntax. That is what makes a deterministic parser feel forgiving instead of
 * brittle, and it is why this problem does not need a language model: the input is not
 * English, it is four tokens drawn from four lists the application already holds.
 *
 * Nothing in this file books anything. It produces a draft for a human to confirm.
 */

import type { Consultant } from '../../../services/consultant/consultantApi'
import {
  DATE_WORDS, DOCTOR_PREFIXES, MAX_DATE_PHRASE_WORDS, SALUTATIONS, SESSION_WORDS, STOPWORDS,
} from './vocab'
import { matchConsultants, type ConsultantEntry } from './match'

export type SessionRef =
  | { kind: 'named'; canonical: string; fromHour: number; toHour: number }
  | { kind: 'time'; canonical: string; hour: number }

export interface ParseResult {
  phone: string | null
  patientNumber: string | null
  name: string | null
  salutation: string | null
  age: number | null
  gender: 'MALE' | 'FEMALE' | 'OTHER' | null

  consultant: Consultant | null
  /** What was typed for the doctor, kept so a correction can be remembered as an alias. */
  consultantFragment: string | null
  /** Populated only when several doctors scored alike and the box must ask. */
  consultantCandidates: Consultant[]

  date: Date | null
  dateCanonical: string | null
  session: SessionRef | null
}

interface Token {
  text: string
  start: number
  end: number
  consumed: boolean
}

export function tokenize(input: string): Token[] {
  const tokens: Token[] = []
  const pattern = /\S+/g
  let match = pattern.exec(input)
  while (match !== null) {
    tokens.push({
      text: match[0].toLowerCase(),
      start: match.index,
      end: match.index + match[0].length,
      consumed: false,
    })
    match = pattern.exec(input)
  }
  return tokens
}

const digitsOf = (value: string) => value.replace(/\D/g, '')
const available = (tokens: Token[]) => tokens.filter(t => !t.consumed)

/** Joins `count` tokens starting at `from`, or null if they are not all free. */
function phrase(tokens: Token[], from: number, count: number): string | null {
  const slice = tokens.slice(from, from + count)
  if (slice.length < count || slice.some(t => t.consumed)) return null
  return slice.map(t => t.text).join(' ')
}

function consume(tokens: Token[], from: number, count: number): void {
  for (let i = from; i < from + count && i < tokens.length; i++) tokens[i].consumed = true
}

// ── individual buckets ──────────────────────────────────────────────────────

/**
 * Ten digits, the fast path.
 *
 * Worth the extra effort of stitching adjacent tokens together: people paste and dictate
 * numbers as "98765 43210" at least as often as they type them solid, and a phone is the
 * one patient key that resolves through an indexed exact lookup rather than by decrypting
 * every patient in the tenant.
 */
function takePhone(tokens: Token[]): string | null {
  for (let i = 0; i < tokens.length; i++) {
    if (tokens[i].consumed) continue
    const solo = digitsOf(tokens[i].text)
    if (/^(91)?\d{10}$/.test(solo) && !/[a-z]/.test(tokens[i].text.replace(/^\+/, ''))) {
      consume(tokens, i, 1)
      return solo.slice(-10)
    }
    const next = tokens[i + 1]
    const numericOnly = (t: Token) => /^[+\d\s\-()]+$/.test(t.text) && digitsOf(t.text).length > 0
    if (next && !next.consumed && numericOnly(tokens[i]) && numericOnly(next)) {
      const joined = digitsOf(tokens[i].text) + digitsOf(next.text)
      if (/^(91)?\d{10}$/.test(joined)) {
        consume(tokens, i, 2)
        return joined.slice(-10)
      }
    }
  }
  return null
}

/** Something like OP1234 or OP-2024-0173 — letters, then at least three digits. */
function takePatientNumber(tokens: Token[]): string | null {
  for (const token of tokens) {
    if (token.consumed) continue
    if (/^[a-z]{1,6}[-/]?\d{2,}([-/]\d+)*$/.test(token.text) && digitsOf(token.text).length >= 3) {
      token.consumed = true
      return token.text.toUpperCase()
    }
  }
  return null
}

function takeAgeGender(tokens: Token[]): { age: number | null; gender: ParseResult['gender'] } {
  let age: number | null = null
  let gender: ParseResult['gender'] = null

  for (const token of tokens) {
    if (token.consumed) continue

    const combined = /^(\d{1,3})\s*\/?\s*(m|f|o|male|female|other)$/.exec(token.text)
    if (combined) {
      age = Number(combined[1])
      gender = combined[2].startsWith('m') ? 'MALE' : combined[2].startsWith('f') ? 'FEMALE' : 'OTHER'
      token.consumed = true
      continue
    }

    const years = /^(\d{1,3})\s*(y|yr|yrs|years|yo)$/.exec(token.text)
    if (years) {
      age = Number(years[1])
      token.consumed = true
      continue
    }

    if (/^(male|female)$/.test(token.text)) {
      gender = token.text === 'male' ? 'MALE' : 'FEMALE'
      token.consumed = true
    }
  }

  return { age, gender }
}

const startOfToday = (today: Date) => new Date(today.getFullYear(), today.getMonth(), today.getDate())

/** Day-first, per Indian convention: 3/12 is the third of December. */
function parseNumericDate(text: string, today: Date): Date | null {
  const parts = /^(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{2,4}))?$/.exec(text)
  if (parts) {
    const day = Number(parts[1])
    const month = Number(parts[2])
    if (day < 1 || day > 31 || month < 1 || month > 12) return null
    let year = today.getFullYear()
    if (parts[3]) year = parts[3].length === 2 ? 2000 + Number(parts[3]) : Number(parts[3])
    const candidate = new Date(year, month - 1, day)
    if (candidate.getMonth() !== month - 1) return null // 31/02 and friends
    // A bare day/month that has already gone by means next year.
    if (!parts[3] && candidate < startOfToday(today)) candidate.setFullYear(year + 1)
    return candidate
  }

  const ordinal = /^(\d{1,2})(st|nd|rd|th)$/.exec(text)
  if (ordinal) {
    const day = Number(ordinal[1])
    if (day < 1 || day > 31) return null
    const candidate = new Date(today.getFullYear(), today.getMonth(), day)
    if (candidate.getDate() !== day) return null
    if (candidate < startOfToday(today)) candidate.setMonth(candidate.getMonth() + 1)
    return candidate
  }

  return null
}

function takeDate(tokens: Token[], today: Date): { date: Date | null; canonical: string | null } {
  // Multi-word phrases first, so "day after" is not read as the word "day".
  for (let width = MAX_DATE_PHRASE_WORDS; width >= 1; width--) {
    for (let i = 0; i < tokens.length; i++) {
      const candidate = phrase(tokens, i, width)
      if (!candidate) continue
      const word = DATE_WORDS.find(d => d.words.includes(candidate))
      if (word) {
        consume(tokens, i, width)
        return { date: word.resolve(today), canonical: word.canonical }
      }
    }
  }

  for (const token of tokens) {
    if (token.consumed) continue
    const numeric = parseNumericDate(token.text, today)
    if (numeric) {
      token.consumed = true
      return { date: numeric, canonical: token.text }
    }
  }

  return { date: null, canonical: null }
}

function takeSession(tokens: Token[]): SessionRef | null {
  for (const token of tokens) {
    if (token.consumed) continue
    const word = SESSION_WORDS.find(s => s.words.includes(token.text))
    if (word) {
      token.consumed = true
      return { kind: 'named', canonical: word.canonical, fromHour: word.fromHour, toHour: word.toHour }
    }
  }

  // An explicit clock time needs a meridiem or a colon. A bare number stays a number:
  // "10" is far more likely to be part of an age or a date than ten o'clock.
  for (const token of tokens) {
    if (token.consumed) continue
    const time = /^(\d{1,2})(?:[.:](\d{2}))?\s*(am|pm)$/.exec(token.text)
      ?? /^(\d{1,2})[.:](\d{2})$/.exec(token.text)
    if (!time) continue

    let hour = Number(time[1])
    const meridiem = time[3]
    if (meridiem === 'pm' && hour < 12) hour += 12
    if (meridiem === 'am' && hour === 12) hour = 0
    if (hour > 23) continue

    token.consumed = true
    const minute = time[2] ? Number(time[2]) : 0
    const label = `${((hour + 11) % 12) + 1}${minute ? `:${String(minute).padStart(2, '0')}` : ''}${hour < 12 ? 'am' : 'pm'}`
    return { kind: 'time', canonical: label, hour }
  }

  return null
}

/**
 * Separates the doctor from the patient among the leftover words.
 *
 * An explicit "dr" settles it outright. Failing that, the strongest match against the
 * doctor list wins, but only at prefix strength — a merely fuzzy hit is not allowed to
 * turn a patient called Sharman into Dr. Sharma. Whatever is left over is the patient.
 */
function takeConsultant(
  tokens: Token[],
  index: ConsultantEntry[],
  aliases: Record<string, string>,
): { consultant: Consultant | null; fragment: string | null; candidates: Consultant[] } {
  const marker = tokens.findIndex(t => !t.consumed && DOCTOR_PREFIXES.has(t.text))
  if (marker >= 0) {
    tokens[marker].consumed = true
    for (let width = 2; width >= 1; width--) {
      const fragment = phrase(tokens, marker + 1, width)
      if (!fragment) continue
      const matches = matchConsultants(fragment, index, aliases)
      if (matches.length > 0) {
        consume(tokens, marker + 1, width)
        return pick(matches, fragment)
      }
    }
    return { consultant: null, fragment: null, candidates: [] }
  }

  type Candidate = { matches: ReturnType<typeof matchConsultants>; at: number; width: number; fragment: string }
  let strong: Candidate | null = null
  let weak: Candidate | null = null

  for (let width = 2; width >= 1; width--) {
    for (let i = 0; i < tokens.length; i++) {
      const fragment = phrase(tokens, i, width)
      if (!fragment || STOPWORDS.has(fragment) || /\d/.test(fragment)) continue
      const matches = matchConsultants(fragment, index, aliases)
      if (matches.length === 0) continue

      if (matches[0].score >= STRONG_MATCH) {
        if (!strong || matches[0].score > strong.matches[0].score) strong = { matches, at: i, width, fragment }
      } else if (matches[0].score >= WEAK_MATCH) {
        if (!weak || matches[0].score > weak.matches[0].score) weak = { matches, at: i, width, fragment }
      }
    }
  }

  if (strong) {
    consume(tokens, strong.at, strong.width)
    return pick(strong.matches, strong.fragment)
  }

  /*
   * A misspelling and a patient's surname look identical from here — "sharrma" is
   * probably Dr. Sharma, "sharman" is probably a patient, and nothing in the text says
   * which. So a weak match is offered rather than taken: the tokens stay part of the
   * name, and the doctor appears as a question the receptionist answers with one click.
   * Answering it stores the alias, which reparses the same line with the doctor resolved
   * and the name correctly shortened.
   */
  if (weak) {
    return {
      consultant: null,
      fragment: weak.fragment,
      candidates: weak.matches.slice(0, 3).map(m => m.consultant),
    }
  }

  return { consultant: null, fragment: null, candidates: [] }
}

/** Prefix strength: enough to claim the tokens outright. */
const STRONG_MATCH = 200
/** Phonetic or near-miss: enough to offer, never enough to decide. */
const WEAK_MATCH = 50

/** One clear winner, or the top few for the box to ask about. */
function pick(
  matches: ReturnType<typeof matchConsultants>,
  fragment: string,
): { consultant: Consultant | null; fragment: string; candidates: Consultant[] } {
  const top = matches[0]
  const runnerUp = matches[1]
  const decisive = !runnerUp || top.score - runnerUp.score >= 40
  return {
    consultant: decisive ? top.consultant : null,
    fragment,
    candidates: decisive ? [] : matches.slice(0, 5).map(m => m.consultant),
  }
}

// ── the whole line ──────────────────────────────────────────────────────────

export function parseBooking(
  input: string,
  index: ConsultantEntry[],
  aliases: Record<string, string>,
  today: Date = new Date(),
): ParseResult {
  const tokens = tokenize(input)

  const phone = takePhone(tokens)
  const patientNumber = takePatientNumber(tokens)
  const { age, gender } = takeAgeGender(tokens)
  const { date, canonical } = takeDate(tokens, today)
  const session = takeSession(tokens)
  const doctor = takeConsultant(tokens, index, aliases)

  // Noise is consumed last: until the doctor pass has run, a word like "see" might still
  // have been part of a name.
  for (const token of tokens) {
    if (!token.consumed && STOPWORDS.has(token.text)) token.consumed = true
  }

  let salutation: string | null = null
  const leftover = available(tokens)
  if (leftover.length > 0 && SALUTATIONS[leftover[0].text]) {
    salutation = SALUTATIONS[leftover[0].text]
    leftover[0].consumed = true
  }

  // Initials and hyphens are split rather than kept: "k.ravi" is two words to a person,
  // and the walk-in name column downstream accepts letters and spaces only.
  const nameWords: string[] = []
  for (const token of tokens) {
    if (token.consumed) continue
    if (!/^[a-z][a-z.'-]*$/.test(token.text)) continue
    token.consumed = true
    token.text.split(/[.'-]+/).filter(Boolean).forEach(part => nameWords.push(part))
  }

  const name = nameWords.length > 0
    ? nameWords.map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
    : null

  return {
    phone,
    patientNumber,
    name,
    salutation,
    age,
    gender,
    consultant: doctor.consultant,
    consultantFragment: doctor.fragment,
    consultantCandidates: doctor.candidates,
    date,
    dateCanonical: canonical,
    session,
  }
}
