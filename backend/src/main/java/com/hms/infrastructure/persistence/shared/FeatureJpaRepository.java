package com.hms.infrastructure.persistence.shared;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface FeatureJpaRepository extends JpaRepository<FeatureEntity, UUID> {
    Optional<FeatureEntity> findByFeatureKey(String featureKey);
    @Query("SELECT f FROM FeatureEntity f WHERE f.module = :module")
    List<FeatureEntity> findByModule(@Param("module") String module);
}
