package com.hms.infrastructure.persistence.shared;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.*;
@Entity @Table(name = "roles") @Getter @Setter
public class RoleEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "name", nullable = false, unique = true, length = 50) private String name;
    @Column(name = "description", length = 255) private String description;
    @Column(name = "status", nullable = false) private short status = 1;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_features",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "feature_id"))
    private Set<FeatureEntity> features = new HashSet<>();
}
