package com.hms.infrastructure.persistence.shared;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import java.util.*;
import java.util.UUID;

import org.hibernate.annotations.Filter;

@Entity @Table(name = "roles") @Getter @Setter
@Filter(name = "branchFilter", condition = "(branch_id = :branchId OR branch_id IS NULL)")
public class RoleEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;

    // Global unique on name was dropped in V096. Uniqueness is now per-tenant
    // (uq_roles_tenant_name). Do NOT add @Column(unique=true) back.
    @Column(name = "name", nullable = false, length = 50) private String name;
    @Column(name = "description", length = 255) private String description;
    @Column(name = "status", nullable = false) private short status = 1;

    @Column(name = "tenant_id") private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private TenantEntity tenant;

    @Column(name = "branch_id") private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    private com.hms.infrastructure.persistence.tenant.BranchEntity branch;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_features",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "feature_id"))
    private Set<FeatureEntity> features = new HashSet<>();
}
