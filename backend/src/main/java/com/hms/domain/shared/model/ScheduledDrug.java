package com.hms.domain.shared.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

/**
 * ScheduledDrug master — defines scheduled drug types.
 * e.g. "H", "H1"
 */
@Entity
@Table(name = "scheduled_drugs")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "1=1")
public class ScheduledDrug extends AuditableEntity {
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;
}
