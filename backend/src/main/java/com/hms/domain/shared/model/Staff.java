package com.hms.domain.shared.model;

import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "staff") @Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "(branch_id IS NULL OR branch_id = :branchId)")
public class Staff extends AuditableEntity {

    @PiiField(category = PiiField.PiiCategory.NAME, description = "Staff full name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "staff_type", length = 30)
    private String staffType;

    @PiiField(category = PiiField.PiiCategory.CONTACT, description = "Staff contact number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact", length = 512)
    private String contact;

    /**
     * HMAC-SHA256 token of contact number — enables duplicate-check
     * without decrypting. Maintained by StaffController / BulkImportService.
     */
    @Column(name = "contact_token", length = 64)
    private String contactToken;

    @PiiField(category = PiiField.PiiCategory.EMAIL, description = "Staff email address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", length = 512)
    private String email;

    @Column(name = "designation", length = 100)
    private String designation;
}
