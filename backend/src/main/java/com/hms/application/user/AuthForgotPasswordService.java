package com.hms.application.user;

import com.hms.application.smtp.SmtpConfigService;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.shared.*;
import com.hms.security.encryption.PiiSearchTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthForgotPasswordService {

    private final UserJpaRepository userRepo;
    private final PasswordResetOtpJpaRepository otpRepo;
    private final SmtpConfigService smtpService;
    private final PiiSearchTokenService searchTokenService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void requestForgotPasswordOtp(String email) {
        String trimmedEmail = email.trim().toLowerCase();
        String emailToken = searchTokenService.token(trimmedEmail);

        UserEntity user = userRepo.findByEmailToken(emailToken)
            .orElseThrow(() -> new ResourceNotFoundException("User with email '" + email + "' not found"));

        // Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(1000000));

        // Delete any existing OTPs for this email to clean up
        otpRepo.findAll().stream()
            .filter(o -> o.getEmail().equalsIgnoreCase(trimmedEmail))
            .forEach(otpRepo::delete);

        // Save new OTP
        PasswordResetOtpEntity otpEntity = new PasswordResetOtpEntity();
        otpEntity.setEmail(trimmedEmail);
        otpEntity.setOtp(otp);
        otpEntity.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        otpEntity.setVerified(false);
        otpRepo.save(otpEntity);

        // Send OTP via SMTP
        smtpService.sendResetPasswordOtp(trimmedEmail, otp, user.getTenantId(), user.getBranchId());
    }

    @Transactional
    public void verifyOtp(String email, String otp) {
        String trimmedEmail = email.trim().toLowerCase();
        PasswordResetOtpEntity otpEntity = otpRepo.findFirstByEmailAndOtpOrderByCreatedAtDesc(trimmedEmail, otp)
            .orElseThrow(() -> new BusinessRuleViolationException("Invalid or incorrect OTP"));

        if (otpEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleViolationException("OTP has expired. Please request a new one.");
        }

        otpEntity.setVerified(true);
        otpRepo.save(otpEntity);
    }

    @Transactional
    public void resetPassword(String email, String otp, String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessRuleViolationException("Password cannot be empty");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessRuleViolationException("Passwords do not match");
        }

        String trimmedEmail = email.trim().toLowerCase();
        PasswordResetOtpEntity otpEntity = otpRepo.findFirstByEmailAndOtpAndVerifiedTrueOrderByCreatedAtDesc(trimmedEmail, otp)
            .orElseThrow(() -> new BusinessRuleViolationException("OTP verification has expired or is invalid. Please verify again."));

        if (otpEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleViolationException("OTP verification session has expired. Please request a new OTP.");
        }

        String emailToken = searchTokenService.token(trimmedEmail);
        UserEntity user = userRepo.findByEmailToken(emailToken)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Update password hash using selective update to avoid merging/flushing lazy collections like roles
        userRepo.updatePassword(user.getId(), passwordEncoder.encode(newPassword), Instant.now());

        // Delete the used OTP record
        otpRepo.delete(otpEntity);
        log.info("Password successfully reset for user: {}", user.getUsername());
    }
}
