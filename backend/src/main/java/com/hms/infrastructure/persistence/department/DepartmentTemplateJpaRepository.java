package com.hms.infrastructure.persistence.department;

import com.hms.domain.shared.model.DepartmentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface DepartmentTemplateJpaRepository extends JpaRepository<DepartmentTemplate, UUID> {

    @Query("SELECT dt FROM DepartmentTemplate dt WHERE dt.department.id = :deptId")
    List<DepartmentTemplate> findByDepartmentId(@Param("deptId") UUID deptId);

    @Query("SELECT dt FROM DepartmentTemplate dt WHERE dt.template.id = :templateId AND dt.department.id = :deptId")
    Optional<DepartmentTemplate> findByTemplateIdAndDepartmentId(@Param("templateId") UUID templateId, @Param("deptId") UUID deptId);

    @Modifying
    @Query("DELETE FROM DepartmentTemplate dt WHERE dt.department.id = :deptId")
    void deleteByDepartmentId(@Param("deptId") UUID deptId);
}
