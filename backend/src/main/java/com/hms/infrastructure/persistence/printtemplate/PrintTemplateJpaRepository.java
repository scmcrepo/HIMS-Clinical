package com.hms.infrastructure.persistence.printtemplate;

import com.hms.domain.shared.model.PrintTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrintTemplateJpaRepository extends JpaRepository<PrintTemplate, UUID> {

    @Query("SELECT p FROM PrintTemplate p WHERE p.status = 1 ORDER BY p.name")
    List<PrintTemplate> findAllActive();

    @Query("SELECT p FROM PrintTemplate p WHERE p.status = 1 AND p.documentType = :docType AND p.isDefault = true ORDER BY p.name")
    Optional<PrintTemplate> findDefaultByDocumentType(@Param("docType") String docType);

    @Query("SELECT p FROM PrintTemplate p WHERE p.status = 1 AND p.documentType = :docType ORDER BY p.isDefault DESC, p.name")
    List<PrintTemplate> findByDocumentType(@Param("docType") String docType);

    List<PrintTemplate> findByNameIgnoreCaseAndDocumentTypeIgnoreCase(String name, String documentType);
}
