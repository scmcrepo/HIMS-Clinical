package com.hms.infrastructure.persistence.shared;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.hms.domain.shared.model.Department;
import java.time.Instant;
import java.util.*;

@Entity @Table(name = "users") @Getter @Setter
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "username", nullable = false, unique = true, length = 20) private String username;
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
