package com.hms.infrastructure.persistence.visit;
import com.hms.domain.visit.model.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface VisitJpaRepository extends JpaRepository<Visit, UUID> {
    @Query("SELECT v FROM Visit v WHERE v.patientId = :pid ORDER BY v.visitDate DESC")
    List<Visit> findByPatientId(@Param("pid") UUID patientId);
    @Query("SELECT v FROM Visit v WHERE v.patientId = :pid AND (v.bedStatus = true OR v.billStatus = true) AND v.cancelled = false ORDER BY v.visitDate DESC")
    Optional<Visit> findActiveIPVisit(@Param("pid") UUID patientId);
    @Query("SELECT v FROM Visit v WHERE v.visitDate = :date AND v.visitType = com.hms.domain.visit.model.VisitType.OP ORDER BY v.checkedTime ASC")
    List<Visit> findByDate(@Param("date") LocalDate date);
    @Query("SELECT v FROM Visit v WHERE v.billId = :bid")
    Optional<Visit> findByBillId(@Param("bid") UUID billId);
    @Query("SELECT COUNT(v) FROM Visit v WHERE v.patientId = :pid AND v.consultantId = :cid AND v.visitDate = :date")
    long countByPatientConsultantDate(@Param("pid") UUID pid, @Param("cid") UUID cid, @Param("date") LocalDate date);
    @Query("SELECT v FROM Visit v WHERE v.visitType = :type AND v.visitDate = :date ORDER BY v.visitDate DESC")
    Page<Visit> findByTypeAndDate(@Param("type") VisitType type, @Param("date") LocalDate date, Pageable pageable);
    @Query("SELECT v FROM Visit v WHERE v.patientId = :pid AND v.dischargeDate = :dd")
    Optional<Visit> findByPatientAndDischargeDate(@Param("pid") UUID patientId, @Param("dd") LocalDate dischargeDate);
}
