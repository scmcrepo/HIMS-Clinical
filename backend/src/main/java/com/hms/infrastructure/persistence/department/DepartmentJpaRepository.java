package com.hms.infrastructure.persistence.department;
import com.hms.domain.shared.model.Department;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface DepartmentJpaRepository extends JpaRepository<Department, UUID> {
    @Query("SELECT d FROM Department d WHERE d.status = 1 ORDER BY d.name ASC") List<Department> findAllActive();
    @Query("SELECT d FROM Department d ORDER BY d.name ASC") List<Department> findAllOrdered();
    @Query("SELECT d FROM Department d WHERE d.status = 1 AND LOWER(d.name) LIKE LOWER(CONCAT('%',:q,'%'))") List<Department> searchByName(@org.springframework.data.repository.query.Param("q") String q);
    Optional<Department> findByNameIgnoreCase(String name);
}
