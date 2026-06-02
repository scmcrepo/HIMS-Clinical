package com.hms.infrastructure.sequence;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Stores formatted sequence numbers for every numbered entity.
 *
 * Join pattern (from B8 — same as legacy NumberSequence):
 *   Patient.patientNo @OneToOne @JoinColumn(name="id")
 *   Bill.billNo       @OneToOne @JoinColumn(name="id")
 *   etc.
 *
 * The entity PK (id) = the owning entity's PK.
 * typeId also = owning entity's PK (redundant but matches legacy schema).
 * value = the formatted number string e.g. "OP-00123".
 */
@Entity
@Table(name = "number_sequences")
@Getter
@Setter
@NoArgsConstructor
public class NumberSequenceEntity {

    /**
     * Same UUID as the entity this number belongs to.
     * Must be set to the owning entity's id before saving.
     */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The formatted number value e.g. "BILL-00123" */
    @Column(name = "value", nullable = false, length = 40)
    private String value;

    /** Same as id — retained for backward compatibility with legacy schema */
    @Column(name = "type_id", updatable = false)
    private UUID typeId;
}
