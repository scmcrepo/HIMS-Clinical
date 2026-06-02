package com.hms.infrastructure.persistence.department;

import com.hms.domain.shared.model.DepartmentCategories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface DepartmentCategoriesJpaRepository extends JpaRepository<DepartmentCategories, UUID> {

    @Query("SELECT dc FROM DepartmentCategories dc WHERE dc.department.id = :deptId")
    List<DepartmentCategories> findByDepartmentId(@Param("deptId") UUID deptId);

    @Modifying
    @Query("DELETE FROM DepartmentCategories dc WHERE dc.department.id = :deptId")
    void deleteByDepartmentId(@Param("deptId") UUID deptId);
}
