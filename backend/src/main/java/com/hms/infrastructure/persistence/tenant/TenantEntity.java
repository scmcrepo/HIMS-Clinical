package com.hms.infrastructure.persistence.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform-level tenant (one independent hospital installation on a shared deployment).
 * Intentionally NOT a subclass of AuditableEntity: tenants are platform objects, not
 * tenant-scoped business data, and must never carry a tenant_id of their own.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
public class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, length = 60)
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "contact_number", length = 50)
    private String contactNumber;

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
}
