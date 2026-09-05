package com.hms.infrastructure.persistence.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Not tenant-filtered, and cannot be: every query here runs during login, before
 * authentication establishes a TenantContext. Lookups are by user id, which is
 * already tenant-specific.
 */
public interface UserMfaCredentialJpaRepository extends JpaRepository<UserMfaCredentialEntity, UUID> {

    Optional<UserMfaCredentialEntity> findByUserId(UUID userId);

    /**
     * Which of these users have a CONFIRMED credential.
     *
     * <p>Confirmed only. An abandoned enrolment is not a second factor, and
     * counting it would overstate coverage in exactly the gauge an administrator
     * uses to decide whether it is safe to switch the mode to REQUIRED.
     */
    @Query("SELECT c.userId FROM UserMfaCredentialEntity c "
         + "WHERE c.userId IN :userIds AND c.confirmedAt IS NOT NULL")
    List<UUID> confirmedUserIdsIn(@Param("userIds") List<UUID> userIds);
}
