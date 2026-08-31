package com.hms.infrastructure.persistence.compliance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Notice lookups. Tenant scope comes from the Hibernate {@code tenantFilter}
 * enabled on the session, exactly as for every other {@code AuditableEntity}
 * repository — there is no filter-free query here.
 */
public interface ConsentNoticeJpaRepository extends JpaRepository<ConsentNoticeEntity, UUID> {

    /**
     * The notice to show for this purpose and language.
     *
     * <p>Ordered so a DRAFT placeholder is only reached when no ACTIVE notice
     * exists. Once counsel supplies real text and it is marked ACTIVE, the
     * placeholder stops being served without any code change.
     */
    @Query("SELECT n FROM ConsentNoticeEntity n "
         + "WHERE n.purpose = :purpose AND n.language = :language "
         + "AND n.noticeState IN ('ACTIVE', 'DRAFT') AND n.effectiveTo IS NULL "
         + "ORDER BY CASE WHEN n.noticeState = 'ACTIVE' THEN 0 ELSE 1 END, n.effectiveFrom DESC")
    List<ConsentNoticeEntity> findCandidates(@Param("purpose") String purpose,
                                             @Param("language") String language);

    Optional<ConsentNoticeEntity> findByPurposeAndVersionAndLanguage(
        String purpose, String version, String language);

    /** Backs the {@code hms_consent_notice_draft_remaining} gauge. */
    long countByNoticeState(String noticeState);
}
