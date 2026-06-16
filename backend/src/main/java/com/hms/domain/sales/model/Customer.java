package com.hms.domain.sales.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor
public class Customer extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 150) private String name;
    @Column(name = "address", columnDefinition = "TEXT") private String address;
    @Column(name = "contact_no", length = 20) private String contactNo;
    @Column(name = "email", length = 120) private String email;
}
