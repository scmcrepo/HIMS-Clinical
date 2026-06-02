package com.hms.infrastructure.persistence.supplier;
import com.hms.domain.inventory.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface SupplierJpaRepository extends JpaRepository<Supplier, UUID> {
    @Query("SELECT s FROM Supplier s WHERE s.status = 1 ORDER BY s.name ASC")
    List<Supplier> findAllActive();
    @Query("SELECT s FROM Supplier s ORDER BY s.status DESC, s.name ASC")
    List<Supplier> findAllOrdered();
}
