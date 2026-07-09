package com.hms.infrastructure.persistence.diagtemplate;

import com.hms.domain.diagnostic.model.DiagnosticTemplate;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DiagnosticTemplateJpaRepository extends JpaRepository<DiagnosticTemplate, UUID> {

    @Query("SELECT t FROM DiagnosticTemplate t LEFT JOIN FETCH t.department LEFT JOIN FETCH t.labTemplateDetails WHERE t.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND (t.chargeId IS NULL OR t.chargeId IN (SELECT c.id FROM com.hms.domain.catalog.model.ServiceCatalogItem c WHERE c.status = com.hms.domain.shared.model.EntityStatus.ACTIVE)) ORDER BY t.name")
    List<DiagnosticTemplate> findAllActive();

    @Query("SELECT t FROM DiagnosticTemplate t WHERE t.tenantId = :tenantId AND t.branchId = :branchId AND UPPER(TRIM(t.name)) = UPPER(TRIM(:name))")
    List<DiagnosticTemplate> findByTenantIdAndBranchIdAndNameIgnoreCase(@Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId, @Param("name") String name);

    @Query("SELECT t FROM DiagnosticTemplate t LEFT JOIN FETCH t.department LEFT JOIN FETCH t.labTemplateDetails WHERE t.status != com.hms.domain.shared.model.EntityStatus.DELETED ORDER BY t.name")
    List<DiagnosticTemplate> findAllNonDeleted();

    @Query("SELECT t FROM DiagnosticTemplate t LEFT JOIN FETCH t.department LEFT JOIN FETCH t.labTemplateDetails WHERE t.chargeId = :chargeId AND t.status = com.hms.domain.shared.model.EntityStatus.ACTIVE")
    List<DiagnosticTemplate> findByChargeId(@Param("chargeId") UUID chargeId);

    @Query("SELECT t FROM DiagnosticTemplate t LEFT JOIN FETCH t.department LEFT JOIN FETCH t.labTemplateDetails WHERE t.chargeId = :chargeId AND t.status != com.hms.domain.shared.model.EntityStatus.DELETED")
    List<DiagnosticTemplate> findByChargeIdAll(@Param("chargeId") UUID chargeId);

    @Query("SELECT t FROM DiagnosticTemplate t LEFT JOIN FETCH t.department LEFT JOIN FETCH t.labTemplateDetails WHERE t.department.id = :deptId AND t.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND (t.chargeId IS NULL OR t.chargeId IN (SELECT c.id FROM com.hms.domain.catalog.model.ServiceCatalogItem c WHERE c.status = com.hms.domain.shared.model.EntityStatus.ACTIVE)) ORDER BY t.orderNumber")
    List<DiagnosticTemplate> findByDepartmentId(@Param("deptId") UUID deptId);
}
