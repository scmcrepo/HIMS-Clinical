package com.hms.domain.billing.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.Convert;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line on a bill — replaces legacy BillDetail.
 *
 * status=null means ACTIVE (mirrors legacy ServiceStatus null=active).
 * total = amount - discountAmount (computed, never stored).
 */
@Entity
@Table(name = "charge_line_items")
@Getter
@Setter
@NoArgsConstructor
public class ChargeLineItem extends AuditableEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Column(name = "item_name", length = 200)
    private String itemName;

    @Column(name = "service_catalog_item_id", nullable = false)
    private UUID serviceCatalogItemId;

    @Column(name = "pricing_tier_id")
    private UUID pricingTierId;

    @Column(name = "diagnostic_order_id")
    private UUID diagnosticOrderId;

    @Column(name = "diagnostic_order_line_id")
    private UUID diagnosticOrderLineId;

    @Column(name = "pharmacy_sale_id")
    private UUID pharmacySaleId;

    @Column(name = "pharmacy_return_id")
    private UUID pharmacyReturnId;

    @Column(name = "package_group_id")
    private UUID packageGroupId;

    @Column(name = "amount", nullable = false)
    private long amount = 0L;

    @Column(name = "unit_rate", nullable = false)
    private long unitRate = 0L;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "quantitative", nullable = false)
    private boolean quantitative = false;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount = 0L;

    @Column(name = "disallowed_amount", nullable = false)
    private long disallowedAmount = 0L;

    // Nullable in DB — null means ACTIVE
    @Convert(converter = ChargeLineStatusConverter.class)
    @Column(name = "line_status")
    private ChargeLineStatus lineStatus;

    @Column(name = "bed_charge_from")
    private Instant bedChargeFrom;

    @Column(name = "bed_charge_to")
    private Instant bedChargeTo;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    // ── Behaviour ─────────────────────────────────────────────────────────────

    /** Computed total — never stored. */
    public long computeTotal() {
        return amount - discountAmount;
    }

    public boolean isActive()    { return lineStatus == null;                        }
    public boolean isCancelled() { return lineStatus == ChargeLineStatus.CANCELLED;  }
    public boolean isRefunded()  { return lineStatus == ChargeLineStatus.REFUNDED;   }

    public boolean isBedCharge() { return bedChargeFrom != null; }

    /** Cancel this line — records reason and timestamp. */
    public void cancel(String reason) {
        this.lineStatus      = ChargeLineStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt  = Instant.now();
    }

    public void markRefunded() {
        this.lineStatus      = ChargeLineStatus.REFUNDED;
        this.cancelledAt = Instant.now();
    }

    public void markModified() {
        this.lineStatus = ChargeLineStatus.MODIFIED;
    }

    // Transient audit fields — populated before saving BillDetailModified record
    @Transient private long   auditPreviousRate;
    @Transient private int    auditPreviousQty;
    @Transient private long   auditPreviousAmt;
    @Transient private String auditReason;

}
