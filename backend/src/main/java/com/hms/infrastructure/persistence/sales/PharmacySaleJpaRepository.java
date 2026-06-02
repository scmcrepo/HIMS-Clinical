package com.hms.infrastructure.persistence.sales;
import com.hms.domain.sales.model.PharmacySale;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface PharmacySaleJpaRepository extends JpaRepository<PharmacySale, UUID> {
    @Query("SELECT s FROM PharmacySale s WHERE s.patientId = :pid ORDER BY s.saleDate DESC")
    List<PharmacySale> findByPatientId(@Param("pid") UUID patientId);
    @Query("SELECT s FROM PharmacySale s WHERE s.saleDate = :date ORDER BY s.createdAt DESC")
    List<PharmacySale> findBySaleDate(@Param("date") LocalDate date);
    @Query("SELECT s FROM PharmacySale s WHERE s.status = 1 AND s.saleStatus = 0 AND s.departmentId = :deptId")
    List<PharmacySale> findDraftByDepartment(@Param("deptId") UUID departmentId);
    @Query("SELECT s FROM PharmacySale s WHERE s.encounterId = :eid ORDER BY s.saleDate DESC")
    List<PharmacySale> findByEncounterId(@Param("eid") UUID encounterId);

    @Query("SELECT s.sequenceNumber FROM PharmacySale s WHERE s.sequenceNumber LIKE 'DF-%'")
    List<String> findDraftSequenceNumbers();
}
