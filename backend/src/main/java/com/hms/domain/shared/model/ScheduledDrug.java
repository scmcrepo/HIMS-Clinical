package com.hms.domain.shared.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * ScheduledDrug master — defines scheduled drug types.
 * e.g. "H", "H1"
 */
@Entity
@Table(name = "scheduled_drugs")
@Getter
@Setter
@NoArgsConstructor
public class ScheduledDrug extends AuditableEntity {
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;
}
