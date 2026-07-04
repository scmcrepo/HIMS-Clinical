package com.hms.domain.appointment.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.domain.encounter.model.VisitMode;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * An appointment booked for a patient with a clinical provider.
 *
 * Temporary fields (tempPatientName, tempPatientPhone) for walk-in/unregistered patients
 * are PII and must be encrypted.
 */
@Entity
@Table(name = "appointments", indexes = {
    @Index(name = "idx_apt_date_provider", columnList = "appointment_date,primary_provider_id"),
    @Index(name = "idx_apt_patient",       columnList = "patient_id"),
    @Index(name = "idx_apt_status",        columnList = "status")
})
@Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class Appointment extends AuditableEntity {

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "slot_id")
    private UUID slotId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "appointment_status", nullable = false)
    private AppointmentStatus appointmentStatus = AppointmentStatus.BOOKED;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "visit_mode", nullable = false)
    private VisitMode visitMode = VisitMode.APPOINTMENT;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Temporary fields for non-registered patients
    @Column(name = "temp_patient_salutation")
    private String tempPatientSalutation;

    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Patient name must contain only alphabets")
    @PiiField(category = PiiField.PiiCategory.NAME, description = "Unregistered patient name (temp appointment)")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "temp_patient_name", length = 512)
    private String tempPatientName;

    @Column(name = "temp_patient_gender")
    private String tempPatientGender;

    @PiiField(category = PiiField.PiiCategory.CONTACT, description = "Unregistered patient phone (temp appointment)")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "temp_patient_phone", length = 512)
    private String tempPatientPhone;

    @Column(name = "temp_patient_age")
    private Integer tempPatientAge;

    // ── Behaviour ────────────────────────────────────────────────────────────

    public boolean isBooked()    { return appointmentStatus == AppointmentStatus.BOOKED;     }
    public boolean isCancelled() { return appointmentStatus == AppointmentStatus.CANCELLED;  }
    public boolean isCheckedIn() { return appointmentStatus == AppointmentStatus.CHECKED_IN; }

    public void reschedule() {
        if (isCancelled()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Cannot reschedule a cancelled appointment");
        }
        if (isCheckedIn()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Cannot reschedule — patient has already checked in");
        }
        this.appointmentStatus = AppointmentStatus.RESCHEDULED;
    }

    public void checkIn() {
        if (isCancelled()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Cannot check in — appointment is cancelled");
        }
        if (isCheckedIn()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Patient is already checked in for this appointment");
        }
        this.appointmentStatus = AppointmentStatus.CHECKED_IN;
    }

    public void cancel() {
        if (isCheckedIn()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Cannot cancel — patient is already checked in");
        }
        this.appointmentStatus = AppointmentStatus.CANCELLED;
    }
}
