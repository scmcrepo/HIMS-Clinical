package com.hms.infrastructure.persistence.charge;
import com.hms.domain.charge.model.Tariff;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface TariffJpaRepository extends JpaRepository<Tariff, UUID> {
    @Query("SELECT t FROM Tariff t WHERE t.charge.id = :chargeId")
    List<Tariff> findByChargeId(@Param("chargeId") UUID chargeId);
    @Query("SELECT COUNT(cli) FROM com.hms.domain.billing.model.ChargeLineItem cli WHERE cli.serviceCatalogItemId = :chargeId")
    long countBillUsage(@Param("chargeId") UUID chargeId);
}
