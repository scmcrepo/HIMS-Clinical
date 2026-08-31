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
/**
 * Reviewed under WO-028 and left unscoped. Charge packages are reference data
 * shared across the deployment rather than tenant-owned. If a hospital needs
 * its own package catalogue this becomes a real finding, so revisit before
 * adding per-tenant pricing. Treated as platform-level.
 */
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
