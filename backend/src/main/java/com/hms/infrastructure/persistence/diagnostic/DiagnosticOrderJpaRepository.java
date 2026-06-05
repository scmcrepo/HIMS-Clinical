package com.hms.infrastructure.persistence.diagnostic;
import com.hms.domain.diagnostic.model.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface DiagnosticOrderJpaRepository extends JpaRepository<DiagnosticOrder, UUID> {
    @Query("SELECT o FROM DiagnosticOrder o LEFT JOIN FETCH o.lines LEFT JOIN FETCH o.patient WHERE o.encounterId = :eid ORDER BY o.createdAt DESC")
    List<DiagnosticOrder> findByEncounterId(@Param("eid") UUID encounterId);
    
    @Query("SELECT o FROM DiagnosticOrder o LEFT JOIN FETCH o.lines LEFT JOIN FETCH o.patient WHERE o.patientId = :pid ORDER BY o.createdAt DESC")
    Page<DiagnosticOrder> findByPatientId(@Param("pid") UUID patientId, Pageable pageable);
    
    @Query("SELECT o FROM DiagnosticOrder o LEFT JOIN FETCH o.lines LEFT JOIN FETCH o.patient WHERE o.patientId = :pid AND o.testStatus = :testStatus ORDER BY o.createdAt DESC")
    List<DiagnosticOrder> findByPatientIdAndTestStatus(@Param("pid") UUID patientId, @Param("testStatus") DiagnosticTestStatus testStatus);

    @Query("SELECT o FROM DiagnosticOrder o LEFT JOIN FETCH o.lines LEFT JOIN FETCH o.patient WHERE o.patientId = :pid AND o.paymentStatus IN :paymentStatuses ORDER BY o.createdAt DESC")
    List<DiagnosticOrder> findByPatientIdAndPaymentStatusIn(@Param("pid") UUID patientId, @Param("paymentStatuses") Collection<DiagnosticPaymentStatus> paymentStatuses);

    @Query("SELECT o FROM DiagnosticOrder o LEFT JOIN FETCH o.lines LEFT JOIN FETCH o.patient WHERE o.diagnosticType = :type AND o.orderDate BETWEEN :from AND :to ORDER BY o.createdAt DESC")
    List<DiagnosticOrder> findPendingByTypeAndDateRange(@Param("type") DiagnosticType type, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT o FROM DiagnosticOrder o LEFT JOIN FETCH o.lines WHERE o.encounterId = :eid AND o.billId = :bid AND o.diagnosticType = :type AND o.paymentStatus = :paymentStatus ORDER BY o.createdAt DESC")
    List<DiagnosticOrder> findByEncounterIdAndBillIdAndDiagnosticTypeAndPaymentStatus(@Param("eid") UUID eid, @Param("bid") UUID bid, @Param("type") DiagnosticType type, @Param("paymentStatus") DiagnosticPaymentStatus paymentStatus);

    @Query("SELECT o FROM DiagnosticOrder o JOIN o.lines l WHERE l.id = :lineId")
    Optional<DiagnosticOrder> findByLineId(@Param("lineId") UUID lineId);
}
