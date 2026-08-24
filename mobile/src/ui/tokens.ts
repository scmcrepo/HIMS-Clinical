/**
 * Design tokens.
 *
 * Teal rather than the usual clinical blue, and a large base type scale: the
 * patient population for an Indian hospital app skews older than a consumer
 * app's, and 14pt body text on a 5-inch phone in daylight is the most common
 * accessibility failure in this category.
 */

export const colors = {
  primary: "#18181B",
  primaryDark: "#09090B",
  primarySoft: "#F4F4F5",
  surface: "#FFFFFF",
  surfaceAlt: "#FAFAFA",
  border: "#E4E4E7",
  text: "#18181B",
  textMuted: "#71717A",
  danger: "#DC2626",
  dangerSoft: "#FEE2E2",
  warning: "#D97706",
  warningSoft: "#FEF3C7",
  success: "#16A34A",
  successSoft: "#DCFCE7",
} as const;

export const spacing = {
  xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32,
} as const;

export const radius = { sm: 6, md: 10, lg: 16, pill: 999 } as const;

export const typography = {
  display: { fontSize: 28, fontWeight: "700" as const, lineHeight: 34 },
  title: { fontSize: 22, fontWeight: "700" as const, lineHeight: 28 },
  heading: { fontSize: 18, fontWeight: "600" as const, lineHeight: 24 },
  body: { fontSize: 16, fontWeight: "400" as const, lineHeight: 24 },
  label: { fontSize: 14, fontWeight: "600" as const, lineHeight: 20 },
  caption: { fontSize: 13, fontWeight: "400" as const, lineHeight: 18 },
} as const;

/** Android and iOS both treat 44-48dp as the minimum comfortable target. */
export const MIN_TOUCH_TARGET = 48;
