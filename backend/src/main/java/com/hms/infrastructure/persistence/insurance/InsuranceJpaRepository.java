package com.hms.infrastructure.persistence.insurance;
import com.hms.domain.insurance.model.Insurance;
import com.hms.domain.insurance.model.InsuranceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface InsuranceJpaRepository extends JpaRepository<Insurance, UUID> {
    List<Insurance> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
    List<Insurance> findByBillIdOrderByCreatedAtDesc(UUID billId);
    Optional<Insurance> findByEncounterId(UUID encounterId);
    @Query("SELECT i FROM Insurance i WHERE i.insuranceStatus = :status ORDER BY i.createdAt DESC")
    List<Insurance> findByStatus(@Param("status") InsuranceStatus status);

    /**
     * The insurance desk's landing query (WO-020). Tenant scoping is applied by
     * the Hibernate tenantFilter on the Insurance entity, so this deliberately
     * carries no tenant predicate of its own — adding one here would silently
     * diverge from every other query on this repository.
     */
    @Query("SELECT i FROM Insurance i WHERE i.createdAt >= :start AND i.createdAt < :end "
         + "ORDER BY i.createdAt DESC")
    org.springframework.data.domain.Page<Insurance> findByCreatedAtBetween(@Param("start") Instant start, @Param("end") Instant end, org.springframework.data.domain.Pageable pageable);

    /** Lookup by the blind-index token beside the encrypted claim_no (WO-020). */
    @Query("SELECT i FROM Insurance i WHERE i.claimNoToken = :token ORDER BY i.createdAt DESC")
    List<Insurance> findByClaimNoToken(@Param("token") String token);
}
