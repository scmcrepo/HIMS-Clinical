package com.hms.domain.insurance.model;

/**
 * Courier vendors used for physical claim dockets (WO-020, Stage 6).
 *
 * <p>A hardcoded enum rather than a master table, matching the source system.
 * If a hospital adopts a sixth courier this becomes a settings-managed table —
 * one migration away — but a courier master nobody asked for is a screen nobody
 * maintains.
 */
public enum CourierVendor {
    PROFESSION_COURIER,
    FIRST_FLIGHT,
    ST_COURIER,
    DTDC,
    BLUE_DART
}
