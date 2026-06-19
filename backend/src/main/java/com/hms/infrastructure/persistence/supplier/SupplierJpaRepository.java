package com.hms.infrastructure.persistence.supplier;
import com.hms.domain.inventory.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
import org.springframework.data.repository.query.Param;

public interface SupplierJpaRepository extends JpaRepository<Supplier, UUID> {
    @Query("SELECT s FROM Supplier s WHERE s.status = 1 ORDER BY s.name ASC")
    List<Supplier> findAllActive();
    @Query("SELECT s FROM Supplier s ORDER BY s.status DESC, s.name ASC")
    List<Supplier> findAllOrdered();

    @Query("SELECT COUNT(s) > 0 FROM Supplier s WHERE LOWER(TRIM(s.name)) = LOWER(:name) AND s.status <> :status")
    boolean existsByNameIgnoreCaseAndStatusNot(@Param("name") String name, @Param("status") com.hms.domain.shared.model.EntityStatus status);

    @Query("SELECT COUNT(s) > 0 FROM Supplier s WHERE LOWER(TRIM(s.name)) = LOWER(:name) AND s.status <> :status AND s.id <> :id")
    boolean existsByNameIgnoreCaseAndStatusNotAndIdNot(@Param("name") String name, @Param("status") com.hms.domain.shared.model.EntityStatus status, @Param("id") UUID id);
}

