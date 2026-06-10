package com.hms.domain.patient.model;

import com.hms.domain.shared.model.AuditableEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.UUID;

/**
 * Patient aggregate root.
 *
 * Age is computed on-the-fly from estimatedDateOfBirth (or dateOfBirth if set)
 * — never stored, mirrors legacy @Formula behaviour without SQL coupling.
 *
 * contactNumber validity (exactly 10 digits) gates SMS sending — same rule as legacy.
 */
@Entity
@Table(name = "patients", indexes = {
    @Index(name = "idx_patients_contact", columnList = "contact_number"),
    @Index(name = "idx_patients_status",  columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class Patient extends AuditableEntity {

    @Column(name = "salutation", length = 10)
    private String salutation;

    @Column(name = "patient_type", length = 50)
    private String patientType;

    @NotBlank
    @Size(min = 1, max = 60)
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "First name must contain only alphabets")
    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @NotBlank
    @Size(min = 1, max = 40)
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Last name must contain only alphabets")
    @Column(name = "last_name", nullable = false, length = 40)
    private String lastName;

    @NotNull
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @NotNull
    @PastOrPresent
    @Column(name = "estimated_date_of_birth", nullable = false)
    private LocalDate estimatedDateOfBirth;

    @Column(name = "contact_number", length = 15)
    private String contactNumber;

    @Column(name = "email", length = 120)
    private String email;

    @Size(max = 10, message = "Blood group must be at most 10 characters")
    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "primary_provider_id")
    private UUID primaryProviderId;


    @Column(name = "category_id", updatable = false)
    private UUID categoryId;

    @Column(name = "number_sequence_suffix", length = 20)
    private String numberSequenceSuffix;

    @Column(name = "is_clinical_trial", nullable = false)
    private boolean isClinicalTrial = false;

    @Type(JsonBinaryType.class)
    @Column(name = "pediatric_data", columnDefinition = "jsonb")
    private Map<String, Object> pediatricData;

    @Type(JsonBinaryType.class)
    @Column(name = "template_data", columnDefinition = "jsonb")
    private Map<String, Object> templateData;

    // ── Computed behaviour ───────────────────────────────────────────────────

    public String computeFullName() {
        String sal = (salutation != null && !salutation.isBlank()) ? salutation + " " : "";
        return sal + firstName + " " + lastName;
    }

    /**
     * Computes a human-readable age string.
     * Uses dateOfBirth if set, falls back to estimatedDateOfBirth.
     * Mirrors legacy @Formula:
     *   >= 365 days  → "X years Y months"
     *   < 30 days    → "X days"
     *   else         → "X months"
     */
    public String computeAge() {
        LocalDate dob = dateOfBirth != null ? dateOfBirth : estimatedDateOfBirth;
        if (dob == null) return "Unknown";
        Period period = Period.between(dob, LocalDate.now());
        int totalDays = (int) dob.until(LocalDate.now(), java.time.temporal.ChronoUnit.DAYS);
        if (totalDays < 30) {
            return totalDays + " days";
        } else if (totalDays < 365) {
            return period.getMonths() + " months";
        } else {
            return period.getYears() + " yrs";
        }
    }

    /** SMS is sent only when contact number is exactly 10 digits. */
    public boolean isContactNumberValidForSms() {
        return contactNumber != null && contactNumber.matches("\\d{10}");
    }

    public void toggleClinicalTrial() {
        this.isClinicalTrial = !this.isClinicalTrial;
    }
}
