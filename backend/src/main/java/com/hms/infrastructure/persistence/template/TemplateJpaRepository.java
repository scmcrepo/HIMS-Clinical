package com.hms.infrastructure.persistence.template;

import com.hms.domain.shared.model.Template;
import com.hms.domain.shared.model.CommonTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface TemplateJpaRepository extends JpaRepository<Template, UUID> {

    @Query("SELECT t FROM Template t WHERE t.status = 1 AND t.templateType = :type AND LOWER(t.templateName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Template> findByNameAndType(@Param("name") String name, @Param("type") CommonTemplate type);

    @Query("SELECT t FROM Template t WHERE t.status = 1 AND t.templateType = :type")
    List<Template> findByTemplateType(@Param("type") CommonTemplate type);
}
