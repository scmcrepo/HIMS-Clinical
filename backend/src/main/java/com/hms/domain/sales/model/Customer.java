package com.hms.domain.sales.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import jakarta.persistence.*;
import lombok.*;

/**
 * Walk-in pharmacy customer.
 * PII: name, address, contactNo, email — all encrypted at rest.
 */
@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor
public class Customer extends AuditableEntity {

    @PiiField(category = PiiField.PiiCategory.NAME, description = "Customer name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @PiiField(category = PiiField.PiiCategory.ADDRESS, description = "Customer address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @PiiField(category = PiiField.PiiCategory.CONTACT, description = "Customer contact number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact_no", length = 512)
    private String contactNo;

    @PiiField(category = PiiField.PiiCategory.EMAIL, description = "Customer email")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", length = 512)
    private String email;
}
