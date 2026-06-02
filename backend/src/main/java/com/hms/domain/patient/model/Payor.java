package com.hms.domain.patient.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity @Table(name = "payors") @Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Payor extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 150) private String name;
    
    @Column(name = "code", length = 30) private String code;
    
    @JsonProperty("payerType")
    @Column(name = "type", length = 40) private String type;
    
    @JsonProperty("contactPhone")
    @Column(name = "contact", length = 20) private String contact;

    @Column(name = "contact_person", length = 100) private String contactPerson;
    
    @Column(name = "email", length = 120) private String email;
    @Column(name = "address", columnDefinition = "TEXT") private String address;
}
