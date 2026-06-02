package com.hms.domain.visit.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A clinical visit (encounter session).
 *
 * OP visits are created by:
 *   - PatientController.createPatient() → checkInPatient() if visitTime != null
 *   - AppointmentController.checkIn() → AppointmentService.checkinAppointment()
 *   - DiagnosticController.createDiagnostic() for new OP patients
 *   - VisitController.POST /visit directly
 *
 * IP visits are created ONLY by BedAllocationService.allocateBed().
 * POST /visit forces visitType=OP regardless of client input.
 *
 * VISITSTATUS lifecycle:
 *   CHECKEDIN(0) → CASESHEET_RECORDED(2) [auto: first diagnostic ordered]
 *   → CONSULTATION_STARTED(1) → BILLING_DONE(3)
 */
@Entity
@Table(name = "visits", indexes = {
    @Index(name = "idx_visit_patient",    columnList = "patient_id"),
    @Index(name = "idx_visit_consultant", columnList = "consultant_id"),
    @Index(name = "idx_visit_date",       columnList = "visit_date"),
    @Index(name = "idx_visit_active",     columnList = "patient_id, bed_status, bill_status")
})
@Getter @Setter @NoArgsConstructor
public class Visit extends AuditableEntity {

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "consultant_id", nullable = false, updatable = false)
    private UUID consultantId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "bill_id", updatable = false)
    private UUID billId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "visit_type", nullable = false)
    private VisitType visitType = VisitType.OP;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "checked_time")
    private LocalTime checkedTime;

    @Column(name = "discharge_date")
    private LocalDate dischargeDate;

    @Column(name = "last_bed_id")
    private UUID lastBedId;

    /** true = bed currently allocated to this IP visit */
    @Column(name = "bed_status", nullable = false)
    private boolean bedStatus = false;

    /** true = draft IP bill exists; false = bill generated or no bill */
    @Column(name = "bill_status", nullable = false)
    private boolean billStatus = false;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;


    @Enumerated(EnumType.ORDINAL)
    @Column(name = "visit_status", nullable = false)
    private VisitStatus visitStatus = VisitStatus.CHECKEDIN;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "visit_mode")
    private VisitMode visitMode = VisitMode.WALK_IN;

    @Column(name = "casesheet_created_date")
    private Instant casesheetCreatedDate;

    @Column(name = "bed_no", length = 40)
    private String bedNo;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    // ── Behaviour ─────────────────────────────────────────────────────────────

    public boolean isInpatient()  { return visitType == VisitType.IP; }
    public boolean isOutpatient() { return visitType == VisitType.OP; }
    public boolean isActive()     { return !cancelled && dischargeDate == null; }

    /** Called when first OP diagnostic order is placed */
    public void stampCasesheetDate() {
        if (this.casesheetCreatedDate == null) {
            this.casesheetCreatedDate = Instant.now();
            this.visitStatus = VisitStatus.CASESHEET_RECORDED;
        }
    }

    public void discharge(LocalDate dischargeDate, String bedNo) {
        this.dischargeDate = dischargeDate;
        this.bedStatus     = false;
        this.billStatus    = false;
        if (bedNo != null) this.bedNo = bedNo;
        this.visitStatus = VisitStatus.BILLING_DONE;
    }

    public void allocateBed(UUID bedId) {
        this.lastBedId  = bedId;
        this.bedStatus  = true;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
