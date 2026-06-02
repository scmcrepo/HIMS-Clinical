package com.hms.domain.procurement.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.*;
@Entity @Table(name = "purchase_requests") @Getter @Setter @NoArgsConstructor
public class PurchaseRequest extends AuditableEntity {
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "request_date", nullable = false) private LocalDate requestDate;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "request_status", length = 30) private String requestStatus = "REQUESTED";
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
}
