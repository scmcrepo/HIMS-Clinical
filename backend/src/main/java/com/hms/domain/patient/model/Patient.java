package com.hms.domain.patient.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
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
 * PII fields (firstName, lastName, contactNumber, email, address, bloodGroup)
 * are encrypted at rest using AES-256-GCM via EncryptedStringConverter.
 * Column lengths are expanded to 512 to accommodate the Base64-encoded ciphertext.
 *
 * NOTE: After adding encryption, run Flyway migration V144__expand_patient_pii_columns.sql
 * to widen the columns, then run PiiMigrationRunner to encrypt existing plaintext rows.
 */
@Entity
@Table(name = "patients", indexes = {
    @Index(name = "idx_patients_status", columnList = "status")
    // NOTE: idx_patients_contact index removed — encrypted contact_number cannot be
    // queried by equality. Use patient ID or patient number for lookups instead.
})
@Getter
@Setter
@NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class Patient extends AuditableEntity {

    @Column(name = "salutation", length = 10)
    private String salutation;

    @Column(name = "patient_type", length = 50)
    private String patientType;

    @NotBlank
    @Size(min = 1, max = 60)
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "First name must contain only alphabets")
    @PiiField(category = PiiField.PiiCategory.NAME, description = "Patient first name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "first_name", nullable = false, length = 512)
    private String firstName;

    @NotBlank
    @Size(min = 1, max = 40)
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Last name must contain only alphabets")
    @PiiField(category = PiiField.PiiCategory.NAME, description = "Patient last name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "last_name", nullable = false, length = 512)
    private String lastName;

    @NotNull
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    // dateOfBirth stored as LocalDate (not encrypted); consider pseudonymisation
    // by storing only year+month if full birth date is not clinically required.
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @NotNull
    @PastOrPresent
    @Column(name = "estimated_date_of_birth", nullable = false)
    private LocalDate estimatedDateOfBirth;

    @PiiField(category = PiiField.PiiCategory.CONTACT, description = "Patient mobile / contact number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact_number", length = 512)
    private String contactNumber;

    /**
     * HMAC-SHA256 token of the normalised contact number.
     * Used for exact-match DB lookup without decrypting.
     * Set by PatientManagementService whenever contactNumber changes.
     * Indexed for fast lookup. NOT to be returned in API responses.
     */
    @Column(name = "contact_number_token", length = 64)
    private String contactNumberToken;

    @PiiField(category = PiiField.PiiCategory.EMAIL, description = "Patient email address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", length = 512)
    private String email;

    @Size(max = 10, message = "Blood group must be at most 10 characters")
    @PiiField(category = PiiField.PiiCategory.HEALTH_IDENTIFIER, description = "Patient blood group")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "blood_group", length = 512)
    private String bloodGroup;

    @PiiField(category = PiiField.PiiCategory.ADDRESS, description = "Patient home address")
    @Convert(converter = EncryptedStringConverter.class)
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
