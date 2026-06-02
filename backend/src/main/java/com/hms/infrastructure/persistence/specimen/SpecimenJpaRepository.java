package com.hms.infrastructure.persistence.specimen;
import com.hms.domain.diagnostic.model.Specimen;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SpecimenJpaRepository extends JpaRepository<Specimen, UUID> {
    @Query("SELECT s FROM Specimen s WHERE s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE ORDER BY s.name")
    List<Specimen> findAllActive();

    @Query("SELECT s FROM Specimen s WHERE s.status != com.hms.domain.shared.model.EntityStatus.DELETED ORDER BY s.name")
    List<Specimen> findAllNonDeleted();

    @Query("SELECT s FROM Specimen s WHERE LOWER(s.name) = LOWER(:name) AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE")
    List<Specimen> findByNameIgnoreCase(@Param("name") String name);
}
