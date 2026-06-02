package com.hms.infrastructure.persistence.purchaserequest;
import com.hms.domain.procurement.model.PurchaseRequest;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PurchaseRequestJpaRepository extends JpaRepository<PurchaseRequest, UUID> {
    @Query("SELECT r FROM PurchaseRequest r WHERE r.status = 1 ORDER BY r.requestDate DESC")
    List<PurchaseRequest> findAllActive();
}
