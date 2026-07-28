package com.hms.domain.inventory.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

/**
 * Pharmaceutical/medical supplier.
 * PII: contact, contactPerson, email, address — encrypted.
 * GSTIN is a business identifier encrypted under TAX_ID category.
 */
@Entity
@Table(name = "suppliers")
@Getter @Setter @NoArgsConstructor
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class Supplier extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @PiiField(category = PiiField.PiiCategory.CONTACT, description = "Supplier contact number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact", length = 512)
    private String contact;

    @PiiField(category = PiiField.PiiCategory.NAME, description = "Supplier contact person name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact_person", length = 512)
    private String contactPerson;

    @PiiField(category = PiiField.PiiCategory.EMAIL, description = "Supplier email address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", length = 512)
    private String email;

    @PiiField(category = PiiField.PiiCategory.ADDRESS, description = "Supplier address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @PiiField(category = PiiField.PiiCategory.TAX_ID, description = "Supplier GST registration number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "gstin", length = 512)
    private String gstin;

    @Column(name = "gst_type", length = 50)
    private String gstType;

    public String getGstNumber() {
        return this.gstin;
    }

    public void setGstNumber(String gstNumber) {
        this.gstin = gstNumber;
    }
}
