package com.hms.domain.appointment.model;

/** Ordinals stored in DB — DO NOT reorder. */
public enum AppointmentStatus {
    BOOKED,       // 0
    RESCHEDULED,  // 1
    CHECKED_IN,   // 2
    CANCELLED     // 3
}
