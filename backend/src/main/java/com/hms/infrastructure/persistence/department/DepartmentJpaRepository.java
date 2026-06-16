package com.hms.infrastructure.persistence.department;

import com.hms.domain.shared.model.Department;
import com.hms.domain.shared.model.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface DepartmentJpaRepository extends JpaRepository<Department, UUID> {
    
    List<Department> findAllByStatusOrderByNameAsc(EntityStatus status);
    
    List<Department> findAllByOrderByNameAsc();
    
    List<Department> findAllByStatusAndNameContainingIgnoreCase(EntityStatus status, String q);

    default List<Department> findAllActive() {
        return findAllByStatusOrderByNameAsc(EntityStatus.ACTIVE);
    }

    default List<Department> findAllOrdered() {
        return findAllByOrderByNameAsc();
    }

    default List<Department> searchByName(String q) {
        return findAllByStatusAndNameContainingIgnoreCase(EntityStatus.ACTIVE, q);
    }

    Optional<Department> findByNameIgnoreCase(String name);
}
