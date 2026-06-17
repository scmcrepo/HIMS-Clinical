package com.hms.domain.billing.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "petty_cash")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class PettyCash {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "petty_cash_no", length = 40)
    private String sequenceNumber;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "paid_to", length = 100, nullable = false)
    private String givenTo;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "payment_mode", length = 30, nullable = false)
    private String paymentMode = "CASH";

    @Column(name = "status", length = 20, nullable = false)
    private String status = "Active";

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
