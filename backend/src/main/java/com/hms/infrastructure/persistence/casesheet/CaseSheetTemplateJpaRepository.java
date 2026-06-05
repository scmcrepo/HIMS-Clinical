package com.hms.infrastructure.persistence.casesheet;

import com.hms.domain.casesheet.model.CaseSheetTemplate;
import com.hms.domain.casesheet.model.CaseSheetVisitType;
import com.hms.domain.shared.model.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseSheetTemplateJpaRepository extends JpaRepository<CaseSheetTemplate, UUID> {

    List<CaseSheetTemplate> findByStatusOrderBySpecializationAscNameAsc(EntityStatus status);

    List<CaseSheetTemplate> findByStatusAndSpecializationIgnoreCaseOrderByNameAsc(
            EntityStatus status, String specialization);

    List<CaseSheetTemplate> findByStatusAndVisitTypeOrderByNameAsc(
            EntityStatus status, CaseSheetVisitType visitType);

    List<CaseSheetTemplate> findByStatusAndSpecializationIgnoreCaseAndVisitTypeOrderByNameAsc(
            EntityStatus status, String specialization, CaseSheetVisitType visitType);

    List<CaseSheetTemplate> findByStatusInOrderBySpecializationAscNameAsc(List<EntityStatus> statuses);

    List<CaseSheetTemplate> findByStatusInAndSpecializationIgnoreCaseOrderByNameAsc(
            List<EntityStatus> statuses, String specialization);

    List<CaseSheetTemplate> findByStatusInAndVisitTypeOrderByNameAsc(
            List<EntityStatus> statuses, CaseSheetVisitType visitType);

    List<CaseSheetTemplate> findByStatusInAndSpecializationIgnoreCaseAndVisitTypeOrderByNameAsc(
            List<EntityStatus> statuses, String specialization, CaseSheetVisitType visitType);

    /** Default template for a specialization+visitType; exact match wins over BOTH */
    @Query("""
            SELECT t FROM CaseSheetTemplate t
            WHERE t.status = :status
              AND UPPER(t.specialization) = UPPER(:spec)
              AND (t.visitType = :vt OR t.visitType = com.hms.domain.casesheet.model.CaseSheetVisitType.BOTH)
              AND t.defaultTemplate = TRUE
            ORDER BY CASE t.visitType
                       WHEN :vt THEN 0 ELSE 1 END
            """)
    Optional<CaseSheetTemplate> findDefault(
            @Param("status") EntityStatus status,
            @Param("spec")   String specialization,
            @Param("vt")     CaseSheetVisitType visitType);

    boolean existsByNameIgnoreCaseAndSpecializationIgnoreCaseAndVisitType(
            String name, String specialization, CaseSheetVisitType visitType);
}
