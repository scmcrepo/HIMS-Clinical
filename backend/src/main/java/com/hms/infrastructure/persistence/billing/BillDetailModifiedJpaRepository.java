package com.hms.infrastructure.persistence.billing;
import com.hms.domain.billing.model.BillDetailModified;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface BillDetailModifiedJpaRepository extends JpaRepository<BillDetailModified, UUID> {
    @Query("SELECT m FROM BillDetailModified m WHERE m.chargeLineItemId = :id ORDER BY m.modifiedAt DESC")
    List<BillDetailModified> findByChargeLineItemId(@Param("id") UUID chargeLineItemId);
}
