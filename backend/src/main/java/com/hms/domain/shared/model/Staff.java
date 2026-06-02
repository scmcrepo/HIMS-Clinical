package com.hms.domain.shared.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "staff") @Getter @Setter @NoArgsConstructor
public class Staff extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "staff_type", length = 30) private String staffType;
    @Column(name = "contact", length = 20) private String contact;
    @Column(name = "email", length = 120) private String email;
    @Column(name = "designation", length = 100) private String designation;
}
