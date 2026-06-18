package com.hms.infrastructure.persistence.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A branch (physical location/clinic) within a tenant hospital. Each tenant has at least one
 * branch, created automatically with the tenant. Business data is isolated per-branch.
 *
 * <p>Like {@link TenantEntity}, this is a platform/structural object, not tenant-scoped business
 * data, so it does NOT extend AuditableEntity. It does, however, carry its owning {@code tenant_id}
 * directly so branches can be listed and validated per tenant.
 */
@Entity
@Table(name = "branches")
@Getter
@Setter
public class BranchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "contact_number", length = 50)
    private String contactNumber;

    /** Exactly one branch per tenant should be the default (the auto-created one). */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    /** 1 = active, 0 = inactive. */
    @Column(name = "status", nullable = false)
    private short status = 1;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        modifiedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        modifiedAt = Instant.now();
    }

    public boolean isActive() {
        return status == 1;
    }

    // Explicit accessors for the boolean flag — pinned so the names are stable regardless of
    // Lombok's is-prefix handling, and so all call sites (isDefault()/setDefault()) resolve.
    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean value) {
        this.isDefault = value;
    }
}
