package com.hms.security.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Automatically runs the PII migration runner at application startup
 * to encrypt existing plaintext PII records and compute tokens.
 */
@Component
public class PiiMigrationStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PiiMigrationStartupRunner.class);
    private final PiiMigrationRunner migrationRunner;

    public PiiMigrationStartupRunner(PiiMigrationRunner migrationRunner) {
        this.migrationRunner = migrationRunner;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting PII migration startup runner...");
        try {
            migrationRunner.migratePii();
            log.info("PII migration startup runner completed successfully.");
        } catch (Exception e) {
            log.error("Failed to run PII migration: {}", e.getMessage(), e);
        }
    }
}
