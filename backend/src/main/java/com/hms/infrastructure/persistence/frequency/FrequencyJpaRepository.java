package com.hms.infrastructure.persistence.frequency;
import com.hms.domain.shared.model.Frequency;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface FrequencyJpaRepository extends JpaRepository<Frequency, UUID> {
    @Query("SELECT f FROM Frequency f WHERE f.status = com.hms.domain.shared.model.EntityStatus.ACTIVE ORDER BY f.name")
    List<Frequency> findAllActive();
}
