package com.hms.security.encryption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-001 and F-002 — the two pieces of unfinished business from WO-028.
 *
 * <p>Both defects were silent by nature, so both are asserted at the source
 * level. A behavioural test cannot catch either: a column that holds a mix of
 * ciphertext and plaintext writes and reads perfectly until it meets an old row,
 * and a query against a non-deterministically encrypted column compiles, runs,
 * and returns empty without erroring.
 */
class PiiBackfillConventionTest {

    private static final Path RUNNER = Paths.get(
        "src/main/java/com/hms/security/encryption/PiiMigrationRunner.java");
    private static final Path MIGRATIONS = Paths.get("src/main/resources/db/migration");

    private String runner() throws IOException {
        return Files.readString(RUNNER);
    }

    // ── F-002: every converted column has a backfill ──────────────────────

    /**
     * The columns V208 added converters to. Adding a converter without adding
     * the row here is the defect this test exists to catch: new writes encrypt,
     * old rows stay plaintext, and nothing complains until someone reads one.
     */
    private static final List<String> BACKFILLED_TABLES =
        List.of("visits", "nhcx_transactions", "pharmacy_sales");

    @Test
    @DisplayName("F-002: every table widened by V208 has a migration method")
    void v208TablesHaveBackfillMethods() throws IOException {
        String src = runner();
        assertThat(src).contains("migrateVisits");
        assertThat(src).contains("migrateNhcxTransactions");
        assertThat(src).contains("migratePharmacySales");
    }

    @Test
    @DisplayName("F-002: those methods are actually called from migratePii")
    void backfillMethodsAreWired() throws IOException {
        String src = runner();
        int entry = src.indexOf("public void migratePii()");
        int end = src.indexOf("PII Encryption Migration Complete");
        assertThat(entry).isGreaterThan(-1);

        String body = src.substring(entry, end);
        // An orphaned migration method is exactly as useless as no method —
        // this is the ErasureService.sweep() failure repeating.
        for (String call : List.of("migrateVisits()", "migrateNhcxTransactions()",
                                   "migratePharmacySales()")) {
            assertThat(body)
                .as("%s is defined but never called from migratePii", call)
                .contains(call);
        }
    }

    @Test
    @DisplayName("F-002: V212 adds the progress flag each backfill depends on")
    void v212AddsProgressFlags() throws IOException {
        Path v212 = MIGRATIONS.resolve("V212__pii_backfill_flags_and_otp_token.sql");
        assertThat(Files.exists(v212)).isTrue();

        String sql = Files.readString(v212);
        for (String table : BACKFILLED_TABLES) {
            // Without the flag the batch SELECT fails on a missing column and
            // the whole migration aborts at startup.
            assertThat(sql)
                .as("%s needs pii_encrypted before its backfill can run", table)
                .contains("ALTER TABLE " + table);
        }
        assertThat(sql).contains("pii_encrypted");
    }

    @Test
    @DisplayName("F-002: backfills encrypt only plaintext, so re-running is a no-op")
    void backfillIsIdempotent() throws IOException {
        String src = runner();
        int start = src.indexOf("private void migrateVisits()");
        int end = src.indexOf("private void migrateNhcxTransactions()");
        String body = src.substring(start, end);

        // encryptIfPlaintext checks looksEncrypted() first. Calling enc.encrypt()
        // directly would double-encrypt every row on a second run, which is
        // unrecoverable without the original key and the exact run order.
        assertThat(body).contains("encryptIfPlaintext");
        assertThat(body).doesNotContain("enc.encrypt(");
    }

    // ── F-001: the OTP email is encrypted and queried by token ────────────

    @Test
    @DisplayName("F-001: the OTP email column is encrypted")
    void otpEmailIsEncrypted() throws IOException {
        String entity = Files.readString(Paths.get(
            "src/main/java/com/hms/infrastructure/persistence/shared/PasswordResetOtpEntity.java"));

        assertThat(entity).contains("EncryptedStringConverter.class");
        assertThat(entity).contains("emailToken");
    }

    @Test
    @DisplayName("F-001: nothing queries the OTP table by email")
    void otpIsNotQueriedByEmail() throws IOException {
        String repo = Files.readString(Paths.get(
            "src/main/java/com/hms/infrastructure/persistence/shared/"
            + "PasswordResetOtpJpaRepository.java"));

        // A findByEmail against a non-deterministically encrypted column
        // compiles, runs, and silently matches nothing — password reset breaks
        // for every user with no error anywhere. This is the exact reason the
        // WO-028 attempt at encrypting this column had to be reverted.
        // Matches a declaration, not the docstring above it that explains why
        // the old method was removed. Asserting on the bare name would fail on
        // the explanation, which would be a test punishing documentation.
        assertThat(repo)
            .as("query by emailToken, never by email")
            .doesNotContain("Optional<PasswordResetOtpEntity> findFirstByEmailAndOtp");
        assertThat(repo).contains("findFirstByEmailTokenAndOtp");
    }

    @Test
    @DisplayName("F-001: the reset flow no longer loads every OTP row to filter in Java")
    void resetFlowDoesNotScanTheTable() throws IOException {
        String service = Files.readString(Paths.get(
            "src/main/java/com/hms/application/user/AuthForgotPasswordService.java"));

        // The old cleanup was otpRepo.findAll().stream().filter(...). Already
        // wasteful; once the email column became encrypted it would also have
        // decrypted every row in the table on every password-reset request.
        assertThat(service).doesNotContain("otpRepo.findAll()");
        assertThat(service).contains("deleteByEmailToken");
    }

    @Test
    @DisplayName("F-001: V212 adds the token column and clears the short-lived rows")
    void v212PreparesTheOtpTable() throws IOException {
        String sql = Files.readString(
            MIGRATIONS.resolve("V212__pii_backfill_flags_and_otp_token.sql"));

        assertThat(sql).contains("email_token");
        // The rows have a five-minute TTL. Deleting them avoids a half-migrated
        // authentication table, which would lock users out of their own accounts.
        assertThat(sql).contains("DELETE FROM password_reset_otp");
    }
}
