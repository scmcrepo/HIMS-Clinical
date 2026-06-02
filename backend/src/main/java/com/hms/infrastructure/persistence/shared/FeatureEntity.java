package com.hms.infrastructure.persistence.shared;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
@Entity @Table(name = "features") @Getter @Setter
public class FeatureEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "feature_key", nullable = false, unique = true, length = 80) private String featureKey;
    @Column(name = "description", length = 255) private String description;
    @Column(name = "module", length = 60) private String module;
}
