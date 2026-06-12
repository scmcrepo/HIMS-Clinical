package com.hms.domain.encounter.model;

import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.shared.model.AuditableEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Clinical encounter — replaces legacy Visit entity.
 *
 * IP visits are created only by BedManagementService.allocateBed().
 * OP visits are created by EncounterManagementService or AppointmentSchedulingService on check-in.
 *
 * vitalData and consultantShareMap are JSONB in PostgreSQL — fully indexed.
 */
@Entity
@Table(name = "clinical_encounters", indexes = {
    @Index(name = "idx_ce_patient",       columnList = "patient_id"),
    @Index(name = "idx_ce_started_at",    columnList = "started_at"),
    @Index(name = "idx_ce_status",        columnList = "status"),
    @Index(name = "idx_ce_provider_date", columnList = "primary_provider_id,started_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ClinicalEncounter extends AuditableEntity {

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "primary_provider_id", nullable = false)
    private UUID primaryProviderId;

    @Column(name = "appointment_id", updatable = false)
    private UUID appointmentId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "encounter_type", nullable = false, updatable = false)
    private EncounterType encounterType;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "encounter_status", nullable = false)
    private EncounterStatus encounterStatus = EncounterStatus.CHECKED_IN;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "visit_mode", nullable = false)
    private VisitMode visitMode = VisitMode.WALK_IN;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "checked_in_at")
    private LocalTime checkedInAt;

    @Column(name = "discharged_at")
    private Instant dischargedAt;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;


    @Column(name = "last_bed_id")
    private UUID lastBedId;

    @Column(name = "has_bed", nullable = false)
    private boolean hasBed = false;

    @Column(name = "has_draft_bill", nullable = false)
    private boolean hasDraftBill = false;

    @Column(name = "casesheet_recorded_at")
    private Instant casesheetRecordedAt;

    @Type(JsonBinaryType.class)
    @Column(name = "vital_data", columnDefinition = "jsonb")
    private Map<String, Object> vitalData;

    @Type(JsonBinaryType.class)
    @Column(name = "consultant_share_map", columnDefinition = "jsonb")
    private Map<String, Object> consultantShareMap;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    // ── Behaviour ─────────────────────────────────────────────────────────────

    public boolean isOutpatient() { return encounterType == EncounterType.OUTPATIENT; }
    public boolean isInpatient()  { return encounterType == EncounterType.INPATIENT;  }

    public void updateStatus(EncounterStatus newStatus) {
        this.encounterStatus = newStatus;
    }

    public Instant getDischargedAt() {
        if (this.encounterType == EncounterType.OUTPATIENT && this.dischargedAt == null && this.startedAt != null) {
            Instant autoCheckoutTime = this.startedAt
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .atTime(23, 59, 59)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
            if (Instant.now().isAfter(autoCheckoutTime)) {
                return autoCheckoutTime;
            }
        }
        return this.dischargedAt;
    }

    public void recordCasesheetTimestamp() {
        if (this.casesheetRecordedAt == null) {
            this.casesheetRecordedAt = Instant.now();
            this.encounterStatus = EncounterStatus.CASESHEET_RECORDED;
        }
    }

    public void recordDischarge(Instant dischargeTime) {
        this.dischargedAt = dischargeTime;
        this.hasBed       = false;
        this.hasDraftBill = false;
    }

    public void allocateBed(UUID bedId) {
        this.lastBedId = bedId;
        this.hasBed    = true;
    }

    public void unallocateBed() {
        this.hasBed = false;
    }

    /**
     * Adds or removes a consultant's share entry in the JSONB map.
     * If shareData is null or empty → remove the consultant's key.
     * Otherwise → add/replace the key with the share data.
     */
    public void updateConsultantShare(String consultantId, Map<String, Object> shareData) {
        if (this.consultantShareMap == null) {
            this.consultantShareMap = new java.util.HashMap<>();
        }
        if (shareData == null || shareData.isEmpty()) {
            this.consultantShareMap.remove(consultantId);
        } else {
            this.consultantShareMap.put(consultantId, shareData);
        }
    }
}
