package com.hms.infrastructure.persistence.area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface AreaJpaRepository extends JpaRepository<AreaEntity, UUID> {
    @Query("SELECT a FROM AreaEntity a WHERE a.status = 1 ORDER BY a.name ASC")
    List<AreaEntity> findAllActive();
}
