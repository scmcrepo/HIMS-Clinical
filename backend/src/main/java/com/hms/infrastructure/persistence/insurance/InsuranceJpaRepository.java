package com.hms.infrastructure.persistence.insurance;
import com.hms.domain.insurance.model.Insurance;
import com.hms.domain.insurance.model.InsuranceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface InsuranceJpaRepository extends JpaRepository<Insurance, UUID> {
    List<Insurance> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
    List<Insurance> findByBillIdOrderByCreatedAtDesc(UUID billId);
    Optional<Insurance> findByEncounterId(UUID encounterId);
    @Query("SELECT i FROM Insurance i WHERE i.insuranceStatus = :status ORDER BY i.createdAt DESC")
    List<Insurance> findByStatus(@Param("status") InsuranceStatus status);
}
