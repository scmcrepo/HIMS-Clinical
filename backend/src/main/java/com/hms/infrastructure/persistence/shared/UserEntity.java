package com.hms.infrastructure.persistence.shared;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.hms.domain.shared.model.Department;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import java.time.Instant;
import java.util.*;

@Entity @Table(name = "users") @Getter @Setter
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;

    // Username is globally unique across the whole platform (uq_users_username). This is what
    // lets login resolve a user from username alone — the tenant/branch are then read from the
    // user row, so the user never picks a hospital at login.
    @Column(name = "username", nullable = false, length = 25) private String username;
    @Column(name = "password_hash", nullable = false, length = 72) private String passwordHash;
    @Column(name = "first_name", nullable = false, length = 50) private String firstName;
    @Column(name = "last_name", nullable = false, length = 30) private String lastName;
    @Column(name = "email", length = 120) private String email;
    @Column(name = "phone_no", length = 20) private String phoneNo;
    @Column(name = "salutation", length = 10) private String salutation;
    @Column(name = "status", nullable = false) private short status = 1;
    @Column(name = "account_locked", nullable = false) private boolean accountLocked = false;
    @Column(name = "department_visibility", nullable = false) private short departmentVisibility = 0;
    @Column(name = "speech_language", nullable = false, length = 10) private String speechLanguage = "en-IN";
    @Column(name = "text_auto_suggest", nullable = false) private boolean textAutoSuggest = true;
    @Column(name = "show_casesheet", nullable = false) private boolean showCasesheet = false;
    @Type(JsonBinaryType.class)
    @Column(name = "user_rights", columnDefinition = "jsonb") private Map<String, Object> userRights;

    /** NULLABLE: null => platform-level SUPERADMIN that belongs to no single tenant. */
    @Column(name = "tenant_id") private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private TenantEntity tenant;

    /**
     * NULLABLE: the branch this user is pinned to. Null for SUPERADMIN (no tenant) and for
     * HOSPITAL_ADMIN (tenant-wide, sees all branches). Branch staff have a concrete branch.
     */
    @Column(name = "branch_id") private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    private com.hms.infrastructure.persistence.tenant.BranchEntity branch;

    @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    @Column(name = "modified_at", nullable = false) private Instant modifiedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_departments",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "department_id"))
    private Set<Department> departments = new HashSet<>();

    public Set<String> collectAllFeatureKeys() {
        Set<String> keys = new HashSet<>();
        roles.forEach(r -> r.getFeatures().forEach(f -> keys.add(f.getFeatureKey())));
        return keys;
    }

    public Set<String> collectAllRoleNames() {
        Set<String> names = new HashSet<>();
        roles.forEach(r -> names.add(r.getName()));
        return names;
    }
}
