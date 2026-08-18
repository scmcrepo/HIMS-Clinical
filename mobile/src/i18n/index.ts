import { en, type MessageKey } from "./en";

/**
 * Minimal message resolver. Deliberately not a dependency: the app needs
 * lookup and {placeholder} interpolation and nothing else, and every i18n
 * library in this space brings a locale-data bundle that dwarfs it.
 */
export type MessagePack = Record<string, string>;

const packs: Record<string, MessagePack> = { en };

let activeLocale = "en";

export function setLocale(locale: string): void {
  activeLocale = packs[locale] ? locale : "en";
}

export function getLocale(): string {
  return activeLocale;
}

export function registerPack(locale: string, pack: MessagePack): void {
  packs[locale] = pack;
}

export function t(
  key: MessageKey | string,
  params?: Record<string, string | number>,
): string {
  const pack: MessagePack = packs[activeLocale] ?? (en as unknown as MessagePack);
  // Falls back to English rather than to the raw key: a half-translated Tamil
  // pack should show English words, not "dashboard.greeting".
  const template =
    pack[key] ?? (en as unknown as MessagePack)[key] ?? key;
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (whole: string, name: string) =>
    params[name] === undefined ? whole : String(params[name]),
  );
}

/** True when every key in the English pack has a translation. */
export function missingKeys(locale: string): string[] {
  const pack = packs[locale];
  if (!pack) return Object.keys(en);
  return Object.keys(en).filter((k) => pack[k] === undefined);
}
