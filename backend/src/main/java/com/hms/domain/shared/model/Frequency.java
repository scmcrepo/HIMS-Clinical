package com.hms.domain.shared.model;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

/**
 * Frequency master — defines dosing frequencies for prescriptions.
 * e.g. "1-0-1" (BID) = value 2, "1-1-1" (TDS) = value 3
 */
@Entity @Table(name = "frequencies") @Getter @Setter @NoArgsConstructor
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class Frequency extends AuditableEntity {
    @Column(name = "name",  nullable = false, length = 50) private String name;
    @Column(name = "value", nullable = false)              private Integer value;
}
