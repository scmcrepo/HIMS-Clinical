package com.hms.infrastructure.persistence.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Icd10CodeJpaRepository extends JpaRepository<Icd10CodeEntity, UUID> {

    Optional<Icd10CodeEntity> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Type-ahead for the diagnosis field.
     *
     * <p>Matches a code prefix or any word in the title, because clinicians
     * search both ways — "I21" and "myocardial" should both find the same row.
     *
     * <p>Non-billable codes are excluded: they resolve for historical records
     * but a payer rejects a pre-auth carrying one, and the rejection arrives
     * days later with the patient already admitted.
     */
    @Query("""
           SELECT c FROM Icd10CodeEntity c
           WHERE c.billable = true AND c.status = 1
             AND (UPPER(c.code) LIKE UPPER(CONCAT(:q, '%'))
                  OR UPPER(c.title) LIKE UPPER(CONCAT('%', :q, '%')))
           ORDER BY
             CASE WHEN UPPER(c.code) LIKE UPPER(CONCAT(:q, '%')) THEN 0 ELSE 1 END,
             c.code
           """)
    List<Icd10CodeEntity> search(@Param("q") String query, Pageable pageable);
}
