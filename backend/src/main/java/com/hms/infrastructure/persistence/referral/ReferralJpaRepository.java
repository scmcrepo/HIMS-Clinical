package com.hms.infrastructure.persistence.referral;
import com.hms.domain.patient.model.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface ReferralJpaRepository extends JpaRepository<Referral, UUID> {
    @Query("SELECT r FROM Referral r WHERE r.status = 1 ORDER BY r.name ASC")
    List<Referral> findAllActive();
}
