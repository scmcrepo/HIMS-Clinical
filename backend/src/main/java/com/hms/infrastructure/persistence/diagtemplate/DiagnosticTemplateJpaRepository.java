package com.hms.infrastructure.persistence.diagtemplate;

import com.hms.domain.diagnostic.model.DiagnosticTemplate;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DiagnosticTemplateJpaRepository extends JpaRepository<DiagnosticTemplate, UUID> {

    @Query("SELECT t FROM DiagnosticTemplate t LEFT JOIN FETCH t.department LEFT JOIN FETCH t.labTemplateDetails WHERE t.status = 1 ORDER BY t.name")
    List<DiagnosticTemplate> findAllActive();

    @Query("SELECT t FROM DiagnosticTemplate t LEFT JOIN FETCH t.department LEFT JOIN FETCH t.labTemplateDetails WHERE t.chargeId = :chargeId AND t.status = 1")
    List<DiagnosticTemplate> findByChargeId(@Param("chargeId") UUID chargeId);

    @Query("SELECT t FROM DiagnosticTemplate t LEFT JOIN FETCH t.department LEFT JOIN FETCH t.labTemplateDetails WHERE t.department.id = :deptId AND t.status = 1 ORDER BY t.orderNumber")
    List<DiagnosticTemplate> findByDepartmentId(@Param("deptId") UUID deptId);
}
