/**
 * Design tokens.
 *
 * Teal rather than the usual clinical blue, and a large base type scale: the
 * patient population for an Indian hospital app skews older than a consumer
 * app's, and 14pt body text on a 5-inch phone in daylight is the most common
 * accessibility failure in this category.
 */

export const colors = {
  primary: "#09090B",
  primaryDark: "#000000",
  primarySoft: "#F4F4F5",
  surface: "#FFFFFF",
  surfaceAlt: "#FAFAFA",
  border: "#E4E4E7",
  borderDark: "#D4D4D8",
  text: "#09090B",
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
  display: { fontFamily: "Inter_700Bold", fontSize: 28, fontWeight: "700" as const, lineHeight: 34 },
  title: { fontFamily: "Inter_700Bold", fontSize: 22, fontWeight: "700" as const, lineHeight: 28 },
  heading: { fontFamily: "Inter_600SemiBold", fontSize: 18, fontWeight: "600" as const, lineHeight: 24 },
  body: { fontFamily: "Inter_400Regular", fontSize: 16, fontWeight: "400" as const, lineHeight: 24 },
  label: { fontFamily: "Inter_600SemiBold", fontSize: 14, fontWeight: "600" as const, lineHeight: 20 },
  caption: { fontFamily: "Inter_400Regular", fontSize: 13, fontWeight: "400" as const, lineHeight: 18 },
} as const;

/** Android and iOS both treat 44-48dp as the minimum comfortable target. */
export const MIN_TOUCH_TARGET = 48;

/* -------------------------------------------------------------------------
 * New tokens — added for the premium UI upgrade.
 * ---------------------------------------------------------------------- */

/** Elevation presets — layered surfaces, not decorative heavy shadows. */
export const shadows = {
  sm: {
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.04,
    shadowRadius: 3,
    elevation: 1,
  },
  md: {
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 2,
  },
  lg: {
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 16,
    elevation: 4,
  },
} as const;

/** Animation durations and spring config. Keep fast so healthcare flows aren't slowed. */
export const animation = {
  /** Tap feedback / micro-interaction */
  fast: 150,
  /** Small transitions (content fade-in, indicator slide) */
  normal: 250,
  /** Screen-level transitions, bottom sheets */
  slow: 300,
  /** Spring config for modal entrance / bottom sheet */
  spring: { damping: 20, stiffness: 180, mass: 1 },
  /** Scale factor for press animations */
  pressScale: 0.97,
  cardPressScale: 0.985,
} as const;
