package com.hms.domain.inventory.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name = "stock_indents") @Getter @Setter @NoArgsConstructor
public class StockIndent extends AuditableEntity {
    @Column(name = "indent_from_dept_id", nullable = false) private UUID indentFromDeptId;
    @Column(name = "indent_to_dept_id",   nullable = false) private UUID indentToDeptId;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "indent_date", nullable = false) private java.time.LocalDate indentDate;
    @Column(name = "indent_status", length = 30) private String indentStatus = "INDENT";
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
}
