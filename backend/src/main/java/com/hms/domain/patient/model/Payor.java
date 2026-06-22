package com.hms.domain.patient.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.annotations.Filter;

/**
 * Insurance payor / corporate entity.
 * PII: contact, contactPerson, email, address — encrypted at rest.
 */
@Entity @Table(name = "payors") @Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class Payor extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", length = 30)
    private String code;

    @JsonProperty("payerType")
    @Column(name = "type", length = 40)
    private String type;

    @JsonProperty("contactPhone")
    @PiiField(category = PiiField.PiiCategory.CONTACT, description = "Payor contact phone")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact", length = 512)
    private String contact;

    @PiiField(category = PiiField.PiiCategory.NAME, description = "Payor contact person name")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "contact_person", length = 512)
    private String contactPerson;

    @PiiField(category = PiiField.PiiCategory.EMAIL, description = "Payor email address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", length = 512)
    private String email;

    @PiiField(category = PiiField.PiiCategory.ADDRESS, description = "Payor address")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;
}
