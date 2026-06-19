package com.hms.domain.inventory.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name = "stock_issues") @Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class StockIssue extends AuditableEntity {
    @Column(name = "from_department_id", nullable = false) private UUID fromDepartmentId;
    @Column(name = "to_department_id",   nullable = false) private UUID toDepartmentId;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "issue_date", nullable = false) private java.time.LocalDate issueDate;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
}
