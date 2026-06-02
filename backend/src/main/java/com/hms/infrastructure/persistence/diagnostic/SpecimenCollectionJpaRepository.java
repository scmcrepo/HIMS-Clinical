package com.hms.infrastructure.persistence.diagnostic;
import com.hms.domain.diagnostic.model.SpecimenCollection;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SpecimenCollectionJpaRepository extends JpaRepository<SpecimenCollection, UUID> {
    @Query("SELECT s FROM SpecimenCollection s WHERE s.diagnosticId = :diagId ORDER BY s.createdAt DESC")
    List<SpecimenCollection> findByDiagnosticId(@Param("diagId") UUID diagnosticId);
}
