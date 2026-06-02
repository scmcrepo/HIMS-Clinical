package com.hms.infrastructure.persistence.role;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {
    @Query("SELECT r FROM RoleEntity r LEFT JOIN FETCH r.features WHERE r.status = 1 ORDER BY r.name ASC")
    List<RoleEntity> findAllActiveWithFeatures();
    Optional<RoleEntity> findByName(String name);
}
