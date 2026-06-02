package com.hms.infrastructure.persistence.billing;
import com.hms.domain.billing.model.Bill;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface BillJpaRepository extends JpaRepository<Bill, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bill b WHERE b.id = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") UUID id);
    @Query("SELECT b FROM Bill b WHERE b.patientId = :pid ORDER BY b.createdAt DESC")
    List<Bill> findAllByPatientId(@Param("pid") UUID patientId);
    Optional<Bill> findByEncounterId(UUID encounterId);
    @Query("SELECT b FROM Bill b WHERE b.patientId = :pid AND b.billStatus = 0")
    List<Bill> findDraftBillsByPatientId(@Param("pid") UUID patientId);

    @Query("SELECT b FROM Bill b WHERE b.billDate BETWEEN :from AND :to ORDER BY b.createdAt DESC")
    Page<Bill> findByBillDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
        SELECT b FROM Bill b WHERE 
        (cast(:from as localdate) IS NULL OR b.billDate >= :from) AND 
        (cast(:to as localdate) IS NULL OR b.billDate <= :to) AND
        (:pids IS NULL OR b.patientId IN :pids)
        ORDER BY b.createdAt DESC
    """)
    org.springframework.data.domain.Page<Bill> searchBills(
        @Param("from") java.time.LocalDate from, 
        @Param("to") java.time.LocalDate to, 
        @Param("pids") java.util.List<java.util.UUID> pids,
        org.springframework.data.domain.Pageable pageable);
}
