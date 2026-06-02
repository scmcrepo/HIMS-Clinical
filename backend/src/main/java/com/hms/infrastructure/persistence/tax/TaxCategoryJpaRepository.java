package com.hms.infrastructure.persistence.tax;
import com.hms.domain.inventory.model.TaxCategory;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface TaxCategoryJpaRepository extends JpaRepository<TaxCategory, UUID> {
    @Query("SELECT c FROM TaxCategory c WHERE c.taxId = :taxId") List<TaxCategory> findByTaxId(@Param("taxId") UUID taxId);
}
