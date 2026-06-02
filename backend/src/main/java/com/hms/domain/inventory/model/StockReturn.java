package com.hms.domain.inventory.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name = "stock_returns") @Getter @Setter @NoArgsConstructor
public class StockReturn extends AuditableEntity {
    @Column(name = "from_department_id", nullable = false) private UUID fromDepartmentId;
    @Column(name = "to_department_id",   nullable = false) private UUID toDepartmentId;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "return_date", nullable = false) private java.time.LocalDate returnDate;
    @Column(name = "stock_issue_id") private UUID stockIssueId;
}
