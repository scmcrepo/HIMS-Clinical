package com.hms.domain.attachment.model;

/**
 * Determines storage subdirectory and entity linkage for uploaded files.
 * Ordinals stored in DB — DO NOT reorder.
 */
public enum AttachmentType {
    VISIT,           // 0 — clinical visit / encounter file
    CONSULTANT,      // 1 — consultant photo (one per consultant, upsert)
    INSURANCE,       // 2 — insurance document with category
    DIAGNOSTIC,      // 3 — diagnostic result attachment
    PATIENT_PICTURE, // 4 — patient photo (one per patient, upsert)
    OT,              // 5 — operation theatre file
    DONOR_PICTURE,   // 6 — blood bank donor photo
    CLINICAL_META    // 7 — metadata-only update (no file bytes)
}
