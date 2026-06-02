package com.hms.domain.charge.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Rate for a Charge under a specific BillType (CASH / CREDIT / INSURANCE)
 * and optionally a Payor.
 *
 * Multiple tariffs per charge = different rates per payor type.
 */
@Entity
@Table(name = "tariffs")
@Getter @Setter @NoArgsConstructor
public class Tariff {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @Column(name = "payor_id")
    private UUID payorId;

    @Column(name = "bill_type", nullable = false, length = 20)
    private String billType = "CASH";

    @Column(name = "rate", nullable = false)
    private long rate;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();
}
