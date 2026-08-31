package com.hms.application.compliance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the erasure registry actually covers the schema.
 *
 * <p>The registry's documented failure mode is silent: add a table holding
 * patient data, forget to register it, and erasure keeps reporting success while
 * leaving copies behind. Nobody notices, because the thing that went wrong is an
 * absence.
 *
 * <p>So the test reads the migration directory rather than trusting a list.
 * When a developer adds a {@code patient_id} column and no erasure strategy,
 * this fails and names the table.
 *
 * <p>This is the test that would have caught the WO-024 defects at the time they
 * were written: six stores registered against twenty-one carrying patient data.
 */
class ErasureRegistryCompletenessTest {

    private static final Path MIGRATIONS = Paths.get("src/main/resources/db/migration");

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE TABLE(?:\\s+IF NOT EXISTS)?\\s+([a-z_][a-z0-9_]*)\\s*\\((.*?)\\n\\);",
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern ADD_PATIENT_COLUMN = Pattern.compile(
        "ALTER TABLE\\s+([a-z_][a-z0-9_]*)\\s+ADD COLUMN(?:\\s+IF NOT EXISTS)?\\s+patient_id",
        Pattern.CASE_INSENSITIVE);

    /**
     * Tables carrying patient_id that are deliberately not erasure targets.
     *
     * <p>Each exclusion is a decision someone made, not an oversight, and the
     * reason is written down here so the next person does not have to guess.
     */
    private static final Set<String> DELIBERATELY_EXCLUDED = Set.of(
        // The request record itself. Erasing the record of an erasure request
        // would destroy the evidence that the patient exercised their right and
        // that the hospital honoured it.
        "erasure_requests"
    );

    private Set<String> tablesWithPatientId() throws IOException {
        Set<String> found = new TreeSet<>();
        if (!Files.exists(MIGRATIONS)) {
            return found;
        }
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(file);

                Matcher create = CREATE_TABLE.matcher(sql);
                while (create.find()) {
                    if (create.group(2).matches("(?s).*\\bpatient_id\\b.*")) {
                        found.add(create.group(1).toLowerCase());
                    }
                }
                Matcher alter = ADD_PATIENT_COLUMN.matcher(sql);
                while (alter.find()) {
                    found.add(alter.group(1).toLowerCase());
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("Every table carrying patient_id has an erasure strategy or a documented exclusion")
    void registryCoversSchema() throws IOException {
        Set<String> schema = tablesWithPatientId();
        assertThat(schema)
            .as("migration parsing produced nothing; the test would pass vacuously")
            .isNotEmpty();

        Set<String> registered = ErasureService.registeredStores();
        Set<String> missing = new LinkedHashSet<>(schema);
        missing.removeAll(registered);
        missing.removeAll(DELIBERATELY_EXCLUDED);

        assertThat(missing)
            .as("These tables hold patient data but ErasureService will never visit "
                + "them, so an erasure request would report success while leaving "
                + "copies behind. Add a strategy to ErasureService.TARGETS, or add "
                + "the table to DELIBERATELY_EXCLUDED with a reason.")
            .isEmpty();
    }

    /**
     * Registered stores that hold patient data under a column other than
     * {@code patient_id}, so the schema scan above will not see them.
     *
     * <p>Listing them explicitly rather than loosening the scan: each is a real
     * linkage someone had to go and find, and writing it down is what stops the
     * next person assuming the table was registered by mistake.
     */
    private static final Set<String> LINKED_BY_OTHER_COLUMN = Set.of(
        // Keyed by its own primary key `id`.
        "patients",
        // Records the patient as a generic target_entity_id, because the same
        // table logs tool calls against appointments and encounters too.
        "agent_tool_invocations"
    );

    @Test
    @DisplayName("The registry does not list tables that no longer exist")
    void registryHasNoStaleEntries() throws IOException {
        Set<String> schema = tablesWithPatientId();
        Set<String> stale = new LinkedHashSet<>(ErasureService.registeredStores());
        stale.removeAll(schema);
        stale.removeAll(LINKED_BY_OTHER_COLUMN);

        assertThat(stale)
            .as("A registered store that is not in the schema means the sweep will "
                + "throw and record FAILED for a table that does not exist")
            .isEmpty();
    }

    @Test
    @DisplayName("The primary patient record is registered, and swept last")
    void patientsIsRegisteredAndLast() {
        assertThat(ErasureService.registeredStores()).contains("patients");

        // Ordering matters: clearing the primary record before its derived copies
        // leaves orphans pointing at an anonymised patient.
        var ordered = ErasureService.TARGETS.keySet().stream().toList();
        assertThat(ordered.indexOf("patients"))
            .as("patients must be swept after the derived stores that reference it")
            .isGreaterThan(ordered.indexOf("abha_linkages"));
        assertThat(ordered.getLast())
            .as("consent_records is the audit trail and is retained last of all")
            .isEqualTo("consent_records");
    }

    @Test
    @DisplayName("V206 makes the two previously unreachable PHI stores reachable")
    void v206AddsPatientIdToAgentStores() throws IOException {
        Path v206 = MIGRATIONS.resolve("V206__erasure_reachability_and_lifecycle.sql");
        assertThat(Files.exists(v206)).isTrue();

        String sql = Files.readString(v206);
        // Before V206 neither table had a patient_id, so hitl_escalations was
        // anonymised by a subquery matching every run in the tenant, and
        // agent_idempotency_keys threw on a column that did not exist.
        assertThat(sql).contains("ALTER TABLE hitl_escalations");
        assertThat(sql).contains("ALTER TABLE agent_idempotency_keys");
    }

    @Test
    @DisplayName("No anonymisation SQL matches rows belonging to other patients")
    void anonymisationIsPatientScoped() throws IOException {
        // The original hitl_escalations statement read:
        //   WHERE tenant_id = :tid AND run_id IN
        //     (SELECT run_id FROM hitl_escalations WHERE tenant_id = :tid)
        // which selects every run in the tenant. One patient's erasure would
        // have wiped every other patient's transcript.
        String source = Files.readString(
            Paths.get("src/main/java/com/hms/application/compliance/ErasureService.java"));

        assertThat(source)
            .as("a self-referential subquery in an UPDATE means the predicate is "
                + "not actually narrowing to one patient")
            .doesNotContain("IN (SELECT run_id FROM hitl_escalations");
    }
}
