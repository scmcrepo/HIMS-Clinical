package com.hms.infrastructure.persistence.area;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "areas") @Getter @Setter @NoArgsConstructor
public class AreaEntity extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "pin_code", length = 10) private String pinCode;
}
