package com.hms.domain.inventory.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity 
@Table(name = "suppliers") 
@Getter 
@Setter 
@NoArgsConstructor
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class Supplier extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 150) private String name;
    @Column(name = "contact", length = 20) private String contact;
    @Column(name = "contact_person", length = 100) private String contactPerson;
    @Column(name = "email", length = 120) private String email;
    @Column(name = "address", columnDefinition = "TEXT") private String address;
    @Column(name = "gstin", length = 20) private String gstin;

    public String getGstNumber() {
        return this.gstin;
    }

    public void setGstNumber(String gstNumber) {
        this.gstin = gstNumber;
    }
}

