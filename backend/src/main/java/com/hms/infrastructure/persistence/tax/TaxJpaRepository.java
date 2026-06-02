package com.hms.infrastructure.persistence.tax;
import com.hms.domain.inventory.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface TaxJpaRepository extends JpaRepository<Tax, UUID> {
    @Query("SELECT t FROM Tax t WHERE t.status = 1 ORDER BY t.name") List<Tax> findAllActive();
}
