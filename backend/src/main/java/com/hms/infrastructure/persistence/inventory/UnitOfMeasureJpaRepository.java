package com.hms.infrastructure.persistence.inventory;

import com.hms.domain.inventory.model.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface UnitOfMeasureJpaRepository extends JpaRepository<UnitOfMeasure, UUID> {
    @Query("SELECT u FROM UnitOfMeasure u WHERE u.status = 1 ORDER BY u.name ASC")
    List<UnitOfMeasure> findAllActive();
}
