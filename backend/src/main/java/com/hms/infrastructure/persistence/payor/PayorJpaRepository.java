package com.hms.infrastructure.persistence.payor;
import com.hms.domain.patient.model.Payor;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PayorJpaRepository extends JpaRepository<Payor, UUID> {
    @Query("SELECT p FROM Payor p WHERE p.status = 1 ORDER BY p.name") List<Payor> findAllActive();
    @Query("SELECT p FROM Payor p ORDER BY p.status DESC, p.name ASC") List<Payor> findAllOrdered();
}
