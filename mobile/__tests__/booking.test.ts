import { describe, expect, it } from "vitest";
import type { Appointment, SlotAvailability } from "../src/core/contracts";
import {
  BOOKING_WINDOW_DAYS,
  bookableDates,
  canCancel,
  canReschedule,
  combineDateAndTime,
  isDateBookable,
  isSlotSelectable,
  parseLocalTime,
  slotPressure,
  toIsoDate,
  upcomingAppointments,
} from "../src/core/booking";

/** 20 July 2026, 10:30 local. */
const NOW = new Date(2026, 6, 20, 10, 30, 0);

function slot(over: Partial<SlotAvailability> = {}): SlotAvailability {
  return {
    slotId: "s1",
    fromTime: "14:00:00",
    toTime: "15:00:00",
    maxPatients: 5,
    bookedCount: 1,
    availableCount: 4,
    isAvailable: true,
    ...over,
  };
}

function appointment(over: Partial<Appointment> = {}): Appointment {
  return {
    appointmentId: "a1",
    appointmentDate: "2026-07-22",
    fromTime: "09:00:00",
    toTime: "10:00:00",
    status: "BOOKED",
    consultantId: "c1",
    consultantName: "Dr Srinivas Rao",
    departmentName: "Cardiology",
    branchName: "City Center",
    notes: null,
    ...over,
  };
}

describe("booking window", () => {
  it("offers exactly 30 days starting today", () => {
    const dates = bookableDates(NOW);
    expect(dates).toHaveLength(BOOKING_WINDOW_DAYS);
    expect(dates[0]).toBe("2026-07-20");
    expect(dates[29]).toBe("2026-08-18");
  });

  it("crosses a month boundary with real calendar arithmetic", () => {
    const dates = bookableDates(new Date(2026, 0, 25, 8, 0));
    expect(dates[0]).toBe("2026-01-25");
    expect(dates[29]).toBe("2026-02-23");
  });

  it("handles a leap year correctly", () => {
    const dates = bookableDates(new Date(2028, 1, 25, 8, 0));
    expect(dates).toContain("2028-02-29");
    expect(dates[29]).toBe("2028-03-25");
  });

  it("rejects yesterday and day 31", () => {
    expect(isDateBookable("2026-07-19", NOW)).toBe(false);
    expect(isDateBookable("2026-07-20", NOW)).toBe(true);
    expect(isDateBookable("2026-08-18", NOW)).toBe(true);
    expect(isDateBookable("2026-08-19", NOW)).toBe(false);
  });
});

describe("slot selectability", () => {
  it("allows a future slot with capacity", () => {
    expect(isSlotSelectable(slot(), "2026-07-22", NOW)).toBe(true);
  });

  it("blocks a slot with no remaining capacity", () => {
    expect(
      isSlotSelectable(
        slot({ availableCount: 0, bookedCount: 5, isAvailable: false }),
        "2026-07-22",
        NOW,
      ),
    ).toBe(false);
  });

  it("blocks a slot the server has flagged unavailable even with capacity left", () => {
    expect(
      isSlotSelectable(slot({ isAvailable: false }), "2026-07-22", NOW),
    ).toBe(false);
  });

  it("blocks a slot that has already started today but allows it tomorrow", () => {
    const morning = slot({ fromTime: "09:00:00", toTime: "10:00:00" });
    expect(isSlotSelectable(morning, "2026-07-20", NOW)).toBe(false);
    expect(isSlotSelectable(morning, "2026-07-21", NOW)).toBe(true);
  });

  it("allows a later slot on the same day", () => {
    expect(
      isSlotSelectable(slot({ fromTime: "14:00:00" }), "2026-07-20", NOW),
    ).toBe(true);
  });

  it("blocks a date outside the window regardless of capacity", () => {
    expect(isSlotSelectable(slot(), "2026-09-01", NOW)).toBe(false);
  });

  it("grades pressure for the ✅ / ⚠️ / 🔴 treatment", () => {
    expect(slotPressure(slot({ availableCount: 5 }))).toBe("open");
    expect(slotPressure(slot({ availableCount: 2 }))).toBe("filling");
    expect(slotPressure(slot({ availableCount: 1 }))).toBe("filling");
    expect(slotPressure(slot({ availableCount: 0, isAvailable: false }))).toBe("full");
  });
});

describe("time parsing", () => {
  it("accepts both LocalTime serialisations", () => {
    expect(parseLocalTime("09:00")).toEqual({ hours: 9, minutes: 0 });
    expect(parseLocalTime("09:00:00")).toEqual({ hours: 9, minutes: 0 });
    expect(parseLocalTime(" 14:30:00 ")).toEqual({ hours: 14, minutes: 30 });
  });

  it("rejects nonsense rather than coercing it", () => {
    expect(parseLocalTime("")).toBeNull();
    expect(parseLocalTime("9:00")).toBeNull();
    expect(parseLocalTime("25:00")).toBeNull();
    expect(parseLocalTime("12:75")).toBeNull();
  });

  it("combines date and time in the local calendar", () => {
    const d = combineDateAndTime("2026-07-22", "09:00:00");
    expect(d?.getFullYear()).toBe(2026);
    expect(d?.getMonth()).toBe(6);
    expect(d?.getDate()).toBe(22);
    expect(d?.getHours()).toBe(9);
    expect(combineDateAndTime("nope", "09:00")).toBeNull();
  });
});

describe("cancellation rules", () => {
  it("allows cancelling well ahead of the appointment", () => {
    expect(canCancel(appointment(), NOW).allowed).toBe(true);
  });

  it("closes the window 1 hour before the start time", () => {
    const today = appointment({ appointmentDate: "2026-07-20", fromTime: "11:00:00" });
    // Now is 10:30; the slot starts at 11:00, so the 10:00 cutoff has passed.
    const result = canCancel(today, NOW);
    expect(result.allowed).toBe(false);
    expect(result.reason).toBe("appointment.error.cancelWindowClosed");
  });

  it("allows cancelling with just over one hour to go", () => {
    const today = appointment({ appointmentDate: "2026-07-20", fromTime: "12:00:00" });
    expect(canCancel(today, NOW).allowed).toBe(true);
  });

  it("refuses once the patient has checked in", () => {
    const result = canCancel(appointment({ status: "CHECKED_IN" }), NOW);
    expect(result.allowed).toBe(false);
    expect(result.reason).toBe("appointment.error.alreadyCheckedIn");
  });

  it("refuses an already cancelled appointment with its own message", () => {
    const result = canCancel(appointment({ status: "CANCELLED" }), NOW);
    expect(result.allowed).toBe(false);
    expect(result.reason).toBe("appointment.error.alreadyCancelled");
  });

  it("defers to the server when the time is unparseable", () => {
    expect(canCancel(appointment({ fromTime: "??" }), NOW).allowed).toBe(true);
  });

  it("applies the same rules to reschedule", () => {
    expect(canReschedule(appointment({ status: "CHECKED_IN" }), NOW).allowed).toBe(
      false,
    );
    expect(canReschedule(appointment(), NOW).allowed).toBe(true);
  });
});

describe("dashboard upcoming list", () => {
  it("keeps BOOKED and RESCHEDULED, drops the rest, and sorts by start time", () => {
    const list = upcomingAppointments(
      [
        appointment({ appointmentId: "later", appointmentDate: "2026-07-25" }),
        appointment({ appointmentId: "cancelled", status: "CANCELLED" }),
        appointment({ appointmentId: "done", status: "COMPLETED" }),
        appointment({
          appointmentId: "sooner",
          appointmentDate: "2026-07-21",
          status: "RESCHEDULED",
        }),
        appointment({ appointmentId: "past", appointmentDate: "2026-07-01" }),
      ],
      NOW,
    );
    expect(list.map((a) => a.appointmentId)).toEqual(["sooner", "later"]);
  });

  it("still counts an appointment later today as upcoming", () => {
    const list = upcomingAppointments(
      [appointment({ appointmentDate: "2026-07-20", fromTime: "16:00:00" })],
      NOW,
    );
    expect(list).toHaveLength(1);
  });

  it("keeps a same-day appointment whose slot has already passed, so the patient can still see it", () => {
    const list = upcomingAppointments(
      [appointment({ appointmentDate: "2026-07-20", fromTime: "08:00:00" })],
      NOW,
    );
    expect(list).toHaveLength(1);
  });
});

describe("date formatting helper", () => {
  it("pads month and day", () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe("2026-01-05");
    expect(toIsoDate(new Date(2026, 11, 31))).toBe("2026-12-31");
  });
});
