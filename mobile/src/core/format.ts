/**
 * Display formatting.
 *
 * Deliberately thin: names and ages arrive pre-composed from the server
 * (`fullName`, `age`) because assembling them client-side would mean shipping
 * salutation rules and date-of-birth arithmetic to a device that has no business
 * holding a date of birth it was not given.
 */

const MONTHS_EN = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

/** "2026-07-20" -> "20 Jul 2026". */
export function formatIsoDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  const month = MONTHS_EN[Number(m[2]) - 1] ?? m[2];
  return `${Number(m[3])} ${month} ${m[1]}`;
}

/** "09:00:00" -> "9:00 AM". Twelve-hour clock: the PRD's slot table uses it. */
export function formatTime(value: string): string {
  const m = /^(\d{2}):(\d{2})/.exec(value.trim());
  if (!m) return value;
  const hours = Number(m[1]);
  const minutes = m[2];
  const suffix = hours >= 12 ? "PM" : "AM";
  const display = hours % 12 === 0 ? 12 : hours % 12;
  return `${display}:${minutes} ${suffix}`;
}

export function formatTimeRange(from: string, to: string): string {
  return `${formatTime(from)} – ${formatTime(to)}`;
}

export function formatAge(age: number | null): string {
  if (age === null) return "—";
  return `${age} yrs`;
}

export function formatFileSize(bytes: number | null): string {
  if (bytes === null || bytes < 0) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Initials for the avatar placeholder when a patient has no photo. */
export function initials(fullName: string): string {
  const parts = fullName
    .replace(/^(Mr|Mrs|Ms|Dr|Master)\.?\s+/i, "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return "?";
  const first = (parts[0] as string)[0] ?? "";
  const last = parts.length > 1 ? ((parts[parts.length - 1] as string)[0] ?? "") : "";
  return (first + last).toUpperCase();
}
