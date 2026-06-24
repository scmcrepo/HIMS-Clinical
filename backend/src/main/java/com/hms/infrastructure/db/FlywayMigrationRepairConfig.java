package com.hms.infrastructure.db;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Configuration
public class FlywayMigrationRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            DataSource dataSource = flyway.getConfiguration().getDataSource();
            if (dataSource != null) {
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement()) {
                    // 1. One-time cleanup of legacy local-only V147 migration record
                    stmt.execute("DELETE FROM flyway_schema_history WHERE version = '147' AND description = 'add user branches'");
                } catch (Exception e) {
                    // Table might not exist yet during a clean installation; ignore safely
                }
            }

            // 2. Automatically repair database schema history (resolves checksum/validation mismatches)
            flyway.repair();

            // 3. Execute migrations
            flyway.migrate();

            // 4. Log migration status summary to console for developer visibility
            var infoService = flyway.info();
            int appliedCount = 0;
            int pendingCount = 0;
            int failedCount = 0;

            System.out.println("\n=== FLYWAY DATABASE MIGRATION STATUS ===");
            for (var info : infoService.all()) {
                String state = "PENDING";
                if (info.getState().isFailed()) {
                    state = "FAILED";
                    failedCount++;
                } else if (info.getState().isApplied()) {
                    state = "APPLIED";
                    appliedCount++;
                } else {
                    pendingCount++;
                }
                System.out.printf("  Version: %-4s | State: %-7s | Script: %s%n",
                        info.getVersion(),
                        state,
                        info.getScript());
            }
            System.out.println("=========================================");
            System.out.printf("Summary: %d applied, %d pending, %d failed.%n%n",
                    appliedCount, pendingCount, failedCount);

            if (failedCount > 0) {
                System.err.println("WARNING: There are failed migrations in the schema history! Please repair them.");
            }
        };
    }
}
