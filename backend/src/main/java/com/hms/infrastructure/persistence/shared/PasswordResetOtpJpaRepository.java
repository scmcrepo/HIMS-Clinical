package com.hms.infrastructure.persistence.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Lookups are by {@code emailToken}, never by {@code email}.
 *
 * <p>The email column is encrypted with a non-deterministic converter (F-001), so
 * a query on it would compile, run, and silently match nothing. The old
 * {@code findFirstByEmailAndOtp...} methods were removed rather than deprecated
 * for exactly that reason — leaving them would leave a working-looking method
 * that always returns empty.
 */
public interface PasswordResetOtpJpaRepository extends JpaRepository<PasswordResetOtpEntity, UUID> {

    Optional<PasswordResetOtpEntity> findFirstByEmailTokenAndOtpOrderByCreatedAtDesc(
        String emailToken, String otp);

    Optional<PasswordResetOtpEntity> findFirstByEmailTokenAndOtpAndVerifiedTrueOrderByCreatedAtDesc(
        String emailToken, String otp);

    /**
     * Clear any outstanding OTPs for one address before issuing a new one.
     *
     * <p>Replaces a {@code findAll()} that loaded every OTP row in the database
     * into memory and filtered in Java. That was already wasteful; once the email
     * column became encrypted it would also have decrypted every row on every
     * password-reset request.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetOtpEntity o WHERE o.emailToken = :emailToken")
    int deleteByEmailToken(@Param("emailToken") String emailToken);

    /**
     * Housekeeping for expired rows.
     *
     * <p>These carry a personal email address, so leaving them indefinitely is a
     * small retention problem in a table whose rows stop being useful after five
     * minutes.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetOtpEntity o WHERE o.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") java.time.Instant cutoff);
}
