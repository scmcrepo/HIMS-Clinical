package com.hms.domain.sales.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pharmacy_sale_payments")
@Getter
@Setter
@NoArgsConstructor
public class PharmacySalePayment extends AuditableEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private PharmacySale sale;

    @Column(name = "amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal amount;

    @Column(name = "payment_mode", nullable = false, length = 20)
    private String paymentMode;

    @Column(name = "card_type", length = 50)
    private String cardType;

    @Column(name = "card_number", length = 25)
    private String cardNumber;

    @Column(name = "bank_name", length = 100)
    private String bankName;
}
