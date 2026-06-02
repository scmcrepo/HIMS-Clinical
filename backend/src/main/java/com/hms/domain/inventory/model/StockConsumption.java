package com.hms.domain.inventory.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name = "stock_consumptions") @Getter @Setter @NoArgsConstructor
public class StockConsumption extends AuditableEntity {
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "consumption_date", nullable = false) private java.time.LocalDate consumptionDate;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
}
