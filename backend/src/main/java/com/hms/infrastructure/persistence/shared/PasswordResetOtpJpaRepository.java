package com.hms.infrastructure.persistence.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetOtpJpaRepository extends JpaRepository<PasswordResetOtpEntity, UUID> {
    Optional<PasswordResetOtpEntity> findFirstByEmailAndOtpOrderByCreatedAtDesc(String email, String otp);
    Optional<PasswordResetOtpEntity> findFirstByEmailAndOtpAndVerifiedTrueOrderByCreatedAtDesc(String email, String otp);
}
