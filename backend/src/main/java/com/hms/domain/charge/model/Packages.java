package com.hms.domain.charge.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Packages Entity — models a sub-charge or category mapping for a Package charge.
 */
@Entity
@Table(name = "packages")
@Getter @Setter @NoArgsConstructor
public class Packages {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private Charge packageId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "charge_id")
    private Charge subCharge;

    @Column(name = "charge_category")
    private UUID categoryId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "mode", nullable = false)
    private boolean mode = true; // true = include, false = exclude
}
