import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

/**
 * Tailwind classes are strings. A colour token that does not exist in
 * tailwind.config.ts fails silently — the element renders with no background
 * and nobody notices until someone looks at the screen.
 *
 * <p>This caught a real bug: the ABHA modal was first written with
 * `bg-primary-600`, which does not exist in this project. `primary` here is a
 * shadcn-style DEFAULT/foreground pair with no numeric scale.
 *
 * <p>The check is deliberately narrow — it validates the semantic tokens this
 * project defines itself, not Tailwind's built-in palette, which is always
 * present.
 */

const COMPONENT_DIR = join(__dirname, 'components')

/** Tokens declared as DEFAULT/foreground pairs, so a numeric suffix is invalid. */
const SCALELESS_TOKENS = [
  'primary',
  'secondary',
  'destructive',
  'muted',
  'accent',
  'popover',
  'card',
  'border',
  'input',
  'ring',
  'background',
  'foreground',
]

function componentSources(): { name: string; source: string }[] {
  return readdirSync(COMPONENT_DIR)
    .filter((f) => f.endsWith('.tsx'))
    .map((f) => ({ name: f, source: readFileSync(join(COMPONENT_DIR, f), 'utf8') }))
}

describe('tailwind tokens used by ABHA components', () => {
  it('finds component files to check', () => {
    expect(componentSources().length).toBeGreaterThan(0)
  })

  it('never applies a numeric shade to a scale-less semantic token', () => {
    const offenders: string[] = []

    for (const { name, source } of componentSources()) {
      for (const token of SCALELESS_TOKENS) {
        // e.g. bg-primary-600, text-muted-500, border-accent-200
        const pattern = new RegExp(`\\b(?:bg|text|border|ring|accent|from|to|via)-${token}-\\d{2,3}\\b`, 'g')
        const found = source.match(pattern)
        if (found) offenders.push(`${name}: ${[...new Set(found)].join(', ')}`)
      }
    }

    expect(offenders).toEqual([])
  })

  it('keeps the full ABHA number out of component markup', () => {
    // Components must render abhaNumberMasked, never abhaNumber.
    for (const { name, source } of componentSources()) {
      expect(source, `${name} must not read the unmasked ABHA number`).not.toMatch(
        /\.abhaNumber\b(?!Masked)/,
      )
    }
  })
})
