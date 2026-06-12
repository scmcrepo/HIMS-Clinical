package com.hms.infrastructure.persistence.casesheet;

import com.hms.domain.casesheet.model.DischargeSummaryTemplate;
import com.hms.domain.shared.model.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DischargeSummaryTemplateJpaRepository extends JpaRepository<DischargeSummaryTemplate, UUID> {

    List<DischargeSummaryTemplate> findByStatusInOrderBySpecializationAscNameAsc(List<EntityStatus> statuses);

    List<DischargeSummaryTemplate> findByStatusInAndSpecializationIgnoreCaseOrderByNameAsc(
            List<EntityStatus> statuses, String specialization);

    List<DischargeSummaryTemplate> findByStatusAndSpecializationIgnoreCaseOrderByNameAsc(
            EntityStatus status, String specialization);

    @Query("""
            SELECT t FROM DischargeSummaryTemplate t
            WHERE t.status = :status
              AND UPPER(t.specialization) = UPPER(:spec)
              AND t.defaultTemplate = TRUE
            """)
    Optional<DischargeSummaryTemplate> findDefault(
            @Param("status") EntityStatus status,
            @Param("spec")   String specialization);

    boolean existsByNameIgnoreCaseAndSpecializationIgnoreCase(String name, String specialization);
}
