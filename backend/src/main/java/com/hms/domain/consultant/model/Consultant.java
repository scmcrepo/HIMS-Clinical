package com.hms.domain.consultant.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Consultant (doctor) — referenced by Appointment, Visit, Bill, Diagnostic.
 * fullName is a @Formula — not stored, computed on every query.
 */
@Entity
@Table(name = "consultants", indexes = {
    @Index(name = "idx_con_name", columnList = "first_name,last_name")
})
@Getter @Setter @NoArgsConstructor
public class Consultant extends AuditableEntity {

    @Column(name = "salutation", length = 10)
    private String salutation;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "consultant_type")
    private ConsultantType consultantType;

    @Column(name = "specialisation", length = 100)
    private String specialisation;

    @Column(name = "contact", length = 20)
    private String contact;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "qualification", length = 200)
    private String qualification;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "registration_no", length = 60)
    private String registrationNo;

    @Column(name = "department_id")
    private java.util.UUID departmentId;

    @Column(name = "photo_attachment_id")
    private java.util.UUID photoAttachmentId;

    @Column(name = "user_id")
    private java.util.UUID userId;

    /** Computed full name — not stored */
    @Transient
    public String getFullName() {
        return (salutation != null ? salutation + " " : "") + firstName + " " + lastName;
    }
}
