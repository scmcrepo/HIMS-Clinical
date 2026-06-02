package com.hms.domain.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for every persisted entity in the HMS domain.
 * Audit fields (createdBy, createdAt, modifiedBy, modifiedAt) are populated
 * automatically by Spring Data JPA auditing — no AOP aspect needed.
 * status drives soft-delete: DELETED rows are hidden by repository filters.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", nullable = false)
    private EntityStatus status = EntityStatus.ACTIVE;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "modified_by")
    private UUID modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    // ── Behaviour ────────────────────────────────────────────────────────────

    @JsonIgnore
    public boolean isActive()  { return status == EntityStatus.ACTIVE;  }

    @JsonIgnore
    public boolean isDeleted() { return status == EntityStatus.DELETED; }

    public void softDelete() { this.status = EntityStatus.DELETED;  }
    public void deactivate() { this.status = EntityStatus.INACTIVE; }
    public void activate()   { this.status = EntityStatus.ACTIVE;   }
}
