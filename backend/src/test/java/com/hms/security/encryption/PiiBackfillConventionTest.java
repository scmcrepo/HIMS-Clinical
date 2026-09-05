package com.hms.security.encryption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // ── U-004: the tables no entity maps ──────────────────────────────────

    /**
     * Tables that exist in the schema but are mapped by no {@code @Entity}.
     *
     * <h2>Why this list exists</h2>
     * {@code patient_pediatric} held plaintext children's growth-chart data from
     * V010 until V219. V214 encrypted {@code patients.pediatric_data} and stated
     * in its own header that it was the last plaintext copy of paediatric data.
     * It was wrong, and stayed wrong through four subsequent reviews.
     *
     * <p>The reason is this list. Every encryption audit done on this codebase
     * worked by enumerating entities and checking their converters. A table with
     * no entity is invisible to that method — it cannot appear in an entity scan,
     * a converter registry, or a mapping check. {@code patient_pediatric} showed
     * up in exactly two places in the whole codebase, both times as a string
     * inside a list.
     *
     * <p>So the control is not "check the entities harder". It is to keep the set
     * of unmapped tables enumerated, so that adding one is a decision somebody
     * makes rather than a gap nobody sees.
     *
     * <h2>Two entries were live exposures, now addressed (U-005)</h2>
     * <ul>
     *   <li>{@code sms_logs} — {@code to_number} and {@code message_body}: a
     *       patient's phone number and the content of messages about their care.
     *       V220 encrypts both and adds {@code to_number_token}; it is now in
     *       {@code ErasureService.TARGETS} as DELETE.</li>
     *   <li>{@code template_data} — {@code content}: clinical template content.
     *       V220 encrypts it; registered as RETAIN, matching its parent
     *       {@code clinical_encounters}.</li>
     * </ul>
     *
     * <p>Worth remembering why the erasure-coverage check missed both: it
     * enumerated tables carrying a {@code patient_id} column and reported 24 of
     * 25 covered. Neither of these has one — they reach a patient through a phone
     * number and an encounter. A coverage check keyed on a column name gave a
     * clean bill of health twice while both tables sat outside the registry.
     *
     * <p>They stay listed here because they still have no entity. That is the
     * property this list tracks, and it is the property that hid them.
     */
    private static final List<String> TABLES_WITH_NO_ENTITY = List.of(
        "agent_rollout", "bed_transfers", "bill_audit", "charge_line_modifications",
        "charge_package_excludes", "charge_package_includes", "diagnostic_departments",
        "diagnostic_template_lines", "discount_adjustments", "hospital_profile",
        "line_item_discounts", "patient_categories", "patient_pediatric",
        "pending_receipts", "pharmacy_returns", "print_data_queries",
        "replenishment_request_lines", "replenishment_requests", "sequence_numbers",
        "sms_logs", "sms_templates", "template_data", "user_account_units");

    @Test
    @DisplayName("U-005: sms_logs and template_data are erasable and backfilled")
    void smsLogsAndTemplateDataAreCovered() throws IOException {
        String runner = runner();
        assertThat(runner).contains("migrateSmsLogs");
        assertThat(runner).contains("migrateTemplateData");
        assertThat(runner.indexOf("migrateSmsLogs();")).isGreaterThan(-1);
        assertThat(runner.indexOf("migrateTemplateData();")).isGreaterThan(-1);

        String erasure = Files.readString(Paths.get(
            "src/main/java/com/hms/application/compliance/ErasureService.java"));

        // The encryption was never the main point. Before U-005 a patient could
        // exercise the s. 12 right, receive a receipt saying it completed, and
        // still have their number and the text of messages about their treatment
        // sitting in sms_logs.
        assertThat(erasure)
            .as("sms_logs must be in the erasure registry, not merely encrypted")
            .contains("TARGETS.put(\"sms_logs\"");
        assertThat(erasure).contains("TARGETS.put(\"template_data\"");

        // Ordering is load-bearing: anonymising patients nulls
        // contact_number_token, which is the only route to these rows.
        assertThat(erasure.indexOf("TARGETS.put(\"sms_logs\""))
            .as("sms_logs must be erased before patients, or its rows become "
                + "permanently unreachable")
            .isLessThan(erasure.indexOf("TARGETS.put(\"patients\""));
    }

    @Test
    @DisplayName("U-004: patient_pediatric has a backfill method")
    void patientPediatricHasABackfill() throws IOException {
        String src = runner();

        assertThat(src)
            .as("V219 converts the column; without this method the rows stay "
                + "plaintext children's health data and nothing says so")
            .contains("migratePatientPediatric");

        // Called, not merely defined. A private method nothing invokes is the
        // same defect with extra steps.
        assertThat(src.indexOf("migratePatientPediatric();"))
            .as("the backfill must be wired into migratePii()")
            .isGreaterThan(-1);
    }

    @Test
    @DisplayName("U-004: V219 converts the column and adds a backfill flag")
    void v219PreparesThePediatricTable() throws IOException {
        String sql = Files.readString(
            MIGRATIONS.resolve("V219__encrypt_patient_pediatric.sql"));

        assertThat(sql).contains("ALTER COLUMN pediatric_data TYPE TEXT");
        assertThat(sql).contains("pii_encrypted");
        // Ciphertext is Base64, not a JSON document. Leaving the column JSONB
        // would either fail on write or coerce the value into a JSON string.
        assertThat(sql).doesNotContain("TYPE JSONB");
    }

    @Test
    @DisplayName("U-004: the set of tables with no entity does not grow silently")
    void unmappedTableListIsARatchet() throws IOException {
        // Table names only. This deliberately does NOT parse columns — an earlier
        // attempt at column-level parsing produced twelve false positives because
        // V113-V118 add columns through EXECUTE format() loops. CREATE TABLE names
        // are unambiguous in a way column lists are not.
        List<String> created = new ArrayList<>();
        try (var files = Files.list(MIGRATIONS)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                Matcher m = CREATE_TABLE.matcher(Files.readString(p));
                while (m.find()) {
                    created.add(m.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }

        String java = allEntitySources();
        List<String> unmapped = created.stream()
            .distinct()
            .filter(t -> !java.contains("\"" + t + "\""))
            .filter(t -> !DROPPED_TABLES.contains(t))
            .sorted()
            .toList();

        assertThat(unmapped)
            .as("A new table with no entity is invisible to every entity-based "
                + "review, which is exactly how patient_pediatric kept children's "
                + "health data in the clear for eighteen migrations. If you added "
                + "one deliberately, add it to TABLES_WITH_NO_ENTITY and say in "
                + "the work order how it will be found by the next audit.")
            .isSubsetOf(TABLES_WITH_NO_ENTITY);
    }

    /**
     * Created by one migration and dropped by a later one, so absent from the
     * live schema. All four were retired by V046.
     *
     * <p>{@code areas} is here for a second reason worth remembering: the table
     * was dropped but {@code AreaEntity}, {@code AreaJpaRepository} and
     * {@code AreaController} survived it, so the endpoint returned a 500 for
     * every request until WO-032 deleted them. A dropped table and a live entity
     * is the mirror image of the defect this test class is about.
     */
    private static final List<String> DROPPED_TABLES =
        List.of("areas", "clinical_codes", "order_sets", "order_set_items");

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE TABLE(?:\\s+IF NOT EXISTS)?\\s+([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE);

    private String allEntitySources() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (var paths = Files.walk(Paths.get("src/main/java"))) {
            for (Path p : paths.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                if (src.contains("@Table(") || src.contains("@JoinTable(")) {
                    sb.append(src);
                }
            }
        }
        return sb.toString();
    }
}
