/**
 * Per-hospital theme colour.
 *
 * The application paints its buttons, headers, gradients and focus rings with
 * `neutral-500/600/700`. Those three shades are mapped in `tailwind.config.ts` to the
 * CSS variables set here, so changing a hospital's colour re-skins every screen without
 * touching a single component. Every other neutral shade stays a true grey — body text
 * and borders should not turn purple because someone likes purple.
 *
 * The hospital's choice lives on the tenant row server-side, which is what makes it
 * survive logout and follow the user to another machine. The localStorage copy is a
 * paint-flash optimisation only, never the source of truth.
 */

const STORAGE_KEY = 'hms.theme.color'

/** Tailwind's own neutral 500/600/700 — the look when nothing has been chosen. */
const DEFAULT_RAMP: Ramp = { c500: '115 115 115', c600: '82 82 82', c700: '64 64 64' }

export interface Ramp {
  c500: string
  c600: string
  c700: string
}

export interface ThemePreset {
  name: string
  color: string
}

/**
 * Ready-made colours, each already in the lightness band where white button text
 * stays readable. Offered first because most people want a good colour, not a
 * colour-picking exercise.
 */
export const THEME_PRESETS: ThemePreset[] = [
  { name: 'Slate',     color: '#525252' },
  { name: 'Teal',      color: '#0f6b57' },
  { name: 'Ocean',     color: '#1a5f8a' },
  { name: 'Indigo',    color: '#3d3f8f' },
  { name: 'Plum',      color: '#6b2d5c' },
  { name: 'Clay',      color: '#8a4028' },
  { name: 'Forest',    color: '#2f5d34' },
  { name: 'Charcoal',  color: '#37414f' },
]

export const isValidHex = (value: string): boolean => /^#[0-9a-fA-F]{6}$/.test(value.trim())

// ── colour maths ────────────────────────────────────────────────────────────

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.trim().replace('#', '')
  return [
    parseInt(h.slice(0, 2), 16),
    parseInt(h.slice(2, 4), 16),
    parseInt(h.slice(4, 6), 16),
  ]
}

function rgbToHsl(r: number, g: number, b: number): [number, number, number] {
  const rn = r / 255, gn = g / 255, bn = b / 255
  const max = Math.max(rn, gn, bn), min = Math.min(rn, gn, bn)
  const l = (max + min) / 2
  if (max === min) return [0, 0, l]

  const d = max - min
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
  let h: number
  if (max === rn) h = ((gn - bn) / d + (gn < bn ? 6 : 0)) / 6
  else if (max === gn) h = ((bn - rn) / d + 2) / 6
  else h = ((rn - gn) / d + 4) / 6
  return [h, s, l]
}

function hslToRgb(h: number, s: number, l: number): [number, number, number] {
  if (s === 0) {
    const v = Math.round(l * 255)
    return [v, v, v]
  }
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s
  const p = 2 * l - q
  const channel = (t: number) => {
    let x = t
    if (x < 0) x += 1
    if (x > 1) x -= 1
    if (x < 1 / 6) return p + (q - p) * 6 * x
    if (x < 1 / 2) return q
    if (x < 2 / 3) return p + (q - p) * (2 / 3 - x) * 6
    return p
  }
  return [
    Math.round(channel(h + 1 / 3) * 255),
    Math.round(channel(h) * 255),
    Math.round(channel(h - 1 / 3) * 255),
  ]
}

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v))

/** Relative luminance, per WCAG 2.1. */
function luminance([r, g, b]: [number, number, number]): number {
  const channel = (v: number) => {
    const n = v / 255
    return n <= 0.03928 ? n / 12.92 : Math.pow((n + 0.055) / 1.055, 2.4)
  }
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

/** Contrast of white text on the given colour. */
const contrastOnWhiteText = (rgb: [number, number, number]) => 1.05 / (luminance(rgb) + 0.05)

/** WCAG AA for normal text. Primary buttons are white-on-600 everywhere in this app. */
const MIN_CONTRAST = 4.5

/**
 * Builds the three shades from one chosen colour.
 *
 * The 600 shade sits under white text on every primary button, so it is darkened until
 * white on it clears WCAG AA. That test is done on measured luminance rather than HSL
 * lightness, because the two disagree badly on yellows, limes and cyans: a pale yellow
 * clamped by lightness alone still yields an unreadable button, which is precisely the
 * kind of thing that ships and then only breaks for the one hospital that picked it.
 *
 * 500 and 700 step out from the result, keeping hue and saturation so it still reads as
 * the colour that was chosen.
 */
export function buildRamp(hex: string): Ramp {
  if (!isValidHex(hex)) return DEFAULT_RAMP

  const [r, g, b] = hexToRgb(hex)
  const [h, s, l] = rgbToHsl(r, g, b)
  const at = (lightness: number) => hslToRgb(h, s, lightness)

  let l600 = clamp(l, 0.2, 0.46)
  // Step down in 1% increments until white text is legible, with a floor so a colour
  // can never darken all the way to black.
  for (let i = 0; i < 40 && l600 > 0.1; i++) {
    if (contrastOnWhiteText(at(l600)) >= MIN_CONTRAST) break
    l600 -= 0.01
  }

  const l500 = clamp(l600 + 0.1, 0.24, 0.58)
  const l700 = Math.max(0.08, l600 - 0.08)

  const channels = (lightness: number) => at(lightness).join(' ')
  return { c500: channels(l500), c600: channels(l600), c700: channels(l700) }
}

/** The colour a swatch should show for a given choice — the 600 shade actually used. */
export function previewHex(hex: string): string {
  const { c600 } = buildRamp(hex)
  const [r, g, b] = c600.split(' ').map(Number)
  return '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('')
}

// ── applying ────────────────────────────────────────────────────────────────

/** Paints the ramp onto the document. Passing null or an invalid value restores the default. */
export function applyTheme(hex: string | null | undefined): void {
  const ramp = hex && isValidHex(hex) ? buildRamp(hex) : DEFAULT_RAMP
  const root = document.documentElement
  root.style.setProperty('--brand-500', ramp.c500)
  root.style.setProperty('--brand-600', ramp.c600)
  root.style.setProperty('--brand-700', ramp.c700)
}

/**
 * Remembers the colour for the next page load on this device.
 *
 * Wrapped because storage throws outright in some privacy modes, and a hospital's
 * colour preference is never worth breaking a login over.
 */
export function cacheTheme(hex: string | null): void {
  try {
    if (hex && isValidHex(hex)) localStorage.setItem(STORAGE_KEY, hex)
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* private mode, storage disabled — the server copy still applies on load */
  }
}

export function readCachedTheme(): string | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored && isValidHex(stored) ? stored : null
  } catch {
    return null
  }
}

/**
 * Applies the cached colour immediately, before React renders.
 *
 * Without this the app paints in the default colour for as long as the theme request
 * takes and then repaints — a visible flash on every page load. The server value that
 * arrives moments later is still authoritative and overwrites this.
 */
export function applyCachedThemeEagerly(): void {
  applyTheme(readCachedTheme())
}
