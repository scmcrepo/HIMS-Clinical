package com.hms.domain.shared.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "account_units") @Getter @Setter @NoArgsConstructor
public class AccountUnit extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "code", length = 20) private String code;
}
