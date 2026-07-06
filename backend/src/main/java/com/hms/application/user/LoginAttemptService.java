package com.hms.application.user;

import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks consecutive failed login attempts per user.
 * After {@link #MAX_ATTEMPTS} wrong passwords the account is locked
 * ({@code status=0, account_locked=true}). Only an admin reactivation
 * (via UserManagementService) resets the counter back to 0.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;

    private final UserJpaRepository userRepo;

    /**
     * Called after a wrong-password attempt. Increments the counter and locks
     * the account when the threshold is reached.
     *
     * @return the number of remaining attempts (0 means the account was just locked)
     */
    @Transactional
    public int handleFailedAttempt(UserEntity user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        int remaining = MAX_ATTEMPTS - user.getFailedLoginAttempts();

        if (remaining <= 0) {
            user.setStatus((short) 0);
            user.setAccountLocked(true);
            log.warn("User [{}] locked after {} consecutive failed login attempts",
                    user.getUsername(), MAX_ATTEMPTS);
            remaining = 0;
        } else {
            log.info("Failed login attempt for user [{}]: {} of {} used",
                    user.getUsername(), user.getFailedLoginAttempts(), MAX_ATTEMPTS);
        }
        
        userRepo.save(user);
        return remaining;
    }

    /**
     * Called after a successful password verification. Resets the counter to 0
     * so the user gets a clean slate.
     */
    @Transactional
    public void handleSuccessfulLogin(UserEntity user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            userRepo.save(user);
            log.info("Reset failed login attempts for user [{}]", user.getUsername());
        }
    }

    /**
     * Returns how many attempts the user has left before lockout.
     */
    public int getRemainingAttempts(UserEntity user) {
        return Math.max(0, MAX_ATTEMPTS - user.getFailedLoginAttempts());
    }
}
