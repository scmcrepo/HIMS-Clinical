import type { Appointment, SlotAvailability } from "./contracts";

/**
 * Client-side booking rules.
 *
 * Every rule here is also enforced server-side (WO-018 §7). This copy exists to
 * disable a button rather than to let the patient press it and be told no — it
 * is a courtesy, never the control. If these two ever disagree, the server wins
 * and the app shows the server's error.
 */

/** WO-018 §9 Q1 — answers "how far ahead can patients book?" */
export const BOOKING_WINDOW_DAYS = 30;

/** Cancellation / reschedule closes this long before the slot starts. */
export const CANCEL_CUTOFF_MINUTES = 60;

/** Statuses that mean the hospital has already engaged with the appointment. */
const IMMUTABLE_STATUSES = new Set(["CHECKED_IN", "COMPLETED", "NO_SHOW", "CONSULTED"]);

/** "YYYY-MM-DD" in the device's local calendar, not UTC. */
export function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function startOfLocalDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/**
 * The dates the calendar offers: today through today + 13, inclusive.
 *
 * Local-calendar arithmetic, not `now + n * 86400000`. India has no DST, but a
 * patient roaming, or a device with a manually shifted clock, would otherwise
 * get a 14-day window that quietly becomes 13 or 15 days.
 */
export function bookableDates(now: Date): string[] {
  const base = startOfLocalDay(now);
  const out: string[] = [];
  for (let i = 0; i < BOOKING_WINDOW_DAYS; i += 1) {
    out.push(toIsoDate(new Date(base.getFullYear(), base.getMonth(), base.getDate() + i)));
  }
  return out;
}

export function isDateBookable(isoDate: string, now: Date): boolean {
  return bookableDates(now).includes(isoDate);
}

/**
 * PRD §6c: full slots are shown, not hidden, but are not selectable. Hiding them
 * makes a doctor with a full morning look like a doctor who does not work
 * mornings, and patients then call the front desk to ask — which is the load the
 * portal exists to remove.
 */
export function isSlotSelectable(
  slot: SlotAvailability,
  isoDate: string,
  now: Date,
): boolean {
  if (!slot.isAvailable) return false;
  if (slot.availableCount <= 0) return false;
  if (!isDateBookable(isoDate, now)) return false;
  // A 09:00 slot is not bookable at 09:30 today, though it is on any later date.
  if (isoDate === toIsoDate(now) && hasSlotStarted(slot, now)) return false;
  return true;
}

export function hasSlotStarted(slot: SlotAvailability, now: Date): boolean {
  const start = parseLocalTime(slot.fromTime);
  if (!start) return false;
  const minutesNow = now.getHours() * 60 + now.getMinutes();
  return minutesNow >= start.hours * 60 + start.minutes;
}

/** Accepts "HH:mm" and "HH:mm:ss" — Java LocalTime serialises both. */
export function parseLocalTime(
  value: string,
): { hours: number; minutes: number } | null {
  const m = /^(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value.trim());
  if (!m) return null;
  const hours = Number(m[1]);
  const minutes = Number(m[2]);
  if (hours > 23 || minutes > 59) return null;
  return { hours, minutes };
}

export type SlotPressure = "open" | "filling" | "full";

/** Drives the ✅ / ⚠️ / 🔴 treatment in the PRD's slot table. */
export function slotPressure(slot: SlotAvailability): SlotPressure {
  if (slot.availableCount <= 0 || !slot.isAvailable) return "full";
  if (slot.availableCount <= 2) return "filling";
  return "open";
}

export function combineDateAndTime(isoDate: string, time: string): Date | null {
  const t = parseLocalTime(time);
  const d = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!t || !d) return null;
  return new Date(Number(d[1]), Number(d[2]) - 1, Number(d[3]), t.hours, t.minutes, 0, 0);
}

export interface EligibilityResult {
  allowed: boolean;
  /** i18n key explaining the refusal; null when allowed. */
  reason: string | null;
}

const ALLOWED: EligibilityResult = { allowed: true, reason: null };

/**
 * Cancellation: the domain rules from the backend (no cancel once CHECKED_IN,
 * no cancel of an already-CANCELLED appointment) plus the 1-hour window.
 */
export function canCancel(appointment: Appointment, now: Date): EligibilityResult {
  if (appointment.status === "CANCELLED") {
    return { allowed: false, reason: "appointment.error.alreadyCancelled" };
  }
  // Early check-in or any in-progress / completed status cannot be cancelled or rescheduled
  if (appointment.status !== "BOOKED" && appointment.status !== "RESCHEDULED") {
    return { allowed: false, reason: "appointment.error.alreadyCheckedIn" };
  }
  const start = combineDateAndTime(appointment.appointmentDate, appointment.fromTime);
  if (!start) return ALLOWED; // Unparseable time: let the server decide.
  const cutoff = new Date(start.getTime() - CANCEL_CUTOFF_MINUTES * 60_000);
  if (now.getTime() > cutoff.getTime()) {
    return { allowed: false, reason: "appointment.error.cancelWindowClosed" };
  }
  return ALLOWED;
}

/**
 * Reschedule follows the same rules. The backend additionally refuses to
 * reschedule a CANCELLED appointment; that is mirrored here so the button is
 * absent rather than failing.
 */
export function canReschedule(appointment: Appointment, now: Date): EligibilityResult {
  return canCancel(appointment, now);
}

export function isUpcoming(appointment: Appointment, now: Date): boolean {
  if (appointment.status === "CANCELLED") return false;
  const start = combineDateAndTime(appointment.appointmentDate, appointment.fromTime);
  if (!start) return false;
  return start.getTime() >= startOfLocalDay(now).getTime();
}

/** Dashboard: shows all non-cancelled appointments from today forward as "upcoming". */
export function upcomingAppointments(
  appointments: Appointment[],
  now: Date,
): Appointment[] {
  return appointments
    .filter((a) => isUpcoming(a, now))
    .filter((a) => a.status !== "CANCELLED")
    .sort((a, b) => {
      const at = combineDateAndTime(a.appointmentDate, a.fromTime)?.getTime() ?? 0;
      const bt = combineDateAndTime(b.appointmentDate, b.fromTime)?.getTime() ?? 0;
      return at - bt;
    });
}
