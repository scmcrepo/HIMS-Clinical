package com.hms.application.user;

import com.hms.application.smtp.SmtpConfigService;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.shared.PasswordResetOtpEntity;
import com.hms.infrastructure.persistence.shared.PasswordResetOtpJpaRepository;
import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthForgotPasswordServiceTest {

    @Mock private UserJpaRepository userRepo;
    @Mock private PasswordResetOtpJpaRepository otpRepo;
    @Mock private SmtpConfigService smtpService;
    @Mock private PiiSearchTokenService searchTokenService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthForgotPasswordService authService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setTenantId(UUID.randomUUID());
    }

    @Test
    void requestForgotPasswordOtp_ShouldGenerateAndSendOtp() {
        when(searchTokenService.token("test@test.com")).thenReturn("token123");
        when(userRepo.findByEmailToken("token123")).thenReturn(Optional.of(user));
        
        authService.requestForgotPasswordOtp("test@test.com");
        
        verify(otpRepo).save(any(PasswordResetOtpEntity.class));
        verify(smtpService).sendResetPasswordOtp(eq("test@test.com"), anyString(), eq(user.getTenantId()), any());
    }

    @Test
    void verifyOtp_ShouldSetVerifiedTrue() {
        PasswordResetOtpEntity otp = new PasswordResetOtpEntity();
        otp.setEmail("test@test.com");
        otp.setOtp("123456");
        otp.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        
        when(otpRepo.findFirstByEmailAndOtpOrderByCreatedAtDesc("test@test.com", "123456")).thenReturn(Optional.of(otp));
        
        authService.verifyOtp("test@test.com", "123456");
        
        assertTrue(otp.isVerified());
        verify(otpRepo).save(otp);
    }

    @Test
    void verifyOtp_ShouldThrowException_WhenExpired() {
        PasswordResetOtpEntity otp = new PasswordResetOtpEntity();
        otp.setEmail("test@test.com");
        otp.setOtp("123456");
        otp.setExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES)); // Expired
        
        when(otpRepo.findFirstByEmailAndOtpOrderByCreatedAtDesc("test@test.com", "123456")).thenReturn(Optional.of(otp));
        
        assertThrows(BusinessRuleViolationException.class, () -> authService.verifyOtp("test@test.com", "123456"));
    }

    @Test
    void resetPassword_ShouldUpdatePasswordHash() {
        PasswordResetOtpEntity otp = new PasswordResetOtpEntity();
        otp.setEmail("test@test.com");
        otp.setOtp("123456");
        otp.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        otp.setVerified(true);
        
        when(otpRepo.findFirstByEmailAndOtpAndVerifiedTrueOrderByCreatedAtDesc("test@test.com", "123456")).thenReturn(Optional.of(otp));
        when(searchTokenService.token("test@test.com")).thenReturn("token123");
        when(userRepo.findByEmailToken("token123")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("hashedpass");
        
        authService.resetPassword("test@test.com", "123456", "newpass", "newpass");
        
        verify(userRepo).updatePassword(eq(user.getId()), eq("hashedpass"), any(Instant.class));
        verify(otpRepo).delete(otp);
    }
}
