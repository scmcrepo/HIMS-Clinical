package com.hms.infrastructure.persistence.salesreturn;
import com.hms.domain.sales.model.SalesReturn;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface SalesReturnJpaRepository extends JpaRepository<SalesReturn, UUID> {
    @Query(value = "SELECT * FROM sales_returns WHERE CAST(return_date AS VARCHAR) = :dateStr ORDER BY created_at DESC", nativeQuery = true)
    List<SalesReturn> findByReturnDateStr(@Param("dateStr") String dateStr);

    List<SalesReturn> findBySaleId(UUID saleId);
    List<SalesReturn> findByPatientId(UUID patientId);
}
