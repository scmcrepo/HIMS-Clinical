package com.hms.domain.billing.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.Filter;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.exception.CrossTenantAccessException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "petty_cash")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class PettyCash {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

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

    @PrePersist
    void stampScope() {
        if (tenantId == null) {
            tenantId = TenantContext.get();
        }
        if (branchId == null) {
            branchId = BranchContext.get();
        }
    }

    @PostLoad
    void assertScopeMatches() {
        UUID activeTenant = TenantContext.get();
        if (activeTenant != null && tenantId != null && !activeTenant.equals(tenantId)) {
            throw new CrossTenantAccessException(
                "Attempted cross-tenant access to entity " + getClass().getSimpleName() + " " + id);
        }
        UUID activeBranch = BranchContext.get();
        if (activeBranch != null && branchId != null && !activeBranch.equals(branchId)) {
            throw new CrossTenantAccessException(
                "Attempted cross-branch access to entity " + getClass().getSimpleName() + " " + id);
        }
    }
}
