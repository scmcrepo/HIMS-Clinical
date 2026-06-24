package com.hms.application.user;

import com.hms.domain.smtp.model.SmtpConfig;
import com.hms.infrastructure.persistence.smtp.SmtpConfigRepository;
import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthForgotPasswordStartupRunner implements ApplicationRunner {

    private final SmtpConfigRepository smtpRepo;
    private final UserJpaRepository userRepo;
    private final PiiSearchTokenService searchTokenService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("AuthForgotPasswordStartupRunner: Running startup database updates...");

        // 1. Update the default SMTP configuration password if it is null
        smtpRepo.findAll().stream()
            .filter(c -> "scmcrepo@gmail.com".equalsIgnoreCase(c.getUsername()))
            .filter(c -> c.getPassword() == null || c.getPassword().isBlank())
            .findFirst()
            .ifPresent(c -> {
                c.setPassword("ywke bphk hgyv omkh");
                smtpRepo.save(c);
                log.info("Updated default SMTP config password securely.");
            });

        // 2. Update superadmin user with pasupathiselvam5@gmail.com and its search token
        userRepo.findByUsernameAndStatus("superadmin", (short) 1)
            .ifPresent(u -> {
                u.setEmail("pasupathiselvam5@gmail.com");
                u.setEmailToken(searchTokenService.token("pasupathiselvam5@gmail.com"));
                userRepo.save(u);
                log.info("Seeded superadmin user email 'pasupathiselvam5@gmail.com' and generated search token.");
            });
    }
}
