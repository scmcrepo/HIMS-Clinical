package com.hms.infrastructure.persistence.compliance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The result of sweeping one data store for one erasure request.
 *
 * <p>Making each store an explicit row is what turns "we deleted the patient"
 * into something auditable. An incomplete sweep shows up as a row still PENDING,
 * rather than as a store nobody remembered existed.
 *
 * <p>Not an {@code AuditableEntity}: it is a child of the request, which carries
 * the tenant and the audit stamps.
 */
@Entity
@Table(name = "erasure_targets")
@Getter
@Setter
public class ErasureTargetEntity {

    @Id
    @GeneratedValue
    @Column(name = "id")
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "target_store", nullable = false, length = 60)
    private String targetStore;

    /** PENDING | ERASED | ANONYMISED | RETAINED | FAILED */
    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome = "PENDING";

    @Column(name = "rows_affected")
    private Integer rowsAffected;

    @Column(name = "detail", length = 300)
    private String detail;

    @Column(name = "processed_at")
    private Instant processedAt;
}
