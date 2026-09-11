/**
 * Autocomplete over the same vocabularies the parser reads.
 *
 * Both halves of the box import from one place on purpose. A suggester written separately
 * from its parser drifts, and the failure is invisible in review and infuriating in use:
 * the box offers a word and then cannot understand it once accepted.
 *
 * One thing is deliberately absent — patients. Names and phone numbers are never
 * suggested, ghosted or listed. A reception screen faces the waiting room, so completing
 * "9876" into a real patient's number and name would disclose one person's identity to
 * whoever is standing at the counter, and would turn the box into a walk-up lookup over
 * exactly the data this system encrypts at rest. Patients resolve after Enter, one
 * deliberate match at a time, in the confirmation row.
 */

import { DATE_WORDS, SESSION_WORDS } from './vocab'
import { matchConsultants, type ConsultantEntry } from './match'
import type { ParseResult } from './parse'

export type SuggestionKind = 'doctor' | 'date' | 'session'

export interface Suggestion {
  /** Canonical text that replaces the fragment. Accepting rewrites "tmrw" as "tomorrow". */
  insert: string
  label: string
  hint: string | null
  kind: SuggestionKind
  score: number
  consultantId: string | null
}

/** A session the doctor genuinely sits, once doctor and date are both known. */
export interface SessionOption {
  insert: string
  label: string
  hint: string
  full: boolean
}

export interface SuggestContext {
  index: ConsultantEntry[]
  aliases: Record<string, string>
  frequencies: Record<string, number>
  parsed: ParseResult
  /** Doctors with appointments on the day in view — free evidence that they are sitting. */
  activeConsultantIds: Set<string>
  /** Real sessions for the resolved doctor and date; empty until both are known. */
  sessions: SessionOption[]
}

export interface TokenSpan {
  text: string
  start: number
  end: number
}

/** The whitespace-delimited run the caret sits in, from its start up to the caret. */
export function currentToken(text: string, caret: number): TokenSpan {
  let start = caret
  while (start > 0 && !/\s/.test(text[start - 1])) start--
  let end = caret
  while (end < text.length && !/\s/.test(text[end])) end++
  return { text: text.slice(start, caret).toLowerCase(), start, end }
}

const MAX_SUGGESTIONS = 6

export function suggest(text: string, caret: number, ctx: SuggestContext): Suggestion[] {
  const token = currentToken(text, caret)

  // Never complete anything containing digits: that path leads only to patients.
  if (/\d/.test(token.text)) return []

  if (!token.text) return starters(ctx)

  const { parsed } = ctx
  const out: Suggestion[] = []

  // What is still missing steers the ranking, so the box narrows as it fills up.
  const needsDoctor = !parsed.consultant
  const needsDate = !parsed.date
  const needsSession = !parsed.session

  if (needsDoctor) {
    for (const match of matchConsultants(token.text, ctx.index, ctx.aliases).slice(0, MAX_SUGGESTIONS)) {
      const consultant = match.consultant
      const speciality = consultant.specialisation || consultant.qualification
      const sitting = ctx.activeConsultantIds.has(consultant.id)
      out.push({
        insert: consultant.lastName.toLowerCase(),
        label: `${consultant.salutation || 'Dr.'} ${consultant.firstName} ${consultant.lastName}`.replace(/\s+/g, ' '),
        hint: [speciality, sitting ? 'booked today' : null].filter(Boolean).join(' · ') || null,
        kind: 'doctor',
        score: match.score + 100
          + Math.min((ctx.frequencies[consultant.id] ?? 0) * 5, 40)
          + (sitting ? 30 : 0),
        consultantId: consultant.id,
      })
    }
  }

  if (needsDate) {
    for (const word of DATE_WORDS) {
      if (!word.words.some(w => w.startsWith(token.text))) continue
      out.push({
        insert: word.canonical,
        label: word.canonical,
        hint: formatHint(word.resolve(new Date())),
        kind: 'date',
        score: 90 + (word.canonical === token.text ? 20 : 0) + (needsDoctor ? 0 : 100),
        consultantId: null,
      })
    }
  }

  if (needsSession) {
    // Once the doctor and day are settled these are real windows with real capacity,
    // which is more useful than the generic word and answers "is there space?" inline.
    if (ctx.sessions.length > 0) {
      for (const session of ctx.sessions) {
        if (!session.insert.startsWith(token.text)) continue
        out.push({
          insert: session.insert,
          label: session.label,
          hint: session.hint,
          kind: 'session',
          score: (session.full ? 60 : 200) + 100,
          consultantId: null,
        })
      }
    } else {
      for (const word of SESSION_WORDS) {
        if (!word.words.some(w => w.startsWith(token.text))) continue
        out.push({
          insert: word.canonical,
          label: word.canonical,
          hint: null,
          kind: 'session',
          score: 80,
          consultantId: null,
        })
      }
    }
  }

  return dedupe(out).sort((a, b) => b.score - a.score).slice(0, MAX_SUGGESTIONS)
}

/**
 * What an empty box offers.
 *
 * A blank field with a clever parser behind it is undiscoverable, so the box says what it
 * accepts by showing the doctors this user actually books and the two dates almost every
 * booking uses. It doubles as the no-typing path for anyone who would rather click.
 */
function starters(ctx: SuggestContext): Suggestion[] {
  const doctors = [...ctx.index]
    .map(entry => ({
      entry,
      weight: (ctx.frequencies[entry.consultant.id] ?? 0) * 5
        + (ctx.activeConsultantIds.has(entry.consultant.id) ? 30 : 0),
    }))
    .filter(d => d.weight > 0)
    .sort((a, b) => b.weight - a.weight)
    .slice(0, 3)
    .map(({ entry, weight }): Suggestion => ({
      insert: entry.consultant.lastName.toLowerCase(),
      label: `${entry.consultant.salutation || 'Dr.'} ${entry.consultant.firstName} ${entry.consultant.lastName}`.replace(/\s+/g, ' '),
      hint: entry.consultant.specialisation || entry.consultant.qualification || null,
      kind: 'doctor',
      score: 100 + weight,
      consultantId: entry.consultant.id,
    }))

  const dates: Suggestion[] = ['today', 'tomorrow'].map(canonical => ({
    insert: canonical,
    label: canonical,
    hint: null,
    kind: 'date',
    score: 50,
    consultantId: null,
  }))

  return [...doctors, ...dates]
}

function dedupe(items: Suggestion[]): Suggestion[] {
  const seen = new Set<string>()
  return items.filter(item => {
    const key = `${item.kind}:${item.insert}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

function formatHint(date: Date): string {
  return date.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })
}

/** Substitutes a suggestion for the fragment under the caret and reports the new caret. */
export function applySuggestion(
  text: string,
  caret: number,
  suggestion: Suggestion,
): { text: string; caret: number } {
  const token = currentToken(text, caret)
  const before = text.slice(0, token.start)
  const after = text.slice(token.end)
  const inserted = `${before}${suggestion.insert} `
  return {
    text: `${inserted}${after.replace(/^\s+/, '')}`,
    caret: inserted.length,
  }
}

/**
 * The grey completion drawn after the caret, or null when there is nothing safe to show.
 *
 * Only ever a suffix of the current fragment, and only when the caret is at the end of the
 * line: ghosting mid-string paints the completion over text the user can still see, which
 * reads as corruption rather than as help.
 */
export function ghostFor(text: string, caret: number, top: Suggestion | undefined): string | null {
  if (!top || caret !== text.length) return null
  const token = currentToken(text, caret)
  if (!token.text) return null
  if (!top.insert.startsWith(token.text)) return null
  const suffix = top.insert.slice(token.text.length)
  return suffix.length > 0 ? suffix : null
}
