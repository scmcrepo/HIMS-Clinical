package com.hms.domain.shared.model;
import jakarta.persistence.*;
import lombok.*;
/**
 * Frequency master — defines dosing frequencies for prescriptions.
 * e.g. "1-0-1" (BID) = value 2, "1-1-1" (TDS) = value 3
 */
@Entity @Table(name = "frequencies") @Getter @Setter @NoArgsConstructor
public class Frequency extends AuditableEntity {
    @Column(name = "name",  nullable = false, length = 50) private String name;
    @Column(name = "value", nullable = false)              private Integer value;
}
