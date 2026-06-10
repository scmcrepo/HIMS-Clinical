package com.hms.infrastructure.persistence.scheduleddrug;

import com.hms.domain.shared.model.ScheduledDrug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ScheduledDrugJpaRepository extends JpaRepository<ScheduledDrug, UUID> {
    @Query("SELECT sd FROM ScheduledDrug sd WHERE sd.status = com.hms.domain.shared.model.EntityStatus.ACTIVE ORDER BY sd.name")
    List<ScheduledDrug> findAllActive();

    @Query("SELECT sd FROM ScheduledDrug sd WHERE sd.status != com.hms.domain.shared.model.EntityStatus.DELETED ORDER BY sd.name")
    List<ScheduledDrug> findAllExcludeDeleted();
}
