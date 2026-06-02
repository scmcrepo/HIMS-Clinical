package com.hms.infrastructure.settings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SystemSettingJpaRepository extends JpaRepository<SystemSettingEntity, UUID> {
    List<SystemSettingEntity> findBySettingType(String settingType);
    Optional<SystemSettingEntity> findBySettingTypeAndSettingKey(String settingType, String settingKey);
}
