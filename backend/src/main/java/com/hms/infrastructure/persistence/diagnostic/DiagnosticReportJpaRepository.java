package com.hms.infrastructure.persistence.diagnostic;

import com.hms.domain.diagnostic.model.DiagnosticReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DiagnosticReportJpaRepository extends JpaRepository<DiagnosticReport, UUID> {

    List<DiagnosticReport> findByDiagnosticOrderLineId(UUID orderLineId);

    @Query("SELECT r FROM DiagnosticReport r WHERE r.diagnosticOrderLineId IN :lineIds ORDER BY r.labTemplateDetailId")
    List<DiagnosticReport> findByOrderLineIds(@Param("lineIds") List<UUID> lineIds);

    @Query("SELECT r FROM DiagnosticReport r WHERE r.diagnosticTemplateId = :templateId AND r.diagnosticOrderLineId = :lineId")
    Optional<DiagnosticReport> findCustomReport(@Param("lineId") UUID lineId, @Param("templateId") UUID templateId);

    @Query("SELECT r FROM DiagnosticReport r WHERE r.labTemplateDetailId = :ltdId")
    List<DiagnosticReport> findByLabTemplateDetailId(@Param("ltdId") UUID ltdId);
}
