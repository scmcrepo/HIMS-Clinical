package com.hms.domain.consultant.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import jakarta.persistence.*;
import lombok.*;

/**
 * Consultant (doctor) — referenced by Appointment, Visit, Bill, Diagnostic.
 * PII fields (contact, email, address, registrationNo) are encrypted at rest.
 */
@Entity
@Table(name = "consultants", indexes = {
    @Index(name = "idx_con_name", columnList = "first_name,last_name")
})
@Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "(branch_id IS NULL OR branch_id = :branchId)")
public class Consultant extends AuditableEntity {

    @Column(name = "salutation", length = 10)
    private String salutation;

    @PiiField(category = PiiField.PiiCategory.NAME, description = "Consultant first name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "first_name", nullable = false, length = 512)
    private String firstName;

    @PiiField(category = PiiField.PiiCategory.NAME, description = "Consultant last name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "last_name", nullable = false, length = 512)
    private String lastName;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "consultant_type")
    private ConsultantType consultantType;

    @Column(name = "specialisation", length = 100)
    private String specialisation;

    @PiiField(category = PiiField.PiiCategory.CONTACT, description = "Consultant contact number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact", length = 512)
    private String contact;

    /**
     * HMAC-SHA256 token of contact number — enables duplicate-check
     * and phone-lookup without decrypting. Maintained by ConsultantService.
     */
    @Column(name = "contact_number_token", length = 64)
    private String contactNumberToken;

    @PiiField(category = PiiField.PiiCategory.EMAIL, description = "Consultant email address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", length = 512)
    private String email;

    @Column(name = "qualification", length = 200)
    private String qualification;

    @PiiField(category = PiiField.PiiCategory.ADDRESS, description = "Consultant address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address", length = 1024)
    private String address;

    @PiiField(category = PiiField.PiiCategory.PROFESSIONAL_ID, description = "Medical council registration number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "registration_no", length = 512)
    private String registrationNo;

    @Column(name = "department_id")
    private java.util.UUID departmentId;

    @Column(name = "photo_attachment_id")
    private java.util.UUID photoAttachmentId;

    @Column(name = "user_id")
    private java.util.UUID userId;

    @Transient
    public String getFullName() {
        return (salutation != null ? salutation + " " : "") + firstName + " " + lastName;
    }
}
