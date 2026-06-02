package com.hms.infrastructure.settings;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "system_settings") @Getter @Setter @NoArgsConstructor
public class SystemSettingEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "setting_type", nullable = false, length = 60) private String settingType;
    @Column(name = "setting_key",  nullable = false, length = 80) private String settingKey;
    @Column(name = "setting_value", columnDefinition = "TEXT") private String settingValue;
    @Column(name = "description", length = 255) private String description;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "modified_by") private UUID modifiedBy;
    @Column(name = "modified_at", nullable = false) private Instant modifiedAt;
}
