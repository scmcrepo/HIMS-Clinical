package com.hms.infrastructure.persistence.salesreturn;
import com.hms.domain.sales.model.SalesReturn;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface SalesReturnJpaRepository extends JpaRepository<SalesReturn, UUID> {
    @Query("SELECT sr FROM SalesReturn sr WHERE sr.returnDate = :returnDate ORDER BY sr.createdAt DESC")
    List<SalesReturn> findByReturnDate(@Param("returnDate") LocalDate returnDate);

    List<SalesReturn> findBySaleId(UUID saleId);
    List<SalesReturn> findByPatientId(UUID patientId);
}
