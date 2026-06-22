package com.hms.domain.patient.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import jakarta.persistence.*;
import lombok.*;

/**
 * Referring doctor/entity.
 * PII: name, firstName, lastName, contact, address — encrypted at rest.
 */
@Entity @Table(name = "referrals") @Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "(branch_id IS NULL OR branch_id = :branchId)")
public class Referral extends AuditableEntity {

    @PiiField(category = PiiField.PiiCategory.NAME, description = "Referral entity name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "type", length = 50)
    private String type;

    @PiiField(category = PiiField.PiiCategory.CONTACT, description = "Referral contact number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact", length = 512)
    private String contact;

    @Column(name = "salutation", length = 10)
    private String salutation;

    @PiiField(category = PiiField.PiiCategory.NAME, description = "Referral first name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "first_name", length = 512)
    private String firstName;

    @PiiField(category = PiiField.PiiCategory.NAME, description = "Referral last name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "last_name", length = 512)
    private String lastName;

    @PiiField(category = PiiField.PiiCategory.ADDRESS, description = "Referral address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address", length = 1024)
    private String address;
}
